package io.github.depguard;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.graph.DependencyNode;
import org.eclipse.aether.util.graph.transformer.ConflictResolver;

import io.github.depguard.model.VersionConflict;

/**
 * Walks the resolved (aether) dependency graph of a project and extracts:
 * <ul>
 *   <li>a normalized textual dependency tree,</li>
 *   <li>version conflicts together with the version Maven's "nearest wins" rule selected
 *       and the introduction paths of every overridden version,</li>
 *   <li>the list of winning nodes (the effective dependency set).</li>
 * </ul>
 */
public class DependencyGraphAnalyzer
{
    private static final int MAX_PATHS_PER_NODE = 5;

    private static final int MAX_VISITS = 100_000;

    private static class Occurrence
    {
        final DependencyNode node;

        final List<List<DependencyNode>> paths = new ArrayList<>();

        Occurrence( DependencyNode node )
        {
            this.node = node;
        }
    }

    /**
     * Finds every groupId:artifactId(+classifier+extension) that occurs with more than one version.
     * The winner is the node that Maven's conflict resolver kept (nearest-wins); all other versions
     * are reported with the paths through which they were introduced.
     */
    public List<VersionConflict> findVersionConflicts( DependencyNode root )
    {
        Map<DependencyNode, Occurrence> occurrences = new IdentityHashMap<>();
        collect( root, new ArrayList<>(), occurrences, new int[] { MAX_VISITS } );

        Map<String, List<Occurrence>> byConflictKey = new LinkedHashMap<>();
        for ( Occurrence occurrence : occurrences.values() )
        {
            byConflictKey.computeIfAbsent( conflictKey( occurrence.node.getArtifact() ), k -> new ArrayList<>() )
                .add( occurrence );
        }

        List<VersionConflict> conflicts = new ArrayList<>();
        for ( List<Occurrence> group : byConflictKey.values() )
        {
            Map<String, List<Occurrence>> byVersion = new LinkedHashMap<>();
            for ( Occurrence occurrence : group )
            {
                byVersion.computeIfAbsent( occurrence.node.getArtifact().getVersion(), v -> new ArrayList<>() )
                    .add( occurrence );
            }
            if ( byVersion.size() < 2 )
            {
                continue;
            }

            String winnerVersion = null;
            for ( Map.Entry<String, List<Occurrence>> entry : byVersion.entrySet() )
            {
                for ( Occurrence occurrence : entry.getValue() )
                {
                    if ( !isLoser( occurrence.node ) )
                    {
                        winnerVersion = entry.getKey();
                        break;
                    }
                }
                if ( winnerVersion != null )
                {
                    break;
                }
            }
            if ( winnerVersion == null )
            {
                winnerVersion = byVersion.keySet().iterator().next();
            }

            Artifact artifact = group.get( 0 ).node.getArtifact();
            VersionConflict conflict =
                new VersionConflict( artifact.getGroupId(), artifact.getArtifactId(), winnerVersion );
            for ( Map.Entry<String, List<Occurrence>> entry : byVersion.entrySet() )
            {
                if ( entry.getKey().equals( winnerVersion ) )
                {
                    continue;
                }
                for ( Occurrence occurrence : entry.getValue() )
                {
                    for ( List<DependencyNode> path : occurrence.paths )
                    {
                        conflict.addOverriddenPath( entry.getKey(), renderPath( path ) );
                    }
                }
            }
            conflicts.add( conflict );
        }
        return conflicts;
    }

    /**
     * Returns the effective dependency nodes (conflict winners), excluding the project root.
     */
    public List<DependencyNode> collectWinningNodes( DependencyNode root )
    {
        List<DependencyNode> winners = new ArrayList<>();
        IdentityHashMap<DependencyNode, Boolean> seen = new IdentityHashMap<>();
        collectWinners( root, winners, seen );
        return winners;
    }

    private void collectWinners( DependencyNode node, List<DependencyNode> winners,
                                 IdentityHashMap<DependencyNode, Boolean> seen )
    {
        if ( seen.put( node, Boolean.TRUE ) != null )
        {
            return;
        }
        if ( node.getDependency() != null && !isLoser( node ) )
        {
            winners.add( node );
        }
        for ( DependencyNode child : node.getChildren() )
        {
            collectWinners( child, winners, seen );
        }
    }

