package io.github.depguard;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import io.github.depguard.model.AnalysisResult;
import io.github.depguard.model.DepNode;
import io.github.depguard.model.DynamicVersionViolation;
import io.github.depguard.model.VersionConflict;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.DependencyManagement;
import org.apache.maven.model.Exclusion;
import org.apache.maven.project.MavenProject;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.ArtifactDescriptorRequest;
import org.eclipse.aether.resolution.ArtifactDescriptorResult;
import org.eclipse.aether.resolution.VersionRangeRequest;
import org.eclipse.aether.resolution.VersionRangeResult;
import org.eclipse.aether.resolution.VersionRequest;
import org.eclipse.aether.resolution.VersionResult;
import org.eclipse.aether.version.Version;

/**
 * Walks the full dependency graph of a project by hand: reads each artifact's descriptor (POM),
 * applies scope transitivity, optional handling, exclusions and the root project's
 * dependencyManagement, and records every occurrence of every {@code groupId:artifactId} together
 * with its introduction path. Version conflicts are then resolved with Maven's "nearest wins"
 * rule (shortest path from the root; ties broken by declaration order, i.e. first occurrence in
 * depth-first pre-order).
 */
public class DependencyGraphAnalyzer
{
    private static final int MAX_NODES = 20000;

    private final RepositorySystem repositorySystem;

    private final RepositorySystemSession session;

    private final List<RemoteRepository> remoteRepositories;

    private final List<String> warnings = new ArrayList<String>();

    private final List<DynamicVersionViolation> dynamicViolations = new ArrayList<DynamicVersionViolation>();

    private int nodeCount;

    public DependencyGraphAnalyzer( RepositorySystem repositorySystem, RepositorySystemSession session,
                                    List<RemoteRepository> remoteRepositories )
    {
        this.repositorySystem = repositorySystem;
        this.session = session;
        this.remoteRepositories =
            remoteRepositories == null ? new ArrayList<RemoteRepository>() : remoteRepositories;
    }

    public AnalysisResult analyze( MavenProject project )
    {
        AnalysisResult result = new AnalysisResult();
        String projectCoordinates = project.getGroupId() + ":" + project.getArtifactId() + ":" + project.getVersion();
        result.setProjectCoordinates( projectCoordinates );

        Map<String, Dependency> rootManagement = managementByGa( project.getModel().getDependencyManagement() );

        DepNode root = new DepNode( project.getGroupId(), project.getArtifactId(), project.getVersion(),
                                    "compile", false, null );
        result.setRoot( root );

        List<Dependency> direct = project.getModel().getDependencies();
        if ( direct != null )
        {
            for ( Dependency dependency : direct )
            {
                Dependency effective = applyManagement( dependency, rootManagement );
                addChild( root, effective, null, new ArrayList<Exclusion>(), rootManagement, true );
            }
        }

        result.getWarnings().addAll( warnings );
        result.getDynamicVersionViolations().addAll( dynamicViolations );
        result.getVersionConflicts().addAll( findVersionConflicts( root ) );
        result.setNormalizedTree( renderTree( root, result.getVersionConflicts() ) );
        return result;
    }

    // ------------------------------------------------------------------ graph traversal

