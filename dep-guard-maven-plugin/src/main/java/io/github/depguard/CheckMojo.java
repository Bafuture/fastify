package io.github.depguard;

import java.io.File;
import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.eclipse.aether.graph.DependencyNode;

import io.github.depguard.model.AnalysisResult;
import io.github.depguard.model.ConvergenceViolation;
import io.github.depguard.model.DuplicateClassInfo;
import io.github.depguard.model.DynamicVersionViolation;
import io.github.depguard.model.VersionConflict;

/**
 * Runs all dep-guard checks against the current project: version conflicts, dependency
 * convergence, dynamic versions and duplicate classes. Emits a text and a JSON report and
 * optionally fails the build when configurable thresholds are exceeded.
 */
@Mojo( name = "check", defaultPhase = LifecyclePhase.VERIFY, threadSafe = true )
public class CheckMojo extends AbstractDepGuardMojo
{
    /** Skip all checks. */
    @Parameter( property = "depguard.skip", defaultValue = "false" )
    private boolean skip;

    /** Directory where dep-guard-report.txt / dep-guard-report.json are written. */
    @Parameter( property = "depguard.outputDirectory", defaultValue = "${project.build.directory}/dep-guard" )
    private File outputDirectory;

    /**
     * Expected dependency versions, each {@code groupId:artifactId:version} (exact) or
     * {@code groupId:artifactId} (any version). When non-empty, every resolved dependency must be
     * listed here.
     */
    @Parameter
    private List<String> expectedVersions = new ArrayList<>();

    /**
     * Whitelist of coordinates excluded from version-conflict detection. Entries are
     * {@code groupId:artifactId} or {@code groupId:artifactId:version}; {@code *} is a wildcard.
     */
    @Parameter
    private List<String> ignoredDependencies = new ArrayList<>();

    /** Whitelist of fully-qualified class names excluded from duplicate-class detection. */
    @Parameter
    private List<String> ignoredClasses = new ArrayList<>();

    /** Fail the build when the number of version conflicts exceeds {@link #maxVersionConflicts}. */
    @Parameter( property = "depguard.failOnVersionConflict", defaultValue = "false" )
    private boolean failOnVersionConflict;

    /** Maximum tolerated version conflicts when {@link #failOnVersionConflict} is enabled. */
    @Parameter( property = "depguard.maxVersionConflicts", defaultValue = "0" )
    private int maxVersionConflicts;

    /** Fail the build when the number of duplicate classes exceeds {@link #maxDuplicateClasses}. */
    @Parameter( property = "depguard.failOnDuplicateClass", defaultValue = "false" )
    private boolean failOnDuplicateClass;

    /** Maximum tolerated duplicate classes when {@link #failOnDuplicateClass} is enabled. */
    @Parameter( property = "depguard.maxDuplicateClasses", defaultValue = "0" )
    private int maxDuplicateClasses;

    /** Fail the build on any convergence violation (requires {@link #expectedVersions}). */
    @Parameter( property = "depguard.failOnConvergence", defaultValue = "false" )
    private boolean failOnConvergence;

    /** Fail the build on any dynamic version (LATEST / RELEASE / version range). */
    @Parameter( property = "depguard.failOnDynamicVersion", defaultValue = "false" )
    private boolean failOnDynamicVersion;

    private AnalysisResult result;

