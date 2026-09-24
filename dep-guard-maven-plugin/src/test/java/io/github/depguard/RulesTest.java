package io.github.depguard;

import junit.framework.TestCase;

/** Unit tests for the pure decision rules (no Maven container needed). */
public class RulesTest extends TestCase
{
    public void testDynamicVersionDetection()
    {
        assertTrue( DependencyGraphAnalyzer.isDynamicVersion( "LATEST" ) );
        assertTrue( DependencyGraphAnalyzer.isDynamicVersion( "RELEASE" ) );
        assertTrue( DependencyGraphAnalyzer.isDynamicVersion( "[1.0,)" ) );
        assertTrue( DependencyGraphAnalyzer.isDynamicVersion( "(1.0,2.0]" ) );
        assertFalse( DependencyGraphAnalyzer.isDynamicVersion( "1.0" ) );
        assertFalse( DependencyGraphAnalyzer.isDynamicVersion( "1.0-SNAPSHOT" ) );
    }

    public void testScopeTransitivity()
    {
        assertEquals( "compile", DependencyGraphAnalyzer.deriveScope( "compile", "compile" ) );
        assertEquals( "runtime", DependencyGraphAnalyzer.deriveScope( "compile", "runtime" ) );
        assertNull( DependencyGraphAnalyzer.deriveScope( "compile", "provided" ) );
        assertNull( DependencyGraphAnalyzer.deriveScope( "compile", "test" ) );
        assertEquals( "provided", DependencyGraphAnalyzer.deriveScope( "provided", "compile" ) );
        assertEquals( "runtime", DependencyGraphAnalyzer.deriveScope( "runtime", "compile" ) );
        assertEquals( "test", DependencyGraphAnalyzer.deriveScope( "test", "runtime" ) );
        assertNull( DependencyGraphAnalyzer.deriveScope( "test", "provided" ) );
    }

    public void testPatterns()
    {
        assertTrue( Patterns.matches( "com.acme:*", "com.acme:lib-common" ) );
        assertTrue( Patterns.matches( "com.acme:lib-*:1.0", "com.acme:lib-common:1.0" ) );
        assertTrue( Patterns.matches( "com.acme.dup.*", "com.acme.dup.Clash" ) );
        assertFalse( Patterns.matches( "com.acme.dup.*", "org.other.Clash" ) );
        assertFalse( Patterns.matches( "exact", "exactly" ) );
    }
}
