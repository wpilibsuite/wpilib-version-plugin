package edu.wpi.first.wpilib.versioning

import org.ajoberstar.grgit.Grgit
import org.gradle.api.Project
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.plugins.MavenPublishPlugin
import org.gradle.testfixtures.ProjectBuilder
import org.junit.Test

import java.nio.file.Files

import static org.junit.Assert.assertEquals
import static org.junit.Assert.assertTrue

/**
 * Tests for the wpilib versioning plugin
 */
class WPILibVersioningPluginTests {
    @Test
    public void 'Applying plugin creates extension'() {
        def project = createProjectInstance()
        project.evaluate()
        assertTrue(project.extensions.getByName('WPILibVersion') instanceof WPILibVersioningPluginExtension)
    }

    @Test
    public void 'Setting releaseType to alpha publishes to development repository'() {
        def project = createProjectInstance()
        testPublishSetting(project, ReleaseType.DEV, 'releases/maven/development')
    }

    @Test
    public void 'Setting releaseType to beta publishes to release repository'() {
        def project = createProjectInstance()
        testPublishSetting(project, ReleaseType.OFFICIAL, 'releases/maven/release')
    }

    @Test
    public void 'Setting releaseType to dev adds development dependent repos'() {
        testRepositorySettings(createProjectInstance(), ReleaseType.DEV, 'maven/development')
    }

    @Test
    public void 'Setting releaseType to official adds release dependent repos'() {
        testRepositorySettings(createProjectInstance(), ReleaseType.OFFICIAL, 'maven/release')
    }

    @Test
    public void 'Version Regex Works'() {
        verifyRegex('1.0.0')
        verifyRegex('1.0.0', 'beta-1')
        verifyRegex('1.0.0', 'rc-1')
        verifyRegex('1.0.0', null, 1, 'g01235647')
        verifyRegex('1.0.0', 'beta-1', 0, null)
        verifyRegex('1.0.0', 'rc-1', 0, null)
        verifyRegex('1.0.0', 'beta-1', 1, 'g01235647')
        verifyRegex('1.0.0', 'rc-1', 1, 'g01235647')
    }

    @Test
    public void 'Retrieves Correct Version: 1.0.0 official'() {
        verifyProjectVersion('v1.0.0', '20160803132333', ReleaseType.OFFICIAL, "1.0.0")
    }

    @Test
    public void 'Retrieves Correct Version: 1.0.0 dev'() {
        verifyProjectVersion('v1.0.0', '20160803132333', ReleaseType.DEV, '1.0.0-20160803132333')
    }

    @Test
    public void 'Retrieves Correct Version: 1.0.0-beta-1 official'() {
        verifyProjectVersion('v1.0.0-beta-1', '20160803132333', ReleaseType.OFFICIAL, "1.0.0-beta-1")
    }

    @Test
    public void 'Retrieves Correct Version: 1.0.0-beta-1 dev'() {
        verifyProjectVersion('v1.0.0-beta-1', '20160803132333', ReleaseType.DEV, "1.0.0-beta-1-20160803132333")
    }

    @Test
    public void 'Retrieves Correct Version: 1.0.0-rc-1 official'() {
        verifyProjectVersion('v1.0.0-rc-1', '20160803132333', ReleaseType.OFFICIAL, "1.0.0-rc-1")
    }

    @Test
    public void 'Retrieves Correct Version: 1.0.0-rc-1 dev'() {
        verifyProjectVersion('v1.0.0-rc-1', '20160803132333', ReleaseType.DEV, "1.0.0-rc-1-20160803132333")
    }

    @Test
    public void 'Retrieves Correct Version: 1.0.0 dev dirty'() {
        verifyProjectVersion('v1.0.0', '20160803132333', ReleaseType.DEV, "1.0.0-20160803132333-dirty",
                { project, git ->
                    new File(project.rootDir, "temp").createNewFile()
                })
    }

    @Test
    public void 'Retrieves Correct Version: 1.0.0 dev commits'() {
        def ogit
        verifyProjectVersion('v1.0.0', '20160803132333', ReleaseType.DEV, "1.0.0-20160803132333-1-g${-> ogit.log().get(0).getAbbreviatedId()}",
                { project, git ->
                    ogit = git
                    new File(project.rootDir, "temp").createNewFile()
                    git.add(patterns: ['temp'])
                    git.commit(message: 'second commit')
                })
    }

