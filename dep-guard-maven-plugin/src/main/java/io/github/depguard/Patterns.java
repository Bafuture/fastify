package io.github.depguard;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Glob-style matching used for whitelist entries. {@code *} matches any character sequence;
 * everything else is matched literally.
 */
final class Patterns
{
    private Patterns()
    {
    }

    static boolean matchesAny( List<String> patterns, String value )
    {
        if ( patterns == null )
        {
            return false;
        }
        for ( String pattern : patterns )
        {
            if ( pattern != null && toRegex( pattern.trim() ).matcher( value ).matches() )
            {
                return true;
            }
        }
        return false;
    }

    private static Pattern toRegex( String glob )
    {
        StringBuilder regex = new StringBuilder();
        for ( int i = 0; i < glob.length(); i++ )
        {
            char c = glob.charAt( i );
            if ( c == '*' )
            {
                regex.append( ".*" );
            }
            else
            {
                if ( "\\.[]{}()+-^$|?".indexOf( c ) >= 0 )
                {
                    regex.append( '\\' );
                }
                regex.append( c );
            }
        }
        return Pattern.compile( regex.toString() );
    }
}
