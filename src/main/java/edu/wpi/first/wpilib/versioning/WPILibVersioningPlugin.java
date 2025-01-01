package edu.wpi.first.wpilib.versioning;

import javax.inject.Inject;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.problems.ProblemReporter;
import org.gradle.api.problems.Problems;

/**
 * Determines the remote WPILib repository to use as well as determining the version of published artifacts. This is
 * determined based on git
 */
public class WPILibVersioningPlugin implements Plugin<Project> {
    private final ProblemReporter problemReporter;

    @Inject
    public WPILibVersioningPlugin(Problems problems) {
       this.problemReporter = problems.getReporter(); 
    }

    @Override
    public void apply(Project project) {
        WPILibVersionProvider provider = new GitVersionProvider(problemReporter);
        project.getExtensions().create("wpilibVersioning", WPILibVersioningPluginExtension.class, project, provider);
    }
}
