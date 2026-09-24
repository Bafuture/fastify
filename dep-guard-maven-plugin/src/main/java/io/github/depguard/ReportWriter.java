package io.github.depguard;

import java.util.List;
import java.util.Map;

import io.github.depguard.model.AnalysisResult;
import io.github.depguard.model.ConvergenceViolation;
import io.github.depguard.model.DuplicateClassInfo;
import io.github.depguard.model.DynamicVersionViolation;
import io.github.depguard.model.VersionConflict;

/** Renders the {@link AnalysisResult} as a human readable text report and as JSON. */
public class ReportWriter
{
    public String renderText( AnalysisResult result, List<String> gateFailures )
    {
        StringBuilder sb = new StringBuilder();
        sb.append( "dep-guard report for " ).append( result.getProjectCoordinates() ).append( '\n' );
        sb.append( "======================================================================\n\n" );

        sb.append( "Dependency tree\n" );
        sb.append( "---------------\n" );
        sb.append( result.getNormalizedTree() ).append( '\n' );

        sb.append( "Version conflicts: " ).append( result.getVersionConflicts().size() ).append( '\n' );
        for ( VersionConflict conflict : result.getVersionConflicts() )
        {
            sb.append( "  * " ).append( conflict.getCoordinate() )
                .append( " -> selected " ).append( conflict.getSelectedVersion() ).append( '\n' );
            for ( VersionConflict.Occurrence occurrence : conflict.getOccurrences() )
            {
                sb.append( "      " ).append( occurrence.getVersion() );
                if ( occurrence.getVersion().equals( conflict.getSelectedVersion() ) )
                {
                    sb.append( " [selected]" );
                }
                else
                {
                    sb.append( " [overridden]" );
                }
                sb.append( " via " ).append( occurrence.getPath() ).append( '\n' );
            }
        }
        sb.append( '\n' );

        sb.append( "Convergence violations: " ).append( result.getConvergenceViolations().size() ).append( '\n' );
        for ( ConvergenceViolation violation : result.getConvergenceViolations() )
        {
            sb.append( "  * " ).append( violation.getCoordinate() )
                .append( " expected " ).append( violation.getExpectedVersion() )
                .append( " but found " ).append( violation.getActualVersion() )
                .append( " via " ).append( violation.getPath() ).append( '\n' );
        }
        sb.append( '\n' );

        sb.append( "Dynamic / non-reproducible versions: " )
            .append( result.getDynamicVersionViolations().size() ).append( '\n' );
        for ( DynamicVersionViolation violation : result.getDynamicVersionViolations() )
        {
            sb.append( "  * " ).append( violation.getCoordinate() )
                .append( " declared as " ).append( violation.getDeclaredVersion() )
                .append( " via " ).append( violation.getPath() ).append( '\n' );
        }
        sb.append( '\n' );

        sb.append( "Duplicate classes: " ).append( result.getDuplicateClasses().size() ).append( '\n' );
        for ( DuplicateClassInfo info : result.getDuplicateClasses() )
        {
            sb.append( "  * " ).append( info.getClassName() )
                .append( info.isIdentical() ? " [identical content]" : " [DIFFERENT implementations]" )
                .append( '\n' );
            for ( Map.Entry<String, String> entry : info.getHashByArtifact().entrySet() )
            {
                sb.append( "      " ).append( entry.getKey() )
                    .append( " sha256=" ).append( entry.getValue(), 0, 12 ).append( "...\n" );
            }
        }
        sb.append( '\n' );

        if ( !result.getVersionConflicts().isEmpty() )
        {
            sb.append( "Suggested dependencyManagement (paste into pom.xml)\n" );
            sb.append( "---------------------------------------------------\n" );
            sb.append( renderFixSuggestions( result.getVersionConflicts() ) ).append( '\n' );
        }

        if ( !result.getWarnings().isEmpty() )
        {
            sb.append( "Warnings\n" );
            sb.append( "--------\n" );
            for ( String warning : result.getWarnings() )
            {
                sb.append( "  ! " ).append( warning ).append( '\n' );
            }
            sb.append( '\n' );
        }

        sb.append( "Gate: " ).append( gateFailures.isEmpty() ? "PASSED" : "FAILED" ).append( '\n' );
        for ( String failure : gateFailures )
        {
            sb.append( "  x " ).append( failure ).append( '\n' );
        }
        return sb.toString();
    }

