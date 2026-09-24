package io.github.depguard;

import org.apache.maven.plugin.MojoExecutionException;

/** Integration tests for the {@code check} goal covering the four required scenarios. */
public class CheckMojoTest extends AbstractDepGuardMojoTestCase
{
    /** Scenario 1: a version conflict is detected, the nearest version wins, the loser is listed. */
    public void testVersionConflictDetected()
        throws Exception
    {
        CheckMojo mojo = lookupCheckMojo( "conflict-project" );
        mojo.execute();

        String json = readReport( "conflict-project", "dep-guard-report.json" );
        assertTrue( json.contains( "\"coordinate\": \"com.acme:lib-common\"" ) );
        assertTrue( json.contains( "\"selectedVersion\": \"1.0\"" ) );
        assertTrue( json.contains( "\"versionConflicts\": 1" ) );
        // the overridden 2.0 must appear with its introduction path via lib-y
        assertTrue( json.contains( "lib-y:1.0 -> com.acme:lib-common:2.0" ) );

        String text = readReport( "conflict-project", "dep-guard-report.txt" );
        assertTrue( text.contains( "[overridden]" ) );
        // fix suggestion: dependencyManagement pinning the selected version
        assertTrue( text.contains( "<dependencyManagement>" ) );
        assertTrue( text.contains( "<artifactId>lib-common</artifactId>" ) );
        assertTrue( text.contains( "<version>1.0</version>" ) );
    }

    /** Scenario 2: duplicate classes are found and identical vs different content is told apart. */
    public void testDuplicateClassesDetected()
        throws Exception
    {
        CheckMojo mojo = lookupCheckMojo( "duplicates-project" );
        mojo.execute();

        String json = readReport( "duplicates-project", "dep-guard-report.json" );
        assertTrue( json.contains( "\"className\": \"com.acme.dup.Clash\"" ) );
        assertTrue( json.contains( "\"className\": \"com.acme.dup.Same\"" ) );
        assertTrue( json.contains( "\"duplicateClasses\": 2" ) );

        String text = readReport( "duplicates-project", "dep-guard-report.txt" );
        assertTrue( text.contains( "com.acme.dup.Clash [DIFFERENT implementations]" ) );
        assertTrue( text.contains( "com.acme.dup.Same [identical content]" ) );
        assertTrue( text.contains( "com.acme:lib-dup-a:1.0" ) );
        assertTrue( text.contains( "com.acme:lib-dup-b:1.0" ) );
    }

    /** Scenario 3: a whitelisted coordinate is excluded from the conflict count. */
    public void testWhitelistSuppressesConflict()
        throws Exception
    {
        CheckMojo mojo = lookupCheckMojo( "whitelist-project" );
        mojo.execute(); // must not throw

        String json = readReport( "whitelist-project", "dep-guard-report.json" );
        assertTrue( json.contains( "\"versionConflicts\": 0" ) );
        assertTrue( json.contains( "\"gatePassed\": true" ) );
    }

    /** Scenario 4: maxVersionConflicts=0 turns the same conflict into a build failure. */
    public void testGateFailsBuild()
        throws Exception
    {
        CheckMojo mojo = lookupCheckMojo( "gate-project" );
        try
        {
            mojo.execute();
            fail( "expected MojoExecutionException from the gate" );
        }
        catch ( MojoExecutionException expected )
        {
            assertTrue( expected.getMessage().contains( "version conflicts" ) );
        }

        String json = readReport( "gate-project", "dep-guard-report.json" );
        assertTrue( json.contains( "\"gatePassed\": false" ) );
        assertTrue( json.contains( "version conflicts: found 1, allowed 0" ) );
    }
}
