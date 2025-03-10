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

package org.opensearch.search.aggregations;

import org.opensearch.index.fielddata.ScriptDocValues;
import org.opensearch.script.MockScriptPlugin;
import org.opensearch.script.Script;
import org.opensearch.script.ScriptType;
import org.opensearch.test.OpenSearchTestCase;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

import static java.util.Collections.singletonMap;

/**
 * This class contains various mocked scripts that are used in aggregations integration tests.
 */
public class AggregationTestScriptsPlugin extends MockScriptPlugin {

    // Equivalent to:
    //
    // List values = doc['values'];
    // double[] res = new double[values.size()];
    // for (int i = 0; i < res.length; i++) {
    //      res[i] = values.get(i) - dec;
    // };
    // return res;
    public static final Script DECREMENT_ALL_VALUES = new Script(ScriptType.INLINE, NAME, "decrement all values", singletonMap("dec", 1));

    @Override
    protected Map<String, Function<Map<String, Object>, Object>> pluginScripts() {
        Map<String, Function<Map<String, Object>, Object>> scripts = new HashMap<>();

        scripts.put("20 - _value", vars -> 20.0d - (double) vars.get("_value"));
        scripts.put("_value - 1", vars -> (double) vars.get("_value") - 1);
        scripts.put("_value + 1", vars -> (double) vars.get("_value") + 1);
        scripts.put("_value * -1", vars -> (double) vars.get("_value") * -1);

        scripts.put("_value - dec", vars -> {
            double value = (double) vars.get("_value");
            int dec = (int) vars.get("dec");
            return value - dec;
        });

        scripts.put("_value + inc", vars -> {
            double value = (double) vars.get("_value");
            int inc = (int) vars.get("inc");
            return value + inc;
        });

        scripts.put("doc['value'].value", vars -> {
            Map<?,?> doc = (Map<?,?>) vars.get("doc");
            return doc.get("value");
        });

        scripts.put("doc['value'].value - dec", vars -> {
            int dec = (int) vars.get("dec");
            Map<?,?> doc = (Map<?,?>) vars.get("doc");
            ScriptDocValues.Longs value = (ScriptDocValues.Longs) doc.get("value");
            return value.getValue() - dec;
        });

        scripts.put("doc['value'].value + inc", vars -> {
            int inc = (int) vars.get("inc");
            Map<?,?> doc = (Map<?,?>) vars.get("doc");
            ScriptDocValues.Longs value = (ScriptDocValues.Longs) doc.get("value");
            return value.getValue() + inc;
        });

        scripts.put("doc['values']", vars -> {
            Map<?, ?> doc = (Map<?,?>) vars.get("doc");
            return doc.get("values");
        });

        scripts.put(DECREMENT_ALL_VALUES.getIdOrCode(), vars -> {
            int dec = (int) vars.get("dec");
            Map<?, ?> doc = (Map<?,?>) vars.get("doc");
            ScriptDocValues.Longs values = (ScriptDocValues.Longs) doc.get("values");

            double[] res = new double[values.size()];
            for (int i = 0; i < res.length; i++) {
                res[i] = values.get(i) - dec;
            }
            return res;
        });

        scripts.put("[ doc['value'].value, doc['value'].value - dec ]", vars -> {
            Long a = ((ScriptDocValues.Longs) scripts.get("doc['value'].value").apply(vars)).getValue();
            Long b = (Long) scripts.get("doc['value'].value - dec").apply(vars);
            return new Long[]{a, b};
        });

        scripts.put("[ doc['value'].value, doc['value'].value + inc ]", vars -> {
            Long a = ((ScriptDocValues.Longs) scripts.get("doc['value'].value").apply(vars)).getValue();
            Long b = (Long) scripts.get("doc['value'].value + inc").apply(vars);
            return new Long[]{a, b};
        });

        return scripts;
    }

    @Override
    protected Map<String, Function<Map<String, Object>, Object>> nonDeterministicPluginScripts() {
        Map<String, Function<Map<String, Object>, Object>> scripts = new HashMap<>();

        scripts.put("Math.random()", vars -> OpenSearchTestCase.randomDouble());

        return scripts;
    }
}
