/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.plugin.vector.rescorer;

import org.opensearch.core.ParseField;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;
import org.opensearch.core.xcontent.ConstructingObjectParser;
import org.opensearch.core.xcontent.ObjectParser;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.core.xcontent.XContentParser;
import org.opensearch.search.rescore.RescorerBuilder;
import org.opensearch.search.rescore.RescoreContext;
import org.opensearch.search.rescore.Rescorer;
import org.opensearch.index.query.QueryShardContext;
import org.opensearch.index.query.QueryRewriteContext;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static org.opensearch.core.xcontent.ConstructingObjectParser.constructorArg;
import static org.opensearch.core.xcontent.ConstructingObjectParser.optionalConstructorArg;

/**
 * Builder for the MaxSim rescorer which computes the maximum similarity
 * between query token vectors and document token vectors.
 */
public class MaxSimRescorerBuilder extends RescorerBuilder<MaxSimRescorerBuilder> {

    public static final String NAME = "maxsim";

    private static final ParseField QUERY_VECTORS_FIELD = new ParseField("query_vectors");
    private static final ParseField FIELD_FIELD = new ParseField("field");
    private static final ParseField SIMILARITY_FIELD = new ParseField("similarity");

    final List<List<Float>> queryVectors;
    final String field;
    final String similarity;

    @SuppressWarnings("unchecked")
    private static final ConstructingObjectParser<MaxSimRescorerBuilder, Void> PARSER = new ConstructingObjectParser<>(
        NAME,
        a -> new MaxSimRescorerBuilder(
            (List<List<Float>>) a[0],
            (String) a[1],
            a[2] == null ? "dot_product" : (String) a[2]
        )
    );

    static {
        PARSER.declareField(
            constructorArg(),
            (p, c) -> parseQueryVectors(p),
            QUERY_VECTORS_FIELD,
            ObjectParser.ValueType.OBJECT_ARRAY
        );
        PARSER.declareString(constructorArg(), FIELD_FIELD);
        PARSER.declareString(optionalConstructorArg(), SIMILARITY_FIELD);
    }

    /**
     * Creates a new MaxSimRescorerBuilder with the provided parameters
     *
     * @param queryVectors List of query token vectors
     * @param field Field containing document token vectors
     * @param similarity Similarity function to use (default: dot_product)
     */
    public MaxSimRescorerBuilder(List<List<Float>> queryVectors, String field, String similarity) {
        this.queryVectors = Objects.requireNonNull(queryVectors, "query_vectors must not be null");
        this.field = Objects.requireNonNull(field, "field must not be null");
        this.similarity = similarity == null ? "dot_product" : similarity;
    }

    /**
     * Reads MaxSimRescorerBuilder from a stream
     * 
     * @param in StreamInput to read from
     * @throws IOException if reading from stream fails
     */
    public MaxSimRescorerBuilder(StreamInput in) throws IOException {
        super(in);
        int vectorsSize = in.readVInt();
        queryVectors = new ArrayList<>(vectorsSize);
        for (int i = 0; i < vectorsSize; i++) {
            int vectorDimension = in.readVInt();
            List<Float> vector = new ArrayList<>(vectorDimension);
            for (int j = 0; j < vectorDimension; j++) {
                vector.add(in.readFloat());
            }
            queryVectors.add(vector);
        }
        field = in.readString();
        similarity = in.readString();
    }

    /**
     * Parses query vectors from XContentParser
     * 
     * @param parser XContentParser to read from
     * @return List of query vectors
     * @throws IOException if parsing fails
     */
    private static List<List<Float>> parseQueryVectors(XContentParser parser) throws IOException {
        List<List<Float>> vectors = new ArrayList<>();
        if (parser.currentToken() == XContentParser.Token.START_ARRAY) {
            while (parser.nextToken() != XContentParser.Token.END_ARRAY) {
                vectors.add(parseVector(parser));
            }
        } else {
            throw new IllegalArgumentException("Expected an array of vectors but got " + parser.currentToken());
        }
        return vectors;
    }
    
