package io.github.depguard;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Builds a self-contained Maven repository under {@code target/test-repo} with synthetic
 * artifacts used by the integration tests. All jars and POMs are generated here at test time;
 * nothing is downloaded.
 *
 * <pre>
 * com.acme:lib-common:1.0 / 2.0   two versions of the same library (conflict source)
 * com.acme:lib-x:1.0              depends on lib-common:1.0
 * com.acme:lib-y:1.0              depends on lib-common:2.0
 * com.acme:lib-dup-a:1.0          classes com.acme.dup.Same (=bytes "SAME"), com.acme.dup.Clash (=bytes "CLASH-A")
 * com.acme:lib-dup-b:1.0          classes com.acme.dup.Same (=bytes "SAME"), com.acme.dup.Clash (=bytes "CLASH-B")
 * </pre>
 */
public final class TestRepositoryBuilder
{
    private TestRepositoryBuilder()
    {
    }

    public static void build( File repoDir )
        throws Exception
    {
        File marker = new File( repoDir, ".dep-guard-test-repo" );
        if ( marker.isFile() )
        {
            return;
        }
        Map<String, String> common1 = new LinkedHashMap<String, String>();
        common1.put( "com/acme/common/Common.class", "common-1.0" );
        install( repoDir, "com.acme", "lib-common", "1.0", null, common1 );

        Map<String, String> common2 = new LinkedHashMap<String, String>();
        common2.put( "com/acme/common/Common.class", "common-2.0" );
        install( repoDir, "com.acme", "lib-common", "2.0", null, common2 );

        install( repoDir, "com.acme", "lib-x", "1.0",
                 new String[][] { { "com.acme", "lib-common", "1.0" } },
                 classes( "com/acme/x/X.class", "x" ) );
        install( repoDir, "com.acme", "lib-y", "1.0",
                 new String[][] { { "com.acme", "lib-common", "2.0" } },
                 classes( "com/acme/y/Y.class", "y" ) );

        Map<String, String> dupA = new LinkedHashMap<String, String>();
        dupA.put( "com/acme/dup/Same.class", "SAME-BYTES" );
        dupA.put( "com/acme/dup/Clash.class", "CLASH-A" );
        install( repoDir, "com.acme", "lib-dup-a", "1.0", null, dupA );

        Map<String, String> dupB = new LinkedHashMap<String, String>();
        dupB.put( "com/acme/dup/Same.class", "SAME-BYTES" );
        dupB.put( "com/acme/dup/Clash.class", "CLASH-B" );
        install( repoDir, "com.acme", "lib-dup-b", "1.0", null, dupB );

        if ( !marker.getParentFile().exists() )
        {
            marker.getParentFile().mkdirs();
        }
        Writer w = new OutputStreamWriter( new FileOutputStream( marker ), "UTF-8" );
        w.write( "generated" );
        w.close();
    }

    private static Map<String, String> classes( String name, String content )
    {
        Map<String, String> map = new LinkedHashMap<String, String>();
        map.put( name, content );
        return map;
    }

    private static void install( File repoDir, String groupId, String artifactId, String version,
                                 String[][] dependencies, Map<String, String> classBytes )
        throws Exception
    {
        File dir = new File( repoDir, groupId.replace( '.', '/' ) + "/" + artifactId + "/" + version );
        if ( !dir.exists() && !dir.mkdirs() )
        {
            throw new IllegalStateException( "cannot create " + dir );
        }
        writePom( new File( dir, artifactId + "-" + version + ".pom" ), groupId, artifactId, version,
                  dependencies );
        writeJar( new File( dir, artifactId + "-" + version + ".jar" ), classBytes );
    }

    private static void writePom( File file, String groupId, String artifactId, String version,
                                  String[][] dependencies )
        throws Exception
    {
        StringBuilder sb = new StringBuilder();
        sb.append( "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" );
        sb.append( "<project xmlns=\"http://maven.apache.org/POM/4.0.0\">\n" );
        sb.append( "  <modelVersion>4.0.0</modelVersion>\n" );
        sb.append( "  <groupId>" ).append( groupId ).append( "</groupId>\n" );
        sb.append( "  <artifactId>" ).append( artifactId ).append( "</artifactId>\n" );
        sb.append( "  <version>" ).append( version ).append( "</version>\n" );
        if ( dependencies != null && dependencies.length > 0 )
        {
            sb.append( "  <dependencies>\n" );
            for ( String[] dependency : dependencies )
            {
                sb.append( "    <dependency><groupId>" ).append( dependency[0] )
                    .append( "</groupId><artifactId>" ).append( dependency[1] )
                    .append( "</artifactId><version>" ).append( dependency[2] )
                    .append( "</version></dependency>\n" );
            }
            sb.append( "  </dependencies>\n" );
        }
        sb.append( "</project>\n" );
        Writer writer = new OutputStreamWriter( new FileOutputStream( file ), "UTF-8" );
        try
        {
            writer.write( sb.toString() );
        }
        finally
        {
            writer.close();
        }
    }

    private static void writeJar( File file, Map<String, String> entries )
        throws Exception
    {
        ZipOutputStream zip = new ZipOutputStream( new FileOutputStream( file ) );
        try
        {
            for ( Map.Entry<String, String> entry : entries.entrySet() )
            {
                zip.putNextEntry( new ZipEntry( entry.getKey() ) );
                zip.write( entry.getValue().getBytes( "UTF-8" ) );
                zip.closeEntry();
            }
        }
        finally
        {
            zip.close();
        }
    }
}
