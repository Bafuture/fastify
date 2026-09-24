package io.github.depguard.model;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A fully qualified class name found in more than one resolved artifact. If all copies have the
 * same content hash the duplication is benign ({@link #isIdentical()}); differing hashes mean the
 * classpath order decides which implementation actually loads.
 */
public class DuplicateClassInfo
{
    private final String className;

    /** artifact coordinate (g:a:v) -&gt; sha-256 of the class bytes in that artifact */
    private final Map<String, String> hashByArtifact = new LinkedHashMap<String, String>();

    public DuplicateClassInfo( String className )
    {
        this.className = className;
    }

    public String getClassName()
    {
        return className;
    }

    public Map<String, String> getHashByArtifact()
    {
        return hashByArtifact;
    }

    public boolean isIdentical()
    {
        return hashByArtifact.values().size() > 0
            && new java.util.HashSet<String>( hashByArtifact.values() ).size() == 1;
    }
}
