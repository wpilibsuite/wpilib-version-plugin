package edu.wpi.first.wpilib.versioning

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.plugins.MavenPublishPlugin

/**
 * Determines the remote WPILib repository to use as well as determining the version of published artifacts. This is
 * determined based on git
 */
class WPILibVersioningPlugin implements Plugin<Project> {
    
    // A valid version string from git describe takes the form of v1.0.0-beta-2-1-gbd478ea-dirty, where
    // everything after the v1.0.0 part is optional. Below, I break each piece out in it's own string, and then put
    // the final regex together

    // This is the only required part of the version. This captures a 'v', then 3 numbers separated by '.'.
    // This introduces a capturing group for the main version number called 'version'.
    static final String mainVersion = /v(?<version>[0-9]+\.[0-9]+\.[0-9]+)/

    // This is the beta/rc qualifier. It is a '-', followed by either 'beta' or 'rc', followed by another '-', finally
    // followed by the beta/rc number. This introduces a capturing group for the qualifier number, called 'qualifier'.
    static final String qualifier = /-(?<qualifier>(beta|rc)-[0-9]+)/

    // This is the number of commits since the last annotated tag, and the commit hash of the latest commit. This is
    // a '-', followed by a number, the number of commits, followed by a '-', followed by a 'g', followed by the git
    // abbreviation of the hash of the current commit. This introduces 2 capturing groups, one for the number of
    // commits, 'commits', and one for the commit hash, 'sha'.
    static final String commits = /-(?<commits>[0-9]+)-(?<sha>g[a-f0-9]+)/

    // This is whether or not there are changes in any of the files tracked by git at the time of version number
    // generation. This is a '-', followed by the string 'dirty'. This introduces a capturing group, called 'dirty'.
    static final String dirty = /-(?<dirty>dirty)/

    // This is the final regex. mainVersion is the only element that is required. Each subpart, if it shows up, must
    // show up in full. A fully expanded version of the regex is copied below:
    // ^v(?<version>[0-9]+\.[0-9]+\.[0-9]+)(-(?<qualifier>(beta|rc)-[0-9]+))?(-(?<commits>[0-9]+)-(?<sha>g[a-f0-9]+))?(-(?<dirty>dirty))?$
    static final String versionRegex = "^$mainVersion($qualifier)?($commits)?($dirty)?\$"

    @Override
    void apply(Project project) {
        project.extensions.add("WPILibVersion", WPILibVersioningPluginExtension)

        project.afterEvaluate { evProject ->
            def extension = (WPILibVersioningPluginExtension) evProject.extensions.getByName("WPILibVersion")
            def mavenRemoteBase = extension.remoteUrlBase
            def localMavenBase = extension.mavenLocalBase
            def mavenExtension = extension.releaseType == ReleaseType.ALPHA ? extension.alphaExtension : extension
                    .releaseExtension
            extension.mavenRemoteUrl = extension.mavenRemoteUrl.isEmpty() ? "$mavenRemoteBase/$mavenExtension/" :
                    extension.mavenRemoteUrl
            extension.mavenLocalUrl = extension.mavenLocalUrl.isEmpty() ? "$localMavenBase/$mavenExtension/" :
                    extension.mavenLocalUrl

            // If not overridden, use the root of the root project as the location of git
            if (extension.repositoryRoot == null || extension.repositoryRoot.isEmpty()) {
                extension.repositoryRoot = evProject.rootProject.rootDir.absolutePath
            }

            evProject.allprojects.each { subproj ->
                def mavenExt = subproj.repositories
                mavenExt.maven {
                    it.url = extension.mavenLocalUrl
                }
                mavenExt.maven {
                    it.url = extension.mavenRemoteUrl
                }

                // If the specific subproject isn't publishing maven artifacts, then don't add publication urls
                if (project.plugins.hasPlugin(MavenPublishPlugin)) {
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
    String repositoryRoot
    ReleaseType releaseType
    String remoteUrlBase = "http://first.wpi.edu/FRC/roborio/maven"
    String mavenLocalBase = "${System.getProperty('user.home')}/releases/maven"
    String alphaExtension = "development"
    String releaseExtension = "release"
    String mavenLocalUrl = ""
    String mavenRemoteUrl = ""
}

enum ReleaseType {
    ALPHA, BETA, RC, RELEASE
}