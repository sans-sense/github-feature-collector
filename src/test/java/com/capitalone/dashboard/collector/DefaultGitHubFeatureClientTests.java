package com.capitalone.dashboard.collector;

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.List;

import org.apache.commons.io.IOUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Matchers;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestOperations;

import com.capitalone.dashboard.model.Feature;
import com.capitalone.dashboard.util.Supplier;

@RunWith(MockitoJUnitRunner.class)
public class DefaultGitHubFeatureClientTests {
	@Mock
	private Supplier<RestOperations> restOperationsSupplier;
	@Mock
	private RestOperations rest;
	private DefaultGitHubFeatureClient client;

	@Before
	public void init() {
		when(restOperationsSupplier.get()).thenReturn(rest);
		client = new DefaultGitHubFeatureClient(restOperationsSupplier);
	}

	@Test
	public void getBuildInformation() throws Exception {
		when(rest.exchange(Matchers.any(URI.class), eq(HttpMethod.GET), Matchers.any(HttpEntity.class),
				eq(String.class))).thenReturn(new ResponseEntity<>(getJson("featureDetails_full.json"), HttpStatus.OK));
		List<Feature> featureDetails = client.getFeatures("https://gitlab.pramati.com/Imaginea/KodeBeagle");
		assertThat(featureDetails.size(), is(11));
		Feature firstFeature = featureDetails.get(0);
		assertThat(firstFeature.getsStatus(), is("open"));
	}

	@Test
	public void buildRestURL() {
		String restUrl = client.buildRestURL("https://github.com/Imaginea/KodeBeagle");
		assertEquals("https://api.github.com/repos/Imaginea/KodeBeagle/issues?labels=story&state=all", restUrl);
	}

	private String getJson(String fileName) throws IOException {
		InputStream inputStream = DefaultGitHubFeatureClientTests.class.getResourceAsStream(fileName);
		return IOUtils.toString(inputStream);
	}

}