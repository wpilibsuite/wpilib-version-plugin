package org.wpilib.versioning;

import java.util.List;

import org.gradle.api.Project;

/**
 * Strategy for generating a project version from the extension's git state.
 */
public interface WPILibVersionProvider {
    String getVersion(WPILibVersioningPluginExtension extension, Project project, boolean allTags, List<String> matchGlobs);
}
