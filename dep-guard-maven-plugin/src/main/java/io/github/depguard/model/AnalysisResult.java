package io.github.depguard.model;

import java.util.ArrayList;
import java.util.List;

/** Aggregated outcome of all dep-guard checks for one project. */
public class AnalysisResult
{
    private String projectCoordinates;

    private DepNode root;

    private String normalizedTree;

    private final List<VersionConflict> versionConflicts = new ArrayList<VersionConflict>();

    private final List<ConvergenceViolation> convergenceViolations = new ArrayList<ConvergenceViolation>();

    private final List<DynamicVersionViolation> dynamicVersionViolations = new ArrayList<DynamicVersionViolation>();

    private final List<DuplicateClassInfo> duplicateClasses = new ArrayList<DuplicateClassInfo>();

    private final List<String> warnings = new ArrayList<String>();

    public String getProjectCoordinates()
    {
        return projectCoordinates;
    }

    public void setProjectCoordinates( String projectCoordinates )
    {
        this.projectCoordinates = projectCoordinates;
    }

    public DepNode getRoot()
    {
        return root;
    }

    public void setRoot( DepNode root )
    {
        this.root = root;
    }

    public String getNormalizedTree()
    {
        return normalizedTree;
    }

    public void setNormalizedTree( String normalizedTree )
    {
        this.normalizedTree = normalizedTree;
    }

    public List<VersionConflict> getVersionConflicts()
    {
        return versionConflicts;
    }

    public List<ConvergenceViolation> getConvergenceViolations()
    {
        return convergenceViolations;
    }

    public List<DynamicVersionViolation> getDynamicVersionViolations()
    {
        return dynamicVersionViolations;
    }

    public List<DuplicateClassInfo> getDuplicateClasses()
    {
        return duplicateClasses;
    }

    public List<String> getWarnings()
    {
        return warnings;
    }
}
