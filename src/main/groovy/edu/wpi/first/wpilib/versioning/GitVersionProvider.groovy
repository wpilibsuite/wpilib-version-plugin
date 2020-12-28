package edu.wpi.first.wpilib.versioning

import groovy.transform.CompileStatic
import org.ajoberstar.grgit.Grgit
import org.ajoberstar.grgit.Tag
import org.ajoberstar.grgit.operation.OpenOp
import org.gradle.api.Project

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.regex.Pattern

@CompileStatic
class GitVersionProvider implements WPILibVersionProvider {
    // A valid version string from git describe takes the form of v1.0.0-beta-2-1-gbd478ea, where
    // everything after the v1.0.0 part is optional. Below, I break each piece out in it's own string, and then put
    // the final regex together

    // This is the only required part of the version. This captures a 'v', then 3 numbers separated by '.'.
    // This introduces a capturing group for the major version number called 'major' and the remainder called 'minor'.
    static final String majorVersion = 'major'
    static final String minorVersion = 'minor'
    private static final String mainVersionRegex = "v(?<$majorVersion>[0-9]+)(?<$minorVersion>\\.[0-9]+\\.[0-9]+)"

    // This is the alpha/beta/rc qualifier. It is a '-', followed by 'alpha', 'beta', or 'rc', followed by another '-', finally
    // followed by the alpha/beta/rc number. This introduces a capturing group for the qualifier number, called 'qualifier'.
    static final String qualifier = 'qualifier'
    private static final String qualifierRegex = "-(?<$qualifier>(alpha|beta|rc)-[0-9]+)"

    // This is the number of commits since the last annotated tag, and the commit hash of the latest commit. This is
    // a '-', followed by a number, the number of commits, followed by a '-', followed by a 'g', followed by the git
    // abbreviation of the hash of the current commit. This introduces 2 capturing groups, one for the number of
    // commits, 'commits', and one for the commit hash, 'sha'.
    static final String commits = 'commits'
    static final String sha = 'sha'
    private static final String commitsRegex = /-(?<$commits>[0-9]+)-(?<$sha>g[a-f0-9]+)/

    // This is the final regex. mainVersion is the only element that is required. Each subpart, if it shows up, must
    // show up in full. A fully expanded version of the regex is copied below:
    // ^v(?<version>[0-9]+\.[0-9]+\.[0-9]+)(-(?<qualifier>(alpha|beta|rc)-[0-9]+))?(-(?<commits>[0-9]+)-(?<sha>g[a-f0-9]+))?$
    static final Pattern versionRegex = ~"^$mainVersionRegex($qualifierRegex)?($commitsRegex)?\$"

    private File getGitDir(File currentDir) {
        if (new File(currentDir, '.git').exists())
            return currentDir

        if (currentDir.parentFile == null)
            return null

        return getGitDir(currentDir.parentFile)
    }

    public String getVersion(WPILibVersioningPluginExtension extension, Project project, boolean allTags) {
        String tag = null
        boolean isDirty = false;

        // Check to see if we are running on CI
        if (System.getenv("CI") == "true") {
            def githubRef = System.getenv("GITHUB_REF")
            if (extension.releaseMode && githubRef.startsWith("refs/tags/")) {
                tag = githubRef.replace("refs/tags/", "")

                println "GitHub provided a tag via the GITHUB_REF environment variable: $githubRef"
            }
        }

        if (tag == null) {
            // We are not on CI or CI failed to provide a tag, need to use git
            // Determine the version number and make it available on our plugin extension
            def gitDir = getGitDir(project.rootProject.rootDir)
            // If no git directory was found, print a message to the console and return an empty string
            if (gitDir == null) {
                println "No .git was found in $project.rootProject.rootDir, or any parent directories of that directory."
                println "No version number generated."
                return ''
            }

            Grgit git = Grgit.open(currentDir: (Object)gitDir.absolutePath)
            // Get the tag given by describe
            tag = git.describe(tags: (Object)allTags)

            // Get a list of all tags
            List<Tag> tags = git.tag.list()

            Tag describeTag = null

            if (tag != null && tags != null) {
                // Find tag hash that starts with describe
                tags.find { Tag tg ->
                    if (tag.startsWith(tg.name)) {
                        describeTag = tg
                        return true
                    }
                    return false
                }
            }

            // If we found the tag matching describe
            if (describeTag != null) {
                String commitId = describeTag.commit.id

                // Find all tags matching commit
                // Sort by date
                Tag newestTag = tags.findAll {
                    it.commit.id.equals(commitId)
                }.sort {
                    it.dateTime
                }.last()

                // Replace describe tag with newest
                tag = tag.replace(describeTag.name, newestTag.name)
            }

            isDirty = !git.status().isClean()
        }

        def match = tag =~ versionRegex
        if (!match.matches()) {
            println "Tag is $tag. This does not match the expected version number pattern."
            println "No version number was generated."
            return ''
        }

        def versionBuilder = new StringBuilder()

        versionBuilder.append(match.group(majorVersion))

        // If this is a local build, we'll prepend 424242 to the minor version. This means that locally built versions will
        // always resolve first in the tree
        if (!extension.buildServerMode) {
            versionBuilder.append('.424242')
        }

        versionBuilder.append(match.group(minorVersion))

        if (match.group(qualifier) != null) {
            versionBuilder.append('-').append(match.group(qualifier))
        }

        // For official builds, stop here. No date or repo status accounted for. Otherwise, keep appending new
        // version elements
        if (extension.releaseMode)
            return versionBuilder.toString()

        // For jenkins builds, do not append the date
        // This simplifies jenkins publishing because multiple commit builds can't happen.
        if (!extension.buildServerMode) {
            versionBuilder.append('-').append(extension.time.get())
        }

        if (match.group(commits) != null && match.group(sha) != null)
            versionBuilder.append('-').append(match.group(commits))
                    .append('-').append(match.group(sha))

        if (isDirty)
            versionBuilder.append('-dirty')

        return versionBuilder.toString()
    }

}
