package com.capitalone.dashboard.collector;

import java.util.List;

import com.capitalone.dashboard.model.Feature;

/**
 * Client for fetching job and build information from GitHubCi
 */
public interface GitHubFeatureClient {

    List<Feature> getFeatures(String repoUrl);
    
}
