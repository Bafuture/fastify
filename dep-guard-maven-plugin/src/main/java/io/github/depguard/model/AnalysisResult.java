package io.github.depguard.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Aggregated outcome of all dep-guard checks for one project.
 */
public class AnalysisResult
{
    private String dependencyTree = "";

    private List<VersionConflict> versionConflicts = new ArrayList<>();

    private List<DynamicVersionViolation> dynamicVersions = new ArrayList<>();

    private List<ConvergenceViolation> convergenceViolations = new ArrayList<>();

    private List<DuplicateClassInfo> duplicateClasses = new ArrayList<>();

    private int ignoredVersionConflicts;

    private int ignoredDuplicateClasses;

    public String getDependencyTree()
    {
        return dependencyTree;
    }

    public void setDependencyTree( String dependencyTree )
    {
        this.dependencyTree = dependencyTree;
    }

    public List<VersionConflict> getVersionConflicts()
    {
        return versionConflicts;
    }

    public void setVersionConflicts( List<VersionConflict> versionConflicts )
    {
        this.versionConflicts = versionConflicts;
    }

    public List<DynamicVersionViolation> getDynamicVersions()
    {
        return dynamicVersions;
    }

    public void setDynamicVersions( List<DynamicVersionViolation> dynamicVersions )
    {
        this.dynamicVersions = dynamicVersions;
    }

    public List<ConvergenceViolation> getConvergenceViolations()
    {
        return convergenceViolations;
    }

    public void setConvergenceViolations( List<ConvergenceViolation> convergenceViolations )
    {
        this.convergenceViolations = convergenceViolations;
    }

    public List<DuplicateClassInfo> getDuplicateClasses()
    {
        return duplicateClasses;
    }

    public void setDuplicateClasses( List<DuplicateClassInfo> duplicateClasses )
    {
        this.duplicateClasses = duplicateClasses;
    }

    public int getIgnoredVersionConflicts()
    {
        return ignoredVersionConflicts;
    }

    public void setIgnoredVersionConflicts( int ignoredVersionConflicts )
    {
        this.ignoredVersionConflicts = ignoredVersionConflicts;
    }

    public int getIgnoredDuplicateClasses()
    {
        return ignoredDuplicateClasses;
    }

    public void setIgnoredDuplicateClasses( int ignoredDuplicateClasses )
    {
        this.ignoredDuplicateClasses = ignoredDuplicateClasses;
    }

    public boolean hasViolations()
    {
        return !versionConflicts.isEmpty() || !dynamicVersions.isEmpty()
            || !convergenceViolations.isEmpty() || !duplicateClasses.isEmpty();
    }
}
