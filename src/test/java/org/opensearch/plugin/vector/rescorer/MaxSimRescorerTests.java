/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.plugin.vector.rescorer;

import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.opensearch.common.io.stream.BytesStreamOutput;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.common.xcontent.XContentFactory;
import org.opensearch.core.xcontent.XContentParser;
import org.opensearch.common.xcontent.XContentType;
import org.opensearch.search.rescore.RescoreContext;
import org.opensearch.test.OpenSearchTestCase;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Unit tests for MaxSimRescorer functionality
 */
public class MaxSimRescorerTests extends OpenSearchTestCase {

    private List<List<Float>> createQueryVectors() {
        List<List<Float>> vectors = new ArrayList<>();
        vectors.add(List.of(0.1f, 0.2f, 0.3f));
        vectors.add(List.of(0.4f, 0.5f, 0.6f));
        return vectors;
    }

    public void testMaxSimRescorerBuilder() {
        List<List<Float>> queryVectors = createQueryVectors();
        String field = "token_vectors";
        String similarity = "dot_product";
        
        MaxSimRescorerBuilder builder = new MaxSimRescorerBuilder(queryVectors, field, similarity);
        
        assertEquals("maxsim", builder.getWriteableName());
        assertEquals(queryVectors, builder.queryVectors);
        assertEquals(field, builder.field);
        assertEquals(similarity, builder.similarity);
    }

    public void testSerialization() throws IOException {
        List<List<Float>> queryVectors = createQueryVectors();
        String field = "token_vectors";
        String similarity = "dot_product";
        
        MaxSimRescorerBuilder original = new MaxSimRescorerBuilder(queryVectors, field, similarity);
        
        // Serialize
        BytesStreamOutput output = new BytesStreamOutput();
        original.writeTo(output);
        
        // Deserialize
        StreamInput input = output.bytes().streamInput();
        MaxSimRescorerBuilder deserialized = new MaxSimRescorerBuilder(input);
        
        // Check equality
        assertEquals(original, deserialized);
    }

    public void testToXContent() throws IOException {
        List<List<Float>> queryVectors = createQueryVectors();
        String field = "token_vectors";
        String similarity = "dot_product";
        
        MaxSimRescorerBuilder builder = new MaxSimRescorerBuilder(queryVectors, field, similarity);
        XContentBuilder xContentBuilder = XContentFactory.jsonBuilder();
        builder.toXContent(xContentBuilder, null);
        
        // Parse back
        XContentParser parser = createParser(xContentBuilder);
        parser.nextToken(); // Move to START_OBJECT
        parser.nextToken(); // Move to FIELD_NAME
        
        MaxSimRescorerBuilder parsed = MaxSimRescorerBuilder.fromXContent(parser);
        
        // Check equality
        assertEquals(builder, parsed);
    }

    public void testRescoring() throws IOException {
        // Create a simple in-memory index
        Directory directory = new ByteBuffersDirectory();
        IndexWriterConfig config = new IndexWriterConfig();
        try (IndexWriter writer = new IndexWriter(directory, config)) {
            // Add some test documents
            for (int i = 0; i < 5; i++) {
                Document document = new Document();
                document.add(new TextField("content", "test document " + i, Field.Store.YES));
                writer.addDocument(document);
            }
        }
        
        // Search and rescore
        IndexReader reader = DirectoryReader.open(directory);
        try {
            IndexSearcher searcher = new IndexSearcher(reader);
            
            // Get all documents
            TopDocs topDocs = searcher.search(new MatchAllDocsQuery(), 5);
            assertEquals(5, topDocs.scoreDocs.length);
            
            // Create rescorer
            List<List<Float>> queryVectors = createQueryVectors();
            
            // Create a rescore context with the MaxSimRescoreContext
            MaxSimRescorerBuilder builder = new MaxSimRescorerBuilder(queryVectors, "token_vectors", "dot_product");
            MaxSimRescorerBuilder.MaxSimRescoreContext context = 
                (MaxSimRescorerBuilder.MaxSimRescoreContext) builder.innerBuildContext(3, null);
            TopDocs rescored = MaxSimRescorer.INSTANCE.rescore(topDocs, searcher, context);
            
            // Verify results still have 5 documents
            assertEquals(5, rescored.scoreDocs.length);
            
            // Verify the first 3 have been rescored (scores will be different from original)
            for (int i = 0; i < 3; i++) {
                ScoreDoc original = topDocs.scoreDocs[i];
                ScoreDoc rescored_ = rescored.scoreDocs[i];
                assertEquals(original.doc, rescored_.doc);
                // Scores should be different after rescoring
                assertNotEquals(original.score, rescored_.score, 0.0001f);
            }
            
            // Verify the last 2 have not been rescored (scores should be the same)
            for (int i = 3; i < 5; i++) {
                ScoreDoc original = topDocs.scoreDocs[i];
                ScoreDoc rescored_ = rescored.scoreDocs[i];
                assertEquals(original.doc, rescored_.doc);
                assertEquals(original.score, rescored_.score, 0.0001f);
            }
        } finally {
            reader.close();
            directory.close();
        }
    }
}
