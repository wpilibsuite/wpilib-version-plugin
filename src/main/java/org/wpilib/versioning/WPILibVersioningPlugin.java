package org.wpilib.versioning;

import org.gradle.api.Plugin;
import org.gradle.api.Project;

/**
 * Generates WPILib-compatible project versions from git tags and exposes them through the
 * {@code wpilibVersioning} extension.
 */
public class WPILibVersioningPlugin implements Plugin<Project> {
    @Override
    public void apply(Project project) {
        WPILibVersionProvider provider = new GitVersionProvider();
        project.getExtensions().create("wpilibVersioning", WPILibVersioningPluginExtension.class, project, provider);
    }
}
