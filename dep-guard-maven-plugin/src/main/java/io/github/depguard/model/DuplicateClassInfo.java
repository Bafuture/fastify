package io.github.depguard.model;

import java.util.ArrayList;
import java.util.List;

/**
 * A fully-qualified class name that is present in more than one resolved artifact.
 */
public class DuplicateClassInfo
{
    public static class ArtifactHash
    {
        private final String coordinates;

        private final String sha256;

        public ArtifactHash( String coordinates, String sha256 )
        {
            this.coordinates = coordinates;
            this.sha256 = sha256;
        }

        public String getCoordinates()
        {
            return coordinates;
        }

        public String getSha256()
        {
            return sha256;
        }
    }

    private final String className;

    private final boolean identical;

    private final List<ArtifactHash> artifacts = new ArrayList<>();

    public DuplicateClassInfo( String className, boolean identical )
    {
        this.className = className;
        this.identical = identical;
    }

    public String getClassName()
    {
        return className;
    }

    /**
     * @return true when every copy of the class has the same SHA-256 (harmless shading/copy),
     *         false when at least two artifacts carry different implementations.
     */
    public boolean isIdentical()
    {
        return identical;
    }

    public List<ArtifactHash> getArtifacts()
    {
        return artifacts;
    }

    public void addArtifact( String coordinates, String sha256 )
    {
        artifacts.add( new ArtifactHash( coordinates, sha256 ) );
    }
}
