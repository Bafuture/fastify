package io.github.depguard;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.graph.DependencyNode;

import io.github.depguard.model.ConvergenceViolation;

/**
 * Verifies that every resolved dependency belongs to the configured set of expected versions
 * (dependency convergence). Entries are {@code groupId:artifactId:version} (exact version) or
 * {@code groupId:artifactId} (any version accepted, but the dependency must be declared).
 */
public class ConvergenceChecker
{
    public List<ConvergenceViolation> check( List<DependencyNode> winningNodes, List<String> expectedVersions )
    {
        List<ConvergenceViolation> violations = new ArrayList<>();
        if ( expectedVersions == null || expectedVersions.isEmpty() )
        {
            return violations;
        }

        Map<String, Set<String>> expected = new LinkedHashMap<>();
        for ( String entry : expectedVersions )
        {
            if ( entry == null || entry.trim().isEmpty() )
            {
                continue;
            }
            String[] parts = entry.trim().split( ":" );
            String key = parts[0] + ":" + parts[1];
            if ( parts.length >= 3 )
            {
                expected.computeIfAbsent( key, k -> new LinkedHashSet<>() ).add( parts[2] );
            }
            else
            {
                expected.putIfAbsent( key, null );
            }
        }

        Set<String> reported = new LinkedHashSet<>();
        for ( DependencyNode node : winningNodes )
        {
            Artifact artifact = node.getArtifact();
            String key = artifact.getGroupId() + ":" + artifact.getArtifactId();
            String version = artifact.getVersion();
            if ( !expected.containsKey( key ) )
            {
                if ( reported.add( key + ':' + version ) )
                {
                    violations.add( new ConvergenceViolation( key, version,
                        "resolved dependency is not declared in expectedVersions" ) );
                }
            }
            else
            {
                Set<String> allowed = expected.get( key );
                if ( allowed != null && !allowed.contains( version ) && reported.add( key + ':' + version ) )
                {
                    violations.add( new ConvergenceViolation( key, version,
                        "resolved version is not one of the expected versions " + new TreeSet<>( allowed ) ) );
                }
            }
        }
        return violations;
    }
}
