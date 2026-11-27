# WPILib Versioning Plugin

![CI](https://github.com/wpilibsuite/wpilib-version-plugin/workflows/CI/badge.svg)

This Gradle plugin is responsible for generating a version number scheme we use in WPILib. This is used by most of the other WPILib projects.

## Version scheme

Annotated git tags must use the WPILib form:

```
v<year>.<imageBreaking>-<WPILibBreaking>.<WPILibNonBreaking>
```

Examples:

```
v2027.1-1.0        first beta WPILib release
v2027.1-1.1        nonbreaking WPILib update
v2027.1-2.0        breaking WPILib change against the same image
v2027.10-1.0       FRC kickoff release 
v2027.20-1.0       FTC kickoff release
```

* `<year>` is a four-digit season year.
* `<imageBreaking>` and `<WPILibBreaking>` start at `1` and indicate ABI incompatibility.
* `<WPILibNonBreaking>` starts at `0`.
* `alpha` / `beta` / `rc` qualifiers are no longer used.

Release builds emit the tag without the leading `v`. Non-release builds also append
`-<commits>-g<sha>` and `-dirty` when applicable. Local builds (when `buildServerMode` is `false`)
insert `424242` after the hyphen following the image-breaking number and append a
timestamp so they sort first, for example `2027.1-424242.1.0-20160803132333`.

### Migrating old tags

Legacy tags in the old `YEAR.MAJOR.MINOR` form are still recognized. They are mapped to
`YEAR.0-MAJOR.MINOR` (for example, `v2027.1.0` becomes `2027.0-1.0`) and the plugin prints a warning
that migration to the new tag format is needed. Old `alpha` / `beta` / `rc` qualifiers are accepted
but ignored during migration. Create a new-format tag as soon as possible; the mapped `0`
image-breaking number exists only to let old tags keep working during the transition.
This ensures that the first release with the new tag will be higher then the migrated tag.

## Building

This plugin is written in Java and built with Gradle. To build, run:

```bash
./gradlew build
```

For a full list of tasks, see the output of:

```bash
./gradlew tasks
```

Note that you will not be able to publish to plugins.gradle.org. To get a new version uploaded, contact 333fred.

To publish for local testing, use the following command:

```bash
./gradlew publishToMavenLocal
```

You will then be able to use the `plugins` block with an updated version. You will need to ensure your plugin management is setup in your `settings.gradle` as well. We use the following:

```groovy
pluginManagement {
    repositories {
        mavenLocal()
        gradlePluginPortal()
    }
}
```

## Usage

To use this plugin in your program with a recent version of Gradle, use the following snippet:

```gradle
plugins {
  // NOTE: Substitute latest-version with the latest published version
  id "org.wpilib.WPILibVersioningPlugin" version "latest-version"
}

This plugin introduces the `wpilibVersioning` extension:

```gradle
wpilibVersioning {
    /*
     * Generates the project version from the newest annotated git tag. Tags must be in the form
     * v<year>.<imageBreaking>-<WPILibBreaking>.<WPILibNonBreaking>, for example v2027.1-1.0.
     */
    /*
     * Set to true for CI/build-server builds. When false, 424242 is inserted after
     * the hyphen following the image-breaking number so local artifacts resolve
     * first, and non-release builds also append a timestamp.
     */
    buildServerMode = true

    /*
     * Set to true for official releases. This emits only the core version (no
     * timestamp, commit count/SHA, or dirty marker) and lets CI use the tag from
     * GITHUB_REF instead of git describe.
     */
    releaseMode = false

    /*
     * Set to true to let git describe consider lightweight tags as well as
     * annotated tags. The default, false, considers annotated tags only.
     */
    useAllTags = false

    // Optional: restrict which tags git describe considers.
    // addMatchGlob('v[0-9]*')

    // For internal testing use only. Do not use outside of test code.
    // time = '20160803132333'
}
```

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md)
