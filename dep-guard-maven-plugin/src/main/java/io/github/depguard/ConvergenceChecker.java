package io.github.depguard;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import io.github.depguard.model.AnalysisResult;
import io.github.depguard.model.ConvergenceViolation;
import io.github.depguard.model.DepNode;

/**
 * Dependency convergence check: every occurrence of a {@code groupId:artifactId} listed in
 * {@code expectedVersions} (format {@code groupId:artifactId:version}) must appear with exactly
 * the declared version. Any other resolved (transitive) version is reported.
 */
public class ConvergenceChecker
{
    public void check( AnalysisResult result, List<String> expectedVersions )
    {
        if ( expectedVersions == null || expectedVersions.isEmpty() )
        {
            return;
        }
        Map<String, String> expected = new LinkedHashMap<String, String>();
        for ( String entry : expectedVersions )
        {
            if ( entry == null )
            {
                continue;
            }
            String[] parts = entry.trim().split( ":" );
            if ( parts.length != 3 )
            {
                result.getWarnings().add( "ignoring malformed expectedVersions entry: " + entry );
                continue;
            }
            expected.put( parts[0] + ":" + parts[1], parts[2] );
        }
        walk( result.getRoot(), expected, result );
    }

    private void walk( DepNode node, Map<String, String> expected, AnalysisResult result )
    {
        for ( DepNode child : node.getChildren() )
        {
            String wanted = expected.get( child.ga() );
            if ( wanted != null && !wanted.equals( child.getVersion() ) )
            {
                result.getConvergenceViolations().add(
                    new ConvergenceViolation( child.ga(), wanted, child.getVersion(), child.pathString() ) );
            }
            walk( child, expected, result );
        }
    }
}
