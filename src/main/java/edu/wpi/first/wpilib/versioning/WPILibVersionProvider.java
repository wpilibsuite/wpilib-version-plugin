package edu.wpi.first.wpilib.versioning;

import org.gradle.api.Project;

public interface WPILibVersionProvider {
    String getVersion(WPILibVersioningPluginExtension extension, Project project);
}