    @Override
    public void execute()
        throws MojoExecutionException
    {
        if ( skip )
        {
            getLog().info( "dep-guard checks are skipped." );
            return;
        }

        DependencyNode root = resolveDependencyGraph();
        DependencyGraphAnalyzer analyzer = new DependencyGraphAnalyzer();

        String tree = analyzer.renderTree( root );
        List<DependencyNode> winningNodes = analyzer.collectWinningNodes( root );

        List<VersionConflict> conflicts = analyzer.findVersionConflicts( root );
        List<DynamicVersionViolation> dynamicVersions = new DynamicVersionDetector().detect( project,
            winningNodes, getRepositorySystem(), getRepositorySession(),
            project.getRemoteProjectRepositories(), getLog() );
        List<ConvergenceViolation> convergenceViolations =
            new ConvergenceChecker().check( winningNodes, expectedVersions );
        List<DuplicateClassInfo> duplicateClasses =
            new DuplicateClassDetector().detect( winningNodes, getLog() );

        int ignoredConflicts = 0;
        List<VersionConflict> keptConflicts = new ArrayList<>();
        for ( VersionConflict conflict : conflicts )
        {
            if ( Patterns.matchesAny( ignoredDependencies, conflict.getCoordinate() )
                || Patterns.matchesAny( ignoredDependencies,
                                        conflict.getCoordinate() + ":" + conflict.getWinnerVersion() ) )
            {
                ignoredConflicts++;
            }
            else
            {
                keptConflicts.add( conflict );
            }
        }

        int ignoredDuplicates = 0;
        List<DuplicateClassInfo> keptDuplicates = new ArrayList<>();
        for ( DuplicateClassInfo duplicate : duplicateClasses )
        {
            if ( Patterns.matchesAny( ignoredClasses, duplicate.getClassName() ) )
            {
                ignoredDuplicates++;
            }
            else
            {
                keptDuplicates.add( duplicate );
            }
        }

        AnalysisResult analysis = new AnalysisResult();
        analysis.setDependencyTree( tree );
        analysis.setVersionConflicts( keptConflicts );
        analysis.setDynamicVersions( dynamicVersions );
        analysis.setConvergenceViolations( convergenceViolations );
        analysis.setDuplicateClasses( keptDuplicates );
        analysis.setIgnoredVersionConflicts( ignoredConflicts );
        analysis.setIgnoredDuplicateClasses( ignoredDuplicates );
        this.result = analysis;

        String projectCoordinates = project.getGroupId() + ":" + project.getArtifactId() + ":"
            + project.getVersion();
        ReportWriter reportWriter = new ReportWriter();
        String textReport = reportWriter.buildTextReport( analysis, projectCoordinates );
        String jsonReport = reportWriter.buildJsonReport( analysis, projectCoordinates );

        getLog().info( System.lineSeparator() + textReport );
        writeReports( textReport, jsonReport );

        List<String> failures = new ArrayList<>();
        if ( failOnVersionConflict && keptConflicts.size() > maxVersionConflicts )
        {
            failures.add( keptConflicts.size() + " version conflict(s) found (allowed: "
                + maxVersionConflicts + ")" );
        }
        if ( failOnDuplicateClass && keptDuplicates.size() > maxDuplicateClasses )
        {
            failures.add( keptDuplicates.size() + " duplicate class(es) found (allowed: "
                + maxDuplicateClasses + ")" );
        }
        if ( failOnConvergence && !convergenceViolations.isEmpty() )
        {
            failures.add( convergenceViolations.size() + " convergence violation(s) found" );
        }
        if ( failOnDynamicVersion && !dynamicVersions.isEmpty() )
        {
            failures.add( dynamicVersions.size() + " dynamic version(s) found" );
        }
        if ( !failures.isEmpty() )
        {
            throw new MojoExecutionException( "dep-guard gate failed: " + String.join( "; ", failures )
                + ". See " + new File( outputDirectory, "dep-guard-report.txt" ) );
        }
    }

    private void writeReports( String textReport, String jsonReport )
        throws MojoExecutionException
    {
        if ( outputDirectory == null )
        {
            return;
        }
        try
        {
            Files.createDirectories( outputDirectory.toPath() );
            write( new File( outputDirectory, "dep-guard-report.txt" ), textReport );
            write( new File( outputDirectory, "dep-guard-report.json" ), jsonReport );
        }
        catch ( IOException e )
        {
            throw new MojoExecutionException( "Could not write dep-guard reports: " + e.getMessage(), e );
        }
    }

    private static void write( File file, String content )
        throws IOException
    {
        try ( Writer writer = Files.newBufferedWriter( file.toPath(), StandardCharsets.UTF_8 ) )
        {
            writer.write( content );
        }
    }

    /** Exposed for tests. */
    AnalysisResult getResult()
    {
        return result;
    }
}
