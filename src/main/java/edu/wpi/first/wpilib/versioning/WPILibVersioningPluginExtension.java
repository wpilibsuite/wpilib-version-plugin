package edu.wpi.first.wpilib.versioning;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import org.gradle.api.Project;
import org.gradle.api.provider.Property;

public class WPILibVersioningPluginExtension {
    private final Property<String> version;
    private final Property<String> time;
    private boolean buildServerMode = false;
    private boolean releaseMode = false;

    public WPILibVersioningPluginExtension(Project project, WPILibVersionProvider versionProvider) {
        version = project.getObjects().property(String.class);
        time = project.getObjects().property(String.class);

        LocalDateTime.now();
        time.set(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss")));
        version.set(project.provider(() -> {
            return versionProvider.getVersion(this, project);
        }));
    }

    public Property<String> getTime() {
        return time;
    }

    public Property<String> getVersion() {
        return version;
    }

    public boolean isBuildServerMode() {
        return buildServerMode;
    }

    public void setBuildServerMode(boolean mode) {
        buildServerMode = mode;
    }

    public boolean isReleaseMode() {
        return releaseMode;
    }

    public void setReleaseMode(boolean mode) {
        releaseMode = mode;
    }
}