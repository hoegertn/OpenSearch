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

package org.opensearch.search.aggregations.support;

import org.opensearch.common.Nullable;
import org.opensearch.common.io.stream.StreamInput;
import org.opensearch.common.io.stream.StreamOutput;
import org.opensearch.common.xcontent.XContentBuilder;
import org.opensearch.index.query.QueryBuilder;
import org.opensearch.index.query.QueryShardContext;
import org.opensearch.search.DocValueFormat;
import org.opensearch.search.aggregations.AbstractAggregationBuilder;
import org.opensearch.search.aggregations.AggregationInitializationException;
import org.opensearch.search.aggregations.AggregatorFactories.Builder;
import org.opensearch.search.aggregations.AggregatorFactory;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Similar to {@link ValuesSourceAggregationBuilder}, except it references multiple ValuesSources (e.g. so that an aggregation
 * can pull values from multiple fields).
 *
 * A limitation of this class is that all the ValuesSource's being refereenced must be of the same type.
 */
public abstract class MultiValuesSourceAggregationBuilder<AB extends MultiValuesSourceAggregationBuilder<AB>>
        extends AbstractAggregationBuilder<AB> {


    public abstract static class LeafOnly<AB extends MultiValuesSourceAggregationBuilder<AB>>
            extends MultiValuesSourceAggregationBuilder<AB> {

        protected LeafOnly(String name) {
            super(name);
        }

        protected LeafOnly(LeafOnly<AB> clone, Builder factoriesBuilder, Map<String, Object> metadata) {
            super(clone, factoriesBuilder, metadata);
            if (factoriesBuilder.count() > 0) {
                throw new AggregationInitializationException("Aggregator [" + name + "] of type ["
                    + getType() + "] cannot accept sub-aggregations");
            }
        }

        /**
         * Read from a stream that does not serialize its targetValueType. This should be used by most subclasses.
         */
        protected LeafOnly(StreamInput in) throws IOException {
            super(in);
        }

        @Override
        public AB subAggregations(Builder subFactories) {
            throw new AggregationInitializationException("Aggregator [" + name + "] of type [" +
                getType() + "] cannot accept sub-aggregations");
        }
    }



    private Map<String, MultiValuesSourceFieldConfig> fields = new HashMap<>();
    private ValueType userValueTypeHint = null;
    private String format = null;

    protected MultiValuesSourceAggregationBuilder(String name) {
        super(name);
    }

    protected MultiValuesSourceAggregationBuilder(MultiValuesSourceAggregationBuilder<AB> clone,
                                                  Builder factoriesBuilder, Map<String, Object> metadata) {
        super(clone, factoriesBuilder, metadata);

        this.fields = new HashMap<>(clone.fields);
        this.userValueTypeHint = clone.userValueTypeHint;
        this.format = clone.format;
    }

    /**
     * Read from a stream.
     */
    protected MultiValuesSourceAggregationBuilder(StreamInput in)
        throws IOException {
        super(in);
        read(in);
    }

    /**
     * Read from a stream.
     */
    @SuppressWarnings("unchecked")
    private void read(StreamInput in) throws IOException {
        fields = in.readMap(StreamInput::readString, MultiValuesSourceFieldConfig::new);
        userValueTypeHint = in.readOptionalWriteable(ValueType::readFromStream);
        format = in.readOptionalString();
    }

    @Override
    protected final void doWriteTo(StreamOutput out) throws IOException {
        out.writeMap(fields, StreamOutput::writeString, (o, value) -> value.writeTo(o));
        out.writeOptionalWriteable(userValueTypeHint);
        out.writeOptionalString(format);
        innerWriteTo(out);
    }

    /**
     * Write subclass' state to the stream
     */
    protected abstract void innerWriteTo(StreamOutput out) throws IOException;

    @SuppressWarnings("unchecked")
    protected AB field(String propertyName, MultiValuesSourceFieldConfig config) {
        if (config == null) {
            throw new IllegalArgumentException("[config] must not be null: [" + name + "]");
        }
        this.fields.put(propertyName, config);
        return (AB) this;
    }

    /**
     * Sets the {@link ValueType} for the value produced by this aggregation
     */
    @SuppressWarnings("unchecked")
    public AB userValueTypeHint(ValueType valueType) {
        if (valueType == null) {
            throw new IllegalArgumentException("[userValueTypeHint] must not be null: [" + name + "]");
        }
        this.userValueTypeHint = valueType;
        return (AB) this;
    }

    /**
     * Sets the format to use for the output of the aggregation.
     */
    @SuppressWarnings("unchecked")
    public AB format(String format) {
        if (format == null) {
            throw new IllegalArgumentException("[format] must not be null: [" + name + "]");
        }
        this.format = format;
        return (AB) this;
    }

    /**
     * Aggregations should use this method to define a {@link ValuesSourceType} of last resort.  This will only be used when the resolver
     * can't find a field and the user hasn't provided a value type hint.
     *
     * @return The CoreValuesSourceType we expect this script to yield.
     */
    protected abstract ValuesSourceType defaultValueSourceType();

    @Override
    protected final MultiValuesSourceAggregatorFactory doBuild(QueryShardContext queryShardContext, AggregatorFactory parent,
                                                               Builder subFactoriesBuilder) throws IOException {
        Map<String, ValuesSourceConfig> configs = new HashMap<>(fields.size());
        Map<String, QueryBuilder> filters = new HashMap<>(fields.size());
        fields.forEach((key, value) -> {
            ValuesSourceConfig config = ValuesSourceConfig.resolveUnregistered(queryShardContext, userValueTypeHint,
                value.getFieldName(), value.getScript(), value.getMissing(), value.getTimeZone(), format, defaultValueSourceType());
            configs.put(key, config);
            filters.put(key, value.getFilter());
        });
        DocValueFormat docValueFormat = resolveFormat(format, userValueTypeHint, defaultValueSourceType());
        return innerBuild(queryShardContext, configs, filters, docValueFormat, parent, subFactoriesBuilder);
    }


    private static DocValueFormat resolveFormat(@Nullable String format, @Nullable ValueType valueType,
                                                ValuesSourceType defaultValuesSourceType) {
        if (valueType == null) {
            // If the user didn't send a hint, all we can do is fall back to the default
            return defaultValuesSourceType.getFormatter(format, null);
        }
        DocValueFormat valueFormat = valueType.defaultFormat;
        if (valueFormat instanceof DocValueFormat.Decimal && format != null) {
            valueFormat = new DocValueFormat.Decimal(format);
        }
        return valueFormat;
    }

    protected abstract MultiValuesSourceAggregatorFactory innerBuild(QueryShardContext queryShardContext,
                                                                     Map<String, ValuesSourceConfig> configs,
                                                                     Map<String, QueryBuilder> filters,
                                                                     DocValueFormat format, AggregatorFactory parent,
                                                                     Builder subFactoriesBuilder) throws IOException;


    @Override
    public final XContentBuilder internalXContent(XContentBuilder builder, Params params) throws IOException {
        builder.startObject();
        if (fields != null) {
            for (Map.Entry<String, MultiValuesSourceFieldConfig> fieldEntry : fields.entrySet()) {
                builder.field(fieldEntry.getKey(), fieldEntry.getValue());
            }
        }
        if (format != null) {
            builder.field(CommonFields.FORMAT.getPreferredName(), format);
        }
        if (userValueTypeHint != null) {
            builder.field(CommonFields.VALUE_TYPE.getPreferredName(), userValueTypeHint.getPreferredName());
        }
        doXContentBody(builder, params);
        builder.endObject();
        return builder;
    }

    protected abstract XContentBuilder doXContentBody(XContentBuilder builder, Params params) throws IOException;

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), fields, format, userValueTypeHint);
    }


    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        if (super.equals(obj) == false) return false;

        MultiValuesSourceAggregationBuilder other = (MultiValuesSourceAggregationBuilder) obj;
        return Objects.equals(this.fields, other.fields)
            && Objects.equals(this.format, other.format)
            && Objects.equals(this.userValueTypeHint, other.userValueTypeHint);
    }
}
