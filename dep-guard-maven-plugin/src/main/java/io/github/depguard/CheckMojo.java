package io.github.depguard;

import java.util.ArrayList;
import java.util.List;

import io.github.depguard.model.AnalysisResult;
import io.github.depguard.model.DuplicateClassInfo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;

/**
 * Runs all dep-guard checks (version conflicts, dependency convergence, dynamic versions,
 * duplicate classes), writes text and JSON reports and fails the build when the configured
 * thresholds are exceeded.
 */
@Mojo( name = "check", defaultPhase = LifecyclePhase.VERIFY,
       requiresDependencyResolution = ResolutionScope.TEST, threadSafe = true )
public class CheckMojo extends AbstractDepGuardMojo
{
    /** Maximum allowed version conflicts before the build fails; -1 disables the gate. */
    @Parameter( property = "depguard.maxVersionConflicts", defaultValue = "-1" )
    private int maxVersionConflicts;

    /** Maximum allowed convergence violations; -1 disables the gate. */
    @Parameter( property = "depguard.maxConvergenceViolations", defaultValue = "-1" )
    private int maxConvergenceViolations;

    /** Maximum allowed dynamic/non-reproducible versions; -1 disables the gate. */
    @Parameter( property = "depguard.maxDynamicVersions", defaultValue = "-1" )
    private int maxDynamicVersions;

    /** Maximum allowed duplicate classes with differing implementations; -1 disables the gate. */
    @Parameter( property = "depguard.maxDuplicateClassConflicts", defaultValue = "-1" )
    private int maxDuplicateClassConflicts;

    @Override
    public void execute()
        throws MojoExecutionException
    {
        if ( skip )
        {
            getLog().info( "dep-guard skipped" );
            return;
        }
        AnalysisResult result = analyze();
        List<String> gateFailures = evaluateGate( result );

        ReportWriter reporter = new ReportWriter();
        String text = reporter.renderText( result, gateFailures );
        writeReport( "dep-guard-report.txt", text );
        writeReport( "dep-guard-report.json", reporter.renderJson( result, gateFailures ) );
        writeReport( "dep-guard-tree.txt", result.getNormalizedTree() );
        getLog().info( "\n" + text );

        if ( !gateFailures.isEmpty() )
        {
            StringBuilder message = new StringBuilder( "dep-guard gate failed:" );
            for ( String failure : gateFailures )
            {
                message.append( "\n  - " ).append( failure );
            }
            throw new MojoExecutionException( message.toString() );
        }
    }

    private List<String> evaluateGate( AnalysisResult result )
    {
        List<String> failures = new ArrayList<String>();
        check( failures, "version conflicts", result.getVersionConflicts().size(), maxVersionConflicts );
        check( failures, "convergence violations", result.getConvergenceViolations().size(),
               maxConvergenceViolations );
        check( failures, "dynamic versions", result.getDynamicVersionViolations().size(),
               maxDynamicVersions );
        int conflicting = 0;
        for ( DuplicateClassInfo info : result.getDuplicateClasses() )
        {
            if ( !info.isIdentical() )
            {
                conflicting++;
            }
        }
        check( failures, "duplicate classes with different implementations", conflicting,
               maxDuplicateClassConflicts );
        return failures;
    }

    private static void check( List<String> failures, String label, int actual, int max )
    {
        if ( max >= 0 && actual > max )
        {
            failures.add( label + ": found " + actual + ", allowed " + max );
        }
    }
}
