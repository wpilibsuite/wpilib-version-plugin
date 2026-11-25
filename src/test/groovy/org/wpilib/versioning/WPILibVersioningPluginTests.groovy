package org.wpilib.versioning

import org.ajoberstar.grgit.Grgit
import org.gradle.api.Project
import org.gradle.testfixtures.ProjectBuilder
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.contrib.java.lang.system.EnvironmentVariables

import java.nio.file.Files

import static org.junit.Assert.assertEquals
import static org.junit.Assert.assertFalse
import static org.junit.Assert.assertTrue
import static com.github.stefanbirkner.systemlambda.SystemLambda.tapSystemOut
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
        verifyRegex('2027', '1', '1', '0')
        verifyRegex('2027', '10', '1', '0')
        verifyRegex('2027', '20', '1', '0')
        verifyRegex('2027', '1', '1', '1')
        verifyRegex('2027', '1', '2', '0')
        verifyRegex('2027', '1', '1', '0', 1, 'g01235647')
        verifyRegex('2027', '10', '1', '2', 1, 'g01235647')
    }

    @Test
    void 'Version Regex Rejects Invalid Versions'() {
        assertRegexDoesNotMatch('v2027.0-1.0')            // image breaking starts at 1
        assertRegexDoesNotMatch('v2027.1-0.0')            // WPILib breaking starts at 1
        assertRegexDoesNotMatch('v2027.01-1.0')           // image breaking cannot have leading zeros
        assertRegexDoesNotMatch('v2027.1-01.0')           // WPILib breaking cannot have leading zeros
        assertRegexDoesNotMatch('v2027.1-1.00')           // WPILib nonbreaking cannot have leading zeros
        assertRegexDoesNotMatch('v2027.1-1.01')           // WPILib nonbreaking cannot have leading zeros
        assertRegexDoesNotMatch('v2027.1-1.007')          // WPILib nonbreaking cannot have leading zeros
        assertRegexDoesNotMatch('v27.1-1.0')              // year must be four digits
        assertRegexDoesNotMatch('v20271.1-1.0')           // year must be four digits
        assertRegexDoesNotMatch('v2027.1-1')              // WPILib nonbreaking is required
        assertRegexDoesNotMatch('v2027.1.1.0')            // Python dot separator is not accepted
        assertRegexDoesNotMatch('v2027.1-1.0-beta-1')     // legacy qualifiers are not accepted
        assertRegexDoesNotMatch('v2027.1-1.0-dirty')      // unexpected suffix
        assertRegexDoesNotMatch('v2027.1-1.0-1-gABC')     // commit hash must be lowercase hex
        assertRegexDoesNotMatch('v2027.1')                // image-only form is not accepted
        assertRegexDoesNotMatch('2027.1-1.0')             // leading v is required
        assertRegexDoesNotMatch('V2027.1-1.0')            // leading v must be lowercase
        assertRegexDoesNotMatch('v2027.1-1.0 ')           // trailing whitespace is not accepted
    }

    @Test
    void 'Old YEAR MAJOR MINOR scheme does not match new tag regex'() {
        assertRegexDoesNotMatch('v2027.1.0')
        assertRegexDoesNotMatch('v2027.10.1')
        assertRegexDoesNotMatch('v2027.1.0-beta-1')
        assertRegexDoesNotMatch('2027.1.0')
    }

    @Test
    void 'Legacy Regex Works And Rejects Invalid Values'() {
        verifyLegacyRegex('2027', '1', '0')
        verifyLegacyRegex('2027', '0', '1')
        verifyLegacyRegex('2027', '10', '3')
        verifyLegacyRegex('2027', '0', '0', 'alpha-8')
        verifyLegacyRegex('2027', '1', '0', 'beta-1')
        verifyLegacyRegex('2027', '1', '0', 'rc-2')
        verifyLegacyRegex('2027', '0', '0', 'alpha-8', 127, 'g95bf4897e6')
        assertLegacyRegexDoesNotMatch('v2027.01.0')           // major cannot have leading zeros
        assertLegacyRegexDoesNotMatch('v2027.1.00')           // minor cannot have leading zeros
        assertLegacyRegexDoesNotMatch('v2027.1.0-gamma-1')     // only alpha/beta/rc qualifiers are accepted
        assertLegacyRegexDoesNotMatch('v27.1.0')              // year must be four digits
        assertLegacyRegexDoesNotMatch('2027.1.0')             // leading v is required
        assertLegacyRegexDoesNotMatch('v2027.1.0-1-gABC')     // commit hash must be lowercase hex
    }

    @Test
    void 'Legacy tag is migrated to YEAR_0-MAJOR_MINOR'() {
        verifyProjectVersion('v2027.1.0', null, true, '2027.0-1.0')
        verifyProjectVersion('v2027.10.1', null, true, '2027.0-10.1')
        verifyProjectVersion('v2027.0.1', null, true, '2027.0-0.1')
    }

    @Test
    void 'Legacy qualifier is ignored during migration'() {
        verifyProjectVersion('v2027.0.0-alpha-8', null, true, '2027.0-0.0')
        verifyProjectVersion('v2027.1.0-beta-2', null, true, '2027.0-1.0')
        verifyProjectVersion('v2027.1.0-rc-3', null, true, '2027.0-1.0')
    }

    @Test
    void 'Legacy qualified tag with commits is migrated'() {
        def ogit
        verifyProjectVersion('v2027.0.0-alpha-8', null, false,
                "2027.0-0.0-1-g${-> ogit.log().get(0).getAbbreviatedId()}",
                { project, git ->
                    ogit = git
                    new File(project.rootDir, "temp").createNewFile()
                    git.add(patterns: ['temp'])
                    git.commit(message: 'second commit')
                })
    }

    @Test
    void 'Legacy migration prints warning'() {
        def output = tapSystemOut({
            verifyProjectVersion('v2027.1.0', null, true, '2027.0-1.0')
        })
        assertTrue(output.contains('old YEAR.MAJOR.MINOR format'))
        assertTrue(output.contains('Migration to the new tag format'))
        assertTrue(output.contains('Generated version: 2027.0-1.0'))
    }

    @Test
    void 'Legacy tag local build includes 424242'() {
        verifyProjectVersion('v2027.1.0', '20160803132333', false, '2027.0-424242.1.0-20160803132333',
                null, true)
    }

    @Test
    void 'Legacy tag dev commits and dirty'() {
        def ogit
        verifyProjectVersion('v2027.1.0', null, false,
                "2027.0-1.0-1-g${-> ogit.log().get(0).getAbbreviatedId()}-dirty",
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
    void 'CI override migrates legacy tag'() {
        withEnvironmentVariable("CI", "true")
                .and("GITHUB_REF", "refs/tags/v2027.10.3")
                .execute({ -> verifyProjectVersion('v2027.1.0', null, true, '2027.0-10.3') })
    }

    @Test
    void 'Retrieves Correct Version 2027_1-1_0 official'() {
        verifyProjectVersion('v2027.1-1.0', null, true, "2027.1-1.0")
    }

    @Test
    void 'Retrieves Correct Version 2027_1-1_0 dev'() {
        verifyProjectVersion('v2027.1-1.0', null, false, '2027.1-1.0')
    }

    @Test
    void 'Retrieves Correct Version 2027_10-1_0 FRC kickoff'() {
        verifyProjectVersion('v2027.10-1.0', null, true, "2027.10-1.0")
    }

    @Test
    void 'Retrieves Correct Version 2027_20-1_0 FTC kickoff'() {
        verifyProjectVersion('v2027.20-1.0', null, true, "2027.20-1.0")
    }

    @Test
    void 'Release mode local build omits timestamp'() {
        verifyProjectVersion('v2027.1-1.0', '20160803132333', true, '2027.1-424242.1.0', null, true)
    }

    @Test
    void 'Release mode ignores dirty worktree'() {
        verifyProjectVersion('v2027.1-1.0', null, true, '2027.1-1.0',
                { project, git ->
                    new File(project.rootDir, "temp").createNewFile()
                })
    }

    @Test
    void 'Release mode ignores commits since tag'() {
        verifyProjectVersion('v2027.1-1.0', null, true, '2027.1-1.0',
                { project, git ->
                    new File(project.rootDir, "temp").createNewFile()
                    git.add(patterns: ['temp'])
                    git.commit(message: 'second commit')
                })
    }

    @Test
    void 'Retrieves Correct Version dev dirty'() {
        verifyProjectVersion('v2027.1-1.0', null, false, "2027.1-1.0-dirty",
                { project, git ->
                    new File(project.rootDir, "temp").createNewFile()
                })
    }

    @Test
    void 'Retrieves Correct Version dev commits'() {
        def ogit
        verifyProjectVersion('v2027.1-1.0', null, false, "2027.1-1.0-1-g${-> ogit.log().get(0).getAbbreviatedId()}",
                { project, git ->
                    ogit = git
                    new File(project.rootDir, "temp").createNewFile()
                    git.add(patterns: ['temp'])
                    git.commit(message: 'second commit')
                })
    }

    @Test
    void 'Retrieves Correct Version dev commits dirty'() {
        def ogit
        verifyProjectVersion('v2027.1-1.0', null, false, "2027.1-1.0-1-g${->ogit.log().get(0).getAbbreviatedId()}-dirty",
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
    void 'Retrieves Correct Version 2027_1-424242_1_0 dev localBuild'() {
        verifyProjectVersion('v2027.1-1.0', '20160803132333', false, '2027.1-424242.1.0-20160803132333',
                null, true)
    }

    @Test
    void 'Local build includes dirty worktree'() {
        verifyProjectVersion('v2027.1-1.0', '20160803132333', false, '2027.1-424242.1.0-20160803132333-dirty',
                { project, git ->
                    new File(project.rootDir, "temp").createNewFile()
                }, true)
    }

    @Test
    void 'Local build includes commits since tag'() {
        def ogit
        verifyProjectVersion('v2027.1-1.0', '20160803132333', false,
                "2027.1-424242.1.0-20160803132333-1-g${-> ogit.log().get(0).getAbbreviatedId()}",
                { project, git ->
                    ogit = git
                    new File(project.rootDir, "temp").createNewFile()
                    git.add(patterns: ['temp'])
                    git.commit(message: 'second commit')
                }, true)
    }

    @Test
    void 'Local build includes commits and dirty worktree'() {
        def ogit
        verifyProjectVersion('v2027.1-1.0', '20160803132333', false,
                "2027.1-424242.1.0-20160803132333-1-g${-> ogit.log().get(0).getAbbreviatedId()}-dirty",
                { project, git ->
                    ogit = git
                    new File(project.rootDir, "temp").createNewFile()
                    git.add(patterns: ['temp'])
                    git.commit(message: 'second commit')
                    new File(project.rootDir, "temp1").createNewFile()
                    git.add(patterns: ['temp1'])
                }, true)
    }

    @Test
    public void 'Uses GitHub provided tag instead of git'() {
        withEnvironmentVariable("CI", "true")
                .and("GITHUB_REF", "refs/tags/v2027.10-1.1")
                .execute({ -> verifyProjectVersion('v2027.10-1.0', null, true, "2027.10-1.1") })
    }

    @Test
    public void 'CI ignores GITHUB_REF when not in release mode'() {
        withEnvironmentVariable("CI", "true")
                .and("GITHUB_REF", "refs/tags/v2027.10-9.9")
                .execute({ -> verifyProjectVersion('v2027.1-1.0', null, false, '2027.1-1.0') })
    }

    @Test
    public void 'CI without GITHUB_REF falls back to git'() {
        withEnvironmentVariable("CI", "true")
                .execute({ -> verifyProjectVersion('v2027.1-1.0', null, true, '2027.1-1.0') })
    }

    @Test
    public void 'CI with invalid GITHUB_REF produces empty version'() {
        withEnvironmentVariable("CI", "true")
                .and("GITHUB_REF", "refs/tags/NewInvalidTag")
                .execute({ -> verifyProjectVersion('v2027.1-1.0', null, true, '') })
    }

    @Test
    public void 'CI override ignores dirty worktree'() {
        withEnvironmentVariable("CI", "true")
                .and("GITHUB_REF", "refs/tags/v2027.1-1.1")
                .execute({ -> verifyProjectVersion('v2027.1-1.0', null, true, '2027.1-1.1',
                        { project, git ->
                            new File(project.rootDir, "temp").createNewFile()
                        }) })
    }

    @Test
    public void 'Retrieves empty if annotated tag does not match'() {
        verifyProjectVersion('v2027.1-1.0', null, true, "",
                { project, git ->
                    new File(project.rootDir, "temp").createNewFile()
                    git.add(patterns: ['temp'])
                    git.commit(message: 'second commit')
                    git.tag.add(name: 'NewInvalidTag', annotate: true)
                })
    }

    @Test
    public void 'Retrieves Correct Version 2027_1-1_0 when newest tag is invalid if matchGlob is set'() {
        verifyProjectVersion('v2027.1-1.0', null, true, "2027.1-1.0",
                { project, git ->
                    new File(project.rootDir, "temp").createNewFile()
                    git.add(patterns: ['temp'])
                    git.commit(message: 'second commit')
                    git.tag.add(name: 'NewInvalidTag', annotate: true)

                    project.extensions.getByName('wpilibVersioning').addMatchGlob('v[0-9]*')
                })
    }

    @Test
    public void 'Uses newest valid annotated tag on the same commit'() {
        def tuple = createProjectInstanceWithGit()
        def git = tuple.first
        def project = tuple.second
        def ext = project.extensions.getByName('wpilibVersioning')

        ext.buildServerMode = true
        ext.releaseMode = true

        addAnnotatedTag(git, 'v2027.1-1.0', 1_000_000_000_000L)
        addAnnotatedTag(git, 'v2027.1-1.1', 2_000_000_000_000L)

        assertEquals('2027.1-1.1', ext.version.get())
    }

    @Test
    public void 'Lightweight tags are ignored by default'() {
        def tuple = createProjectInstanceWithGit()
        def git = tuple.first
        def project = tuple.second
        def ext = project.extensions.getByName('wpilibVersioning')

        ext.buildServerMode = true
        ext.releaseMode = true

        git.tag.add(name: 'v2027.1-1.1', annotate: false)

        assertEquals('', ext.version.get())
    }

    @Test
    public void 'useAllTags allows lightweight tags'() {
        def tuple = createProjectInstanceWithGit()
        def git = tuple.first
        def project = tuple.second
        def ext = project.extensions.getByName('wpilibVersioning')

        ext.buildServerMode = true
        ext.releaseMode = true
        ext.useAllTags = true

        git.tag.add(name: 'v2027.1-1.1', annotate: false)

        assertEquals('2027.1-1.1', ext.version.get())
    }

    static def assertRegexDoesNotMatch(String versionTag) {
        def match = versionTag =~ GitVersionProvider.versionRegex
        assertFalse(match.matches())
    }

    static def assertLegacyRegexDoesNotMatch(String versionTag) {
        def match = versionTag =~ GitVersionProvider.legacyVersionRegex
        assertFalse(match.matches())
    }

    static def verifyLegacyRegex(String year, String major, String minor, String qualifier = null,
                                 int numCommits = 0, String hash = null) {
        def strBuilder = new StringBuilder()
        strBuilder.append('v').append(year).append('.').append(major).append('.').append(minor)
        if (qualifier != null) {
            strBuilder.append("-$qualifier")
        }
        if (numCommits != 0 && hash != null) {
            strBuilder.append("-$numCommits-$hash")
        }

        def match = strBuilder.toString() =~ GitVersionProvider.legacyVersionRegex
        assertTrue(match.matches())
        assertEquals(year, match.group(GitVersionProvider.year))
        assertEquals(major, match.group(GitVersionProvider.legacyMajor))
        assertEquals(minor, match.group(GitVersionProvider.legacyMinor))
        assertEquals(qualifier, match.group(GitVersionProvider.legacyQualifier))
        assertEquals(numCommits == 0 ? null : "$numCommits".toString(), match.group(GitVersionProvider.commits))
        assertEquals(hash, match.group(GitVersionProvider.sha))
    }

    static def addAnnotatedTag(Grgit git, String name, long timestampMillis) {
        def tagger = new org.eclipse.jgit.lib.PersonIdent(
                'Test', 'test@example.com',
                new Date(timestampMillis), TimeZone.getTimeZone('UTC'))
        git.repository.jgit.tag()
                .setName(name)
                .setMessage(name)
                .setAnnotated(true)
                .setTagger(tagger)
                .call()
    }

    static def verifyRegex(String year, String imageBreaking, String WPILibBreaking, String WPILibNonBreaking,
                           int numCommits = 0, String hash = null) {
        def strBuilder = new StringBuilder()
        strBuilder.append('v').append(year).append('.').append(imageBreaking)
                .append('-').append(WPILibBreaking).append('.').append(WPILibNonBreaking)

        if (numCommits != 0 && hash != null) {
            strBuilder.append("-$numCommits-$hash")
        }

        def match = strBuilder.toString() =~ GitVersionProvider.versionRegex
        assertTrue(match.matches())
        assertEquals(year, match.group(GitVersionProvider.year))
        assertEquals(imageBreaking, match.group(GitVersionProvider.imageBreaking))
        assertEquals(WPILibBreaking, match.group(GitVersionProvider.WPILibBreaking))
        assertEquals(WPILibNonBreaking, match.group(GitVersionProvider.WPILibNonBreaking))
        assertEquals(numCommits == 0 ? null : "$numCommits".toString(), match.group(GitVersionProvider.commits))
        assertEquals(hash, match.group(GitVersionProvider.sha))
    }

    static def verifyProjectVersion(String gitTag, String time, boolean releaseMode, GString expectedVersion,
                                    afterTag = null, boolean isLocalBuild = false) {
        def tuple = createProjectInstanceWithGit()
        def git = tuple.first
        def project = tuple.second

        project.extensions.getByName('wpilibVersioning').buildServerMode = !isLocalBuild
        project.extensions.getByName('wpilibVersioning').releaseMode = releaseMode

        git.tag.add(name: gitTag, annotate: true)
        if (isLocalBuild) {
            project.extensions.getByName('wpilibVersioning').time.set(time)
        }

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
        assertEquals(expectedVersion, version)
    }

    static def createProjectInstance() {
        def project = ProjectBuilder.builder().build()
        project.pluginManager.apply 'org.wpilib.WPILibVersioningPlugin'
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
        project.pluginManager.apply 'org.wpilib.WPILibVersioningPlugin'
        return new Tuple2<Grgit, Project>(git, project)
    }
}
