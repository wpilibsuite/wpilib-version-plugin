# WPILib Versioning Plugin

[![Build Status](https://travis-ci.org/wpilibsuite/wpilib-version-plugin.svg?branch=master)](https://travis-ci.org/wpilibsuite/wpilib-version-plugin)

This Gradle plugin is responsible for determining what repositories the build should be getting Maven dependencies from, where it should be publishing Maven dependencies to, and for generating a version number scheme we use in WPILib. This is used by most of the other WPILib projects, and can be used by third party projects to automate selecting of which repositories to use.

## Building

This plugin is written in Groovy, and therefore needs to have Groovy installed on your local machine. Groovy is available from here: http://www.groovy-lang.org/. Building is accomplished with Gradle, as with the rest of WPILib. To build, run the following command:

```bash
./gradlew build
```

