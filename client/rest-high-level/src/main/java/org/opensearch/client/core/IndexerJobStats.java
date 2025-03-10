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

package org.opensearch.client.core;

import org.opensearch.common.ParseField;

import java.util.Objects;

public abstract class IndexerJobStats {
    public static ParseField NUM_PAGES = new ParseField("pages_processed");
    public static ParseField NUM_INPUT_DOCUMENTS = new ParseField("documents_processed");
    public static ParseField NUM_OUTPUT_DOCUMENTS = new ParseField("documents_indexed");
    public static ParseField NUM_INVOCATIONS = new ParseField("trigger_count");
    public static ParseField INDEX_TIME_IN_MS = new ParseField("index_time_in_ms");
    public static ParseField SEARCH_TIME_IN_MS = new ParseField("search_time_in_ms");
    public static ParseField PROCESSING_TIME_IN_MS = new ParseField("processing_time_in_ms");
    public static ParseField INDEX_TOTAL = new ParseField("index_total");
    public static ParseField SEARCH_TOTAL = new ParseField("search_total");
    public static ParseField PROCESSING_TOTAL = new ParseField("processing_total");
    public static ParseField SEARCH_FAILURES = new ParseField("search_failures");
    public static ParseField INDEX_FAILURES = new ParseField("index_failures");

    protected final long numPages;
    protected final long numInputDocuments;
    protected final long numOuputDocuments;
    protected final long numInvocations;
    protected final long indexTime;
    protected final long indexTotal;
    protected final long searchTime;
    protected final long searchTotal;
    protected final long processingTime;
    protected final long processingTotal;
    protected final long indexFailures;
    protected final long searchFailures;

    public IndexerJobStats(long numPages, long numInputDocuments, long numOutputDocuments, long numInvocations,
                           long indexTime, long searchTime, long processingTime, long indexTotal, long searchTotal, long processingTotal,
                           long indexFailures, long searchFailures) {
        this.numPages = numPages;
        this.numInputDocuments = numInputDocuments;
        this.numOuputDocuments = numOutputDocuments;
        this.numInvocations = numInvocations;
        this.indexTime = indexTime;
        this.indexTotal = indexTotal;
        this.searchTime = searchTime;
        this.searchTotal = searchTotal;
        this.processingTime = processingTime;
        this.processingTotal = processingTotal;
        this.indexFailures = indexFailures;
        this.searchFailures = searchFailures;
    }

    /**
     * The number of pages read from the input indices
     */
    public long getNumPages() {
        return numPages;
    }

    /**
     * The number of documents read from the input indices
     */
    public long getNumDocuments() {
        return numInputDocuments;
    }

    /**
     * Number of times that the job woke up to write documents
     */
    public long getNumInvocations() {
        return numInvocations;
    }

    /**
     * Number of documents written
     */
    public long getOutputDocuments() {
        return numOuputDocuments;
    }

    /**
     * Number of index failures that have occurred
     */
    public long getIndexFailures() {
        return indexFailures;
    }

    /**
     * Number of failures that have occurred
     */
    public long getSearchFailures() {
        return searchFailures;
    }

    /**
     * Returns the time spent indexing (cumulative) in milliseconds
     */
    public long getIndexTime() {
        return indexTime;
    }

    /**
     * Returns the time spent searching (cumulative) in milliseconds
     */
    public long getSearchTime() {
        return searchTime;
    }

    /**
     * Returns the time spent processing (cumulative) in milliseconds
     */
    public long getProcessingTime() {
        return processingTime;
    }

    /**
     * Returns the total number of indexing requests that have been processed
     * (Note: this is not the number of _documents_ that have been indexed)
     */
    public long getIndexTotal() {
        return indexTotal;
    }

    /**
     * Returns the total number of search requests that have been made
     */
    public long getSearchTotal() {
        return searchTotal;
    }

    /**
     * Returns the total number of processing runs that have been made
     */
    public long getProcessingTotal() {
        return processingTotal;
    }


    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }

        if (other instanceof IndexerJobStats == false) {
            return false;
        }

        IndexerJobStats that = (IndexerJobStats) other;
        return Objects.equals(this.numPages, that.numPages)
                && Objects.equals(this.numInputDocuments, that.numInputDocuments)
                && Objects.equals(this.numOuputDocuments, that.numOuputDocuments)
                && Objects.equals(this.numInvocations, that.numInvocations)
                && Objects.equals(this.indexTime, that.indexTime)
                && Objects.equals(this.searchTime, that.searchTime)
                && Objects.equals(this.processingTime, that.processingTime)
                && Objects.equals(this.indexFailures, that.indexFailures)
                && Objects.equals(this.searchFailures, that.searchFailures)
                && Objects.equals(this.searchTotal, that.searchTotal)
                && Objects.equals(this.processingTotal, that.processingTotal)
                && Objects.equals(this.indexTotal, that.indexTotal);
    }

    @Override
    public int hashCode() {
        return Objects.hash(numPages, numInputDocuments, numOuputDocuments, numInvocations,
                indexTime, searchTime, processingTime, indexFailures, searchFailures, searchTotal,
                indexTotal, processingTotal);
    }

    @Override
    public final String toString() {
        return "{pages=" + numPages
                + ", input_docs=" + numInputDocuments
                + ", output_docs=" + numOuputDocuments
                + ", invocations=" + numInvocations
                + ", index_failures=" + indexFailures
                + ", search_failures=" + searchFailures
                + ", index_time_in_ms=" + indexTime
                + ", index_total=" + indexTotal
                + ", search_time_in_ms=" + searchTime
                + ", search_total=" + searchTotal
                + ", processing_time_in_ms=" + processingTime
                + ", processing_total=" + processingTotal + "}";
    }
}
