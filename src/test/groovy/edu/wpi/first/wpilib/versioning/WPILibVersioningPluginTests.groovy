package edu.wpi.first.wpilib.versioning

import org.gradle.api.Project
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.plugins.MavenPublishPlugin
import org.gradle.testfixtures.ProjectBuilder
import org.junit.Test
import org.junit.matchers.JUnitMatchers

import java.util.regex.Matcher

import static org.junit.Assert.assertEquals
import static org.junit.Assert.assertThat
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
        testPublishSetting(project, ReleaseType.ALPHA, 'releases/maven/development')
    }

    @Test
    public void 'Setting releaseType to beta publishes to release repository'() {
        def project = createProjectInstance()
        testPublishSetting(project, ReleaseType.BETA, 'releases/maven/release')
    }

    @Test
    public void 'Setting releaseType to rc publishes to release repository'() {
        def project = createProjectInstance()
        testPublishSetting(project, ReleaseType.RC, 'releases/maven/release')
    }

    @Test
    public void 'Setting releaseType to release publishes to release repository'() {
        def project = createProjectInstance()
        testPublishSetting(project, ReleaseType.RELEASE, 'releases/maven/release')
    }

    @Test
    public void 'Setting releaseType to alpha adds development dependent repos'() {
        testRepositorySettings(createProjectInstance(), ReleaseType.ALPHA, 'maven/development')
    }

    @Test
    public void 'Setting releaseType to beta adds release dependent repos'() {
        testRepositorySettings(createProjectInstance(), ReleaseType.BETA, 'maven/release')
    }

    @Test
    public void 'Setting releaseType to rc adds release dependent repos'() {
        testRepositorySettings(createProjectInstance(), ReleaseType.RC, 'maven/release')
    }

    @Test
    public void 'Setting releaseType to release adds release dependent repos'() {
        testRepositorySettings(createProjectInstance(), ReleaseType.RELEASE, 'maven/release')
    }

    @Test
    public void 'Version Regex Works'() {
        verifyRegex('1.0.0')
        verifyRegex('1.0.0', 'beta-1')
        verifyRegex('1.0.0', 'rc-1')
        verifyRegex('1.0.0', null, 1, 'g01235647')
        verifyRegex('1.0.0', null, 0, null, true)
        verifyRegex('1.0.0', 'beta-1', 0, null, true)
        verifyRegex('1.0.0', 'rc-1', 0, null, true)
        verifyRegex('1.0.0', 'beta-1', 1, 'g01235647', true)
        verifyRegex('1.0.0', 'rc-1', 1, 'g01235647', true)
    }

    static def verifyRegex(String mainVersion, String qualifier = null, int numCommits = 0, String hash = null, boolean
            isDirty = false) {
        def strBuilder = new StringBuilder()
        strBuilder.append('v').append(mainVersion)

        if (qualifier != null) {
            strBuilder.append("-$qualifier")
        }

        if (numCommits != 0 && hash != null) {
            strBuilder.append("-$numCommits-$hash")
        }

        if (isDirty) {
            strBuilder.append("-dirty")
        }

        def match = strBuilder.toString() =~ WPILibVersioningPlugin.versionRegex
        assertTrue(match.matches())
        assertEquals(mainVersion, match.group('version'))
        assertEquals(qualifier, match.group('qualifier'))
        assertEquals(numCommits == 0 ? null : "$numCommits".toString(), match.group('commits'))
        assertEquals(hash, match.group('sha'))
        assertEquals(isDirty ? 'dirty' : null, match.group('dirty'))
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

    static def createProjectInstance() {
        def project = ProjectBuilder.builder().build()
        project.pluginManager.apply 'edu.wpi.first.wpilib.versioning.WPILibVersioningPlugin'
        return project
    }
}