    /**
     * Parses a single vector from XContentParser
     * 
     * @param parser XContentParser to read from
     * @return Vector as List<Float>
     * @throws IOException if parsing fails
     */
    private static List<Float> parseVector(XContentParser parser) throws IOException {
        List<Float> vector = new ArrayList<>();
        if (parser.currentToken() == XContentParser.Token.START_ARRAY) {
            while (parser.nextToken() != XContentParser.Token.END_ARRAY) {
                if (parser.currentToken() == XContentParser.Token.VALUE_NUMBER) {
                    vector.add(parser.floatValue());
                } else {
                    throw new IllegalArgumentException("Expected a number in vector but got " + parser.currentToken());
                }
            }
        } else {
            throw new IllegalArgumentException("Expected an array for vector but got " + parser.currentToken());
        }
        return vector;
    }

    @Override
    public void doWriteTo(StreamOutput out) throws IOException {
        out.writeVInt(queryVectors.size());
        for (List<Float> vector : queryVectors) {
            out.writeVInt(vector.size());
            for (Float value : vector) {
                out.writeFloat(value);
            }
        }
        out.writeString(field);
        out.writeString(similarity);
    }

    @Override
    public String getWriteableName() {
        return NAME;
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        builder.startObject(NAME);
        builder.startArray(QUERY_VECTORS_FIELD.getPreferredName());
        for (List<Float> vector : queryVectors) {
            builder.startArray();
            for (Float value : vector) {
                builder.value(value);
            }
            builder.endArray();
        }
        builder.endArray();
        builder.field(FIELD_FIELD.getPreferredName(), field);
        builder.field(SIMILARITY_FIELD.getPreferredName(), similarity);
        builder.endObject();
        return builder;
    }

    @Override
    protected RescoreContext innerBuildContext(int windowSize, QueryShardContext context) {
        return new MaxSimRescoreContext(windowSize, this, queryVectors, field, similarity);
    }
    
    /**
     * Custom RescoreContext implementation for MaxSimRescorer
     */
    public static class MaxSimRescoreContext extends RescoreContext {
        private final List<List<Float>> queryVectors;
        private final String field;
        private final String similarity;
        
        public MaxSimRescoreContext(int windowSize, RescorerBuilder<?> rescorerBuilder, 
                                 List<List<Float>> queryVectors, String field, String similarity) {
            super(windowSize, (Rescorer) rescorerBuilder);
            this.queryVectors = queryVectors;
            this.field = field;
            this.similarity = similarity;
        }
        
        public List<List<Float>> getQueryVectors() {
            return queryVectors;
        }
        
        public String getField() {
            return field;
        }
        
        public String getSimilarity() {
            return similarity;
        }
        
        public float getQueryWeight() {
            return 1.0f; // Default weight, can be made configurable if needed
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;
        MaxSimRescorerBuilder that = (MaxSimRescorerBuilder) o;
        return Objects.equals(queryVectors, that.queryVectors) &&
               Objects.equals(field, that.field) &&
               Objects.equals(similarity, that.similarity);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), queryVectors, field, similarity);
    }

    @Override
    protected void doXContent(XContentBuilder builder, Params params) throws IOException {
        builder.startArray(QUERY_VECTORS_FIELD.getPreferredName());
        for (List<Float> vector : queryVectors) {
            builder.startArray();
            for (Float value : vector) {
                builder.value(value);
            }
            builder.endArray();
        }
        builder.endArray();
        builder.field(FIELD_FIELD.getPreferredName(), field);
        builder.field(SIMILARITY_FIELD.getPreferredName(), similarity);
    }

    @Override
    public RescorerBuilder<MaxSimRescorerBuilder> rewrite(QueryRewriteContext context) throws IOException {
        return this;
    }

    /**
     * Parser for creating MaxSimRescorerBuilder from XContent
     * 
     * @param parser XContentParser to parse from
     * @return new MaxSimRescorerBuilder instance
     * @throws IOException if parsing fails
     */
    public static MaxSimRescorerBuilder fromXContent(XContentParser parser) throws IOException {
        return PARSER.apply(parser, null);
    }
}
