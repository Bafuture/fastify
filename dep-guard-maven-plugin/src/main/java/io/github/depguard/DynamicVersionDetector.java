package io.github.depguard;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.apache.maven.model.DependencyManagement;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.project.MavenProject;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.graph.DependencyNode;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.ArtifactDescriptorException;
import org.eclipse.aether.resolution.ArtifactDescriptorRequest;
import org.eclipse.aether.resolution.ArtifactDescriptorResult;

import io.github.depguard.model.DynamicVersionViolation;

/**
 * Detects non-reproducible version declarations: {@code LATEST}, {@code RELEASE} and version ranges
 * such as {@code [1.0,)}. Both the project's own POM and the descriptors of every resolved
 * (transitive) artifact are inspected.
 */
public class DynamicVersionDetector
{
    public List<DynamicVersionViolation> detect( MavenProject project, List<DependencyNode> winningNodes,
                                                 RepositorySystem repositorySystem,
                                                 RepositorySystemSession session,
                                                 List<RemoteRepository> repositories, Log log )
    {
        List<DynamicVersionViolation> violations = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();

        for ( org.apache.maven.model.Dependency dependency : project.getDependencies() )
        {
            check( violations, seen, "project <dependencies>", dependency.getGroupId(), dependency.getArtifactId(),
                   dependency.getVersion() );
        }
        DependencyManagement management = project.getDependencyManagement();
        if ( management != null )
        {
            for ( org.apache.maven.model.Dependency dependency : management.getDependencies() )
            {
                check( violations, seen, "project <dependencyManagement>", dependency.getGroupId(),
                       dependency.getArtifactId(), dependency.getVersion() );
            }
        }

        for ( DependencyNode node : winningNodes )
        {
            org.eclipse.aether.artifact.Artifact artifact = node.getArtifact();
            String source = artifact.getGroupId() + ":" + artifact.getArtifactId() + ":" + artifact.getVersion();
            try
            {
                ArtifactDescriptorResult descriptor = repositorySystem.readArtifactDescriptor( session,
                    new ArtifactDescriptorRequest( artifact, repositories, "dep-guard" ) );
                for ( org.eclipse.aether.graph.Dependency dependency : descriptor.getDependencies() )
                {
                    check( violations, seen, source + " <dependencies>", dependency.getArtifact().getGroupId(),
                           dependency.getArtifact().getArtifactId(), dependency.getArtifact().getVersion() );
                }
                for ( org.eclipse.aether.graph.Dependency dependency : descriptor.getManagedDependencies() )
                {
                    check( violations, seen, source + " <dependencyManagement>",
                           dependency.getArtifact().getGroupId(), dependency.getArtifact().getArtifactId(),
                           dependency.getArtifact().getVersion() );
                }
            }
            catch ( ArtifactDescriptorException e )
            {
                log.debug( "Could not read descriptor of " + source + ": " + e.getMessage() );
            }
        }
        return violations;
    }

    static boolean isDynamic( String version )
    {
        if ( version == null )
        {
            return false;
        }
        String trimmed = version.trim();
        return "LATEST".equalsIgnoreCase( trimmed ) || "RELEASE".equalsIgnoreCase( trimmed )
            || trimmed.indexOf( '[' ) >= 0 || trimmed.indexOf( '(' ) >= 0;
    }

    private static void check( List<DynamicVersionViolation> violations, Set<String> seen, String source,
                               String groupId, String artifactId, String version )
    {
        if ( !isDynamic( version ) )
        {
            return;
        }
        String key = source + '|' + groupId + ':' + artifactId + ':' + version;
        if ( seen.add( key ) )
        {
            violations.add( new DynamicVersionViolation( source, groupId + ":" + artifactId, version ) );
        }
    }
}
