package com.capitalone.dashboard.model;

/**
 * Extension of Collector that stores current build server configuration.
 */
public class GitHubFeatureCollector extends Collector {
    public static final String NAME = "GitHub-Feature";

	public GitHubFeatureCollector() {
        this.setName(NAME);
        this.setCollectorType(CollectorType.ScopeOwner);
        this.setOnline(true);
        this.setEnabled(true);
    }
}
