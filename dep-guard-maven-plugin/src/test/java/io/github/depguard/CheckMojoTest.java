package io.github.depguard;

import java.io.File;
import java.util.List;

import org.apache.maven.plugin.MojoExecutionException;

import io.github.depguard.model.AnalysisResult;
import io.github.depguard.model.ConvergenceViolation;
import io.github.depguard.model.DuplicateClassInfo;
import io.github.depguard.model.DynamicVersionViolation;
import io.github.depguard.model.VersionConflict;

public class CheckMojoTest extends AbstractDepGuardMojoTestCase
{
    public void testVersionConflictDetectedWithNearestWinsAndPaths()
        throws Exception
    {
        CheckMojo mojo = lookupCheckMojo( "conflict-project" );
        mojo.execute();

        AnalysisResult result = mojo.getResult();
        assertNotNull( result );
        assertEquals( 1, result.getVersionConflicts().size() );

        VersionConflict conflict = result.getVersionConflicts().get( 0 );
        assertEquals( "com.sample:lib-a", conflict.getCoordinate() );
        assertEquals( "1.0", conflict.getWinnerVersion() );
        assertTrue( conflict.getOverridden().containsKey( "2.0" ) );

        List<String> paths = conflict.getOverridden().get( "2.0" );
        assertFalse( paths.isEmpty() );
        boolean viaLibB = false;
        for ( String path : paths )
        {
            if ( path.contains( "com.sample:lib-b:1.0" ) && path.endsWith( "com.sample:lib-a:2.0" ) )
            {
                viaLibB = true;
            }
        }
        assertTrue( "overridden version 2.0 must be introduced via lib-b", viaLibB );

        // reports are written
        File reportDir = new File( getBasedir(), "src/test/resources/it/conflict-project/target/dep-guard" );
        assertTrue( new File( reportDir, "dep-guard-report.txt" ).isFile() );
        File json = new File( reportDir, "dep-guard-report.json" );
        assertTrue( json.isFile() );
        String jsonContent = new String( java.nio.file.Files.readAllBytes( json.toPath() ), "UTF-8" );
        assertTrue( jsonContent.contains( "\"versionConflicts\"" ) );
        assertTrue( jsonContent.contains( "\"selectedVersion\": \"1.0\"" ) );
    }

    public void testDuplicateClassesDistinguishIdenticalFromDifferent()
        throws Exception
    {
        CheckMojo mojo = lookupCheckMojo( "conflict-project" );
        mojo.execute();

        AnalysisResult result = mojo.getResult();
        DuplicateClassInfo foo = null;
        for ( DuplicateClassInfo info : result.getDuplicateClasses() )
        {
            if ( "com.sample.shared.Foo".equals( info.getClassName() ) )
            {
                foo = info;
            }
        }
        assertNotNull( "com.sample.shared.Foo must be reported as duplicate", foo );
        assertFalse( "lib-b carries a different implementation", foo.isIdentical() );
        assertEquals( 3, foo.getArtifacts().size() );

        String hashA = null;
        String hashB = null;
        String hashC = null;
        for ( DuplicateClassInfo.ArtifactHash artifact : foo.getArtifacts() )
        {
            if ( artifact.getCoordinates().equals( "com.sample:lib-a:1.0" ) )
            {
                hashA = artifact.getSha256();
            }
            else if ( artifact.getCoordinates().equals( "com.sample:lib-b:1.0" ) )
            {
                hashB = artifact.getSha256();
            }
            else if ( artifact.getCoordinates().equals( "com.sample:lib-c:1.0" ) )
            {
                hashC = artifact.getSha256();
            }
        }
        assertNotNull( hashA );
        assertNotNull( hashB );
        assertNotNull( hashC );
        assertEquals( "lib-a and lib-c carry byte-identical copies", hashA, hashC );
        assertFalse( "lib-b carries a different implementation", hashA.equals( hashB ) );
    }

    public void testWhitelistSuppressesViolations()
        throws Exception
    {
        CheckMojo mojo = lookupCheckMojo( "whitelist-project" );
        mojo.execute();

        AnalysisResult result = mojo.getResult();
        assertNotNull( result );
        assertTrue( "version conflict must be whitelisted", result.getVersionConflicts().isEmpty() );
        assertTrue( "duplicate class must be whitelisted", result.getDuplicateClasses().isEmpty() );
        assertEquals( 1, result.getIgnoredVersionConflicts() );
        assertEquals( 1, result.getIgnoredDuplicateClasses() );
    }

    public void testGateFailsBuildOnVersionConflict()
        throws Exception
    {
        CheckMojo mojo = lookupCheckMojo( "gate-project" );
        try
        {
            mojo.execute();
            fail( "expected MojoExecutionException because failOnVersionConflict=true" );
        }
        catch ( MojoExecutionException expected )
        {
            assertTrue( expected.getMessage().contains( "version conflict" ) );
        }
    }

    public void testConvergenceAndDynamicVersionDetection()
        throws Exception
    {
        CheckMojo mojo = lookupCheckMojo( "convergence-project" );
        mojo.execute();

        AnalysisResult result = mojo.getResult();
        assertNotNull( result );

        boolean undeclaredLibD = false;
        for ( ConvergenceViolation violation : result.getConvergenceViolations() )
        {
            if ( "com.sample:lib-d".equals( violation.getCoordinate() ) )
            {
                undeclaredLibD = true;
            }
        }
        assertTrue( "lib-d is not declared in expectedVersions and must be flagged", undeclaredLibD );

        boolean rangeDetected = false;
        for ( DynamicVersionViolation violation : result.getDynamicVersions() )
        {
            if ( "com.sample:lib-a".equals( violation.getCoordinate() )
                && "[1.0,2.0)".equals( violation.getDeclaredVersion() )
                && violation.getSource().startsWith( "com.sample:lib-d:1.0" ) )
            {
                rangeDetected = true;
            }
        }
        assertTrue( "version range [1.0,2.0) declared by lib-d must be flagged", rangeDetected );
    }
}