    /** dependencyManagement snippet pinning every conflicted coordinate to its selected version. */
    public String renderFixSuggestions( List<VersionConflict> conflicts )
    {
        StringBuilder sb = new StringBuilder();
        sb.append( "<dependencyManagement>\n  <dependencies>\n" );
        for ( VersionConflict conflict : conflicts )
        {
            String[] parts = conflict.getCoordinate().split( ":" );
            sb.append( "    <dependency>\n" );
            sb.append( "      <groupId>" ).append( parts[0] ).append( "</groupId>\n" );
            sb.append( "      <artifactId>" ).append( parts[1] ).append( "</artifactId>\n" );
            sb.append( "      <version>" ).append( conflict.getSelectedVersion() ).append( "</version>\n" );
            sb.append( "    </dependency>\n" );
        }
        sb.append( "  </dependencies>\n</dependencyManagement>\n" );
        return sb.toString();
    }

    public String renderJson( AnalysisResult result, List<String> gateFailures )
    {
        StringBuilder sb = new StringBuilder();
        sb.append( "{\n" );
        field( sb, "project", result.getProjectCoordinates(), true, 1 );
        sb.append( "  \"summary\": {\n" );
        number( sb, "versionConflicts", result.getVersionConflicts().size(), true, 2 );
        number( sb, "convergenceViolations", result.getConvergenceViolations().size(), true, 2 );
        number( sb, "dynamicVersionViolations", result.getDynamicVersionViolations().size(), true, 2 );
        number( sb, "duplicateClasses", result.getDuplicateClasses().size(), true, 2 );
        sb.append( "    \"gatePassed\": " ).append( gateFailures.isEmpty() ).append( '\n' );
        sb.append( "  },\n" );

        sb.append( "  \"versionConflicts\": [\n" );
        for ( int i = 0; i < result.getVersionConflicts().size(); i++ )
        {
            VersionConflict conflict = result.getVersionConflicts().get( i );
            sb.append( "    {\n" );
            field( sb, "coordinate", conflict.getCoordinate(), true, 3 );
            field( sb, "selectedVersion", conflict.getSelectedVersion(), true, 3 );
            sb.append( "      \"occurrences\": [\n" );
            List<VersionConflict.Occurrence> occurrences = conflict.getOccurrences();
            for ( int j = 0; j < occurrences.size(); j++ )
            {
                VersionConflict.Occurrence occurrence = occurrences.get( j );
                sb.append( "        {\"version\": " ).append( quote( occurrence.getVersion() ) )
                    .append( ", \"selected\": " )
                    .append( occurrence.getVersion().equals( conflict.getSelectedVersion() ) )
                    .append( ", \"path\": " ).append( quote( occurrence.getPath() ) ).append( "}" );
                sb.append( j + 1 < occurrences.size() ? ",\n" : "\n" );
            }
            sb.append( "      ]\n" );
            sb.append( "    }" ).append( i + 1 < result.getVersionConflicts().size() ? ",\n" : "\n" );
        }
        sb.append( "  ],\n" );

        sb.append( "  \"convergenceViolations\": [\n" );
        for ( int i = 0; i < result.getConvergenceViolations().size(); i++ )
        {
            ConvergenceViolation violation = result.getConvergenceViolations().get( i );
            sb.append( "    {\"coordinate\": " ).append( quote( violation.getCoordinate() ) )
                .append( ", \"expected\": " ).append( quote( violation.getExpectedVersion() ) )
                .append( ", \"actual\": " ).append( quote( violation.getActualVersion() ) )
                .append( ", \"path\": " ).append( quote( violation.getPath() ) ).append( "}" );
            sb.append( i + 1 < result.getConvergenceViolations().size() ? ",\n" : "\n" );
        }
        sb.append( "  ],\n" );

        sb.append( "  \"dynamicVersionViolations\": [\n" );
        for ( int i = 0; i < result.getDynamicVersionViolations().size(); i++ )
        {
            DynamicVersionViolation violation = result.getDynamicVersionViolations().get( i );
            sb.append( "    {\"coordinate\": " ).append( quote( violation.getCoordinate() ) )
                .append( ", \"declaredVersion\": " ).append( quote( violation.getDeclaredVersion() ) )
                .append( ", \"path\": " ).append( quote( violation.getPath() ) ).append( "}" );
            sb.append( i + 1 < result.getDynamicVersionViolations().size() ? ",\n" : "\n" );
        }
        sb.append( "  ],\n" );

        sb.append( "  \"duplicateClasses\": [\n" );
        for ( int i = 0; i < result.getDuplicateClasses().size(); i++ )
        {
            DuplicateClassInfo info = result.getDuplicateClasses().get( i );
            sb.append( "    {\"className\": " ).append( quote( info.getClassName() ) )
                .append( ", \"identical\": " ).append( info.isIdentical() )
                .append( ", \"artifacts\": [" );
            int j = 0;
            for ( Map.Entry<String, String> entry : info.getHashByArtifact().entrySet() )
            {
                if ( j++ > 0 )
                {
                    sb.append( ", " );
                }
                sb.append( "{\"coordinates\": " ).append( quote( entry.getKey() ) )
                    .append( ", \"sha256\": " ).append( quote( entry.getValue() ) ).append( "}" );
            }
            sb.append( "]}" );
            sb.append( i + 1 < result.getDuplicateClasses().size() ? ",\n" : "\n" );
        }
        sb.append( "  ],\n" );

        sb.append( "  \"warnings\": [" );
        for ( int i = 0; i < result.getWarnings().size(); i++ )
        {
            sb.append( i == 0 ? "" : ", " ).append( quote( result.getWarnings().get( i ) ) );
        }
        sb.append( "],\n" );

        sb.append( "  \"gateFailures\": [" );
        for ( int i = 0; i < gateFailures.size(); i++ )
        {
            sb.append( i == 0 ? "" : ", " ).append( quote( gateFailures.get( i ) ) );
        }
        sb.append( "]\n" );
        sb.append( "}\n" );
        return sb.toString();
    }

