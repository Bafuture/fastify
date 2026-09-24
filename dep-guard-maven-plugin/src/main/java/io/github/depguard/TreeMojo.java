package io.github.depguard;

import io.github.depguard.model.AnalysisResult;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.ResolutionScope;

/**
 * Prints the normalized dependency tree of the project, including transitive, optional and
 * provided/test scoped dependencies, with conflict omissions marked.
 */
@Mojo( name = "tree", defaultPhase = LifecyclePhase.VERIFY,
       requiresDependencyResolution = ResolutionScope.TEST, threadSafe = true )
public class TreeMojo extends AbstractDepGuardMojo
{
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
        getLog().info( "\n" + result.getNormalizedTree() );
        writeReport( "dep-guard-tree.txt", result.getNormalizedTree() );
    }
}
