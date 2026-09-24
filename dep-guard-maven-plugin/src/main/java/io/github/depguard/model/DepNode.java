package io.github.depguard.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * One occurrence of an artifact in the dependency graph. The graph may contain several nodes for
 * the same {@code groupId:artifactId} with different versions (one per introduction path); conflict
 * detection later decides which node Maven's "nearest wins" rule would select.
 */
public class DepNode
{
    private final String groupId;

    private final String artifactId;

    private final String version;

    private final String scope;

    private final boolean optional;

    private final DepNode parent;

    private final List<DepNode> children = new ArrayList<DepNode>();

    /** Omission reason for display purposes, e.g. "cyclic", "optional", null if fully resolved. */
    private String note;

    public DepNode( String groupId, String artifactId, String version, String scope, boolean optional,
                    DepNode parent )
    {
        this.groupId = groupId;
        this.artifactId = artifactId;
        this.version = version;
        this.scope = scope;
        this.optional = optional;
        this.parent = parent;
    }

    public String getGroupId()
    {
        return groupId;
    }

    public String getArtifactId()
    {
        return artifactId;
    }

    public String getVersion()
    {
        return version;
    }

    public String getScope()
    {
        return scope;
    }

    public boolean isOptional()
    {
        return optional;
    }

    public DepNode getParent()
    {
        return parent;
    }

    public List<DepNode> getChildren()
    {
        return children;
    }

    public String getNote()
    {
        return note;
    }

    public void setNote( String note )
    {
        this.note = note;
    }

    public String ga()
    {
        return groupId + ":" + artifactId;
    }

    public String gav()
    {
        return ga() + ":" + version;
    }

    public int depth()
    {
        int depth = 0;
        DepNode node = this;
        while ( node.parent != null )
        {
            depth++;
            node = node.parent;
        }
        return depth;
    }

    /** Path from the project root (inclusive) down to this node. */
    public List<DepNode> path()
    {
        List<DepNode> path = new ArrayList<DepNode>();
        DepNode node = this;
        while ( node != null )
        {
            path.add( node );
            node = node.parent;
        }
        Collections.reverse( path );
        return path;
    }

    public String pathString()
    {
        StringBuilder sb = new StringBuilder();
        for ( DepNode node : path() )
        {
            if ( sb.length() > 0 )
            {
                sb.append( " -> " );
            }
            sb.append( node.gav() );
        }
        return sb.toString();
    }

    @Override
    public String toString()
    {
        return gav() + " (" + scope + ( optional ? ", optional" : "" ) + ")";
    }
}