    private static void field( StringBuilder sb, String name, String value, boolean comma, int indent )
    {
        indent( sb, indent );
        sb.append( quote( name ) ).append( ": " ).append( quote( value ) ).append( comma ? ",\n" : "\n" );
    }

    private static void number( StringBuilder sb, String name, int value, boolean comma, int indent )
    {
        indent( sb, indent );
        sb.append( quote( name ) ).append( ": " ).append( value ).append( comma ? ",\n" : "\n" );
    }

    private static void indent( StringBuilder sb, int indent )
    {
        for ( int i = 0; i < indent; i++ )
        {
            sb.append( "  " );
        }
    }

    static String quote( String value )
    {
        StringBuilder sb = new StringBuilder( "\"" );
        for ( int i = 0; i < value.length(); i++ )
        {
            char c = value.charAt( i );
            switch ( c )
            {
                case '"':
                    sb.append( "\\\"" );
                    break;
                case '\\':
                    sb.append( "\\\\" );
                    break;
                case '\n':
                    sb.append( "\\n" );
                    break;
                case '\r':
                    sb.append( "\\r" );
                    break;
                case '\t':
                    sb.append( "\\t" );
                    break;
                default:
                    if ( c < 0x20 )
                    {
                        sb.append( String.format( "\\u%04x", (int) c ) );
                    }
                    else
                    {
                        sb.append( c );
                    }
            }
        }
        return sb.append( '"' ).toString();
    }
}
