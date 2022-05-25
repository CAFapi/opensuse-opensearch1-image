/*
 * Copyright 2022 Micro Focus or one of its affiliates.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package cafapi.opensuse.opensearch1;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;

import org.apache.http.HttpHost;
import org.apache.http.ParseException;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.nio.client.HttpAsyncClientBuilder;
import org.apache.http.ssl.SSLContextBuilder;
import org.apache.http.util.EntityUtils;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;
import org.junit.rules.TestWatcher;
import org.junit.runner.Description;
import org.opensearch.client.Request;
import org.opensearch.client.Response;
import org.opensearch.client.RestClient;
import org.opensearch.client.RestClientBuilder;
import org.opensearch.client.json.jackson.JacksonJsonpMapper;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch._types.HealthStatus;
import org.opensearch.client.opensearch._types.mapping.Property;
import org.opensearch.client.opensearch.cluster.HealthResponse;
import org.opensearch.client.opensearch.indices.CreateIndexRequest;
import org.opensearch.client.opensearch.indices.CreateIndexResponse;
import org.opensearch.client.opensearch.indices.IndexSettings;
import org.opensearch.client.transport.OpenSearchTransport;
import org.opensearch.client.transport.rest_client.RestClientTransport;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

public final class ContainerIT {

    private final static ObjectMapper objectMapper = new ObjectMapper();
    private static final int CONNECT_TIMEOUT = 60000;
    private static final int SOCKET_TIMEOUT = 60000;

    private final RestClient restClient;
    private final OpenSearchTransport transport;
    private final OpenSearchClient client;

    public ContainerIT() {
        restClient = getOpenSearchRestClient();
        transport = getOpenSearchTransport();
        client = new OpenSearchClient(transport);
    }

    @Rule
    public TestRule watcher = new TestWatcher() {
        @Override
        protected void starting(final Description description) {
            System.out.println("Running test: " + description.getMethodName());
        }
    };

    @Test
    public void testIndexCreation() throws IOException {
        try {
            System.out.println("Got OpenSearchClient :" + client);

            final HealthResponse response = client.cluster().health();
            System.out.println("Got HealthResponse :" + response);
            final HealthStatus status = response.status();
            System.out.println("Got HealthStatus :" + status.jsonValue());
            assertEquals("Elasticsearch status not green", HealthStatus.Green, status);

            // Create an index
            final String index = "container_test";
            final IndexSettings.Builder settingsBuilder = new IndexSettings.Builder();
            settingsBuilder.numberOfShards("1");
            settingsBuilder.numberOfReplicas("0");
            final IndexSettings indexSettings = settingsBuilder.build();

            final CreateIndexRequest.Builder indexBuilder = new CreateIndexRequest.Builder();
            indexBuilder.index(index);
            indexBuilder.settings(indexSettings);

            final Map<String, Property> fields = Collections.singletonMap("text", Property.of(p -> p.text(f -> f.store(false))));
            final Property text = Property.of(p -> p.text(t -> t.fields(fields)));
            indexBuilder.mappings(m -> m.properties("message", text));

            final CreateIndexRequest createIndexRequest = indexBuilder.build();

            final CreateIndexResponse createIndexResponse = client.indices().create(createIndexRequest);

            assertTrue("Index response was not acknowledged", createIndexResponse.acknowledged());
            assertTrue("All shards were not copied", createIndexResponse.shardsAcknowledged());
        } catch (final Exception e) {
            e.printStackTrace();
            fail("testIndexCreation failed" + e);
        } finally {
            restClient.close();
        }
    }

    // @Test
    public void testClusterHealth() throws IOException {
        try {
            System.out.println("Got OpenSearchClient :" + client);

            final HealthResponse response = client.cluster().health();
            System.out.println("Got HealthResponse :" + response);
            final HealthStatus status = response.status();
            System.out.println("Got HealthStatus :" + status.jsonValue());
            assertEquals("Elasticsearch status not green", HealthStatus.Green, status);
        } catch (final Exception e) {
            e.printStackTrace();
            fail("testClusterHealth failed" + e);
        } finally {
            restClient.close();
        }
    }

    // @Test
    public void testClusterStatus() throws IOException {
        final Response response;
        try {
            System.out.println("Executing ES cluster health check...");
            // Exclude system indices from healthcheck
            final String endPoint = "_cluster/health/*,-.*";
            final Request healthRequest = new Request("GET", endPoint);
            healthRequest.addParameter("wait_for_status", "yellow");
            response = restClient.performRequest(healthRequest);
        } catch (final IOException ex) {
            System.out.println("Error executing cluster health check request." + ex);
            ex.printStackTrace();
            fail("Error executing cluster health check request.");
            return;
        }

        final String healthResponse;
        try {
            healthResponse = EntityUtils.toString(response.getEntity());
        } catch (final ParseException | IOException ex) {
            System.out.println("Error parsing cluster health check Response" + ex);
            fail("Error parsing cluster health check Response.");
            return;
        }

        System.out.println("ClusterHealthResponse : " + healthResponse);

        if (healthResponse == null) {
            System.out.println("No HealthCheck response from ElasticSearch");
            fail("No HealthCheck response from ElasticSearch");
            return;
        }

        final String status;
        try {
            status = objectMapper.readTree(healthResponse).get("status").asText();
        } catch (JsonProcessingException ex) {
            System.out.println("HealthCheck response could not be processed" + ex);
            fail("HealthCheck response could not be processed");
            return;
        }

        System.out.println("Got ES status : " + status);
        if (status.equals("red")) {
            System.out.println("Elasticsearch is unhealthy.");
            fail("Elastic search status invalid: " + status);
        }
        assertEquals("Elasticsearch status not green", "green", status);
    }

    private OpenSearchTransport getOpenSearchTransport() {
        System.out.println("Creating OpenSearchTransport...");
        final OpenSearchTransport transport = new RestClientTransport(restClient, new JacksonJsonpMapper());
        return transport;
    }

    private RestClient getOpenSearchRestClient() {
        System.out.println("getOpenSearchRestClient...");
        final String userName = Optional.ofNullable(System.getProperty("user")).orElse("admin");
        final String password = Optional.ofNullable(System.getProperty("password")).orElse("admin");

        final CredentialsProvider credentialsProvider = new BasicCredentialsProvider();
        credentialsProvider.setCredentials(AuthScope.ANY, new UsernamePasswordCredentials(userName, password));
        try {
            System.out.println("Client to " + System.getenv("OPENSEARCH_SCHEME") + "://" + System.getenv("OPENSEARCH_HOST") + ":"
                + System.getenv("OPENSEARCH_PORT"));

            final HttpHost httpHost = new HttpHost(System.getenv("OPENSEARCH_HOST"), Integer.parseInt(System.getenv("OPENSEARCH_PORT")),
                System.getenv("OPENSEARCH_SCHEME"));

            final RestClientBuilder builder = RestClient.builder(httpHost);

            builder.setRequestConfigCallback(
                requestConfigBuilder -> requestConfigBuilder.setConnectTimeout(CONNECT_TIMEOUT).setSocketTimeout(SOCKET_TIMEOUT));

            builder.setHttpClientConfigCallback(httpAsyncClientBuilder -> httpAsyncClientBuilder);

            builder.setHttpClientConfigCallback(new RestClientBuilder.HttpClientConfigCallback() {
                @Override
                public HttpAsyncClientBuilder customizeHttpClient(final HttpAsyncClientBuilder httpClientBuilder) {
                    httpClientBuilder.setDefaultCredentialsProvider(credentialsProvider)
                        .setSSLHostnameVerifier(NoopHostnameVerifier.INSTANCE).setKeepAliveStrategy(
                            (response, context) -> 3600000/* 1hour */);
                    try {
                        httpClientBuilder
                            .setSSLContext(SSLContextBuilder.create().loadTrustMaterial(null, (chains, authType) -> true).build());
                    } catch (final KeyManagementException | NoSuchAlgorithmException | KeyStoreException e) {
                        e.printStackTrace();
                    }
                    return httpClientBuilder;
                }
            });

            System.out.println("Creating RestClient...");
            return builder.build();
        } catch (final Exception e) {
            e.printStackTrace();
            fail("Unable to create OpenSearch client");
            return null;
        }
    }

}
