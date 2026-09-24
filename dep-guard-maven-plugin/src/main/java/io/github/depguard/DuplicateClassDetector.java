package io.github.depguard;

import java.io.File;
import java.io.InputStream;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import io.github.depguard.model.DepNode;
import io.github.depguard.model.DuplicateClassInfo;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.ArtifactRequest;
import org.eclipse.aether.resolution.ArtifactResult;

/**
 * Resolves the jar of every effective (conflict-winning) dependency and indexes every
 * {@code .class} entry by fully qualified class name. A class name found in more than one
 * artifact is a duplicate; the SHA-256 of the class bytes decides whether the copies are
 * identical or actually different implementations.
 */
public class DuplicateClassDetector
{
    private final RepositorySystem repositorySystem;

    private final RepositorySystemSession session;

    private final List<RemoteRepository> remoteRepositories;

    private final Set<String> scopes;

    public DuplicateClassDetector( RepositorySystem repositorySystem, RepositorySystemSession session,
                                   List<RemoteRepository> remoteRepositories, Collection<String> scopes )
    {
        this.repositorySystem = repositorySystem;
        this.session = session;
        this.remoteRepositories =
            remoteRepositories == null ? new ArrayList<RemoteRepository>() : remoteRepositories;
        this.scopes = new LinkedHashSet<String>( scopes );
    }

    /**
     * @param root        analyzed dependency graph
     * @param whitelisted class-name patterns to ignore
     * @param warnings    collects jars that could not be resolved/read
     */
    public List<DuplicateClassInfo> detect( DepNode root, List<String> whitelisted, List<String> warnings )
    {
        Map<String, DepNode> effective = new LinkedHashMap<String, DepNode>();
        collectEffective( root, effective );

        Map<String, DuplicateClassInfo> byClass = new LinkedHashMap<String, DuplicateClassInfo>();
        for ( DepNode node : effective.values() )
        {
            if ( !scopes.contains( node.getScope() ) )
            {
                continue;
            }
            File jar = resolveJar( node, warnings );
            if ( jar == null || !jar.isFile() )
            {
                continue;
            }
            indexJar( node, jar, byClass, warnings );
        }

        List<DuplicateClassInfo> duplicates = new ArrayList<DuplicateClassInfo>();
        for ( Map.Entry<String, DuplicateClassInfo> entry : byClass.entrySet() )
        {
            DuplicateClassInfo info = entry.getValue();
            if ( info.getHashByArtifact().size() > 1
                && !Patterns.matchesAny( whitelisted, info.getClassName() ) )
            {
                duplicates.add( info );
            }
        }
        return duplicates;
    }

    /** Keeps, per groupId:artifactId, the node Maven would select (nearest, then first declared). */
    private void collectEffective( DepNode node, Map<String, DepNode> effective )
    {
        for ( DepNode child : node.getChildren() )
        {
            DepNode current = effective.get( child.ga() );
            if ( current == null || child.depth() < current.depth() )
            {
                effective.put( child.ga(), child );
            }
            collectEffective( child, effective );
        }
    }

    private File resolveJar( DepNode node, List<String> warnings )
    {
        try
        {
            ArtifactResult result = repositorySystem.resolveArtifact( session,
                new ArtifactRequest(
                    new DefaultArtifact( node.getGroupId(), node.getArtifactId(), "jar", node.getVersion() ),
                    remoteRepositories, null ) );
            return result.getArtifact().getFile();
        }
        catch ( Exception e )
        {
            warnings.add( "could not resolve jar of " + node.gav() + ": " + e.getMessage() );
            return null;
        }
    }

    private void indexJar( DepNode node, File jar, Map<String, DuplicateClassInfo> byClass,
                           List<String> warnings )
    {
        ZipFile zip = null;
        try
        {
            zip = new ZipFile( jar );
            java.util.Enumeration<? extends ZipEntry> entries = zip.entries();
            while ( entries.hasMoreElements() )
            {
                ZipEntry entry = entries.nextElement();
                String name = entry.getName();
                if ( entry.isDirectory() || !name.endsWith( ".class" ) || name.startsWith( "META-INF/" ) )
                {
                    continue;
                }
                String className = name.substring( 0, name.length() - ".class".length() ).replace( '/', '.' );
                InputStream in = zip.getInputStream( entry );
                String hash;
                try
                {
                    hash = sha256( in );
                }
                finally
                {
                    in.close();
                }
                DuplicateClassInfo info = byClass.get( className );
                if ( info == null )
                {
                    info = new DuplicateClassInfo( className );
                    byClass.put( className, info );
                }
                info.getHashByArtifact().put( node.gav(), hash );
            }
        }
        catch ( Exception e )
        {
            warnings.add( "could not scan " + jar + ": " + e.getMessage() );
        }
        finally
        {
            if ( zip != null )
            {
                try
                {
                    zip.close();
                }
                catch ( Exception ignored )
                {
                    // ignore
                }
            }
        }
    }

    private static String sha256( InputStream in )
        throws Exception
    {
        MessageDigest digest = MessageDigest.getInstance( "SHA-256" );
        byte[] buffer = new byte[8192];
        int read;
        while ( ( read = in.read( buffer ) ) >= 0 )
        {
            digest.update( buffer, 0, read );
        }
        byte[] hash = digest.digest();
        StringBuilder sb = new StringBuilder();
        for ( byte b : hash )
        {
            sb.append( String.format( "%02x", b & 0xff ) );
        }
        return sb.toString();
    }
}
