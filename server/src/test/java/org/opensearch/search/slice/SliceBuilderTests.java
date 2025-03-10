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

package org.opensearch.search.slice;

import org.apache.lucene.analysis.MockAnalyzer;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.DocValuesType;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.MatchNoDocsQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.opensearch.LegacyESVersion;
import org.opensearch.Version;
import org.opensearch.action.OriginalIndices;
import org.opensearch.action.search.SearchRequest;
import org.opensearch.cluster.ClusterState;
import org.opensearch.cluster.metadata.IndexMetadata;
import org.opensearch.cluster.metadata.Metadata;
import org.opensearch.cluster.routing.GroupShardsIterator;
import org.opensearch.cluster.routing.OperationRouting;
import org.opensearch.cluster.routing.PlainShardIterator;
import org.opensearch.cluster.routing.ShardIterator;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.Nullable;
import org.opensearch.common.Strings;
import org.opensearch.common.io.stream.NamedWriteableRegistry;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.xcontent.XContentBuilder;
import org.opensearch.common.xcontent.XContentFactory;
import org.opensearch.common.xcontent.XContentParser;
import org.opensearch.common.xcontent.XContentType;
import org.opensearch.index.IndexSettings;
import org.opensearch.index.fielddata.IndexNumericFieldData;
import org.opensearch.index.mapper.IdFieldMapper;
import org.opensearch.index.mapper.MappedFieldType;
import org.opensearch.index.mapper.MapperService;
import org.opensearch.index.mapper.TextSearchInfo;
import org.opensearch.index.mapper.ValueFetcher;
import org.opensearch.index.query.QueryShardContext;
import org.opensearch.index.shard.ShardId;
import org.opensearch.search.internal.ShardSearchRequest;
import org.opensearch.search.lookup.SearchLookup;
import org.opensearch.test.OpenSearchTestCase;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.opensearch.test.EqualsHashCodeTestUtils.checkEqualsAndHashCode;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.instanceOf;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class SliceBuilderTests extends OpenSearchTestCase {
    private static final int MAX_SLICE = 20;

    private static SliceBuilder randomSliceBuilder() {
        int max = randomIntBetween(2, MAX_SLICE);
        int id = randomIntBetween(1, max - 1);
        String field = randomAlphaOfLengthBetween(5, 20);
        return new SliceBuilder(field, id, max);
    }

    private static SliceBuilder serializedCopy(SliceBuilder original) throws IOException {
        return copyWriteable(original, new NamedWriteableRegistry(Collections.emptyList()), SliceBuilder::new);
    }

    private static SliceBuilder mutate(SliceBuilder original) {
        switch (randomIntBetween(0, 2)) {
            case 0: return new SliceBuilder(original.getField() + "_xyz", original.getId(), original.getMax());
            case 1: return new SliceBuilder(original.getField(), original.getId() - 1, original.getMax());
            case 2:
            default: return new SliceBuilder(original.getField(), original.getId(), original.getMax() + 1);
        }
    }

    private IndexSettings createIndexSettings(Version indexVersionCreated, int numShards) {
        Settings settings = Settings.builder()
            .put(IndexMetadata.SETTING_VERSION_CREATED, indexVersionCreated)
            .put(IndexMetadata.SETTING_NUMBER_OF_SHARDS, numShards)
            .put(IndexMetadata.SETTING_NUMBER_OF_REPLICAS, 0)
            .build();
        IndexMetadata indexState = IndexMetadata.builder("index").settings(settings).build();
        return new IndexSettings(indexState, Settings.EMPTY);
    }

    private ShardSearchRequest createRequest(int shardId) {
        return createRequest(shardId, Strings.EMPTY_ARRAY, null);
    }

    private ShardSearchRequest createRequest(int shardId, String[] routings, String preference) {
        return new ShardSearchRequest(OriginalIndices.NONE, new SearchRequest().preference(preference).allowPartialSearchResults(true),
            new ShardId("index", "index", shardId), 1, null, 0f, System.currentTimeMillis(), null, routings);
    }

    private QueryShardContext createShardContext(Version indexVersionCreated, IndexReader reader,
                                                 String fieldName, DocValuesType dvType, int numShards, int shardId) {
        MappedFieldType fieldType = new MappedFieldType(fieldName, true, false, dvType != null,
            TextSearchInfo.NONE, Collections.emptyMap()) {

            @Override
            public ValueFetcher valueFetcher(MapperService mapperService, SearchLookup searchLookup, String format) {
                throw new UnsupportedOperationException();
            }

            @Override
            public String typeName() {
                return null;
            }

            @Override
            public Query termQuery(Object value, @Nullable QueryShardContext context) {
                return null;
            }

            public Query existsQuery(QueryShardContext context) {
                return null;
            }
        };
        QueryShardContext context = mock(QueryShardContext.class);
        when(context.fieldMapper(fieldName)).thenReturn(fieldType);
        when(context.getIndexReader()).thenReturn(reader);
        when(context.getShardId()).thenReturn(shardId);
        IndexSettings indexSettings = createIndexSettings(indexVersionCreated, numShards);
        when(context.getIndexSettings()).thenReturn(indexSettings);
        if (dvType != null) {
            IndexNumericFieldData fd = mock(IndexNumericFieldData.class);
            when(context.getForField(fieldType)).thenReturn(fd);
        }
        return context;

    }

    public void testSerialization() throws Exception {
        SliceBuilder original = randomSliceBuilder();
        SliceBuilder deserialized = serializedCopy(original);
        assertEquals(deserialized, original);
        assertEquals(deserialized.hashCode(), original.hashCode());
        assertNotSame(deserialized, original);
    }

    public void testEqualsAndHashcode() throws Exception {
        checkEqualsAndHashCode(randomSliceBuilder(), SliceBuilderTests::serializedCopy, SliceBuilderTests::mutate);
    }

    public void testFromXContent() throws Exception {
        SliceBuilder sliceBuilder = randomSliceBuilder();
        XContentBuilder builder = XContentFactory.contentBuilder(randomFrom(XContentType.values()));
        if (randomBoolean()) {
            builder.prettyPrint();
        }
        builder.startObject();
        sliceBuilder.innerToXContent(builder);
        builder.endObject();
        try (XContentParser parser = createParser(shuffleXContent(builder))) {
            SliceBuilder secondSliceBuilder = SliceBuilder.fromXContent(parser);
            assertNotSame(sliceBuilder, secondSliceBuilder);
            assertEquals(sliceBuilder, secondSliceBuilder);
            assertEquals(sliceBuilder.hashCode(), secondSliceBuilder.hashCode());
        }
    }

    public void testInvalidArguments() throws Exception {
        Exception e = expectThrows(IllegalArgumentException.class, () -> new SliceBuilder("field", -1, 10));
        assertEquals("id must be greater than or equal to 0", e.getMessage());

        e = expectThrows(IllegalArgumentException.class, () -> new SliceBuilder("field", 10, -1));
        assertEquals("max must be greater than 1", e.getMessage());

        e = expectThrows(IllegalArgumentException.class, () -> new SliceBuilder("field", 10, 0));
        assertEquals("max must be greater than 1", e.getMessage());

        e = expectThrows(IllegalArgumentException.class, () -> new SliceBuilder("field", 10, 5));
        assertEquals("max must be greater than id", e.getMessage());

        e = expectThrows(IllegalArgumentException.class, () -> new SliceBuilder("field", 1000, 1000));
        assertEquals("max must be greater than id", e.getMessage());
        e = expectThrows(IllegalArgumentException.class, () -> new SliceBuilder("field", 1001, 1000));
        assertEquals("max must be greater than id", e.getMessage());
    }

    public void testToFilterSimple() throws IOException {
        Directory dir = new ByteBuffersDirectory();
        try (IndexWriter writer = new IndexWriter(dir, newIndexWriterConfig(new MockAnalyzer(random())))) {
            writer.commit();
        }
        try (IndexReader reader = DirectoryReader.open(dir)) {
            QueryShardContext context =
                createShardContext(Version.CURRENT, reader, "_id", DocValuesType.SORTED_NUMERIC, 1,0);
            SliceBuilder builder = new SliceBuilder(5, 10);
            Query query = builder.toFilter(null, createRequest(0), context, Version.CURRENT);
            assertThat(query, instanceOf(TermsSliceQuery.class));

            assertThat(builder.toFilter(null, createRequest(0), context, Version.CURRENT), equalTo(query));
            try (IndexReader newReader = DirectoryReader.open(dir)) {
                when(context.getIndexReader()).thenReturn(newReader);
                assertThat(builder.toFilter(null, createRequest(0), context, Version.CURRENT), equalTo(query));
            }
        }
    }

    public void testToFilterRandom() throws IOException {
        Directory dir = new ByteBuffersDirectory();
        try (IndexWriter writer = new IndexWriter(dir, newIndexWriterConfig(new MockAnalyzer(random())))) {
            writer.commit();
        }
        try (IndexReader reader = DirectoryReader.open(dir)) {
            QueryShardContext context =
                createShardContext(Version.CURRENT, reader, "field", DocValuesType.SORTED_NUMERIC, 1,0);
            SliceBuilder builder = new SliceBuilder("field", 5, 10);
            Query query = builder.toFilter(null, createRequest(0), context, Version.CURRENT);
            assertThat(query, instanceOf(DocValuesSliceQuery.class));
            assertThat(builder.toFilter(null, createRequest(0), context, Version.CURRENT), equalTo(query));
            try (IndexReader newReader = DirectoryReader.open(dir)) {
                when(context.getIndexReader()).thenReturn(newReader);
                assertThat(builder.toFilter(null, createRequest(0), context, Version.CURRENT), equalTo(query));
            }

            // numSlices > numShards
            int numSlices = randomIntBetween(10, 100);
            int numShards = randomIntBetween(1, 9);
            Map<Integer, AtomicInteger> numSliceMap = new HashMap<>();
            for (int i = 0; i < numSlices; i++) {
                for (int j = 0; j < numShards; j++) {
                    SliceBuilder slice = new SliceBuilder("_id", i, numSlices);
                    context = createShardContext(Version.CURRENT, reader, "_id", DocValuesType.SORTED, numShards, j);
                    Query q = slice.toFilter(null, createRequest(j), context, Version.CURRENT);
                    if (q instanceof TermsSliceQuery || q instanceof MatchAllDocsQuery) {
                        AtomicInteger count = numSliceMap.get(j);
                        if (count == null) {
                            count = new AtomicInteger(0);
                            numSliceMap.put(j, count);
                        }
                        count.incrementAndGet();
                        if (q instanceof MatchAllDocsQuery) {
                            assertThat(count.get(), equalTo(1));
                        }
                    } else {
                        assertThat(q, instanceOf(MatchNoDocsQuery.class));
                    }
                }
            }
            int total = 0;
            for (Map.Entry<Integer, AtomicInteger> e : numSliceMap.entrySet()) {
                total += e.getValue().get();
            }
            assertThat(total, equalTo(numSlices));

            // numShards > numSlices
            numShards = randomIntBetween(4, 100);
            numSlices = randomIntBetween(2, numShards - 1);
            List<Integer> targetShards = new ArrayList<>();
            for (int i = 0; i < numSlices; i++) {
                for (int j = 0; j < numShards; j++) {
                    SliceBuilder slice = new SliceBuilder("_id", i, numSlices);
                    context = createShardContext(Version.CURRENT, reader, "_id", DocValuesType.SORTED, numShards, j);
                    Query q = slice.toFilter(null, createRequest(j), context, Version.CURRENT);
                    if (q instanceof MatchNoDocsQuery == false) {
                        assertThat(q, instanceOf(MatchAllDocsQuery.class));
                        targetShards.add(j);
                    }
                }
            }
            assertThat(targetShards.size(), equalTo(numShards));
            assertThat(new HashSet<>(targetShards).size(), equalTo(numShards));

            // numShards == numSlices
            numShards = randomIntBetween(2, 10);
            numSlices = numShards;
            for (int i = 0; i < numSlices; i++) {
                for (int j = 0; j < numShards; j++) {
                    SliceBuilder slice = new SliceBuilder("_id", i, numSlices);
                    context = createShardContext(Version.CURRENT, reader, "_id", DocValuesType.SORTED, numShards, j);
                    Query q = slice.toFilter(null, createRequest(j), context, Version.CURRENT);
                    if (i == j) {
                        assertThat(q, instanceOf(MatchAllDocsQuery.class));
                    } else {
                        assertThat(q, instanceOf(MatchNoDocsQuery.class));
                    }
                }
            }
        }
    }

    public void testInvalidField() throws IOException {
        Directory dir = new ByteBuffersDirectory();
        try (IndexWriter writer = new IndexWriter(dir, newIndexWriterConfig(new MockAnalyzer(random())))) {
            writer.commit();
        }
        try (IndexReader reader = DirectoryReader.open(dir)) {
            QueryShardContext context = createShardContext(Version.CURRENT, reader, "field", null, 1,0);
            SliceBuilder builder = new SliceBuilder("field", 5, 10);
            IllegalArgumentException exc = expectThrows(IllegalArgumentException.class,
                () -> builder.toFilter(null, createRequest(0), context, Version.CURRENT));
            assertThat(exc.getMessage(), containsString("cannot load numeric doc values"));
        }
    }

    public void testToFilterDeprecationMessage() throws IOException {
        Directory dir = new ByteBuffersDirectory();
        try (IndexWriter writer = new IndexWriter(dir, newIndexWriterConfig(new MockAnalyzer(random())))) {
            writer.commit();
        }
        try (IndexReader reader = DirectoryReader.open(dir)) {
            QueryShardContext context = createShardContext(LegacyESVersion.V_6_3_0, reader, "_uid", null, 1,0);
            SliceBuilder builder = new SliceBuilder("_uid", 5, 10);
            Query query = builder.toFilter(null, createRequest(0), context, Version.CURRENT);
            assertThat(query, instanceOf(TermsSliceQuery.class));
            assertThat(builder.toFilter(null, createRequest(0), context, Version.CURRENT), equalTo(query));
            assertWarnings("Computing slices on the [_uid] field is deprecated for 6.x indices, use [_id] instead");
        }
    }

    public void testSerializationBackcompat() throws IOException {
        SliceBuilder sliceBuilder = new SliceBuilder(1, 5);
        assertEquals(IdFieldMapper.NAME, sliceBuilder.getField());

        SliceBuilder copy62 = copyWriteable(sliceBuilder,
                new NamedWriteableRegistry(Collections.emptyList()),
                SliceBuilder::new, LegacyESVersion.V_6_2_0);
        assertEquals(sliceBuilder, copy62);

        SliceBuilder copy63 = copyWriteable(copy62,
                new NamedWriteableRegistry(Collections.emptyList()),
                SliceBuilder::new, LegacyESVersion.V_6_3_0);
        assertEquals(sliceBuilder, copy63);
    }

    public void testToFilterWithRouting() throws IOException {
        Directory dir = new ByteBuffersDirectory();
        try (IndexWriter writer = new IndexWriter(dir, newIndexWriterConfig(new MockAnalyzer(random())))) {
            writer.commit();
        }
        ClusterService clusterService = mock(ClusterService.class);
        ClusterState state = mock(ClusterState.class);
        when(state.metadata()).thenReturn(Metadata.EMPTY_METADATA);
        when(clusterService.state()).thenReturn(state);
        OperationRouting routing = mock(OperationRouting.class);
        GroupShardsIterator<ShardIterator> it = new GroupShardsIterator<>(
            Collections.singletonList(
                new PlainShardIterator(new ShardId("index", "index", 1), Collections.emptyList())
            )
        );
        when(routing.searchShards(any(), any(), any(), any())).thenReturn(it);
        when(clusterService.operationRouting()).thenReturn(routing);
        when(clusterService.getSettings()).thenReturn(Settings.EMPTY);
        try (IndexReader reader = DirectoryReader.open(dir)) {
            QueryShardContext context = createShardContext(Version.CURRENT, reader, "field", DocValuesType.SORTED, 5, 0);
            SliceBuilder builder = new SliceBuilder("field", 6, 10);
            String[] routings = new String[] { "foo" };
            Query query = builder.toFilter(clusterService, createRequest(1, routings, null), context, Version.CURRENT);
            assertEquals(new DocValuesSliceQuery("field", 6, 10), query);
            query = builder.toFilter(clusterService, createRequest(1, Strings.EMPTY_ARRAY, "foo"), context, Version.CURRENT);
            assertEquals(new DocValuesSliceQuery("field", 6, 10), query);
            query = builder.toFilter(clusterService, createRequest(1, Strings.EMPTY_ARRAY, "foo"), context, LegacyESVersion.V_6_2_0);
            assertEquals(new DocValuesSliceQuery("field", 1, 2), query);
        }
    }
}
