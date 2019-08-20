package edu.wpi.first.wpilib.versioning

import groovy.transform.CompileStatic
import org.gradle.api.Plugin
import org.gradle.api.Project

/**
 * Determines the remote WPILib repository to use as well as determining the version of published artifacts. This is
 * determined based on git
 */
@CompileStatic
class WPILibVersioningPlugin implements Plugin<Project> {
    @Override
    void apply(Project project) {
        project.extensions.create("wpilibVersioning", WPILibVersioningPluginExtension, project, new GitVersionProvider())

    }
}
