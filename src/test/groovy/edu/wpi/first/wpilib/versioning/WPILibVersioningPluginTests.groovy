package edu.wpi.first.wpilib.versioning

import org.gradle.api.Project
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.plugins.MavenPublishPlugin
import org.gradle.testfixtures.ProjectBuilder
import org.junit.Test
import org.junit.matchers.JUnitMatchers

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
