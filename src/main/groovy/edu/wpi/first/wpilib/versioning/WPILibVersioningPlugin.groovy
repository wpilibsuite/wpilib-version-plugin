package edu.wpi.first.wpilib.versioning

import org.ajoberstar.grgit.Grgit
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.plugins.MavenPublishPlugin

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.regex.Pattern

/**
 * Determines the remote WPILib repository to use as well as determining the version of published artifacts. This is
 * determined based on git
 */
class WPILibVersioningPlugin implements Plugin<Project> {

    // A valid version string from git describe takes the form of v1.0.0-beta-2-1-gbd478ea, where
    // everything after the v1.0.0 part is optional. Below, I break each piece out in it's own string, and then put
    // the final regex together

    // This is the only required part of the version. This captures a 'v', then 3 numbers separated by '.'.
    // This introduces a capturing group for the main version number called 'version'.
    static final String mainVersion = 'version'
    private static final String mainVersionRegex = "v(?<$mainVersion>[0-9]+\\.[0-9]+\\.[0-9]+)"

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

    private String getVersion(WPILibVersioningPluginExtension extension, Project project) {
        // Determine the version number and make it available on our plugin extension
        def gitDir = getGitDir(project.rootProject.rootDir)
        // If no git directory was found, print a message to the console and return an empty string
        if (gitDir == null) {
            println "No .git was found in $project.rootProject.rootDir, or any parent directories of that directory."
            println "No version number generated."
            return ''
        }
        def git = Grgit.open(currentDir: gitDir.absolutePath)
        String tag = git.describe()
        boolean isDirty = !git.status().isClean()
        def match = tag =~ versionRegex
        if (!match.matches()) {
            println "Latest annotated tag is $tag. This does not match the expected version number pattern."
            println "No version number was generated."
            return ''
        }

        def versionBuilder = new StringBuilder()
        // If this is a local build, we'll prepend 424242 to the version. This means that locally built versions will
        // always resolve first in the tree
        if (!project.hasProperty('jenkinsBuild')) {
            versionBuilder.append('424242.')
        }

        versionBuilder.append(match.group(mainVersion))

        if (match.group(qualifier) != null) {
            versionBuilder.append('-').append(match.group(qualifier))
        }

        // For official builds, stop here. No date or repo status accounted for. Otherwise, keep appending new
        // version elements
        if (extension.releaseType == ReleaseType.OFFICIAL)
            return versionBuilder.toString()

        versionBuilder.append('-').append(extension.time)

        if (match.group(commits) != null && match.group(sha) != null)
            versionBuilder.append('-').append(match.group(commits))
                    .append('-').append(match.group(sha))

        if (isDirty)
            versionBuilder.append('-dirty')

        return versionBuilder.toString()
    }

    @Override
    void apply(Project project) {
        project.extensions.add("WPILibVersion", WPILibVersioningPluginExtension)
        def ext = (WPILibVersioningPluginExtension) project.extensions.getByName('WPILibVersion')

        if (project.hasProperty('releaseType'))
            ext.releaseType = ReleaseType.valueOf((String) project.property('releaseType'))

        if (ext.time == null || ext.time.empty) {
            def date = LocalDateTime.now()
            def formatter = DateTimeFormatter.ofPattern('yyyyMMddHHmmss')
            ext.time = date.format(formatter)
        }

        project.afterEvaluate { evProject ->
            def extension = (WPILibVersioningPluginExtension) evProject.extensions.getByName("WPILibVersion")
            def mavenRemoteBase = extension.remoteUrlBase
            def localMavenBase = extension.mavenLocalBase
            def mavenExtension = extension.releaseType == ReleaseType.DEV ? extension.devExtension : extension
                    .officialExtension
            extension.mavenRemoteUrl = extension.mavenRemoteUrl.isEmpty() ? "$mavenRemoteBase/$mavenExtension/" :
                    extension.mavenRemoteUrl
            extension.mavenLocalUrl = extension.mavenLocalUrl.isEmpty() ? "$localMavenBase/$mavenExtension/" :
                    extension.mavenLocalUrl

            if (extension.generateVersion)
                extension.version = getVersion(extension, evProject)

            evProject.allprojects.each { subproj ->
                def mavenExt = subproj.repositories
                mavenExt.maven {
                    it.url = extension.mavenLocalUrl
                }
                mavenExt.maven {
                    it.url = extension.mavenRemoteUrl
                }

                // If the specific subproject isn't publishing maven artifacts, then don't add publication urls
                if (subproj.plugins.hasPlugin(MavenPublishPlugin)) {
                    def publishingExt = (PublishingExtension) subproj.extensions.getByType(PublishingExtension)
                    publishingExt.repositories.maven {
                        it.url = extension.mavenLocalUrl
                    }
                }
            }
        }
    }
}

class WPILibVersioningPluginExtension {
    ReleaseType releaseType = ReleaseType.DEV
    String remoteUrlBase = 'http://first.wpi.edu/FRC/roborio/maven'
    String mavenLocalBase = "${System.getProperty('user.home')}/releases/maven"
    String devExtension = 'development'
    String officialExtension = 'release'
    String mavenLocalUrl = ''
    String mavenRemoteUrl = ''
    boolean generateVersion = true
    String version = ''
    String time
}

enum ReleaseType {
    OFFICIAL, DEV
}