    @Test
    public void 'Retrieves Correct Version: 1.0.0 dev commits dirty'() {
        def ogit
        verifyProjectVersion('v1.0.0', '20160803132333', ReleaseType.DEV, "1.0.0-20160803132333-1-g${->ogit.log().get(0).getAbbreviatedId()}-dirty",
                { project, git ->
                    ogit = git
                    new File(project.rootDir, "temp").createNewFile()
                    git.add(patterns: ['temp'])
                    git.commit(message: 'second commit')
                    new File(project.rootDir, "temp1").createNewFile()
                    git.add(patterns: ['temp1'])
                })
    }

    static def verifyRegex(String mainVersion, String qualifier = null, int numCommits = 0, String hash = null) {
        def strBuilder = new StringBuilder()
        strBuilder.append('v').append(mainVersion)

        if (qualifier != null) {
            strBuilder.append("-$qualifier")
        }

        if (numCommits != 0 && hash != null) {
            strBuilder.append("-$numCommits-$hash")
        }

        def match = strBuilder.toString() =~ WPILibVersioningPlugin.versionRegex
        assertTrue(match.matches())
        assertEquals(mainVersion, match.group(WPILibVersioningPlugin.mainVersion))
        assertEquals(qualifier, match.group(WPILibVersioningPlugin.qualifier))
        assertEquals(numCommits == 0 ? null : "$numCommits".toString(), match.group(WPILibVersioningPlugin.commits))
        assertEquals(hash, match.group(WPILibVersioningPlugin.sha))
    }

    static def testPublishSetting(Project project, ReleaseType projectType, String expectedPath) {
        project.pluginManager.apply(MavenPublishPlugin)
        project.extensions.getByName('WPILibVersion').releaseType = projectType
        project.evaluate()
        project.extensions.getByType(PublishingExtension).repositories.all {
            def path = it.url.path
            assertTrue("Search string is $path, expected is $expectedPath", (boolean) path.contains(expectedPath))
        }
    }

    static def testRepositorySettings(Project project, ReleaseType projectType, String expectedPath) {
        project.extensions.getByName('WPILibVersion').releaseType = projectType
        project.evaluate()
        project.repositories.all {
            def path = it.url.toString()
            assertTrue("Search string is $path, expected path is $expectedPath", (boolean) path.contains(expectedPath))
        }
    }

    static def verifyProjectVersion(String gitTag, String time, ReleaseType type, GString expectedVersion,
                                    afterTag = null) {
        def tuple = createProjectInstanceWithGit()
        def git = tuple.first
        def project = tuple.second

        git.tag.add(name: gitTag, annotate: true)
        project.extensions.getByName('WPILibVersion').releaseType = type
        project.extensions.getByName('WPILibVersion').time = time

        // Call the afterTag closure if it's not null. This allows tests to modify the output by adding things to the
        // project, creating new commits, etc.
        if (afterTag != null)
            afterTag(project, git)

        project.evaluate()
        def version = project.extensions.getByName('WPILibVersion').version
        assertEquals(expectedVersion.toString(), version)
    }

    static def verifyProjectVersion(String gitTag, String time, ReleaseType type, String expectedVersion,
                                    afterTag = null) {
        def tuple = createProjectInstanceWithGit()
        def git = tuple.first
        def project = tuple.second

        git.tag.add(name: gitTag, annotate: true)
        project.extensions.getByName('WPILibVersion').releaseType = type
        project.extensions.getByName('WPILibVersion').time = time

        // Call the afterTag closure if it's not null. This allows tests to modify the output by adding things to the
        // project, creating new commits, etc.
        if (afterTag != null)
            afterTag(project, git)

        project.evaluate()
        def version = project.extensions.getByName('WPILibVersion').version
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
        git.commit(message: 'initial commit')
        def project = ProjectBuilder.builder().withProjectDir(tempDir.toFile()).build()
        project.pluginManager.apply 'edu.wpi.first.wpilib.versioning.WPILibVersioningPlugin'
        return new Tuple2<Grgit, Project>(git, project)
    }
}
