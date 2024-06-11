# WPILib Versioning Plugin

![CI](https://github.com/wpilibsuite/wpilib-version-plugin/workflows/CI/badge.svg)

This Gradle plugin is responsible for determining what repositories the build should be getting Maven dependencies from, where it should be publishing Maven dependencies to, and for generating a version number scheme we use in WPILib. This is used by most of the other WPILib projects, and can be used by third party projects to automate selecting of which repositories to use.

## Building

This plugin is written in Java, and therefore needs to have Java installed on your local machine. Java is available from [here](https://adoptium.net/en-GB/temurin/releases/?version=11) or on Ubuntu by running `sudo apt install openjdk-11-jdk`. Building is accomplished with Gradle, as with the rest of WPILib. To build, run the following command:

```bash
./gradlew build
```

For a full list of tasks, see the output of:

```bash
./gradlew tasks
```

Note that you will not be able to publish to plugins.gradle.org. To get a new version uploaded, contact 333fred.

To publish for local testing, use the following command.
```bash
./gradlew publishToMavenLocal -PlocalPublish
```
You will then be able to use the `plugins` block with an updated version. You will need to ensure your plugin management is setup in your `settings.gradle` as well. We use the following
```groovy
pluginManagement {
    repositories {
        mavenLocal()
        gradlePluginPortal()
    }
}
```


## Usage

To use this plugin in your program with Gradle version >= 2.1, use the following snippit:

```gradle
plugins {
  // NOTE: Substitute latest-version with the latest published version
  id "edu.wpi.first.wpilib.versioning.WPILibVersioningPlugin" version "latest-version"
}
```

For other versions use the following (note that using this plugin with less than Gradle 3.0 hasn't been tested):

```gradle
buildscript {
  repositories {
    maven {
      url "https://plugins.gradle.org/m2/"
    }
  }
  dependencies {
    // NOTE: Substitute latest-version with the latest published version
    classpath "gradle.plugin.edu.wpi.first.wpilib.versioning:wpilib-version-plugin:latest-version"
  }
}

apply plugin: "edu.wpi.first.wpilib.versioning.WPILibVersioningPlugin"
```

This plugin introduces the following extension:


```gradle
WPILibVersion {
    /*
     * This triggers version generation, which is used by the WPILib repositories to generate the correct version number
     * for this build. You must have an annotated git tag in the form v1.0.0-(beta|rc)-1, where v1.0.0 is mandatory
     * and the -(beta|rc)-1 part is optional. If you don't have this tag, a warning message will be printed. Set to
     * false to disable version generation.
     */
    generateVersion = false

    /*
     * This controls whether you use the developer repositories or the release repositories. For competition, use should
     * use the official repositories. "dev" is only useful when developing with WPILib. Valid options are "official"
     * and "dev". You *must* set this last, as setting it triggers evaluation of the plugin.
     */
    releaseType = "official"

    /*
     * The rest of the parameters are for WPILib internal use only. Don't set them unless you know what you're doing.
     * The default values have been copied here for documentation purposes.
     */
    remoteUrlBase = 'https://first.wpi.edu/FRC/roborio/maven'
    mavenLocalBase = "${System.getProperty('user.home')}/releases/maven"
    devExtension = 'development'
    officialExtension = 'release'
    mavenLocalUrl = ''
    mavenRemoteUrl = ''
    version = ''
    time // For internal testing use only. Do not use outside of test code.
}
```

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md)
