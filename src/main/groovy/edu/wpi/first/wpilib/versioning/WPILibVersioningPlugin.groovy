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