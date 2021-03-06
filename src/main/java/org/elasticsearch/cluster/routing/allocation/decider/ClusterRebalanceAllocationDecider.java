/*
 * Licensed to Elasticsearch under one or more contributor
 * license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright
 * ownership. Elasticsearch licenses this file to you under
 * the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.elasticsearch.cluster.routing.allocation.decider;

import org.elasticsearch.cluster.routing.ShardRouting;
import org.elasticsearch.cluster.routing.allocation.RoutingAllocation;
import org.elasticsearch.common.inject.Inject;
import org.elasticsearch.common.settings.Settings;

import java.util.Locale;

/**
 * This {@link AllocationDecider} controls re-balancing operations based on the
 * cluster wide active shard state. This decided can not be configured in
 * real-time and should be pre-cluster start via
 * <tt>cluster.routing.allocation.allow_rebalance</tt>. This setting respects the following
 * values:
 * <ul>
 * <li><tt>indices_primaries_active</tt> - Re-balancing is allowed only once all
 * primary shards on all indices are active.</li>
 * 
 * <li><tt>indices_all_active</tt> - Re-balancing is allowed only once all
 * shards on all indices are active.</li>
 * 
 * <li><tt>always</tt> - Re-balancing is allowed once a shard replication group
 * is active</li>
 * </ul>
 */
public class ClusterRebalanceAllocationDecider extends AllocationDecider {

    /**
     * An enum representation for the configured re-balance type. 
     */
    public static enum ClusterRebalanceType {
        /**
         * Re-balancing is allowed once a shard replication group is active
         */
        ALWAYS,
        /**
         * Re-balancing is allowed only once all primary shards on all indices are active.
         */
        INDICES_PRIMARIES_ACTIVE,
        /**
         * Re-balancing is allowed only once all shards on all indices are active. 
         */
        INDICES_ALL_ACTIVE
    }

    private final ClusterRebalanceType type;

    @Inject
    public ClusterRebalanceAllocationDecider(Settings settings) {
        super(settings);
        String allowRebalance = settings.get("cluster.routing.allocation.allow_rebalance", "indices_all_active");
        if ("always".equalsIgnoreCase(allowRebalance)) {
            type = ClusterRebalanceType.ALWAYS;
        } else if ("indices_primaries_active".equalsIgnoreCase(allowRebalance) || "indicesPrimariesActive".equalsIgnoreCase(allowRebalance)) {
            type = ClusterRebalanceType.INDICES_PRIMARIES_ACTIVE;
        } else if ("indices_all_active".equalsIgnoreCase(allowRebalance) || "indicesAllActive".equalsIgnoreCase(allowRebalance)) {
            type = ClusterRebalanceType.INDICES_ALL_ACTIVE;
        } else {
            logger.warn("[cluster.routing.allocation.allow_rebalance] has a wrong value {}, defaulting to 'indices_all_active'", allowRebalance);
            type = ClusterRebalanceType.INDICES_ALL_ACTIVE;
        }
        logger.debug("using [cluster.routing.allocation.allow_rebalance] with [{}]", type.toString().toLowerCase(Locale.ROOT));
    }

    @Override
    public Decision canRebalance(ShardRouting shardRouting, RoutingAllocation allocation) {
        if (type == ClusterRebalanceType.INDICES_PRIMARIES_ACTIVE) {
            // check if there are unassigned primaries.
            if ( allocation.routingNodes().hasUnassignedPrimaries() ) {
                return allocation.decision(Decision.NO, "cluster has unassigned primary shards");
            }
            // check if there are initializing primaries that don't have a relocatingNodeId entry.
            if ( allocation.routingNodes().hasInactivePrimaries() ) {
                return allocation.decision(Decision.NO, "cluster has inactive primary shards");
            }

            return allocation.decision(Decision.YES, "all primary shards are active");
        }
        if (type == ClusterRebalanceType.INDICES_ALL_ACTIVE) {
            // check if there are unassigned shards.
            if ( allocation.routingNodes().hasUnassignedShards() ) {
                return allocation.decision(Decision.NO, "cluster has unassigned shards");
            }
            // in case all indices are assigned, are there initializing shards which
            // are not relocating?
            if ( allocation.routingNodes().hasInactiveShards() ) {
                return allocation.decision(Decision.NO, "cluster has inactive shards");
            }
        }
        // type == Type.ALWAYS
        return allocation.decision(Decision.YES, "all shards are active");
    }
}
