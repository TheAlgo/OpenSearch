/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */

/*
 * Licensed to Elasticsearch under one or more contributor
 * license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright
 * ownership. Elasticsearch licenses this file to you under
 * the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

/*
 * Modifications Copyright OpenSearch Contributors. See
 * GitHub history for details.
 */

package org.opensearch.cluster.routing;

import org.opensearch.Version;
import org.opensearch.cluster.ClusterState;
import org.opensearch.cluster.OpenSearchAllocationTestCase;
import org.opensearch.cluster.health.ClusterStateHealth;
import org.opensearch.cluster.metadata.IndexMetadata;
import org.opensearch.cluster.metadata.Metadata;
import org.opensearch.cluster.node.DiscoveryNodes;
import org.opensearch.cluster.node.DiscoveryNodes.Builder;
import org.opensearch.cluster.routing.IndexRoutingTable;
import org.opensearch.cluster.routing.IndexShardRoutingTable;
import org.opensearch.cluster.routing.RoutingTable;
import org.opensearch.cluster.routing.ShardRouting;
import org.opensearch.cluster.routing.allocation.AllocationService;
import org.opensearch.cluster.routing.allocation.FailedShard;
import org.opensearch.common.settings.Settings;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.opensearch.cluster.routing.ShardRoutingState.INITIALIZING;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;

public class PrimaryTermsTests extends OpenSearchAllocationTestCase {

    private static final String TEST_INDEX_1 = "test1";
    private static final String TEST_INDEX_2 = "test2";
    private int numberOfShards;
    private int numberOfReplicas;
    private static final Settings DEFAULT_SETTINGS = Settings.builder().put(IndexMetadata.SETTING_VERSION_CREATED, Version.CURRENT).build();
    private AllocationService allocationService;
    private ClusterState clusterState;

    private final Map<String, long[]> primaryTermsPerIndex = new HashMap<>();

    @Override
    public void setUp() throws Exception {
        super.setUp();
        this.allocationService = createAllocationService(Settings.builder()
                .put("cluster.routing.allocation.node_concurrent_recoveries", Integer.MAX_VALUE) // don't limit recoveries
                .put("cluster.routing.allocation.node_initial_primaries_recoveries", Integer.MAX_VALUE)
                .build());
        this.numberOfShards = randomIntBetween(1, 5);
        this.numberOfReplicas = randomIntBetween(0, 5);
        logger.info("Setup test with {} shards and {} replicas.", this.numberOfShards, this.numberOfReplicas);
        this.primaryTermsPerIndex.clear();
        Metadata metadata = Metadata.builder()
                .put(createIndexMetadata(TEST_INDEX_1))
                .put(createIndexMetadata(TEST_INDEX_2))
                .build();

        RoutingTable routingTable = new RoutingTable.Builder()
                .add(new IndexRoutingTable.Builder(metadata.index(TEST_INDEX_1).getIndex()).initializeAsNew(metadata.index(TEST_INDEX_1))
                        .build())
                .add(new IndexRoutingTable.Builder(metadata.index(TEST_INDEX_2).getIndex()).initializeAsNew(metadata.index(TEST_INDEX_2))
                        .build())
                .build();

        this.clusterState = ClusterState.builder(org.opensearch.cluster.ClusterName.CLUSTER_NAME_SETTING.getDefault(Settings.EMPTY))
            .metadata(metadata).routingTable(routingTable).build();
    }

    /**
     * puts primary shard indexRoutings into initializing state
     */
    private void initPrimaries() {
        logger.info("adding {} nodes and performing rerouting", this.numberOfReplicas + 1);
        Builder discoBuilder = DiscoveryNodes.builder();
        for (int i = 0; i < this.numberOfReplicas + 1; i++) {
            discoBuilder = discoBuilder.add(newNode("node" + i));
        }
        this.clusterState = ClusterState.builder(clusterState).nodes(discoBuilder).build();
        ClusterState rerouteResult = allocationService.reroute(clusterState, "reroute");
        assertThat(rerouteResult, not(equalTo(this.clusterState)));
        applyRerouteResult(rerouteResult);
        primaryTermsPerIndex.keySet().forEach(this::incrementPrimaryTerm);
    }

    private void incrementPrimaryTerm(String index) {
        final long[] primaryTerms = primaryTermsPerIndex.get(index);
        for (int i = 0; i < primaryTerms.length; i++) {
            primaryTerms[i]++;
        }
    }

    private void incrementPrimaryTerm(String index, int shard) {
        primaryTermsPerIndex.get(index)[shard]++;
    }

    private boolean startInitializingShards(String index) {
        final List<ShardRouting> startedShards = clusterState.getRoutingNodes().shardsWithState(index, INITIALIZING);
        logger.info("start primary shards for index [{}]: {} ", index, startedShards);
        ClusterState rerouteResult = startShardsAndReroute(allocationService, clusterState, startedShards);
        boolean changed = rerouteResult.equals(clusterState) == false;
        applyRerouteResult(rerouteResult);
        return changed;
    }

