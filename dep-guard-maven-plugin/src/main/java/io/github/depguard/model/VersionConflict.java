package io.github.depguard.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A groupId:artifactId that appears in the dependency graph with more than one version.
 */
public class VersionConflict
{
    private final String groupId;

    private final String artifactId;

    private final String winnerVersion;

    /** overridden version -> introduction paths (root first) */
    private final Map<String, List<String>> overridden = new LinkedHashMap<>();

    public VersionConflict( String groupId, String artifactId, String winnerVersion )
    {
        this.groupId = groupId;
        this.artifactId = artifactId;
        this.winnerVersion = winnerVersion;
    }

    public String getGroupId()
    {
        return groupId;
    }

    public String getArtifactId()
    {
        return artifactId;
    }

    public String getCoordinate()
    {
        return groupId + ":" + artifactId;
    }

    public String getWinnerVersion()
    {
        return winnerVersion;
    }

    public Map<String, List<String>> getOverridden()
    {
        return overridden;
    }

    public void addOverriddenPath( String version, String path )
    {
        List<String> paths = overridden.computeIfAbsent( version, k -> new ArrayList<>() );
        if ( !paths.contains( path ) )
        {
            paths.add( path );
        }
    }
}
