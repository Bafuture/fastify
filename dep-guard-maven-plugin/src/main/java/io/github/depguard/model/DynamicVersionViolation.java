package io.github.depguard.model;

/**
 * A dependency declared with a non-reproducible version: {@code LATEST}, {@code RELEASE} or a
 * version range such as {@code [1.0,)}.
 */
public class DynamicVersionViolation
{
    private final String coordinate;

    private final String declaredVersion;

    private final String path;

    public DynamicVersionViolation( String coordinate, String declaredVersion, String path )
    {
        this.coordinate = coordinate;
        this.declaredVersion = declaredVersion;
        this.path = path;
    }

    public String getCoordinate()
    {
        return coordinate;
    }

    public String getDeclaredVersion()
    {
        return declaredVersion;
    }

    public String getPath()
    {
        return path;
    }
}