    private void applyRerouteResult(ClusterState newClusterState) {
        ClusterState previousClusterState = this.clusterState;
        ClusterState.Builder builder = ClusterState.builder(newClusterState).incrementVersion();
        if (previousClusterState.routingTable() != newClusterState.routingTable()) {
            builder.routingTable(RoutingTable.builder(newClusterState.routingTable()).version(newClusterState.routingTable().version() + 1)
                    .build());
        }
        if (previousClusterState.metadata() != newClusterState.metadata()) {
            builder.metadata(Metadata.builder(newClusterState.metadata()).version(newClusterState.metadata().version() + 1));
        }
        this.clusterState = builder.build();
        final ClusterStateHealth clusterHealth = new ClusterStateHealth(clusterState);
        logger.info("applied reroute. active shards: p [{}], t [{}], init shards: [{}], relocating: [{}]",
                clusterHealth.getActivePrimaryShards(), clusterHealth.getActiveShards(),
                clusterHealth.getInitializingShards(), clusterHealth.getRelocatingShards());
    }

    private void failSomePrimaries(String index) {
        final IndexRoutingTable indexShardRoutingTable = clusterState.routingTable().index(index);
        Set<Integer> shardIdsToFail = new HashSet<>();
        for (int i = 1 + randomInt(numberOfShards - 1); i > 0; i--) {
            shardIdsToFail.add(randomInt(numberOfShards - 1));
        }
        logger.info("failing primary shards {} for index [{}]", shardIdsToFail, index);
        List<FailedShard> failedShards = new ArrayList<>();
        for (int shard : shardIdsToFail) {
            failedShards.add(new FailedShard(indexShardRoutingTable.shard(shard).primaryShard(), "test", null, randomBoolean()));
            incrementPrimaryTerm(index, shard); // the primary failure should increment the primary term;
        }
        applyRerouteResult(allocationService.applyFailedShards(this.clusterState, failedShards,Collections.emptyList()));
    }

    private void addNodes() {
        DiscoveryNodes.Builder nodesBuilder = DiscoveryNodes.builder(clusterState.nodes());
        final int newNodes = randomInt(10);
        logger.info("adding [{}] nodes", newNodes);
        for (int i = 0; i < newNodes; i++) {
            nodesBuilder.add(newNode("extra_" + i));
        }
        this.clusterState = ClusterState.builder(clusterState).nodes(nodesBuilder).build();
        applyRerouteResult(allocationService.reroute(this.clusterState, "nodes added"));

    }

    private IndexMetadata.Builder createIndexMetadata(String indexName) {
        primaryTermsPerIndex.put(indexName, new long[numberOfShards]);
        final IndexMetadata.Builder builder = new IndexMetadata.Builder(indexName)
                .settings(DEFAULT_SETTINGS)
                .numberOfReplicas(this.numberOfReplicas)
                .numberOfShards(this.numberOfShards);
        for (int i = 0; i < numberOfShards; i++) {
            builder.primaryTerm(i, randomInt(200));
            primaryTermsPerIndex.get(indexName)[i] = builder.primaryTerm(i);
        }
        return builder;
    }

    private void assertAllPrimaryTerm() {
        primaryTermsPerIndex.keySet().forEach(this::assertPrimaryTerm);
    }

    private void assertPrimaryTerm(String index) {
        final long[] terms = primaryTermsPerIndex.get(index);
        final IndexMetadata indexMetadata = clusterState.metadata().index(index);
        for (IndexShardRoutingTable shardRoutingTable : this.clusterState.routingTable().index(index)) {
            final int shard = shardRoutingTable.shardId().id();
            assertThat("primary term mismatch between indexMetadata of [" + index + "] and shard [" + shard + "]'s routing",
                    indexMetadata.primaryTerm(shard), equalTo(terms[shard]));
        }
    }

    public void testPrimaryTermMetadataSync() {
        assertAllPrimaryTerm();

        initPrimaries();
        assertAllPrimaryTerm();

        startInitializingShards(TEST_INDEX_1);
        assertAllPrimaryTerm();

        startInitializingShards(TEST_INDEX_2);
        assertAllPrimaryTerm();

        // now start all replicas too
        startInitializingShards(TEST_INDEX_1);
        startInitializingShards(TEST_INDEX_2);
        assertAllPrimaryTerm();

        // relocations shouldn't change much
        addNodes();
        assertAllPrimaryTerm();
        boolean changed = true;
        while (changed) {
            changed = startInitializingShards(TEST_INDEX_1);
            assertAllPrimaryTerm();
            changed |= startInitializingShards(TEST_INDEX_2);
            assertAllPrimaryTerm();
        }

        // primary promotion
        failSomePrimaries(TEST_INDEX_1);
        assertAllPrimaryTerm();

        // stablize cluster
        changed = true;
        while (changed) {
            changed = startInitializingShards(TEST_INDEX_1);
            assertAllPrimaryTerm();
            changed |= startInitializingShards(TEST_INDEX_2);
            assertAllPrimaryTerm();
        }
    }
}
