package edu.wpi.first.wpilib.versioning

import org.ajoberstar.grgit.Grgit
import org.gradle.api.Project
import org.gradle.testfixtures.ProjectBuilder
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.contrib.java.lang.system.EnvironmentVariables

import java.nio.file.Files

import static org.junit.Assert.assertEquals
import static org.junit.Assert.assertTrue
import static com.github.stefanbirkner.systemlambda.SystemLambda.withEnvironmentVariable

/**
 * Tests for the wpilib versioning plugin
 */
class WPILibVersioningPluginTests {
    @Rule
    public final EnvironmentVariables environmentVariables = new EnvironmentVariables()

    @Before
    void before() {
        environmentVariables.clear("CI", "GITHUB_REF")
    }

    @Test
    void 'Applying plugin creates extension'() {
        def project = createProjectInstance()
        project.evaluate()
        assertTrue(project.extensions.getByName('wpilibVersioning') instanceof WPILibVersioningPluginExtension)
    }

    @Test
    void 'Applying plugin with no tag works'() {
        def project = createProjectInstanceWithGit().second
        project.evaluate()
        assertTrue(project.extensions.getByName('wpilibVersioning') instanceof WPILibVersioningPluginExtension)
        WPILibVersioningPluginExtension ext = project.extensions.getByName('wpilibVersioning')
        ext.getVersion().get()
    }

    @Test
    void 'Version Regex Works'() {
        verifyRegex('1', '.0.0')
        verifyRegex('1', '.0.0', 'beta-1')
        verifyRegex('1', '.0.0', 'rc-1')
        verifyRegex('1', '.0.0', null, 1, 'g01235647')
        verifyRegex('1', '.0.0', 'beta-1', 0, null)
        verifyRegex('1', '.0.0', 'rc-1', 0, null)
        verifyRegex('1', '.0.0', 'beta-1', 1, 'g01235647')
        verifyRegex('1', '.0.0', 'rc-1', 1, 'g01235647')
    }

    @Test
    void 'Retrieves Correct Version 1_0_0 official'() {
        verifyProjectVersion('v1.0.0', null, true, "1.0.0")
    }

    @Test
    void 'Retrieves Correct Version 1_0_0 dev'() {
        verifyProjectVersion('v1.0.0', null, false, '1.0.0')
    }

    @Test
    void 'Retrieves Correct Version 1_0_0-alpha-1 official'() {
        verifyProjectVersion('v1.0.0-alpha-1', null, true, "1.0.0-alpha-1")
    }

    @Test
    void 'Retrieves Correct Version 1_0_0-alpha-1 dev'() {
        verifyProjectVersion('v1.0.0-alpha-1', null, false, "1.0.0-alpha-1")
    }


    @Test
    void 'Retrieves Correct Version 1_0_0-beta-1 official'() {
        verifyProjectVersion('v1.0.0-beta-1', null, true, "1.0.0-beta-1")
    }

    @Test
    void 'Retrieves Correct Version 1_0_0-beta-1 dev'() {
        verifyProjectVersion('v1.0.0-beta-1', null, false, "1.0.0-beta-1")
    }

    @Test
    void 'Retrieves Correct Version 1_0_0-rc-1 official'() {
        verifyProjectVersion('v1.0.0-rc-1', null, true, "1.0.0-rc-1")
    }

    @Test
    void 'Retrieves Correct Version 1_0_0-rc-1 dev'() {
        verifyProjectVersion('v1.0.0-rc-1', null, false, "1.0.0-rc-1")
    }

    @Test
    void 'Retrieves Correct Version 1_0_0 dev dirty'() {
        verifyProjectVersion('v1.0.0', null, false, "1.0.0-dirty",
                { project, git ->
                    new File(project.rootDir, "temp").createNewFile()
                })
    }

    @Test
    void 'Retrieves Correct Version 1_0_0 dev commits'() {
        def ogit
        verifyProjectVersion('v1.0.0', null, false, "1.0.0-1-g${-> ogit.log().get(0).getAbbreviatedId()}",
                { project, git ->
                    ogit = git
                    new File(project.rootDir, "temp").createNewFile()
                    git.add(patterns: ['temp'])
                    git.commit(message: 'second commit')
                })
    }

    @Test
    void 'Retrieves Correct Version 1_0_0 dev commits dirty'() {
        def ogit
        verifyProjectVersion('v1.0.0', null, false, "1.0.0-1-g${->ogit.log().get(0).getAbbreviatedId()}-dirty",
                { project, git ->
                    ogit = git
                    new File(project.rootDir, "temp").createNewFile()
                    git.add(patterns: ['temp'])
                    git.commit(message: 'second commit')
                    new File(project.rootDir, "temp1").createNewFile()
                    git.add(patterns: ['temp1'])
                })
    }

    @Test
    void 'Retrieves Correct Version 1_424242_0_0 dev localBuild'() {
        verifyProjectVersion('v1.0.0-rc-1', '20160803132333', false, '1.424242.0.0-rc-1-20160803132333',
                null, true)
    }

