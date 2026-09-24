package io.github.depguard;

/** The tree goal prints the normalized graph and marks conflict omissions. */
public class TreeMojoTest extends AbstractDepGuardMojoTestCase
{
    public void testTreeRendering()
        throws Exception
    {
        TreeMojo mojo = lookupTreeMojo( "conflict-project" );
        mojo.execute();

        String tree = readReport( "conflict-project", "dep-guard-tree.txt" );
        assertTrue( tree.contains( "com.acme.it:conflict-project:1.0" ) );
        assertTrue( tree.contains( "+- com.acme:lib-x:1.0 (compile)" ) );
        assertTrue( tree.contains( "\\- com.acme:lib-y:1.0 (compile)" ) );
        assertTrue( tree.contains( "com.acme:lib-common:1.0 (compile)" ) );
        assertTrue( tree.contains( "com.acme:lib-common:2.0 (compile) (omitted for conflict with 1.0)" ) );
    }
}