    private void addChild( DepNode parent, Dependency dependency, String parentScope,
                           List<Exclusion> inheritedExclusions, Map<String, Dependency> rootManagement,
                           boolean direct )
    {
        if ( nodeCount++ > MAX_NODES )
        {
            warnOnce( "dependency graph exceeds " + MAX_NODES + " nodes, traversal truncated" );
            return;
        }
        String scope = dependency.getScope() == null || dependency.getScope().length() == 0
            ? "compile"
            : dependency.getScope();
        boolean optional = dependency.isOptional();

        if ( !direct )
        {
            if ( optional )
            {
                return; // optional transitive dependencies are not included by Maven
            }
            scope = deriveScope( parentScope, scope );
            if ( scope == null )
            {
                return; // omitted by scope transitivity (provided/test below a dependency)
            }
        }

        String version = dependency.getVersion();
        if ( version == null || version.length() == 0 )
        {
            warnings.add( "skipping " + dependency.getGroupId() + ":" + dependency.getArtifactId()
                + ": no version available after dependencyManagement" );
            return;
        }

        DepNode node = new DepNode( dependency.getGroupId(), dependency.getArtifactId(), version, scope,
                                    optional, parent );

        if ( isDynamicVersion( version ) )
        {
            dynamicViolations.add( new DynamicVersionViolation( node.ga(), version, node.pathString() ) );
            String resolved = resolveDynamicVersion( node.ga(), version );
            if ( resolved == null )
            {
                node.setNote( "unresolvable dynamic version " + version );
                parent.getChildren().add( node );
                return;
            }
            node = new DepNode( node.getGroupId(), node.getArtifactId(), resolved, scope, optional, parent );
        }

        if ( isExcluded( dependency.getGroupId(), dependency.getArtifactId(), inheritedExclusions ) )
        {
            return; // excluded by an ancestor
        }

        parent.getChildren().add( node );

        if ( isOnPath( parent, node.ga(), node.getVersion() ) )
        {
            node.setNote( "cyclic" );
            return;
        }

        List<Exclusion> exclusions = new ArrayList<Exclusion>( inheritedExclusions );
        if ( dependency.getExclusions() != null )
        {
            exclusions.addAll( dependency.getExclusions() );
        }

        ArtifactDescriptorResult descriptor;
        try
        {
            descriptor = repositorySystem.readArtifactDescriptor( session,
                new ArtifactDescriptorRequest(
                    new DefaultArtifact( node.getGroupId(), node.getArtifactId(), "pom", node.getVersion() ),
                    remoteRepositories, null ) );
        }
        catch ( Exception e )
        {
            node.setNote( "descriptor unreadable: " + e.getMessage() );
            warnings.add( "could not read descriptor of " + node.gav() + ": " + e.getMessage() );
            return;
        }

        Map<String, Dependency> ownManagement = toModelManagement( descriptor );
        List<org.eclipse.aether.graph.Dependency> children = descriptor.getDependencies();
        if ( children == null )
        {
            return;
        }
        for ( org.eclipse.aether.graph.Dependency child : children )
        {
            Dependency model = toModelDependency( child );
            Dependency managed = ownManagement.get( gaOf( model ) );
            if ( managed != null )
            {
                model = applyManagement( model, ownManagement );
            }
            // the root project's dependencyManagement pins transitive versions (Maven behaviour)
            model = overrideWithRootManagement( model, rootManagement );
            addChild( node, model, scope, exclusions, rootManagement, false );
        }
    }

    private boolean isOnPath( DepNode parent, String ga, String version )
    {
        String gav = ga + ":" + version;
        DepNode node = parent;
        while ( node != null )
        {
            if ( node.gav().equals( gav ) )
            {
                return true;
            }
            node = node.getParent();
        }
        return false;
    }

    private boolean isExcluded( String groupId, String artifactId, List<Exclusion> exclusions )
    {
        for ( Exclusion exclusion : exclusions )
        {
            if ( wildcardMatch( exclusion.getGroupId(), groupId )
                && wildcardMatch( exclusion.getArtifactId(), artifactId ) )
            {
                return true;
            }
        }
        return false;
    }

    private static boolean wildcardMatch( String pattern, String value )
    {
        return "*".equals( pattern ) || pattern.equals( value );
    }

    /**
     * Maven scope transitivity: rows = scope of the dependency that brings the transitive one in,
     * columns = scope declared for the transitive dependency. Empty cells mean omission.
     */
    static String deriveScope( String parentScope, String childScope )
    {
        if ( "compile".equals( parentScope ) || "system".equals( parentScope ) )
        {
            if ( "compile".equals( childScope ) || "system".equals( childScope ) )
            {
                return "compile";
            }
            if ( "runtime".equals( childScope ) )
            {
                return "runtime";
            }
            return null;
        }
        if ( "provided".equals( parentScope ) )
        {
            if ( "compile".equals( childScope ) || "runtime".equals( childScope ) )
            {
                return "provided";
            }
            return null;
        }
        if ( "runtime".equals( parentScope ) )
        {
            if ( "compile".equals( childScope ) || "runtime".equals( childScope ) )
            {
                return "runtime";
            }
            return null;
        }
        if ( "test".equals( parentScope ) )
        {
            if ( "compile".equals( childScope ) || "runtime".equals( childScope ) )
            {
                return "test";
            }
            return null;
        }
        return childScope;
    }

