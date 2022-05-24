/*
 * Copyright 2022-2022 Micro Focus or one of its affiliates.
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

import java.io.IOException;
import java.util.Collections;
import java.util.Map;

import org.apache.http.HttpHost;
import org.junit.Test;
import org.opensearch.client.RestClient;
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

public final class ContainerIT {

    @Test
    public void testIndexCreation() throws IOException {
        try (final OpenSearchTransport transport = getOpenSearchTransport()) {
            final OpenSearchClient client = new OpenSearchClient(transport);

            // Check cluster health
            final HealthResponse response = client.cluster().health();
            final HealthStatus status = response.status();
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
        }
    }

    private static OpenSearchTransport getOpenSearchTransport() {
        final RestClient restClient = RestClient
            .builder(new HttpHost(System.getenv("OPENSEARCH_HOST"), Integer.parseInt(System.getenv("OPENSEARCH_PORT")), "http")).build();

        final OpenSearchTransport transport = new RestClientTransport(restClient, new JacksonJsonpMapper());
        return transport;
    }
}
