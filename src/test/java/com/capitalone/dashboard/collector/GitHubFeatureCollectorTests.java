package com.capitalone.dashboard.collector;

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import java.util.HashMap;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.runners.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class GitHubFeatureCollectorTests {
	private GitHubFeatureCollectorTask task;

	@Before
	public void init() {
		task = new GitHubFeatureCollectorTask(null, null, null, null, null, null, null);
	}

	@Test
	public void encodeMap() {
		Map<String, Long> repoUrlVsTime = new HashMap<>();
		String key = "imaginea/kodebeagle";
		long now = System.currentTimeMillis();
		repoUrlVsTime.put(key, now);
		String encodedValue = task.encodeMap(repoUrlVsTime);
		assertThat(encodedValue, is(String.format("%s--%s~", key, now)));
	}

	@Test
	public void encodeMapMultipleValues() {
		Map<String, Long> repoUrlVsTime = new HashMap<>();
		String key = "imaginea/kodebeagle";
		long now = System.currentTimeMillis();
		repoUrlVsTime.put(key, now);
		repoUrlVsTime.put("blah blah", now);
		String encodedValue = task.encodeMap(repoUrlVsTime);
		assertTrue(encodedValue.contains("blah blah"));
	} 

	@Test
	public void decodeMap() {
		String values = "blah blah--1472743481745~imaginea/kodebeagle--1472743481745~";
		Map<String, Long> decodeMap = task.decodeMap(values);
		assertTrue(decodeMap.containsKey("blah blah"));
	}
}