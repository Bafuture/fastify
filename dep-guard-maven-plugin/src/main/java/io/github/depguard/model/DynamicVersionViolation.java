package io.github.depguard.model;

/**
 * A declared dependency version that is not fixed (LATEST, RELEASE, or a version range),
 * which makes the build non-reproducible.
 */
public class DynamicVersionViolation
{
    private final String source;

    private final String coordinate;

    private final String declaredVersion;

    public DynamicVersionViolation( String source, String coordinate, String declaredVersion )
    {
        this.source = source;
        this.coordinate = coordinate;
        this.declaredVersion = declaredVersion;
    }

    public String getSource()
    {
        return source;
    }

    public String getCoordinate()
    {
        return coordinate;
    }

    public String getDeclaredVersion()
    {
        return declaredVersion;
    }
}
