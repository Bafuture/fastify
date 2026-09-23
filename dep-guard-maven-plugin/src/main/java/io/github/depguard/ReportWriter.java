package io.github.depguard;

import java.util.List;
import java.util.Map;

import io.github.depguard.model.AnalysisResult;
import io.github.depguard.model.ConvergenceViolation;
import io.github.depguard.model.DuplicateClassInfo;
import io.github.depguard.model.DynamicVersionViolation;
import io.github.depguard.model.VersionConflict;

/**
 * Renders the {@link AnalysisResult} as a human-readable text report, a machine-readable JSON
 * document, and a ready-to-paste {@code <dependencyManagement>} suggestion snippet.
 */
public class ReportWriter
{
    public String buildTextReport( AnalysisResult result, String projectCoordinates )
    {
        StringBuilder sb = new StringBuilder();
        sb.append( "================================================================\n" );
        sb.append( " dep-guard report for " ).append( projectCoordinates ).append( '\n' );
        sb.append( "================================================================\n\n" );

        sb.append( "[Dependency tree]\n" );
        sb.append( result.getDependencyTree() ).append( '\n' );

        sb.append( "[Version conflicts] (" ).append( result.getVersionConflicts().size() ).append( ")\n" );
        if ( result.getVersionConflicts().isEmpty() )
        {
            sb.append( "  none\n" );
        }
        for ( VersionConflict conflict : result.getVersionConflicts() )
        {
            sb.append( "  " ).append( conflict.getCoordinate() ).append( '\n' );
            sb.append( "    selected: " ).append( conflict.getWinnerVersion() ).append( " (nearest-wins)\n" );
            sb.append( "    overridden:\n" );
            for ( Map.Entry<String, List<String>> entry : conflict.getOverridden().entrySet() )
            {
                sb.append( "      " ).append( entry.getKey() ).append( '\n' );
                for ( String path : entry.getValue() )
                {
                    sb.append( "        path: " ).append( path ).append( '\n' );
                }
            }
        }
        sb.append( '\n' );

        sb.append( "[Dynamic / non-reproducible versions] (" ).append( result.getDynamicVersions().size() )
            .append( ")\n" );
        if ( result.getDynamicVersions().isEmpty() )
        {
            sb.append( "  none\n" );
        }
        for ( DynamicVersionViolation violation : result.getDynamicVersions() )
        {
            sb.append( "  " ).append( violation.getCoordinate() ).append( " declares \"" )
                .append( violation.getDeclaredVersion() ).append( "\" in " ).append( violation.getSource() )
                .append( '\n' );
        }
        sb.append( '\n' );

        sb.append( "[Convergence violations] (" ).append( result.getConvergenceViolations().size() ).append( ")\n" );
        if ( result.getConvergenceViolations().isEmpty() )
        {
            sb.append( "  none\n" );
        }
        for ( ConvergenceViolation violation : result.getConvergenceViolations() )
        {
            sb.append( "  " ).append( violation.getCoordinate() ).append( ':' ).append( violation.getVersion() )
                .append( " - " ).append( violation.getMessage() ).append( '\n' );
        }
        sb.append( '\n' );

        sb.append( "[Duplicate classes] (" ).append( result.getDuplicateClasses().size() ).append( ")\n" );
        if ( result.getDuplicateClasses().isEmpty() )
        {
            sb.append( "  none\n" );
        }
        for ( DuplicateClassInfo duplicate : result.getDuplicateClasses() )
        {
            sb.append( "  " ).append( duplicate.getClassName() )
                .append( duplicate.isIdentical() ? "  [identical bytecode]" : "  [DIFFERENT implementations]" )
                .append( '\n' );
            for ( DuplicateClassInfo.ArtifactHash artifact : duplicate.getArtifacts() )
            {
                sb.append( "    " ).append( artifact.getCoordinates() ).append( "  sha256:" )
                    .append( artifact.getSha256() ).append( '\n' );
            }
        }
        sb.append( '\n' );

        if ( !result.getVersionConflicts().isEmpty() )
        {
            sb.append( "[Suggested dependencyManagement]\n" );
            sb.append( buildSuggestions( result.getVersionConflicts() ) );
        }
        if ( result.getIgnoredVersionConflicts() > 0 || result.getIgnoredDuplicateClasses() > 0 )
        {
            sb.append( "\n[Whitelisted] " ).append( result.getIgnoredVersionConflicts() )
                .append( " version conflict(s), " ).append( result.getIgnoredDuplicateClasses() )
                .append( " duplicate class(es) suppressed by configuration\n" );
        }
        return sb.toString();
    }

    public String buildSuggestions( List<VersionConflict> conflicts )
    {
        StringBuilder sb = new StringBuilder();
        sb.append( "<dependencyManagement>\n  <dependencies>\n" );
        for ( VersionConflict conflict : conflicts )
        {
            sb.append( "    <dependency>\n" );
            sb.append( "      <groupId>" ).append( conflict.getGroupId() ).append( "</groupId>\n" );
            sb.append( "      <artifactId>" ).append( conflict.getArtifactId() ).append( "</artifactId>\n" );
            sb.append( "      <version>" ).append( conflict.getWinnerVersion() ).append( "</version>\n" );
            sb.append( "    </dependency>\n" );
        }
        sb.append( "  </dependencies>\n</dependencyManagement>\n" );
        return sb.toString();
    }

