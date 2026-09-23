package io.github.depguard;

import java.io.File;
import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Builds a self-contained local Maven repository under {@code target/test-repo} with a handful of
 * synthetic artifacts. All jars and POMs are generated at test time; nothing is downloaded.
 *
 * <pre>
 * com.sample:lib-a:1.0  classes: c.s.shared.Foo("foo-v1"), c.s.a.A
 * com.sample:lib-a:2.0  classes: c.s.shared.Foo("foo-v2"), c.s.a.A2
 * com.sample:lib-b:1.0  depends lib-a:2.0; classes: c.s.shared.Foo("foo-b"), c.s.b.B
 * com.sample:lib-c:1.0  classes: c.s.shared.Foo("foo-v1" = same bytes as lib-a:1.0), c.s.c.C
 * com.sample:lib-d:1.0  depends lib-a:[1.0,2.0) (dynamic range); classes: c.s.d.D
 * </pre>
 */
final class TestRepositoryBuilder
{
    private TestRepositoryBuilder()
    {
    }

    static void build( File repoDir )
        throws IOException
    {
        install( repoDir, "com.sample", "lib-a", "1.0", null,
                 classes( "com/sample/shared/Foo.class", "foo-v1", "com/sample/a/A.class", "a" ) );
        install( repoDir, "com.sample", "lib-a", "2.0", null,
                 classes( "com/sample/shared/Foo.class", "foo-v2", "com/sample/a/A2.class", "a2" ) );
        writeMetadata( repoDir, "com.sample", "lib-a", "1.0", "2.0" );
        install( repoDir, "com.sample", "lib-b", "1.0", new String[][] { { "com.sample", "lib-a", "2.0" } },
                 classes( "com/sample/shared/Foo.class", "foo-b", "com/sample/b/B.class", "b" ) );
        install( repoDir, "com.sample", "lib-c", "1.0", null,
                 classes( "com/sample/shared/Foo.class", "foo-v1", "com/sample/c/C.class", "c" ) );
        install( repoDir, "com.sample", "lib-d", "1.0",
                 new String[][] { { "com.sample", "lib-a", "[1.0,2.0)" } },
                 classes( "com/sample/d/D.class", "d" ) );
    }

    private static void writeMetadata( File repoDir, String groupId, String artifactId, String... versions )
        throws IOException
    {
        File dir = new File( repoDir, groupId.replace( '.', '/' ) + "/" + artifactId );
        Files.createDirectories( dir.toPath() );
        StringBuilder metadata = new StringBuilder();
        metadata.append( "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<metadata>\n" );
        metadata.append( "  <groupId>" ).append( groupId ).append( "</groupId>\n" );
        metadata.append( "  <artifactId>" ).append( artifactId ).append( "</artifactId>\n" );
        metadata.append( "  <versioning>\n    <versions>\n" );
        for ( String version : versions )
        {
            metadata.append( "      <version>" ).append( version ).append( "</version>\n" );
        }
        metadata.append( "    </versions>\n  </versioning>\n</metadata>\n" );
        write( new File( dir, "maven-metadata-local.xml" ), metadata.toString() );
    }

    private static Map<String, String> classes( String... nameAndContent )
    {
        Map<String, String> classes = new LinkedHashMap<>();
        for ( int i = 0; i < nameAndContent.length; i += 2 )
        {
            classes.put( nameAndContent[i], nameAndContent[i + 1] );
        }
        return classes;
    }

    private static void install( File repoDir, String groupId, String artifactId, String version,
                                 String[][] dependencies, Map<String, String> classes )
        throws IOException
    {
        File dir = new File( repoDir, groupId.replace( '.', '/' ) + "/" + artifactId + "/" + version );
        Files.createDirectories( dir.toPath() );

        StringBuilder pom = new StringBuilder();
        pom.append( "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" );
        pom.append( "<project xmlns=\"http://maven.apache.org/POM/4.0.0\">\n" );
        pom.append( "  <modelVersion>4.0.0</modelVersion>\n" );
        pom.append( "  <groupId>" ).append( groupId ).append( "</groupId>\n" );
        pom.append( "  <artifactId>" ).append( artifactId ).append( "</artifactId>\n" );
        pom.append( "  <version>" ).append( version ).append( "</version>\n" );
        pom.append( "  <packaging>jar</packaging>\n" );
        if ( dependencies != null && dependencies.length > 0 )
        {
            pom.append( "  <dependencies>\n" );
            for ( String[] dependency : dependencies )
            {
                pom.append( "    <dependency><groupId>" ).append( dependency[0] )
                    .append( "</groupId><artifactId>" ).append( dependency[1] )
                    .append( "</artifactId><version>" ).append( dependency[2] )
                    .append( "</version></dependency>\n" );
            }
            pom.append( "  </dependencies>\n" );
        }
        pom.append( "</project>\n" );
        write( new File( dir, artifactId + "-" + version + ".pom" ), pom.toString() );

        File jar = new File( dir, artifactId + "-" + version + ".jar" );
        try ( ZipOutputStream zip = new ZipOutputStream( Files.newOutputStream( jar.toPath() ) ) )
        {
            for ( Map.Entry<String, String> entry : classes.entrySet() )
            {
                zip.putNextEntry( new ZipEntry( entry.getKey() ) );
                zip.write( entry.getValue().getBytes( StandardCharsets.UTF_8 ) );
                zip.closeEntry();
            }
        }
    }

    private static void write( File file, String content )
        throws IOException
    {
        try ( Writer writer = Files.newBufferedWriter( file.toPath(), StandardCharsets.UTF_8 ) )
        {
            writer.write( content );
        }
    }
}
