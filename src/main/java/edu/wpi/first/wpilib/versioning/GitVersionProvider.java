package edu.wpi.first.wpilib.versioning;

import org.ajoberstar.grgit.Grgit;
import org.ajoberstar.grgit.Tag;
import org.gradle.api.Project;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class GitVersionProvider implements WPILibVersionProvider {
    // A valid version string from git describe takes the form of v1.0.0-beta-2-1-gbd478ea, where
    // everything after the v1.0.0 part is optional. Below, I break each piece out in it's own string, and then put
    // the final regex together

    // This is the only required part of the version. This captures a 'v', then 3 numbers separated by '.'.
    // This introduces a capturing group for the major version number called 'major' and the remainder called 'minor'.
    static final String majorVersion = "major";
    static final String minorVersion = "minor";
    private static final String mainVersionRegex = "v(?<" + majorVersion + ">[0-9]+)(?<" + minorVersion + ">\\.[0-9]+\\.[0-9]+)";

    public static String getMainVersionRegex() {
        return mainVersionRegex;
    }

    // This is the alpha/beta/rc qualifier. It is a '-', followed by 'alpha', 'beta', or 'rc', followed by another '-', finally
    // followed by the alpha/beta/rc number. This introduces a capturing group for the qualifier number, called 'qualifier'.
    static final String qualifier = "qualifier";
    private static final String qualifierRegex = "-(?<" + qualifier + ">(alpha|beta|rc)-[0-9]+)";

    public static String getQualifierRegex() {
        return qualifierRegex;
    }

    // This is the number of commits since the last annotated tag, and the commit hash of the latest commit. This is
    // a '-', followed by a number, the number of commits, followed by a '-', followed by a 'g', followed by the git
    // abbreviation of the hash of the current commit. This introduces 2 capturing groups, one for the number of
    // commits, 'commits', and one for the commit hash, 'sha'.
    static final String commits = "commits";
    static final String sha = "sha";
    private static final String commitsRegex = "-(?<" + commits + ">[0-9]+)-(?<" + sha + ">g[a-f0-9]+)";

    public static String getCommitsRegex() {
        return commitsRegex;
    }

    // This is the final regex. mainVersion is the only element that is required. Each subpart, if it shows up, must
    // show up in full. A fully expanded version of the regex is copied below:
    // ^v(?<major>[0-9]+)(?<minor>\.[0-9]+\.[0-9]+)(-(?<qualifier>(alpha|beta|rc)-[0-9]+))?(-(?<commits>[0-9]+)-(?<sha>g[a-f0-9]+))?$
    static final Pattern versionRegex = Pattern.compile( "^" + mainVersionRegex + "(" + qualifierRegex + ")?(" + commitsRegex + ")?$");

    public static Pattern getVersionRegex() {
        return versionRegex;
    }

    private File getGitDir(File currentDir) {
        if (new File(currentDir, ".git").exists())
            return currentDir;

        if (currentDir.getParentFile() == null)
            return null;

        return getGitDir(currentDir.getParentFile());
    }

    public String getVersion(WPILibVersioningPluginExtension extension, Project project, boolean allTags) {
        String tag = null;
        boolean isDirty = false;

        String ciEnv = System.getenv("CI");

        // Check to see if we are running on CI
        if (ciEnv != null && ciEnv.equals("true")) {
            String githubRef = System.getenv("GITHUB_REF");
            if (extension.isReleaseMode() && githubRef != null && githubRef.startsWith("refs/tags/")) {
                tag = githubRef.replace("refs/tags/", "");

                System.out.println("GitHub provided a tag via the GITHUB_REF environment variable: " + githubRef);
            }
        }

        if (tag == null) {
            // We are not on CI or CI failed to provide a tag, need to use git
            // Determine the version number and make it available on our plugin extension
            File gitDir = getGitDir(project.getRootProject().getRootDir());
            // If no git directory was found, print a message to the console and return an empty string
            if (gitDir == null) {
                System.out.println("No .git was found in " + project.getRootProject().getRootDir() + ", or any parent directories of that directory.");
                System.out.println("No version number generated.");
                return "";
            }

            Map<String, Object> openArgs = Map.of("currentDir", (Object)gitDir.getAbsolutePath());
            Map<String, Object> describeArgs = Map.of("tags", (Object)allTags);

            Grgit git = Grgit.open(openArgs);
            // Get the tag given by describe
            tag = git.describe(describeArgs);

            // Get a list of all tags

            List<Tag> tags = git.getTag().list();

            Tag describeTag = null;

            if (tag != null && tags != null) {
                // Find tag hash that starts with describe
                for (Tag tg : tags) {
                    if (tag.startsWith(tg.getName())) {
                        describeTag = tg;
                        break;
                    }
                }

            }

            // If we found the tag matching describe
            if (describeTag != null) {
                String commitId = describeTag.getCommit().getId();

                Tag newestTag = tags.stream()
                    .filter(x -> x.getCommit().getId().equals(commitId))
                    .sorted((x, y) -> x.getDateTime().compareTo(y.getDateTime()))
                    .reduce(null, (x, y) -> y);

                // Replace describe tag with newest
                tag = tag.replace(describeTag.getName(), newestTag.getName());
            }

            isDirty = !git.status().isClean();
        }

        Matcher match = versionRegex.matcher(tag);

        //def match = tag =~ versionRegex
        if (!match.matches()) {
            System.out.println("Tag is " + tag + ". This does not match the expected version number pattern.");
            System.out.println("No version number was generated.");
            return "";
        }

        StringBuilder versionBuilder = new StringBuilder();

        versionBuilder.append(match.group(majorVersion));

        // If this is a local build, we'll prepend 424242 to the minor version. This means that locally built versions will
        // always resolve first in the tree
        if (!extension.isBuildServerMode()) {
            versionBuilder.append(".424242");
        }

        versionBuilder.append(match.group(minorVersion));

        if (match.group(qualifier) != null) {
            versionBuilder.append('-').append(match.group(qualifier));
        }

        // For official builds, stop here. No date or repo status accounted for. Otherwise, keep appending new
        // version elements
        if (extension.isReleaseMode())
            return versionBuilder.toString();

        // For jenkins builds, do not append the date
        // This simplifies jenkins publishing because multiple commit builds can't happen.
        if (!extension.isBuildServerMode()) {
            versionBuilder.append('-').append(extension.getTime().get());
        }

        if (match.group(commits) != null && match.group(sha) != null)
            versionBuilder.append('-').append(match.group(commits))
                    .append('-').append(match.group(sha));

        if (isDirty)
            versionBuilder.append("-dirty");

        return versionBuilder.toString();
    }

}
