package io.github.depguard.model;

/**
 * A resolved dependency whose coordinate/version is not part of the configured expected version set.
 */
public class ConvergenceViolation
{
    private final String coordinate;

    private final String version;

    private final String message;

    public ConvergenceViolation( String coordinate, String version, String message )
    {
        this.coordinate = coordinate;
        this.version = version;
        this.message = message;
    }

    public String getCoordinate()
    {
        return coordinate;
    }

    public String getVersion()
    {
        return version;
    }

    public String getMessage()
    {
        return message;
    }
}
