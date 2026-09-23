package io.github.depguard;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.DependencyManagement;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.collection.CollectRequest;
import org.eclipse.aether.graph.DependencyNode;
import org.eclipse.aether.graph.Exclusion;
import org.eclipse.aether.resolution.DependencyRequest;
import org.eclipse.aether.resolution.DependencyResolutionException;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.util.graph.transformer.ConflictResolver;

/**
 * Base class for the dep-guard goals: resolves the full dependency graph of the current project
 * (including transitive, optional and provided/test scoped dependencies, honouring exclusions)
 * through the aether {@link RepositorySystem}.
 */
public abstract class AbstractDepGuardMojo extends AbstractMojo
{
    @Parameter( defaultValue = "${project}", readonly = true, required = true )
    protected MavenProject project;

    @Parameter( defaultValue = "${session}", readonly = true, required = true )
    protected MavenSession session;

    private RepositorySystem repositorySystem;

    private RepositorySystemSession repositorySession;

    /**
     * Allows tests (or embedding code) to supply the repository system directly. When unset, the
     * component is looked up from the Maven session at runtime.
     */
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
                throw new MojoExecutionException( "Could not look up RepositorySystem: " + e.getMessage(), e );
            }
        }
        return repositorySystem;
    }

    /**
     * Returns a session that keeps conflict losers in the graph (verbose conflict resolution), so
     * that overridden versions remain visible for analysis. The user's own session is not mutated;
     * a copy-on-write wrapper is created on first use.
     */
    protected RepositorySystemSession getRepositorySession()
    {
        if ( repositorySession == null )
        {
            RepositorySystemSession delegate = session.getRepositorySession();
            if ( delegate.getConfigProperties().get( ConflictResolver.CONFIG_PROP_VERBOSE ) == null )
            {
                DefaultRepositorySystemSession verbose = new DefaultRepositorySystemSession( delegate );
                verbose.setConfigProperty( ConflictResolver.CONFIG_PROP_VERBOSE, Boolean.TRUE );
                delegate = verbose;
            }
            repositorySession = delegate;
        }
        return repositorySession;
    }

    protected DependencyNode resolveDependencyGraph()
        throws MojoExecutionException
    {
        try
        {
            CollectRequest collectRequest = new CollectRequest();
            org.eclipse.aether.artifact.Artifact rootArtifact = new DefaultArtifact( project.getGroupId(),
                project.getArtifactId(), "pom", project.getVersion() );
            // mark the project itself as already resolved so the resolver does not look it up
            rootArtifact = rootArtifact.setFile( project.getFile() );
            collectRequest.setRootArtifact( rootArtifact );
            for ( org.apache.maven.model.Dependency dependency : project.getDependencies() )
            {
                collectRequest.addDependency( toAether( dependency ) );
            }
            DependencyManagement management = project.getDependencyManagement();
            if ( management != null )
            {
                for ( org.apache.maven.model.Dependency dependency : management.getDependencies() )
                {
                    collectRequest.addManagedDependency( toAether( dependency ) );
                }
            }
            List<org.eclipse.aether.repository.RemoteRepository> repositories =
                project.getRemoteProjectRepositories();
            collectRequest.setRepositories(
                repositories != null ? repositories : Collections.emptyList() );

            DependencyRequest request = new DependencyRequest( collectRequest, null );
            return getRepositorySystem()
                .resolveDependencies( getRepositorySession(), request ).getRoot();
        }
        catch ( DependencyResolutionException e )
        {
            throw new MojoExecutionException( "Failed to resolve project dependencies: " + e.getMessage(), e );
        }
    }

    private static org.eclipse.aether.graph.Dependency toAether( org.apache.maven.model.Dependency dependency )
    {
        String scope = dependency.getScope();
        if ( scope == null || scope.isEmpty() )
        {
            scope = "compile";
        }
        boolean optional = dependency.isOptional() || "true".equals( dependency.getOptional() );
        List<Exclusion> exclusions = new ArrayList<>();
        if ( dependency.getExclusions() != null )
        {
            for ( org.apache.maven.model.Exclusion exclusion : dependency.getExclusions() )
            {
                exclusions.add( new Exclusion( exclusion.getGroupId(), exclusion.getArtifactId(), "*", "*" ) );
            }
        }
        String version = dependency.getVersion() == null ? "" : dependency.getVersion();
        DefaultArtifact artifact = new DefaultArtifact( dependency.getGroupId(), dependency.getArtifactId(),
            dependency.getClassifier(), dependency.getType() == null ? "jar" : dependency.getType(), version );
        return new org.eclipse.aether.graph.Dependency( artifact, scope, optional, exclusions );
    }
}
