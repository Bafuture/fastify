package io.github.depguard;

public class TreeMojoTest extends AbstractDepGuardMojoTestCase
{
    public void testTreeContainsAllScopesAndConflictAnnotation()
        throws Exception
    {
        TreeMojo mojo = lookupTreeMojo( "conflict-project" );
        mojo.execute();

        String tree = mojo.getTreeText();
        assertNotNull( tree );
        assertTrue( tree.contains( "io.github.depguard.it:conflict-project:pom:1.0" ) );
        assertTrue( tree.contains( "com.sample:lib-a:jar:1.0:compile" ) );
        assertTrue( tree.contains( "com.sample:lib-b:jar:1.0:compile" ) );
        assertTrue( tree.contains( "com.sample:lib-c:jar:1.0:compile" ) );
        assertTrue( "loser of the lib-a conflict must be annotated",
                    tree.contains( "com.sample:lib-a:jar:2.0:compile (omitted for conflict with 1.0)" ) );
    }
}
