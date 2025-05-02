/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.plugin.vector;

import org.opensearch.plugins.Plugin;
import org.opensearch.plugins.SearchPlugin;
import org.opensearch.plugins.SearchPlugin.RescorerSpec;
import org.opensearch.plugin.vector.rescorer.MaxSimRescorerBuilder;

import java.util.ArrayList;
import java.util.List;

/**
 * Plugin for supporting late interaction retrieval models in OpenSearch.
 * These models work by comparing multiple token-level vectors per document
 * with query token vectors using MaxSim scoring.
 */
public class LateInteractionPlugin extends Plugin implements SearchPlugin {

    @Override
    public List<RescorerSpec<?>> getRescorers() {
        List<RescorerSpec<?>> rescorers = new ArrayList<>();
        
        rescorers.add(new RescorerSpec<MaxSimRescorerBuilder>(
            MaxSimRescorerBuilder.NAME,
            (in) -> new MaxSimRescorerBuilder(in),
            (parser) -> MaxSimRescorerBuilder.fromXContent(parser)
        ));
        
        return rescorers;
    }
}
