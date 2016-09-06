package com.capitalone.dashboard.collector;

import static com.capitalone.dashboard.model.CollectorType.SCM;
import static com.capitalone.dashboard.model.CollectorType.ScopeOwner;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.bson.types.ObjectId;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.TaskScheduler;

import com.capitalone.dashboard.model.Collector;
import com.capitalone.dashboard.model.CollectorItem;
import com.capitalone.dashboard.model.CollectorType;
import com.capitalone.dashboard.model.Component;
import com.capitalone.dashboard.model.Feature;
import com.capitalone.dashboard.model.GitHubFeatureCollector;
import com.capitalone.dashboard.repository.BaseCollectorRepository;
import com.capitalone.dashboard.repository.CollectorItemRepository;
import com.capitalone.dashboard.repository.ComponentRepository;
import com.capitalone.dashboard.repository.FeatureRepository;
import com.capitalone.dashboard.repository.GitHubFeatureCollectorRepository;

/**
 * CollectorTask that fetches Build information from GitHubCi
 */
@org.springframework.stereotype.Component
public class GitHubFeatureCollectorTask extends CollectorTask<GitHubFeatureCollector> {

	private static final String ROW_SEPARATOR = "~";
	private static final String COL_SEPARATOR = "--";
	private static final SimpleDateFormat dateFormatter = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
	private static final String TEAM_ID = "default";

	private final GitHubFeatureCollectorRepository githubCiCollectorRepository;
	private final GitHubFeatureClient githubCiClient;
	private final GitHubFeatureSettings githubFeatureSettings;
	private final ComponentRepository dbComponentRepository;
	private final CollectorItemRepository collectorItemRepository;
	private final FeatureRepository featureRepository;

	@Autowired
	public GitHubFeatureCollectorTask(TaskScheduler taskScheduler,
			GitHubFeatureCollectorRepository githubCiCollectorRepository,
			CollectorItemRepository collectorItemRepository, FeatureRepository featureRepository,
			GitHubFeatureClient githubCiClient, GitHubFeatureSettings githubCiSettings,
			ComponentRepository dbComponentRepository) {
		super(taskScheduler, GitHubFeatureCollector.NAME);
		this.githubCiCollectorRepository = githubCiCollectorRepository;
		this.collectorItemRepository = collectorItemRepository;
		this.githubCiClient = githubCiClient;
		this.githubFeatureSettings = githubCiSettings;
		this.dbComponentRepository = dbComponentRepository;
		this.featureRepository = featureRepository;
	}

	@Override
	public GitHubFeatureCollector getCollector() {
		return new GitHubFeatureCollector();
	}

	@Override
	public BaseCollectorRepository<GitHubFeatureCollector> getCollectorRepository() {
		return githubCiCollectorRepository;
	}

	@Override
	public String getCron() {
		return githubFeatureSettings.getCron();
	}

	@Override
	public void collect(GitHubFeatureCollector collector) {
		long start = System.currentTimeMillis();
		log("Collecting jobs", start);
		List<ObjectId> collectorIds = new ArrayList<>();
		collectorIds.add(collector.getId());
		List<CollectorItem> collectorItems = collectorItemRepository.findByCollectorIdIn(collectorIds);
		if (collectorItems == null || collectorItems.isEmpty() == true) {
			log("For time reconciliation it is necessary that at least one collector item for github feature collector be created");
			return;
		}
		Map<String, Tuple> lastUpdatedLookup = createRepoNameVsCollectorItemLookup(collectorItems);
		CollectorItem defaultCollectorItem = collectorItems.get(0);

		List<Component> componentsForGitHub = collectCandidateRepositories(collector);
		log("Collected components " + componentsForGitHub.size(), start);
		for (Component component : componentsForGitHub) {
			List<CollectorItem> scms = component.getCollectorItems(SCM);
			for (CollectorItem scm : scms) {
				Map<String, Object> scmOptions = scm.getOptions();
				if (scmOptions != null && scmOptions.get("url") != null) {
					String repoUrl = (String) scmOptions.get("url");
					Tuple lastUpdatedTuple = lastUpdatedLookup.get(repoUrl);
					if (shouldProcess(lastUpdatedTuple)) {
						long lastUpdated = (lastUpdatedTuple != null) ? lastUpdatedTuple.time : 0l;
						log("Processing featuress for repo url " + repoUrl);
						addNewFeatures(repoUrl, lastUpdated, component.getId());
						updateLastUpdated(repoUrl, lastUpdatedTuple, defaultCollectorItem);
						dbComponentRepository.save(component);
					} else {
						log("Skipping component with repo url " + repoUrl);
					}
				}
			}
		}
		log("Finished", start);
	}

	private void updateLastUpdated(String repoUrl, Tuple lastUpdatedTuple, CollectorItem defaultItem) {
		CollectorItem itemToUpdate = (lastUpdatedTuple == null) ? defaultItem : lastUpdatedTuple.item;
		encodeRepoTime(repoUrl, itemToUpdate);
		collectorItemRepository.save(itemToUpdate);
	}

	private Map<String, Tuple> createRepoNameVsCollectorItemLookup(List<CollectorItem> items) {
		Map<String, Tuple> lookup = new HashMap<>();
		if (items != null && items.isEmpty() == false) {
			for (CollectorItem item : items) {
				lookup.putAll(decodeRepoTime(item));
			}
		}
		return lookup;
	}

