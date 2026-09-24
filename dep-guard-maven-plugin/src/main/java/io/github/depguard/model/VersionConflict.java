package io.github.depguard.model;

import java.util.ArrayList;
import java.util.List;

/**
 * A {@code groupId:artifactId} that appears in the graph with more than one version. Exactly one
 * occurrence is the selected ("winning") version according to Maven's nearest-wins rule; every
 * other occurrence is recorded with its full introduction path.
 */
public class VersionConflict
{
    private final String coordinate;

    private final String selectedVersion;

    private final List<Occurrence> occurrences = new ArrayList<Occurrence>();

    public VersionConflict( String coordinate, String selectedVersion )
    {
        this.coordinate = coordinate;
        this.selectedVersion = selectedVersion;
    }

    public String getCoordinate()
    {
        return coordinate;
    }

    public String getSelectedVersion()
    {
        return selectedVersion;
    }

    public List<Occurrence> getOccurrences()
    {
        return occurrences;
    }

    public List<Occurrence> getOverridden()
    {
        List<Occurrence> overridden = new ArrayList<Occurrence>();
        for ( Occurrence occurrence : occurrences )
        {
            if ( !occurrence.getVersion().equals( selectedVersion ) )
            {
                overridden.add( occurrence );
            }
        }
        return overridden;
    }

    public static class Occurrence
    {
        private final String version;

        private final String path;

        private final int depth;

        public Occurrence( String version, String path, int depth )
        {
            this.version = version;
            this.path = path;
            this.depth = depth;
        }

        public String getVersion()
        {
            return version;
        }

        public String getPath()
        {
            return path;
        }

        public int getDepth()
        {
            return depth;
        }
    }
}