    @Test
    public void 'Uses GitHub provided tag instead of git'() {
        withEnvironmentVariable("CI", "true")
                .and("GITHUB_REF", "refs/tags/v1.0.0-beta-2")
                .execute({ -> verifyProjectVersion('v1.0.0-beta-1', null, true, "1.0.0-beta-2") })
    }

    @Test
    public void 'Retrieves empty if annotated tag does not match'() {
        verifyProjectVersion('v1.0.0', null, true, "",
                { project, git ->
                    new File(project.rootDir, "temp").createNewFile()
                    git.add(patterns: ['temp'])
                    git.commit(message: 'second commit')
                    git.tag.add(name: 'NewInvalidTag', annotate: true)
                })
    }

    @Test
    public void 'Retrieves Correct Version 1_0_0 when newest tag is invalid if matchGlob is set'() {
        verifyProjectVersion('v1.0.0', null, true, "1.0.0",
                { project, git ->
                    new File(project.rootDir, "temp").createNewFile()
                    git.add(patterns: ['temp'])
                    git.commit(message: 'second commit')
                    git.tag.add(name: 'NewInvalidTag', annotate: true)

                    project.extensions.getByName('wpilibVersioning').addMatchGlob("[v]*[.]*[.]*")
                })
    }

    static def verifyRegex(String majorVersion, String minorVersion, String qualifier = null, int numCommits = 0, String hash = null) {
        def strBuilder = new StringBuilder()
        strBuilder.append('v').append(majorVersion).append(minorVersion)

        if (qualifier != null) {
            strBuilder.append("-$qualifier")
        }

        if (numCommits != 0 && hash != null) {
            strBuilder.append("-$numCommits-$hash")
        }

        def match = strBuilder.toString() =~ GitVersionProvider.versionRegex
        assertTrue(match.matches())
        assertEquals(majorVersion, match.group(GitVersionProvider.majorVersion))
        assertEquals(minorVersion, match.group(GitVersionProvider.minorVersion))
        assertEquals(qualifier, match.group(GitVersionProvider.qualifier))
        assertEquals(numCommits == 0 ? null : "$numCommits".toString(), match.group(GitVersionProvider.commits))
        assertEquals(hash, match.group(GitVersionProvider.sha))
    }

    static def verifyProjectVersion(String gitTag, String time, boolean releaseMode, GString expectedVersion,
                                    afterTag = null) {
        def tuple = createProjectInstanceWithGit()
        def git = tuple.first
        def project = tuple.second

        project.extensions.getByName('wpilibVersioning').buildServerMode = true
        project.extensions.getByName('wpilibVersioning').releaseMode = releaseMode

        git.tag.add(name: gitTag, annotate: true)
        project.extensions.getByName('wpilibVersioning').time.set(time)

        // Call the afterTag closure if it's not null. This allows tests to modify the output by adding things to the
        // project, creating new commits, etc.
        if (afterTag != null)
            afterTag(project, git)

        def version = project.extensions.getByName('wpilibVersioning').version.get()
        assertEquals(expectedVersion.toString(), version)
    }

    static def verifyProjectVersion(String gitTag, String time, boolean releaseMode, String expectedVersion,
                                    afterTag = null, boolean isLocalBuild = false) {
        def tuple = createProjectInstanceWithGit()
        def git = tuple.first
        def project = tuple.second

        project.extensions.getByName('wpilibVersioning').buildServerMode = !isLocalBuild

        if (isLocalBuild) {
            project.extensions.getByName('wpilibVersioning').time.set(time)
        }
        project.extensions.getByName('wpilibVersioning').releaseMode = releaseMode

        git.tag.add(name: gitTag, annotate: true)

        // Call the afterTag closure if it's not null. This allows tests to modify the output by adding things to the
        // project, creating new commits, etc.
        if (afterTag != null)
            afterTag(project, git)


        def version = project.extensions.getByName('wpilibVersioning').version.get()
        println("Obtained Version: " + version)
        assertEquals(expectedVersion, version)
    }

    static def createProjectInstance() {
        def project = ProjectBuilder.builder().build()
        project.pluginManager.apply 'edu.wpi.first.wpilib.versioning.WPILibVersioningPlugin'
        return project
    }

    static def createProjectInstanceWithGit() {
        def tempDir = Files.createTempDirectory('versionPluginTest')
        def git = Grgit.init(dir: tempDir.toString())
        def ignoreFile = new File(tempDir.toFile(), ".gitignore")
        ignoreFile.createNewFile()
        ignoreFile.text = "userHome"
        git.add(patterns: ['.gitignore'])
        git.commit(message: 'initial commit')
        def project = ProjectBuilder.builder().withProjectDir(tempDir.toFile()).build()
        project.pluginManager.apply 'edu.wpi.first.wpilib.versioning.WPILibVersioningPlugin'
        return new Tuple2<Grgit, Project>(git, project)
    }
}