    public String buildJsonReport( AnalysisResult result, String projectCoordinates )
    {
        StringBuilder sb = new StringBuilder();
        sb.append( "{\n" );
        sb.append( "  \"project\": " ).append( json( projectCoordinates ) ).append( ",\n" );
        sb.append( "  \"summary\": {\n" );
        sb.append( "    \"versionConflicts\": " ).append( result.getVersionConflicts().size() ).append( ",\n" );
        sb.append( "    \"dynamicVersions\": " ).append( result.getDynamicVersions().size() ).append( ",\n" );
        sb.append( "    \"convergenceViolations\": " ).append( result.getConvergenceViolations().size() )
            .append( ",\n" );
        sb.append( "    \"duplicateClasses\": " ).append( result.getDuplicateClasses().size() ).append( ",\n" );
        sb.append( "    \"ignoredVersionConflicts\": " ).append( result.getIgnoredVersionConflicts() )
            .append( ",\n" );
        sb.append( "    \"ignoredDuplicateClasses\": " ).append( result.getIgnoredDuplicateClasses() )
            .append( '\n' );
        sb.append( "  },\n" );

        sb.append( "  \"versionConflicts\": [\n" );
        for ( int i = 0; i < result.getVersionConflicts().size(); i++ )
        {
            VersionConflict conflict = result.getVersionConflicts().get( i );
            sb.append( "    {\n" );
            sb.append( "      \"coordinate\": " ).append( json( conflict.getCoordinate() ) ).append( ",\n" );
            sb.append( "      \"selectedVersion\": " ).append( json( conflict.getWinnerVersion() ) )
                .append( ",\n" );
            sb.append( "      \"overridden\": [\n" );
            int j = 0;
            for ( Map.Entry<String, List<String>> entry : conflict.getOverridden().entrySet() )
            {
                sb.append( "        {\n" );
                sb.append( "          \"version\": " ).append( json( entry.getKey() ) ).append( ",\n" );
                sb.append( "          \"paths\": [" );
                for ( int k = 0; k < entry.getValue().size(); k++ )
                {
                    sb.append( k == 0 ? "" : ", " ).append( json( entry.getValue().get( k ) ) );
                }
                sb.append( "]\n" );
                sb.append( "        }" ).append( ++j < conflict.getOverridden().size() ? "," : "" ).append( '\n' );
            }
            sb.append( "      ]\n" );
            sb.append( "    }" ).append( i + 1 < result.getVersionConflicts().size() ? "," : "" ).append( '\n' );
        }
        sb.append( "  ],\n" );

        sb.append( "  \"dynamicVersions\": [\n" );
        for ( int i = 0; i < result.getDynamicVersions().size(); i++ )
        {
            DynamicVersionViolation violation = result.getDynamicVersions().get( i );
            sb.append( "    {\"coordinate\": " ).append( json( violation.getCoordinate() ) )
                .append( ", \"declaredVersion\": " ).append( json( violation.getDeclaredVersion() ) )
                .append( ", \"source\": " ).append( json( violation.getSource() ) ).append( "}" )
                .append( i + 1 < result.getDynamicVersions().size() ? "," : "" ).append( '\n' );
        }
        sb.append( "  ],\n" );

        sb.append( "  \"convergenceViolations\": [\n" );
        for ( int i = 0; i < result.getConvergenceViolations().size(); i++ )
        {
            ConvergenceViolation violation = result.getConvergenceViolations().get( i );
            sb.append( "    {\"coordinate\": " ).append( json( violation.getCoordinate() ) )
                .append( ", \"version\": " ).append( json( violation.getVersion() ) )
                .append( ", \"message\": " ).append( json( violation.getMessage() ) ).append( "}" )
                .append( i + 1 < result.getConvergenceViolations().size() ? "," : "" ).append( '\n' );
        }
        sb.append( "  ],\n" );

        sb.append( "  \"duplicateClasses\": [\n" );
        for ( int i = 0; i < result.getDuplicateClasses().size(); i++ )
        {
            DuplicateClassInfo duplicate = result.getDuplicateClasses().get( i );
            sb.append( "    {\n" );
            sb.append( "      \"className\": " ).append( json( duplicate.getClassName() ) ).append( ",\n" );
            sb.append( "      \"identical\": " ).append( duplicate.isIdentical() ).append( ",\n" );
            sb.append( "      \"artifacts\": [\n" );
            List<DuplicateClassInfo.ArtifactHash> artifacts = duplicate.getArtifacts();
            for ( int k = 0; k < artifacts.size(); k++ )
            {
                sb.append( "        {\"coordinates\": " ).append( json( artifacts.get( k ).getCoordinates() ) )
                    .append( ", \"sha256\": " ).append( json( artifacts.get( k ).getSha256() ) ).append( "}" )
                    .append( k + 1 < artifacts.size() ? "," : "" ).append( '\n' );
            }
            sb.append( "      ]\n" );
            sb.append( "    }" ).append( i + 1 < result.getDuplicateClasses().size() ? "," : "" ).append( '\n' );
        }
        sb.append( "  ]\n" );
        sb.append( "}\n" );
        return sb.toString();
    }

    private static String json( String value )
    {
        if ( value == null )
        {
            return "null";
        }
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
