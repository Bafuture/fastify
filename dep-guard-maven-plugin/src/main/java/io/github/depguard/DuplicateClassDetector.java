package io.github.depguard;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.apache.maven.plugin.logging.Log;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.graph.DependencyNode;

import io.github.depguard.model.DuplicateClassInfo;

/**
 * Scans every resolved jar and reports fully-qualified class names that appear in more than one
 * artifact. Classes whose copies all share the same SHA-256 are flagged as identical (usually
 * harmless shading); differing hashes mean diverging implementations.
 */
public class DuplicateClassDetector
{
    public List<DuplicateClassInfo> detect( List<DependencyNode> winningNodes, Log log )
    {
        // class name -> sha256 -> artifact coordinates (g:a:v)
        Map<String, Map<String, Set<String>>> byClass = new TreeMap<>();

        for ( DependencyNode node : winningNodes )
        {
            Artifact artifact = node.getArtifact();
            File file = artifact.getFile();
            if ( file == null || !file.isFile() || !file.getName().endsWith( ".jar" ) )
            {
                continue;
            }
            String coordinates =
                artifact.getGroupId() + ':' + artifact.getArtifactId() + ':' + artifact.getVersion();
            try ( ZipFile zip = new ZipFile( file ) )
            {
                Enumeration<? extends ZipEntry> entries = zip.entries();
                while ( entries.hasMoreElements() )
                {
                    ZipEntry entry = entries.nextElement();
                    String name = entry.getName();
                    if ( entry.isDirectory() || !name.endsWith( ".class" ) || name.startsWith( "META-INF/" ) )
                    {
                        continue;
                    }
                    String simpleName = name.substring( name.lastIndexOf( '/' ) + 1 );
                    if ( "module-info.class".equals( simpleName ) || "package-info.class".equals( simpleName ) )
                    {
                        continue;
                    }
                    String hash;
                    try ( InputStream in = zip.getInputStream( entry ) )
                    {
                        hash = sha256( in );
                    }
                    String className = name.substring( 0, name.length() - ".class".length() ).replace( '/', '.' );
                    byClass.computeIfAbsent( className, k -> new LinkedHashMap<>() )
                        .computeIfAbsent( hash, h -> new LinkedHashSet<>() ).add( coordinates );
                }
 }
            catch ( IOException e )
            {
                log.warn( "Could not scan " + file + ": " + e.getMessage() );
            }
        }

        List<DuplicateClassInfo> duplicates = new ArrayList<>();
        for ( Map.Entry<String, Map<String, Set<String>>> entry : byClass.entrySet() )
        {
            Set<String> artifacts = new LinkedHashSet<>();
            for ( Set<String> coordinates : entry.getValue().values() )
            {
                for ( String coordinate : coordinates )
                {
                    artifacts.add( coordinate.substring( 0, coordinate.lastIndexOf( ':' ) ) );
                }
            }
            if ( artifacts.size() < 2 )
            {
                continue;
            }
            DuplicateClassInfo info = new DuplicateClassInfo( entry.getKey(), entry.getValue().size() == 1 );
            for ( Map.Entry<String, Set<String>> hashEntry : entry.getValue().entrySet() )
            {
                for ( String coordinate : hashEntry.getValue() )
                {
                    info.addArtifact( coordinate, hashEntry.getKey() );
                }
            }
            duplicates.add( info );
        }
        return duplicates;
    }

    private static String sha256( InputStream in ) throws IOException
    {
        MessageDigest digest;
        try
        {
            digest = MessageDigest.getInstance( "SHA-256" );
        }
        catch ( NoSuchAlgorithmException e )
        {
            throw new IllegalStateException( e );
        }
        byte[] buffer = new byte[8192];
        int read;
        while ( ( read = in.read( buffer ) ) >= 0 )
        {
            digest.update( buffer, 0, read );
        }
        StringBuilder hex = new StringBuilder();
        for ( byte b : digest.digest() )
        {
            hex.append( Character.forDigit( ( b >> 4 ) & 0xF, 16 ) ).append( Character.forDigit( b & 0xF, 16 ) );
        }
        return hex.toString();
    }
}
