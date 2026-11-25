package org.wpilib.versioning;

import org.eclipse.jgit.api.DescribeCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.errors.InvalidPatternException;
import org.gradle.api.Project;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class GitVersionProvider implements WPILibVersionProvider {
    // A valid version string from git describe takes the form of
    // v2027.1-1.0-1-gbd478ea, where everything after v2027.1-1.0 is optional.
    // The required version format is:
    // v<year>.<imageBreaking>-<WPILibBreaking>.<WPILibNonBreaking>
    // Breaking numbers start at 1 and nonbreaking numbers start at 0.
    // Legacy YEAR.MAJOR.MINOR tags are migrated to YEAR.0-MAJOR.MINOR with a warning;
    // any alpha/beta/rc qualifier is ignored.

    static final String year = "year";
    static final String imageBreaking = "imageBreaking";
    static final String WPILibBreaking = "WPILibBreaking";
    static final String WPILibNonBreaking = "WPILibNonBreaking";

    private static final String mainVersionRegex = "v(?<" + year + ">[1-9][0-9]{3})"
            + "\\.(?<" + imageBreaking + ">[1-9][0-9]*)"
            + "-(?<" + WPILibBreaking + ">[1-9][0-9]*)"
            + "\\.(?<" + WPILibNonBreaking + ">(?:0|[1-9][0-9]*))";

    public static String getMainVersionRegex() {
        return mainVersionRegex;
    }

    // This is the number of commits since the last annotated tag, and the commit
    // hash of the latest commit. This introduces two capturing groups, one for
    // the number of commits, 'commits', and one for the commit hash, 'sha'.
    static final String commits = "commits";
    static final String sha = "sha";
    private static final String commitsRegex = "-(?<" + commits + ">[0-9]+)-(?<" + sha + ">g[a-f0-9]+)";

    public static String getCommitsRegex() {
        return commitsRegex;
    }

    // Legacy WPILib tags use YEAR.MAJOR.MINOR. They are migrated to
    // YEAR.0-MAJOR.MINOR so existing tags keep working until a new-format tag is created.
    static final String legacyMajor = "major";
    static final String legacyMinor = "minor";
    static final String legacyQualifier = "legacyQualifier";
    private static final String legacyMainVersionRegex = "v(?<" + year + ">[1-9][0-9]{3})"
            + "\\.(?<" + legacyMajor + ">(?:0|[1-9][0-9]*))"
            + "\\.(?<" + legacyMinor + ">(?:0|[1-9][0-9]*))";
    private static final String legacyQualifierRegex = "-(?<" + legacyQualifier + ">(alpha|beta|rc)-[0-9]+)";

    static final Pattern legacyVersionRegex = Pattern
            .compile("^" + legacyMainVersionRegex + "(" + legacyQualifierRegex + ")?" + "(" + commitsRegex + ")?$");

    public static Pattern getLegacyVersionRegex() {
        return legacyVersionRegex;
    }

    // This is the final regex. The main version is the only required element.
    // A fully expanded version of the regex is copied below:
    // ^v(?<year>[1-9][0-9]{3})\.(?<imageBreaking>[1-9][0-9]*)-(?<WPILibBreaking>[1-9][0-9]*)\.(?<WPILibNonBreaking>(?:0|[1-9][0-9]*))(-(?<commits>[0-9]+)-(?<sha>g[a-f0-9]+))?$
    static final Pattern versionRegex = Pattern
            .compile("^" + mainVersionRegex + "(" + commitsRegex + ")?$");

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

    public String getVersion(WPILibVersioningPluginExtension extension, Project project, boolean allTags,
            List<String> matchGlobs) {
        try {
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
                // If no git directory was found, print a message to the console and return an
                // empty string
                if (gitDir == null) {
                    System.out.println("No .git was found in " + project.getRootProject().getRootDir()
                            + ", or any parent directories of that directory.");
                    System.out.println("No version number generated.");
                    return "";
                }

                Git git = Git.open(gitDir);
                DescribeCommand describe = git.describe();
                describe.setAlways(false);
                describe.setLong(false);
                describe.setTags(allTags);
                describe.setMatch(matchGlobs.toArray(String[]::new));
                tag = describe.call();

                List<GitTag> tags = git.tagList().call().stream().map(x -> GitTag.fromRef(git, x)).filter(x -> x != null).toList();

                GitTag describeTag = null;

                if (tag != null && tags != null) {
                    // Find tag hash that starts with describe
                    for (GitTag tg : tags) {
                        if (tag.startsWith(tg.getName())) {
                            describeTag = tg;
                            break;
                        }
                    }

                }

                // If we found the tag matching describe
                if (describeTag != null) {

                    String commitId = describeTag.getCommitId();

                    GitTag newestTag = tags.stream()
                            .filter(x -> x.getCommitId().equals(commitId))
                            .sorted((x, y) -> x.getDateTime().compareTo(y.getDateTime()))
                            .reduce(null, (x, y) -> y);

                    // Replace describe tag with newest
                    tag = tag.replace(describeTag.getName(), newestTag.getName());
                }

                isDirty = !git.status().call().isClean();
            }

            Matcher match = null;
            boolean isLegacy = false;
            if (tag != null) {
                match = versionRegex.matcher(tag);
                if (!match.matches()) {
                    Matcher legacyMatch = legacyVersionRegex.matcher(tag);
                    if (legacyMatch.matches()) {
                        match = legacyMatch;
                        isLegacy = true;
                    } else {
                        match = null;
                    }
                }
            }

            if (match == null) {
                System.out.println("Tag is " + tag + ". This does not match the expected version number pattern.");
                System.out.println("No version number was generated.");
                return "";
            }

            String resolvedYear = match.group(year);
            String resolvedImageBreaking = isLegacy ? "0" : match.group(imageBreaking);
            String resolvedWPILibBreaking = isLegacy ? match.group(legacyMajor) : match.group(WPILibBreaking);
            String resolvedWPILibNonBreaking = isLegacy ? match.group(legacyMinor) : match.group(WPILibNonBreaking);

            StringBuilder versionBuilder = new StringBuilder();

            versionBuilder.append(resolvedYear)
                    .append('.').append(resolvedImageBreaking);

            // If this is a local build, insert 424242 after the image-breaking separator.
            // This means that locally built versions will always resolve first in the tree.
            if (!extension.isBuildServerMode()) {
                versionBuilder.append("-424242.");
            } else {
                versionBuilder.append('-');
            }

            versionBuilder.append(resolvedWPILibBreaking)
                    .append('.').append(resolvedWPILibNonBreaking);

            // For official builds, stop here. No date or repo status accounted for.
            // Otherwise, keep appending new version elements.
            if (!extension.isReleaseMode()) {
                // Build-server builds omit the date.
                // This simplifies publishing because multiple commit builds can't happen.
                if (!extension.isBuildServerMode()) {
                    versionBuilder.append('-').append(extension.getTime().get());
                }

                if (match.group(commits) != null && match.group(sha) != null)
                    versionBuilder.append('-').append(match.group(commits))
                            .append('-').append(match.group(sha));

                if (isDirty)
                    versionBuilder.append("-dirty");
            }

            String generatedVersion = versionBuilder.toString();

            if (isLegacy) {
                System.out.println("Warning: Tag " + tag + " uses the old YEAR.MAJOR.MINOR format. "
                        + "Migration to the new tag format "
                        + "v<year>.<imageBreaking>-<WPILibBreaking>.<WPILibNonBreaking> is needed. "
                        + "Generated version: " + generatedVersion);
            }

            return generatedVersion;
        } catch (IOException | GitAPIException | InvalidPatternException e) {
            throw new RuntimeException("Error determining git version", e);
        }
    }

}