    // ------------------------------------------------------------------ version conflicts

    private List<VersionConflict> findVersionConflicts( DepNode root )
    {
        Map<String, List<DepNode>> byGa = new LinkedHashMap<String, List<DepNode>>();
        collect( root, byGa );
        List<VersionConflict> conflicts = new ArrayList<VersionConflict>();
        for ( Map.Entry<String, List<DepNode>> entry : byGa.entrySet() )
        {
            Set<String> versions = new LinkedHashSet<String>();
            for ( DepNode node : entry.getValue() )
            {
                versions.add( node.getVersion() );
            }
            if ( versions.size() < 2 )
            {
                continue;
            }
            DepNode winner = null;
            for ( DepNode node : entry.getValue() )
            {
                if ( winner == null || node.depth() < winner.depth() )
                {
                    winner = node; // pre-order keeps declaration order for equal depths
                }
            }
            VersionConflict conflict = new VersionConflict( entry.getKey(), winner.getVersion() );
            for ( DepNode node : entry.getValue() )
            {
                conflict.getOccurrences()
                    .add( new VersionConflict.Occurrence( node.getVersion(), node.pathString(), node.depth() ) );
            }
            conflicts.add( conflict );
        }
        return conflicts;
    }

    private void collect( DepNode node, Map<String, List<DepNode>> byGa )
    {
        for ( DepNode child : node.getChildren() )
        {
            List<DepNode> list = byGa.get( child.ga() );
            if ( list == null )
            {
                list = new ArrayList<DepNode>();
                byGa.put( child.ga(), list );
            }
            list.add( child );
            collect( child, byGa );
        }
    }

    // ------------------------------------------------------------------ tree rendering

    private String renderTree( DepNode root, List<VersionConflict> conflicts )
    {
        Map<String, String> selectedByGa = new HashMap<String, String>();
        for ( VersionConflict conflict : conflicts )
        {
            selectedByGa.put( conflict.getCoordinate(), conflict.getSelectedVersion() );
        }
        StringBuilder sb = new StringBuilder();
        sb.append( root.gav() ).append( '\n' );
        for ( int i = 0; i < root.getChildren().size(); i++ )
        {
            render( root.getChildren().get( i ), "", i == root.getChildren().size() - 1, selectedByGa, sb );
        }
        return sb.toString();
    }

    private void render( DepNode node, String prefix, boolean last, Map<String, String> selectedByGa,
                         StringBuilder sb )
    {
        sb.append( prefix ).append( last ? "\\- " : "+- " );
        sb.append( node.gav() ).append( " (" ).append( node.getScope() );
        if ( node.isOptional() )
        {
            sb.append( ", optional" );
        }
        sb.append( ')' );
        String selected = selectedByGa.get( node.ga() );
        if ( selected != null && !selected.equals( node.getVersion() ) )
        {
            sb.append( " (omitted for conflict with " ).append( selected ).append( ')' );
        }
        if ( node.getNote() != null )
        {
            sb.append( " (" ).append( node.getNote() ).append( ')' );
        }
        sb.append( '\n' );
        String childPrefix = prefix + ( last ? "   " : "|  " );
        for ( int i = 0; i < node.getChildren().size(); i++ )
        {
            render( node.getChildren().get( i ), childPrefix, i == node.getChildren().size() - 1, selectedByGa,
                    sb );
        }
    }

    // ------------------------------------------------------------------ dynamic versions

    static boolean isDynamicVersion( String version )
    {
        if ( "LATEST".equals( version ) || "RELEASE".equals( version ) )
        {
            return true;
        }
        char first = version.charAt( 0 );
        return first == '[' || first == '(';
    }

    private String resolveDynamicVersion( String ga, String version )
    {
        try
        {
            String[] parts = ga.split( ":" );
            if ( version.startsWith( "[" ) || version.startsWith( "(" ) )
            {
                VersionRangeResult result = repositorySystem.resolveVersionRange( session,
                    new VersionRangeRequest(
                        new DefaultArtifact( parts[0], parts[1], "jar", version ), remoteRepositories, null ) );
                Version highest = result.getHighestVersion();
                return highest == null ? null : highest.toString();
            }
            VersionResult result = repositorySystem.resolveVersion( session,
                new VersionRequest(
                    new DefaultArtifact( parts[0], parts[1], "jar", version ), remoteRepositories, null ) );
            return result.getVersion();
        }
        catch ( Exception e )
        {
            warnings.add( "could not resolve dynamic version " + ga + ":" + version + ": " + e.getMessage() );
            return null;
        }
    }

