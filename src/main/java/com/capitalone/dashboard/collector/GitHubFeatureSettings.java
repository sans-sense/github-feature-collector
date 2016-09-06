package com.capitalone.dashboard.collector;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Bean to hold settings specific to the GitHubCi collector.
 */
@Component
@ConfigurationProperties(prefix = "github-feature")
public class GitHubFeatureSettings {


    private String cron;
    private String host;
    private String key;
    // for the same repo, refresh only after this tim
    private int checkAfterMins = 30;

	public String getHost() {
		return host;
	}

	public void setHost(String host) {
		this.host = host;
	}

	public String getCron() {
        return cron;
    }

    public void setCron(String cron) {
        this.cron = cron;
    }

	public String getKey() {
		return key;
	}
	
	public void setKey(String key) {
		this.key = key;
	}
	
	public long getCheckAfterMillis() {
		return checkAfterMins * 60 * 1000;
	}
	
	public void setCheckAfter(int checkAfter) {
		this.checkAfterMins = checkAfter;
	}
}
