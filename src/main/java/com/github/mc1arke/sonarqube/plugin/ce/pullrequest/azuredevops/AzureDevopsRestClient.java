/*
 * Copyright (C) 2021 Michael Clarke
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 *
 */
package com.github.mc1arke.sonarqube.plugin.ce.pullrequest.azuredevops;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.mc1arke.sonarqube.plugin.ce.pullrequest.azuredevops.model.Comment;
import com.github.mc1arke.sonarqube.plugin.ce.pullrequest.azuredevops.model.CommentThread;
import com.github.mc1arke.sonarqube.plugin.ce.pullrequest.azuredevops.model.CommentThreadResponse;
import com.github.mc1arke.sonarqube.plugin.ce.pullrequest.azuredevops.model.GitPullRequestStatus;
import com.github.mc1arke.sonarqube.plugin.ce.pullrequest.azuredevops.model.PullRequest;
import com.github.mc1arke.sonarqube.plugin.ce.pullrequest.azuredevops.model.enums.CommentThreadStatus;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.RequestBuilder;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.sonar.api.utils.log.Logger;
import org.sonar.api.utils.log.Loggers;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

public class AzureDevopsRestClient implements AzureDevopsClient {

    private static final Logger LOGGER = Loggers.get(AzureDevopsRestClient.class);
    private static final String API_VERSION = "4.1";

    private final String authToken;
    private final String apiUrl;
    private final ObjectMapper objectMapper;

    public AzureDevopsRestClient(String apiUrl, String authToken, ObjectMapper objectMapper) {
        super();
        this.apiUrl = apiUrl;
        this.authToken = authToken;
        this.objectMapper = objectMapper;
    }

    @Override
    public void submitPullRequestStatus(String projectId, String repositoryName, int pullRequestId, GitPullRequestStatus status) throws IOException {
        String url = String.format("%s/%s/_apis/git/repositories/%s/pullRequests/%s/statuses?api-version=%s", apiUrl, URLEncoder.encode(projectId, StandardCharsets.UTF_8.name()), URLEncoder.encode(repositoryName, StandardCharsets.UTF_8.name()), pullRequestId, API_VERSION);
        execute(url, "post", objectMapper.writeValueAsString(status), null);
    }

    @Override
    public List<CommentThread> retrieveThreads(String projectId, String repositoryName, int pullRequestId) throws IOException {
        String url = String.format("%s/%s/_apis/git/repositories/%s/pullRequests/%s/threads?api-version=%s", apiUrl, URLEncoder.encode(projectId, StandardCharsets.UTF_8.name()), URLEncoder.encode(repositoryName, StandardCharsets.UTF_8.name()), pullRequestId, API_VERSION);
        return Objects.requireNonNull(execute(url, "get", null, CommentThreadResponse.class)).getValue();
    }

    @Override
    public void createThread(String projectId, String repositoryName, int pullRequestId, CommentThread thread) throws IOException {
        String url = String.format("%s/%s/_apis/git/repositories/%s/pullRequests/%s/threads?api-version=%s", apiUrl, URLEncoder.encode(projectId, StandardCharsets.UTF_8.name()), URLEncoder.encode(repositoryName, StandardCharsets.UTF_8.name()), pullRequestId, API_VERSION);
        execute(url, "post", objectMapper.writeValueAsString(thread), null);
    }

    @Override
    public void addCommentToThread(String projectId, String repositoryName, int pullRequestId, int threadId, Comment comment) throws IOException {
        String url = String.format("%s/%s/_apis/git/repositories/%s/pullRequests/%s/threads/%s/comments?api-version=%s", apiUrl, URLEncoder.encode(projectId, StandardCharsets.UTF_8.name()), URLEncoder.encode(repositoryName, StandardCharsets.UTF_8.name()), pullRequestId, threadId, API_VERSION);
        execute(url, "post", objectMapper.writeValueAsString(comment), null);
    }

    @Override
    public void updateThreadStatus(String projectId, String repositoryName, int pullRequestId, int threadId, CommentThreadStatus status) throws IOException {
        String url = String.format("%s/%s/_apis/git/repositories/%s/pullRequests/%s/threads/%s?api-version=%s", apiUrl, URLEncoder.encode(projectId, StandardCharsets.UTF_8.name()), URLEncoder.encode(repositoryName, StandardCharsets.UTF_8.name()), pullRequestId, threadId, API_VERSION);

        CommentThread commentThread = new CommentThread(CommentThreadStatus.CLOSED);
        execute(url, "patch", objectMapper.writeValueAsString(commentThread), null);
    }

    @Override
    public PullRequest retrievePullRequest(String projectId, String repositoryName, int pullRequestId) throws IOException {
        String url = String.format("%s/%s/_apis/git/repositories/%s/pullRequests/%s?api-version=%s", apiUrl, URLEncoder.encode(projectId, StandardCharsets.UTF_8.name()), URLEncoder.encode(repositoryName, StandardCharsets.UTF_8.name()), pullRequestId, API_VERSION);
        return execute(url, "get", null, PullRequest.class);
    }

    private <T> T execute(String url, String method, String content, Class<T> type) throws IOException {
        RequestBuilder requestBuilder = RequestBuilder.create(method)
                .setUri(url)
                .addHeader("Authorization", "Basic " + authToken)
                .addHeader("Content-type", ContentType.APPLICATION_JSON.withCharset(StandardCharsets.UTF_8.name()).toString());

        Optional.ofNullable(content).ifPresent(body -> requestBuilder.setEntity(new StringEntity(body, StandardCharsets.UTF_8.name())));
        Optional.ofNullable(type).ifPresent(responseType -> requestBuilder.addHeader("Accept", ContentType.APPLICATION_JSON.getMimeType()));

        try (CloseableHttpClient httpClient = HttpClients.createSystem()) {
            HttpResponse httpResponse = httpClient.execute(requestBuilder.build());

            validateResponse(httpResponse);

            if (null == type) {
                return null;
            }
            return objectMapper.readValue(EntityUtils.toString(httpResponse.getEntity(), StandardCharsets.UTF_8.name()), type);
        }
    }

    private static void validateResponse(HttpResponse httpResponse) {
        if (httpResponse.getStatusLine().getStatusCode() == 200) {
            return;
        }

        String responseContent;
        try {
            responseContent = EntityUtils.toString(httpResponse.getEntity(), StandardCharsets.UTF_8.name());
        } catch (IOException ex) {
            LOGGER.warn("Could not decode response entity", ex);
            responseContent = "";
        }

        LOGGER.error("Azure Devops response status did not match expected value. Expected: 200"
                + System.lineSeparator()
                + Arrays.stream(httpResponse.getAllHeaders())
                    .map(h -> h.getName() + ": " + h.getValue())
                    .collect(Collectors.joining(System.lineSeparator()))
                + System.lineSeparator()
                + responseContent);

        throw new IllegalStateException("An unexpected response code was returned from the Azure Devops API - Expected: 200, Got: " + httpResponse.getStatusLine().getStatusCode());
    }
}
