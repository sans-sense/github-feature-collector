package com.capitalone.dashboard.collector;

import java.net.MalformedURLException;
import java.net.URI;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.joda.time.format.DateTimeFormatter;
import org.joda.time.format.ISODateTimeFormat;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestOperations;

import com.capitalone.dashboard.model.Feature;
import com.capitalone.dashboard.util.Supplier;

/**
 * GitHubCiClient implementation that uses RestTemplate and JSONSimple to fetch
 * information from GitHubCi instances.
 */
@Component
public class DefaultGitHubFeatureClient implements GitHubFeatureClient {
	private static final Logger LOG = LoggerFactory.getLogger(DefaultGitHubFeatureClient.class);

	private final RestOperations rest;
	private static final SimpleDateFormat SIMPLE_FORMATTER = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
	private static final DateTimeFormatter ISO_FORMATTER = ISODateTimeFormat.dateTime();
	
	@Autowired
	public DefaultGitHubFeatureClient(Supplier<RestOperations> restOperationsSupplier) {
		this.rest = restOperationsSupplier.get();
	}

	@Override
	public List<Feature> getFeatures(String repoUrl) {
		List<Feature> features = new ArrayList<>();
		try {
			String featuresUrl = buildRestURL(repoUrl);
			ResponseEntity<String> restResponse = makeRestCall(featuresUrl);
			String response = restResponse.getBody();
			if (StringUtils.isEmpty(response)) {
				LOG.error("Error getting build details for URL =" + featuresUrl);
			} else {
				JSONParser parser = new JSONParser();
				try {
					JSONArray featuresJson = (JSONArray) parser.parse(response);
					for (Object rawJson : featuresJson) {
						JSONObject featureJson = (JSONObject) rawJson;
						Feature feature = getFeature(featureJson);
						features.add(feature);
					}
				} catch (ParseException | java.text.ParseException e) {
					LOG.error("Parsing build: " + featuresUrl, e);
				}
			}
		} catch (RestClientException | MalformedURLException exc) {
			LOG.error("Exception loading build details: " + exc.getMessage() + ". URL =" + repoUrl, exc);
		}
		return features;
	}

	private Feature getFeature(JSONObject featureJson) throws java.text.ParseException {
		Feature feature = new Feature();
		feature.setsId(getString(featureJson, "id"));
		feature.setsNumber(getString(featureJson, "number"));
		feature.setsName(getString(featureJson, "title"));
		feature.setsStatus(getString(featureJson, "state"));
		feature.setsSprintBeginDate(getDateAsString(featureJson, "created_at"));
		feature.setsSprintEndDate(getDateAsString(featureJson, "closed_at"));
		feature.setChangeDate(getDateAsString(featureJson, "updated_at"));
		return feature;
	}

	// get date as string, as feature understands string and mongo likes it's date strings cooked a certain way ISODate
	private String getDateAsString(JSONObject featureJson, String key) {
		String isoDate = null;
		try {
			Long time = getTime(featureJson, key);
			if (time != null ) {
				isoDate = ISO_FORMATTER.print(time);
			}
		} catch (Exception exc) {
			LOG.warn("Could not understand date for key" + key + " in json " + featureJson);
		}
		return isoDate;
	}

	protected Long getTime(JSONObject buildJson, String fieldName) throws java.text.ParseException {
		String rawValue = getString(buildJson, fieldName);
		if (rawValue != null && rawValue.length() > 0) {
			return SIMPLE_FORMATTER.parse(rawValue).getTime();
		} else {
			return null;
		}
	}

	protected String buildRestURL(String repoUrl) {
		String[] splits = repoUrl.split("/");
		if (splits.length < 2) {
			throw new IllegalArgumentException("Expected a valid repo url, but got " + repoUrl);
		}
		int totalLength = splits.length;
		// pick up the last 2
		return "https://api.github.com/repos/" + splits[totalLength - 2] + "/" + splits[totalLength - 1]
				+ "/issues?labels=story&state=all";
	}

	private String getString(JSONObject json, String key) {
		Object value = json.get(key);
		if (value != null) {
			return value.toString();
		} else {
			return "";
		}
	}

	protected ResponseEntity<String> makeRestCall(String sUrl) throws MalformedURLException {
		URI thisuri = URI.create(sUrl);
		return rest.exchange(thisuri, HttpMethod.GET, new HttpEntity<>(createHeaders()), String.class);

	}

	protected HttpHeaders createHeaders() {
		// -H 'GitHub-API-Version: 3' -H 'Content-Type: application/json' -H
		// 'Accept: application/json'
		HttpHeaders headers = new HttpHeaders();
		headers.set("GitHub-API-Version", "3");
		headers.set("Accept", "application/json");
		return headers;
	}

}