	private Map<String, Tuple> decodeRepoTime(CollectorItem item) {
		// All this hack as collector items for the collector are searched by
		// UI, so if we create a collector item for every repo the UI would show
		// too many options confusing everyone
		String updateInfo = getAllLastUpdatedInfo(item);
		if (updateInfo == null || updateInfo.length() == 0) {
			return Collections.emptyMap();
		} else {
			Map<String, Long> repoVsTime = decodeMap(updateInfo);
			Map<String, Tuple> repoVsTuple = new HashMap<>();
			for (String key : repoVsTime.keySet()) {
				repoVsTuple.put(key, new Tuple(repoVsTime.get(key), item));
			}
			return repoVsTuple;
		}
	}

	private String getAllLastUpdatedInfo(CollectorItem item) {
		String updateInfo = (String) item.getOptions().get("lastUpdatedRepo");
		return updateInfo;
	}

	private void encodeRepoTime(String repoUrl, CollectorItem item) {
		String updateInfo = getAllLastUpdatedInfo(item);
		Map<String, Long> repoUrlVsTime = new HashMap<>();
		if (updateInfo == null) {
			repoUrlVsTime.put(repoUrl, System.currentTimeMillis());
		} else {
			repoUrlVsTime = decodeMap(updateInfo);
		}
		repoUrlVsTime.put(repoUrl, System.currentTimeMillis());
		item.getOptions().put("lastUpdatedRepo", encodeMap(repoUrlVsTime));
	}

	protected Map<String, Long> decodeMap(String updateInfo) {
		Map<String, Long> repoUrlVsTime = new HashMap<>();
		String[] repoAndTimeRows = updateInfo.split(ROW_SEPARATOR);
		for (String repoAndTime : repoAndTimeRows) {
			if (repoAndTime.length() > 0) {
				String[] repoAndTimeSplit = repoAndTime.split(COL_SEPARATOR);
				if (repoAndTimeSplit.length == 2) {
					repoUrlVsTime.put(repoAndTimeSplit[0], Long.parseLong(repoAndTimeSplit[1]));
				}
			}
		}
		return repoUrlVsTime;
	}

	protected String encodeMap(Map<String, Long> repoUrlVsTime) {
		StringBuilder builder = new StringBuilder();
		for (Entry<String, Long> urlAndTime : repoUrlVsTime.entrySet()) {
			builder.append(urlAndTime.getKey()).append(COL_SEPARATOR).append(urlAndTime.getValue())
					.append(ROW_SEPARATOR);
		}
		return builder.toString();
	}

	public void onStartup() {
		super.onStartup();
		log("Firing non-scheduled run");
		this.run();
		// ui looks for collector items for this collector for dashboard feature
		// widget configuration
		addCollectorItemIfAbsent(GitHubFeatureCollector.NAME);
	}

	private void addCollectorItemIfAbsent(String collectoName) {
		Collector collector = getCollectorRepository().findByName(collectoName);
		if (collector == null) {
			log("Problems running collector task, as collector has not yet been created");
		} else {
			String niceName = "issues-labelled-story";
			List<CollectorItem> collectorItems = collectorItemRepository.findByCollectorIdAndNiceName(collector.getId(),
					niceName);
			if (collectorItems == null || collectorItems.isEmpty()) {
				CollectorItem item = new CollectorItem();
				item.setDescription(niceName);
				item.setNiceName(niceName);
				item.setEnabled(true);
				item.getOptions().put("teamId", TEAM_ID);
				item.setCollectorId(collector.getId());
				collectorItemRepository.save(item);
				log("saved collector item for github collector task " + item);
			}
		}
	}

	private boolean shouldProcess(Tuple lastUpdateInfo) {
		if (lastUpdateInfo == null) {
			return true;
		} else {
			// if updated recently, then don't updated
			return (System.currentTimeMillis() - lastUpdateInfo.time) > githubFeatureSettings.getCheckAfterMillis();
		}
	}

	private void addNewFeatures(String repoUrl, long lastUpdated, ObjectId collectorItemId) {
		// sEpicID needed for SuperFeatureComparator.compare, sSprintEndDate
		// needed for querying, sEstimate needed for
		// FeatureServiceImpl.getFeatureEstimates
		for (Feature feature : githubCiClient.getFeatures(repoUrl)) {
			if (getTimeDefaultingToNow(feature.getChangeDate()) > lastUpdated) {
				feature.setCollectorId(collectorItemId);
				feature.setIsDeleted("False");
				feature.setsTeamID(TEAM_ID);
				feature.setsSprintName(TEAM_ID);
				feature.setsSprintID(TEAM_ID);
				featureRepository.save(feature);
			}
		}
	}

	private List<Component> collectCandidateRepositories(Collector collector) {
		List<Component> components = new ArrayList<>();
		for (Component comp : dbComponentRepository.findAll()) {
			if (hasCollectorItems(comp)) {
				if (isRelevant(comp.getCollectorItems().get(CollectorType.ScopeOwner), collector)) {
					components.add(comp);
				}
			}
		}
		return components;
	}

	private boolean hasCollectorItems(Component comp) {
		return comp.getCollectorItems() != null && !comp.getCollectorItems().isEmpty();
	}

	private boolean isRelevant(List<CollectorItem> itemList, Collector collector) {
		if (itemList != null) {
			for (CollectorItem ci : itemList) {
				if (ci != null && ci.getCollectorId().equals(collector.getId())) {
					return true;
				}
			}
		}
		return false;
	}

	protected long getTimeDefaultingToNow(String date) {
		try {
			return dateFormatter.parse(date).getTime();
		} catch (Exception e) {
			return System.currentTimeMillis();
		}
	}

	private static class Tuple {

		private long time;
		private CollectorItem item;

		private Tuple(long time, CollectorItem item) {
			this.time = time;
			this.item = item;
		}
	}
}
