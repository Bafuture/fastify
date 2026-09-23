package io.github.depguard;

import java.io.File;
import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.eclipse.aether.graph.DependencyNode;

/**
 * Prints the normalized dependency tree of the current project (transitive, optional and
 * provided/test scoped dependencies included; conflict-omitted nodes are annotated).
 */
@Mojo( name = "tree", threadSafe = true )
public class TreeMojo extends AbstractDepGuardMojo
{
    /** File the rendered tree is written to. */
    @Parameter( property = "depguard.treeFile",
                defaultValue = "${project.build.directory}/dep-guard/dep-guard-tree.txt" )
    private File treeFile;

    private String treeText;

    @Override
    public void execute()
        throws MojoExecutionException
    {
        DependencyNode root = resolveDependencyGraph();
        treeText = new DependencyGraphAnalyzer().renderTree( root );
        getLog().info( System.lineSeparator() + treeText );
        if ( treeFile != null )
        {
            try
            {
                Files.createDirectories( treeFile.getParentFile().toPath() );
                try ( Writer writer = Files.newBufferedWriter( treeFile.toPath(), StandardCharsets.UTF_8 ) )
                {
                    writer.write( treeText );
                }
            }
            catch ( IOException e )
            {
                throw new MojoExecutionException( "Could not write dependency tree: " + e.getMessage(), e );
            }
        }
    }

    /** Exposed for tests. */
    String getTreeText()
    {
        return treeText;
    }
}
