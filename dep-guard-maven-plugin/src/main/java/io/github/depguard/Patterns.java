package io.github.depguard;

import java.util.List;

/**
 * Minimal glob matching used for whitelist entries. Supports {@code *} (any sequence, including
 * none) and {@code ?} (single character); everything else matches literally.
 */
public final class Patterns
{
    private Patterns()
    {
    }

    public static boolean matchesAny( List<String> patterns, String value )
    {
        if ( patterns == null || value == null )
        {
            return false;
        }
        for ( String pattern : patterns )
        {
            if ( pattern != null && matches( pattern.trim(), value ) )
            {
                return true;
            }
        }
        return false;
    }

    public static boolean matches( String pattern, String value )
    {
        return matchesRegion( pattern, 0, value, 0 );
    }

    private static boolean matchesRegion( String pattern, int p, String value, int v )
    {
        int patternLength = pattern.length();
        int valueLength = value.length();
        while ( p < patternLength )
        {
            char c = pattern.charAt( p );
            if ( c == '*' )
            {
                while ( p < patternLength && pattern.charAt( p ) == '*' )
                {
                    p++;
                }
                if ( p == patternLength )
                {
                    return true;
                }
                for ( int i = v; i <= valueLength; i++ )
                {
                    if ( matchesRegion( pattern, p, value, i ) )
                    {
                        return true;
                    }
                }
                return false;
            }
            if ( v >= valueLength )
            {
                return false;
            }
            if ( c != '?' && c != value.charAt( v ) )
            {
                return false;
            }
            p++;
            v++;
        }
        return v == valueLength;
    }
}
