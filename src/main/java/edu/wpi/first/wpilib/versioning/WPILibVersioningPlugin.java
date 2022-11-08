package edu.wpi.first.wpilib.versioning;

import org.gradle.api.Plugin;
import org.gradle.api.Project;

/**
 * Determines the remote WPILib repository to use as well as determining the version of published artifacts. This is
 * determined based on git
 */
public class WPILibVersioningPlugin implements Plugin<Project> {
    @Override
    public void apply(Project project) {
        WPILibVersionProvider provider = new GitVersionProvider();
        project.getExtensions().create("wpilibVersioning", WPILibVersioningPluginExtension.class, project, provider);
    }
}
