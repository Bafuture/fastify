package io.github.depguard;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.Arrays;
import java.util.List;

import io.github.depguard.model.AnalysisResult;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.repository.RemoteRepository;

/** Shared configuration and analysis plumbing for the dep-guard goals. */
public abstract class AbstractDepGuardMojo extends AbstractMojo
{
    @Parameter( defaultValue = "${project}", readonly = true, required = true )
    protected MavenProject project;

    @Parameter( defaultValue = "${session}", readonly = true, required = true )
    protected MavenSession session;

    @Parameter( defaultValue = "${project.remoteProjectRepositories}", readonly = true )
    protected List<RemoteRepository> remoteRepositories;

    /** Directory where reports are written. */
    @Parameter( property = "depguard.outputDirectory",
                defaultValue = "${project.build.directory}/dep-guard" )
    protected File outputDirectory;

    /** Skip all dep-guard checks. */
    @Parameter( property = "depguard.skip", defaultValue = "false" )
    protected boolean skip;

    /**
     * Expected dependency versions, each in the form {@code groupId:artifactId:version}. Every
     * occurrence of a listed coordinate must resolve to exactly this version.
     */
    @Parameter
    protected List<String> expectedVersions;

    /**
     * Whitelisted dependency coordinates. Entries are {@code groupId:artifactId} or
     * {@code groupId:artifactId:version} and may contain {@code *} wildcards. Whitelisted
     * coordinates are excluded from version-conflict and convergence counting.
     */
    @Parameter
    protected List<String> whitelistCoordinates;

    /** Whitelisted fully qualified class names (may contain {@code *} wildcards). */
    @Parameter
    protected List<String> whitelistClasses;

    /** Jar scopes considered for duplicate-class detection. */
    @Parameter
    protected List<String> duplicateCheckScopes;

    private RepositorySystem repositorySystem;

    /** Test hook: the plugin-testing harness cannot always inject the aether RepositorySystem. */
    public void setRepositorySystem( RepositorySystem repositorySystem )
    {
        this.repositorySystem = repositorySystem;
    }

    protected RepositorySystem getRepositorySystem()
        throws MojoExecutionException
    {
        if ( repositorySystem == null )
        {
            try
            {
                repositorySystem = session.getContainer().lookup( RepositorySystem.class );
            }
            catch ( Exception e )
            {
                throw new MojoExecutionException( "could not look up RepositorySystem: " + e.getMessage(), e );
            }
        }
        return repositorySystem;
    }

    protected List<String> duplicateCheckScopes()
    {
        if ( duplicateCheckScopes == null || duplicateCheckScopes.isEmpty() )
        {
            return Arrays.asList( "compile", "runtime", "provided" );
        }
        return duplicateCheckScopes;
    }

    protected AnalysisResult analyze()
        throws MojoExecutionException
    {
        DependencyGraphAnalyzer analyzer = new DependencyGraphAnalyzer( getRepositorySystem(),
            session.getRepositorySession(), remoteRepositories );
        AnalysisResult result = analyzer.analyze( project );
        new ConvergenceChecker().check( result, expectedVersions );
        applyCoordinateWhitelist( result );
        result.getDuplicateClasses().addAll( new DuplicateClassDetector( getRepositorySystem(),
            session.getRepositorySession(), remoteRepositories, duplicateCheckScopes() )
                .detect( result.getRoot(), whitelistClasses, result.getWarnings() ) );
        return result;
    }

    /**
     * Falls back to {@code <project-dir>/target/dep-guard} when the
     * {@code project.build.directory} expression was not interpolated (e.g. under the
     * plugin-testing harness with a model-only project).
     */
    private void resolveOutputDirectory()
    {
        if ( outputDirectory != null && outputDirectory.getPath().indexOf( '$' ) < 0 )
        {
            return;
        }
        String buildDir = project.getBuild() != null && project.getBuild().getDirectory() != null
            ? project.getBuild().getDirectory()
            : "target";
        File dir = new File( buildDir );
        if ( !dir.isAbsolute() )
        {
            File basedir = project.getBasedir() != null ? project.getBasedir() : new File( "" );
            dir = new File( basedir, buildDir );
        }
        outputDirectory = new File( dir, "dep-guard" );
    }

    private void applyCoordinateWhitelist( AnalysisResult result )
    {
        if ( whitelistCoordinates == null || whitelistCoordinates.isEmpty() )
        {
            return;
        }
        java.util.Iterator<io.github.depguard.model.VersionConflict> conflicts =
            result.getVersionConflicts().iterator();
        while ( conflicts.hasNext() )
        {
            io.github.depguard.model.VersionConflict conflict = conflicts.next();
            for ( io.github.depguard.model.VersionConflict.Occurrence occurrence : conflict.getOccurrences() )
            {
                if ( Patterns.matchesAny( whitelistCoordinates, conflict.getCoordinate() )
                    || Patterns.matchesAny( whitelistCoordinates,
                                            conflict.getCoordinate() + ":" + occurrence.getVersion() ) )
                {
                    conflicts.remove();
                    break;
                }
            }
        }
        java.util.Iterator<io.github.depguard.model.ConvergenceViolation> violations =
            result.getConvergenceViolations().iterator();
        while ( violations.hasNext() )
        {
            io.github.depguard.model.ConvergenceViolation violation = violations.next();
            if ( Patterns.matchesAny( whitelistCoordinates, violation.getCoordinate() )
                || Patterns.matchesAny( whitelistCoordinates,
                                        violation.getCoordinate() + ":" + violation.getActualVersion() ) )
            {
                violations.remove();
            }
        }
    }

    protected void writeReport( String name, String content )
        throws MojoExecutionException
    {
        resolveOutputDirectory();
        if ( !outputDirectory.exists() && !outputDirectory.mkdirs() )
        {
            throw new MojoExecutionException( "could not create " + outputDirectory );
        }
        File file = new File( outputDirectory, name );
        Writer writer = null;
        try
        {
            writer = new OutputStreamWriter( new FileOutputStream( file ), "UTF-8" );
            writer.write( content );
        }
        catch ( Exception e )
        {
            throw new MojoExecutionException( "could not write " + file + ": " + e.getMessage(), e );
        }
        finally
        {
            if ( writer != null )
            {
                try
                {
                    writer.close();
                }
                catch ( Exception ignored )
                {
                    // ignore
                }
            }
        }
        getLog().info( "wrote " + file );
    }
}