    // ------------------------------------------------------------------ management helpers

    private void warnOnce( String message )
    {
        if ( !warnings.contains( message ) )
        {
            warnings.add( message );
        }
    }

    private static String gaOf( Dependency dependency )
    {
        return dependency.getGroupId() + ":" + dependency.getArtifactId();
    }

    private static Map<String, Dependency> managementByGa( DependencyManagement management )
    {
        Map<String, Dependency> map = new LinkedHashMap<String, Dependency>();
        if ( management != null && management.getDependencies() != null )
        {
            for ( Dependency dependency : management.getDependencies() )
            {
                map.put( gaOf( dependency ), dependency );
            }
        }
        return map;
    }

    private static Map<String, Dependency> toModelManagement( ArtifactDescriptorResult descriptor )
    {
        Map<String, Dependency> map = new LinkedHashMap<String, Dependency>();
        List<org.eclipse.aether.graph.Dependency> managed = descriptor.getManagedDependencies();
        if ( managed != null )
        {
            for ( org.eclipse.aether.graph.Dependency dependency : managed )
            {
                Dependency model = toModelDependency( dependency );
                map.put( gaOf( model ), model );
            }
        }
        return map;
    }

    private static Dependency toModelDependency( org.eclipse.aether.graph.Dependency dependency )
    {
        Dependency model = new Dependency();
        model.setGroupId( dependency.getArtifact().getGroupId() );
        model.setArtifactId( dependency.getArtifact().getArtifactId() );
        model.setVersion( dependency.getArtifact().getVersion() );
        model.setScope( dependency.getScope() );
        model.setOptional( dependency.isOptional() );
        for ( org.eclipse.aether.graph.Exclusion exclusion : dependency.getExclusions() )
        {
            Exclusion modelExclusion = new Exclusion();
            modelExclusion.setGroupId( exclusion.getGroupId() );
            modelExclusion.setArtifactId( exclusion.getArtifactId() );
            model.addExclusion( modelExclusion );
        }
        return model;
    }

    /** Fills in missing attributes from dependencyManagement (does not override explicit values). */
    private static Dependency applyManagement( Dependency dependency, Map<String, Dependency> management )
    {
        Dependency managed = management.get( gaOf( dependency ) );
        if ( managed == null )
        {
            return dependency;
        }
        Dependency copy = dependency.clone();
        if ( copy.getVersion() == null || copy.getVersion().length() == 0 )
        {
            copy.setVersion( managed.getVersion() );
        }
        if ( copy.getScope() == null || copy.getScope().length() == 0 )
        {
            copy.setScope( managed.getScope() );
        }
        if ( managed.getExclusions() != null )
        {
            for ( Exclusion exclusion : managed.getExclusions() )
            {
                copy.addExclusion( exclusion );
            }
        }
        return copy;
    }

    /**
     * The root project's dependencyManagement pins transitive dependencies: an explicitly managed
     * version/scope overrides whatever an intermediate POM declares (this is the standard Maven
     * mechanism for forcing transitive versions).
     */
    private static Dependency overrideWithRootManagement( Dependency dependency,
                                                          Map<String, Dependency> rootManagement )
    {
        Dependency managed = rootManagement.get( gaOf( dependency ) );
        if ( managed == null )
        {
            return dependency;
        }
        Dependency copy = dependency.clone();
        if ( managed.getVersion() != null && managed.getVersion().length() > 0 )
        {
            copy.setVersion( managed.getVersion() );
        }
        if ( managed.getScope() != null && managed.getScope().length() > 0 )
        {
            copy.setScope( managed.getScope() );
        }
        if ( managed.getExclusions() != null )
        {
            for ( Exclusion exclusion : managed.getExclusions() )
            {
                copy.addExclusion( exclusion );
            }
        }
        return copy;
    }
}