    /**
     * Renders a normalized textual tree. Nodes omitted by conflict resolution are annotated with
     * the winning version; nodes already expanded are printed as one-liners suffixed with "(*)".
     */
    public String renderTree( DependencyNode root )
    {
        StringBuilder sb = new StringBuilder();
        sb.append( label( root ) ).append( '\n' );
        IdentityHashMap<DependencyNode, Boolean> expanded = new IdentityHashMap<>();
        expanded.put( root, Boolean.TRUE );
        renderChildren( root, "", sb, expanded );
        return sb.toString();
    }

    private void renderChildren( DependencyNode node, String prefix, StringBuilder sb,
                                 IdentityHashMap<DependencyNode, Boolean> expanded )
    {
        List<DependencyNode> children = node.getChildren();
        for ( int i = 0; i < children.size(); i++ )
        {
            DependencyNode child = children.get( i );
            boolean last = i == children.size() - 1;
            boolean alreadyExpanded = expanded.put( child, Boolean.TRUE ) != null;
            sb.append( prefix ).append( last ? "\\- " : "+- " ).append( label( child ) );
            if ( alreadyExpanded )
            {
                sb.append( " (*)" );
            }
            sb.append( '\n' );
            if ( !alreadyExpanded )
            {
                renderChildren( child, prefix + ( last ? "   " : "|  " ), sb, expanded );
            }
        }
    }

    static String label( DependencyNode node )
    {
        Artifact artifact = node.getArtifact();
        StringBuilder label = new StringBuilder();
        label.append( artifact.getGroupId() ).append( ':' ).append( artifact.getArtifactId() ).append( ':' )
            .append( artifact.getExtension() ).append( ':' ).append( artifact.getVersion() );
        Dependency dependency = node.getDependency();
        if ( dependency != null )
        {
            if ( dependency.getScope() != null && !dependency.getScope().isEmpty() )
            {
                label.append( ':' ).append( dependency.getScope() );
            }
            if ( dependency.isOptional() )
            {
                label.append( " (optional)" );
            }
        }
        Object winner = node.getData().get( ConflictResolver.NODE_DATA_WINNER );
        if ( winner instanceof DependencyNode )
        {
            label.append( " (omitted for conflict with " )
                .append( ( (DependencyNode) winner ).getArtifact().getVersion() ).append( ')' );
        }
        return label.toString();
    }

    static boolean isLoser( DependencyNode node )
    {
        return node.getData().get( ConflictResolver.NODE_DATA_WINNER ) != null;
    }

    private void collect( DependencyNode node, List<DependencyNode> path, Map<DependencyNode, Occurrence> acc,
                          int[] budget )
    {
        if ( budget[0] <= 0 )
        {
            return;
        }
        budget[0]--;
        List<DependencyNode> newPath = new ArrayList<>( path );
        newPath.add( node );
        if ( node.getDependency() != null )
        {
            Occurrence occurrence = acc.get( node );
            if ( occurrence == null )
            {
                occurrence = new Occurrence( node );
                acc.put( node, occurrence );
            }
            if ( occurrence.paths.size() < MAX_PATHS_PER_NODE )
            {
                occurrence.paths.add( newPath );
            }
        }
        for ( DependencyNode child : node.getChildren() )
        {
            collect( child, newPath, acc, budget );
        }
    }

    private static String conflictKey( Artifact artifact )
    {
        return artifact.getGroupId() + ':' + artifact.getArtifactId() + ':' + artifact.getClassifier() + ':'
            + artifact.getExtension();
    }

    private static String renderPath( List<DependencyNode> path )
    {
        StringBuilder sb = new StringBuilder();
        for ( DependencyNode node : path )
        {
            if ( sb.length() > 0 )
            {
                sb.append( " -> " );
            }
            Artifact artifact = node.getArtifact();
            sb.append( artifact.getGroupId() ).append( ':' ).append( artifact.getArtifactId() ).append( ':' )
                .append( artifact.getVersion() );
        }
        return sb.toString();
    }
}
