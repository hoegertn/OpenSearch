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
 *    http://www.apache.org/licenses/LICENSE-2.0
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

package org.opensearch.search.aggregations.pipeline;

import org.opensearch.script.Script;
import org.opensearch.script.ScriptType;
import org.opensearch.search.aggregations.BasePipelineAggregationTestCase;
import org.opensearch.search.aggregations.pipeline.BucketHelpers.GapPolicy;

import java.util.HashMap;
import java.util.Map;

import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;
import static org.hamcrest.CoreMatchers.equalTo;

public class BucketSelectorTests extends BasePipelineAggregationTestCase<BucketSelectorPipelineAggregationBuilder> {

    @Override
    protected BucketSelectorPipelineAggregationBuilder createTestAggregatorFactory() {
        String name = randomAlphaOfLengthBetween(3, 20);
        Map<String, String> bucketsPaths = new HashMap<>();
        int numBucketPaths = randomIntBetween(1, 10);
        for (int i = 0; i < numBucketPaths; i++) {
            bucketsPaths.put(randomAlphaOfLengthBetween(1, 20), randomAlphaOfLengthBetween(1, 40));
        }
        Script script;
        if (randomBoolean()) {
            script = mockScript("script");
        } else {
            Map<String, Object> params = new HashMap<>();
            if (randomBoolean()) {
                params.put("foo", "bar");
            }
            ScriptType type = randomFrom(ScriptType.values());
            script =
                new Script(type, type == ScriptType.STORED ? null : randomFrom("my_lang", Script.DEFAULT_SCRIPT_LANG), "script", params);
        }
        BucketSelectorPipelineAggregationBuilder factory = new BucketSelectorPipelineAggregationBuilder(name, bucketsPaths, script);
        if (randomBoolean()) {
            factory.gapPolicy(randomFrom(GapPolicy.values()));
        }
        return factory;
    }

    public void testNoParent() {
        assertThat(validate(emptyList(), new BucketSelectorPipelineAggregationBuilder("foo", emptyMap(), new Script("foo"))),
            equalTo("Validation Failed: 1: bucket_selector aggregation [foo] must be declared inside of another aggregation;"));
    }
}
