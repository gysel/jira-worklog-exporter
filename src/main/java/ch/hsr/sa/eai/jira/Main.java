package ch.hsr.sa.eai.jira;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;

import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.atlassian.jira.rest.client.api.JiraRestClient;
import com.atlassian.jira.rest.client.api.domain.Issue;
import com.atlassian.jira.rest.client.api.domain.IssueField;
import com.atlassian.jira.rest.client.api.domain.SearchResult;
import com.atlassian.jira.rest.client.api.domain.Worklog;
import com.atlassian.jira.rest.client.internal.async.AsynchronousJiraRestClientFactory;

public class Main {

	private static final String EPIC_LINK_CUSTOMFIELD = "customfield_10008";
	private static final String PROJECT_NAME = "BA";
	
	private final Logger log = LoggerFactory.getLogger(Main.class);

	public static void main(String[] args) throws URISyntaxException, IOException, JSONException, InterruptedException, ExecutionException {
		String link = args[0];
		String user = args[1];
		String pw = args[2];
		new Main().start(user, pw, link);
	}

	private void start(String user, String pw, String link) throws URISyntaxException, IOException, JSONException, InterruptedException, ExecutionException {
		final AsynchronousJiraRestClientFactory factory = new AsynchronousJiraRestClientFactory();
		URI url = new URI(link);
		JiraRestClient restClient = factory.createWithBasicHttpAuthentication(url, user, pw);
		try {
			Set<String> fields = new HashSet<String>();
			// fields.add("*all");
			fields.add("worklog");
			fields.add("summary");
			fields.add("issuetype");
			fields.add("created");
			fields.add("updated");
			fields.add("project");
			fields.add("status");
			fields.add("parent");
			fields.add("sprint");
			fields.add(EPIC_LINK_CUSTOMFIELD); // Epic Link
			final SearchResult issues = restClient.getSearchClient().searchJql("project='"+PROJECT_NAME+"'", 999, 0, fields).claim();
			log.info("{} issues found.", issues.getTotal());
			log.info("Key\tSummary\tParent\tParent Summary\tEpic\tSpent (min)\t Date\tPerson");
			DateTimeFormatter formatter = DateTimeFormat.forPattern("yyyy-MM-dd");
			for (Issue issue : issues.getIssues()) {
				IssueField parent = issue.getField("parent");
				JSONObject parentValue = null;
				JSONObject parentFields = null;
				String parentKey = null;
				String parentSummary = null;
				if (parent != null) {
					parentValue = (JSONObject) parent.getValue();
					parentFields = (JSONObject) parentValue.get("fields");
					parentKey = (String) parentValue.get("key");
					parentSummary = (String) parentFields.get("summary");
				} else {
					parentKey = issue.getKey();
					parentSummary = issue.getSummary();
				}
				// fields
				String key = issue.getKey();
				String summary = issue.getSummary();
				String epic = extractEpic(parentKey, restClient);
				for (Worklog w : issue.getWorklogs()) {
					String person = w.getAuthor().getName();
					int spent = w.getMinutesSpent();
					DateTime date = w.getCreationDate();
					log.info("{}\t{}\t{}\t{}\t{}\t{}\t{}\t{}", key, summary, parentKey, parentSummary, epic, spent, formatter.print(date), person);
				}
			}
		} finally {
			restClient.close();
		}
	}

	private static final Map<String, String> EPIC_LINK_CACHE = new HashMap<>();
	
	private String extractEpic(String key, JiraRestClient restClient) throws InterruptedException, ExecutionException {
		if(!EPIC_LINK_CACHE.containsKey(key)) {
			Issue issue = restClient.getIssueClient().getIssue(key).get();
			IssueField epicLink = issue.getFieldByName("Epic Link");
			String epic = (String) (epicLink != null && epicLink.getValue() != null ? epicLink.getValue() : "");
			EPIC_LINK_CACHE.put(key, epic);
		}
		return EPIC_LINK_CACHE.get(key);
	}
}
