package io.github.depguard.model;

/**
 * A dependency occurrence whose version does not match the version declared in the plugin's
 * {@code expectedVersions} configuration.
 */
public class ConvergenceViolation
{
    private final String coordinate;

    private final String expectedVersion;

    private final String actualVersion;

    private final String path;

    public ConvergenceViolation( String coordinate, String expectedVersion, String actualVersion, String path )
    {
        this.coordinate = coordinate;
        this.expectedVersion = expectedVersion;
        this.actualVersion = actualVersion;
        this.path = path;
    }

    public String getCoordinate()
    {
        return coordinate;
    }

    public String getExpectedVersion()
    {
        return expectedVersion;
    }

    public String getActualVersion()
    {
        return actualVersion;
    }

    public String getPath()
    {
        return path;
    }
}
