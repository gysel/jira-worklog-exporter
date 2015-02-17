package ch.hsr.sa.eai.jira;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashSet;
import java.util.Set;

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
	private final Logger log = LoggerFactory.getLogger(Main.class);

	public static void main(String[] args) throws URISyntaxException,
			IOException, JSONException {
		String link = args[0];
		String user = args[1];
		String pw = args[2];
		new Main().start(user, pw, link);
	}

	private void start(String user, String pw, String link)
			throws URISyntaxException, IOException, JSONException {
		final AsynchronousJiraRestClientFactory factory = new AsynchronousJiraRestClientFactory();
		URI url = new URI(link);
		JiraRestClient restClient = factory.createWithBasicHttpAuthentication(
				url, user, pw);
		try {
			Set<String> fields = new HashSet<String>();
			fields.add("worklog");
			fields.add("summary");
			fields.add("issuetype");
			fields.add("created");
			fields.add("updated");
			fields.add("project");
			fields.add("status");
			fields.add("parent");
			fields.add("sprint");
			final SearchResult issues = restClient.getSearchClient()
					.searchJql("project='HSR'", 999, 0, fields).claim();
			log.info("{} issues found.", issues.getTotal());
			log.info("Key\tSummary\tParent\tParent Summary\tSpent (min)\t Date\tPerson");
			DateTimeFormatter formatter = DateTimeFormat
					.forPattern("yyyy-MM-dd");
			for (Issue issue : issues.getIssues()) {
				IssueField parent = issue.getField("parent");
				JSONObject parentValue = null;
				JSONObject parentFields = null;
				Object parentKey = null;
				Object parentSummary = null;
				if (parent != null) {
					parentValue = (JSONObject) parent.getValue();
					parentFields = (JSONObject) parentValue.get("fields");
					parentKey = parentValue.get("key");
					parentSummary = parentFields.get("summary");
				}
				// fields
				String key = issue.getKey();
				String summary = issue.getSummary();
				//
				for (Worklog w : issue.getWorklogs()) {
					String person = w.getAuthor().getName();
					int spent = w.getMinutesSpent();
					DateTime date = w.getCreationDate();
					log.info("{}\t{}\t{}\t{}\t{}\t{}\t{}", key, summary,
							parentKey, parentSummary, spent,
							formatter.print(date), person);
				}
			}
		} finally {
			restClient.close();
		}
	}
}
