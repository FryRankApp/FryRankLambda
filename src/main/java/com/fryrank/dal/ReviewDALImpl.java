package com.fryrank.dal;

import com.fryrank.model.*;
import lombok.AllArgsConstructor;
import lombok.NonNull;
import lombok.extern.log4j.Log4j2;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationOperation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;
import org.springframework.data.mongodb.core.aggregation.LookupOperation;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.stereotype.Repository;
import com.mongodb.MongoClientSettings;
import com.mongodb.ConnectionString;
import com.mongodb.MongoException;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoClient;
import org.bson.Document;
import java.util.concurrent.TimeUnit;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static com.fryrank.Constants.ACCOUNT_ID_KEY;
import static com.fryrank.Constants.PRIMARY_KEY;
import static com.fryrank.Constants.REVIEW_COLLECTION_NAME;
import static com.fryrank.Constants.PUBLIC_USER_METADATA_COLLECTION_NAME;
import static com.fryrank.Constants.USER_METADATA_OUTPUT_FIELD_NAME;
import static com.fryrank.Constants.DATABASE_URI_ENV_VAR;
import static org.springframework.data.mongodb.core.aggregation.Aggregation.*;

@Repository
@Log4j2
@AllArgsConstructor
public class ReviewDALImpl implements ReviewDAL {

    final String RESTAURANT_ID_KEY = "restaurantId";

    private final MongoTemplate mongoTemplate;

    private final List<AggregationOperation> AGGREGATION_OPERATIONS_FOR_PUBLIC_USER_METADATA_COLLECTION_JOIN =
            new ArrayList<>(Arrays.asList(
                    LookupOperation.newLookup()
                            .from(PUBLIC_USER_METADATA_COLLECTION_NAME)
                            .localField(ACCOUNT_ID_KEY)
                            .foreignField(PRIMARY_KEY)
                            .as(USER_METADATA_OUTPUT_FIELD_NAME),
                    Aggregation.unwind("userMetadata")
            ));

    public ReviewDALImpl() {
        log.info("Initializing ReviewDALImpl");
        String databaseUri = System.getenv(DATABASE_URI_ENV_VAR);
        
        if (databaseUri == null || databaseUri.isEmpty()) {
            throw new IllegalStateException("DATABASE_URI_ENV_VAR is not set");
        }

        ConnectionString connectionString = new ConnectionString(databaseUri);

        // Configure MongoDB client with explicit settings
        MongoClientSettings settings = MongoClientSettings.builder()
            .applyConnectionString(connectionString)
            .applyToSocketSettings(builder -> 
                builder.connectTimeout(10, TimeUnit.SECONDS)
                       .readTimeout(10, TimeUnit.SECONDS))
            .build();

        log.info("Creating MongoDB client with custom settings");
        MongoClient mongoClient = MongoClients.create(settings);
        
        log.info("Creating MongoTemplate");
        this.mongoTemplate = new MongoTemplate(mongoClient, connectionString.getDatabase());
        
        // Test the connection
        try {
            log.info("Testing MongoDB connection with ping command");
            Document pingResult = this.mongoTemplate.getDb().runCommand(new Document("ping", 1));
            log.info("Successfully connected to MongoDB. Ping result: {}", pingResult);
        } catch (MongoException e) {
            log.error("Failed to connect to MongoDB: {}", e.getMessage(), e);
            if (e.getMessage().contains("Authentication failed")) {
                throw new IllegalStateException("MongoDB authentication failed. Please check username and password.", e);
            } else if (e.getMessage().contains("Server selection timed out")) {
                throw new IllegalStateException("MongoDB server selection timed out. Please check network connectivity and MongoDB Atlas status.", e);
            } else {
                throw new IllegalStateException("Failed to connect to MongoDB: " + e.getMessage(), e);
            }
        }
    }

    @Override
    public GetAllReviewsOutput getAllReviewsByRestaurantId(@NonNull final String restaurantId) {
        log.info("Getting all reviews for restaurantId: {}", restaurantId);

        List<AggregationOperation> aggregationOperations = new ArrayList<>(AGGREGATION_OPERATIONS_FOR_PUBLIC_USER_METADATA_COLLECTION_JOIN);
        final Criteria equalToRestaurantIdCriteria = Criteria.where(RESTAURANT_ID_KEY).is(restaurantId);
        aggregationOperations.add(match(equalToRestaurantIdCriteria));

        final Aggregation aggregation = newAggregation(aggregationOperations);
        final AggregationResults<Review> result = mongoTemplate.aggregate(aggregation, REVIEW_COLLECTION_NAME, Review.class);

        return new GetAllReviewsOutput(result.getMappedResults());
    }

    @Override
    public GetAllReviewsOutput getAllReviewsByAccountId(@NonNull final String accountId) {
        log.info("Getting all reviews for accountId: {}", accountId);
        List<AggregationOperation> aggregationOperations = new ArrayList<>(AGGREGATION_OPERATIONS_FOR_PUBLIC_USER_METADATA_COLLECTION_JOIN);
        final Criteria equalToAccountIdCriteria = Criteria.where(ACCOUNT_ID_KEY).is(accountId);
        aggregationOperations.add(match(equalToAccountIdCriteria));

        final Aggregation aggregation = newAggregation(aggregationOperations);
        final AggregationResults<Review> result = mongoTemplate.aggregate(aggregation, REVIEW_COLLECTION_NAME, Review.class);

        return new GetAllReviewsOutput(result.getMappedResults());
    }
}
