
package com.fryrank.dal;

import com.fryrank.model.*;
import com.fryrank.util.DynamoDbUtils;
import lombok.AllArgsConstructor;
import lombok.NonNull;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.stream.Collectors;

import static com.fryrank.Constants.ACCOUNT_ID_KEY;
import static com.fryrank.Constants.ISO_DATE_TIME;
import static com.fryrank.util.EnvironmentUtils.getRequiredEnv;

@Repository
@Log4j2
@AllArgsConstructor
public class ReviewDALImpl implements ReviewDAL {

    private static final String TABLE_NAME = getRequiredEnv("REVIEW_TABLE_NAME");
    private static final String USER_METADATA_TABLE_NAME = getRequiredEnv("PUBLIC_USER_METADATA_TABLE_NAME");

    // Primary key attributes
    private static final String RESTAURANT_ID_KEY = "restaurantId";  // Partition key
    private static final String IDENTIFIER_KEY = "identifier";        // Sort key

    // Other attribute names
    private static final String SCORE_KEY = "score";
    private static final String TITLE_KEY = "title";
    private static final String BODY_KEY = "body";
    private static final String USERNAME_KEY = "username";
    private static final String IS_REVIEW_KEY = "isReview";
    private static final String IS_REVIEW_VALUE = "true";  // Constant value for GSI partition key

    // Identifier prefix for reviews
    private static final String REVIEW_IDENTIFIER_PREFIX = "REVIEW:";

    // Aggregate row constants
    private static final String AGGREGATE_IDENTIFIER = "AGGREGATE";
    private static final String AGGREGATE_TIMESTAMP = "AGGREGATE";

    // Aggregate attribute names
    private static final String TOTAL_SCORE_KEY = "totalScore";
    private static final String REVIEW_COUNT_KEY = "reviewCount";
    private static final String AVERAGE_SCORE_KEY = "averageScore";

    // GSI names
    private static final String RESTAURANT_ID_TIME_INDEX = "restaurantId-time-index";
    private static final String ACCOUNT_ID_TIME_INDEX = "accountId-time-index";
    private static final String RECENT_REVIEWS_INDEX = "recent-reviews-index";

    private final DynamoDbClient dynamoDb;

    public ReviewDALImpl() {
        this.dynamoDb = DynamoDbUtils.client();
    }

    @Override
    public GetAllReviewsOutput getAllReviewsByRestaurantId(@NonNull final String restaurantId) {
        log.info("Getting all reviews for restaurantId: {}", restaurantId);

        final QueryRequest request = QueryRequest.builder()
                .tableName(TABLE_NAME)
                .indexName(RESTAURANT_ID_TIME_INDEX)
                .keyConditionExpression("#rid = :restaurantId")
                .expressionAttributeNames(Map.of("#rid", RESTAURANT_ID_KEY))
                .expressionAttributeValues(Map.of(
                        ":restaurantId", AttributeValue.builder().s(restaurantId).build()
                ))
                .scanIndexForward(false)  // Most recent first
                .build();

        final QueryResponse response = dynamoDb.query(request);
        final List<Review> reviews = response.items().stream()
                .map(this::mapItemToReviewWithUserMetadata)
                .collect(Collectors.toList());

        return new GetAllReviewsOutput(reviews);
    }

    @Override
    public GetAllReviewsOutput getAllReviewsByAccountId(@NonNull final String accountId) {
        log.info("Getting all reviews for accountId: {}", accountId);

        final QueryRequest request = QueryRequest.builder()
                .tableName(TABLE_NAME)
                .indexName(ACCOUNT_ID_TIME_INDEX)
                .keyConditionExpression("#aid = :accountId")
                .expressionAttributeNames(Map.of("#aid", ACCOUNT_ID_KEY))
                .expressionAttributeValues(Map.of(
                        ":accountId", AttributeValue.builder().s(accountId).build()
                ))
                .scanIndexForward(false)  // Most recent first
                .build();

        final QueryResponse response = dynamoDb.query(request);
        final List<Review> reviews = response.items().stream()
                .map(this::mapItemToReviewWithUserMetadata)
                .collect(Collectors.toList());

        return new GetAllReviewsOutput(reviews);
    }

    @Override
    public GetAllReviewsOutput getRecentReviews(@NonNull final Integer count) {
        log.info("Getting {} recent reviews", count);

        final QueryRequest request = QueryRequest.builder()
                .tableName(TABLE_NAME)
                .indexName(RECENT_REVIEWS_INDEX)
                .keyConditionExpression("#ir = :isReview")
                .expressionAttributeNames(Map.of("#ir", IS_REVIEW_KEY))
                .expressionAttributeValues(Map.of(
                        ":isReview", AttributeValue.builder().s(IS_REVIEW_VALUE).build()
                ))
                .scanIndexForward(false)  // Descending by isoDateTime (most recent first)
                .limit(count)
                .build();

        final QueryResponse response = dynamoDb.query(request);
        final List<Review> reviews = response.items().stream()
                .map(this::mapItemToReviewWithUserMetadata)
                .collect(Collectors.toList());

        return new GetAllReviewsOutput(reviews);
    }

    @Override
    public GetAggregateReviewInformationOutput getAggregateReviewInformationForRestaurants(
            @NonNull final List<String> restaurantIds,
            @NonNull final AggregateReviewFilter aggregateReviewFilter
    ) {
        log.info("Getting aggregate review information for {} restaurants", restaurantIds.size());

        Map<String, AggregateReviewInformation> restaurantIdToAggregateReviewInformation = new HashMap<>();

        for (String restaurantId : restaurantIds) {
            final QueryRequest request = QueryRequest.builder()
                    .tableName(TABLE_NAME)
                    .keyConditionExpression("#rid = :restaurantId")
                    .filterExpression("attribute_exists(#aid)")
                    .expressionAttributeNames(Map.of(
                            "#rid", RESTAURANT_ID_KEY,
                            "#aid", ACCOUNT_ID_KEY
                    ))
                    .expressionAttributeValues(Map.of(
                            ":restaurantId", AttributeValue.builder().s(restaurantId).build()
                    ))
                    .build();

            final QueryResponse response = dynamoDb.query(request);
            final List<Map<String, AttributeValue>> items = response.items();

            if (!items.isEmpty()) {
                double sum = 0.0;
                int validCount = 0;
                for (Map<String, AttributeValue> item : items) {
                    AttributeValue scoreAttr = item.get(SCORE_KEY);
                    if (scoreAttr != null && scoreAttr.n() != null) {
                        sum += Double.parseDouble(scoreAttr.n());
                        validCount++;
                    }
                }

                if (validCount > 0) {
                    double avgScore = sum / validCount;
                    final Float averageScore = aggregateReviewFilter.getIncludeRating()
                            ? BigDecimal.valueOf(avgScore).setScale(1, RoundingMode.DOWN).floatValue()
                            : null;

                    restaurantIdToAggregateReviewInformation.put(
                            restaurantId,
                            new AggregateReviewInformation(restaurantId, averageScore)
                    );
                }
            }
        }

        return new GetAggregateReviewInformationOutput(restaurantIdToAggregateReviewInformation);
    }

    /**
     * Creates a new review in the database and also performs aggregation logic to add a new or update an existing
     * aggregate row.
     *
     * @param review
     * @return the Review object that was created.
     */
    @Override
    public Review addNewReview(@NonNull final Review review) {
        log.info("Adding new review for restaurantId: {} and accountId: {}",
                review.getRestaurantId(), review.getAccountId());

        final String identifier = REVIEW_IDENTIFIER_PREFIX + review.getAccountId();

        final Map<String, AttributeValue> reviewItem = new HashMap<>();
        reviewItem.put(RESTAURANT_ID_KEY, AttributeValue.builder().s(review.getRestaurantId()).build());
        reviewItem.put(IDENTIFIER_KEY, AttributeValue.builder().s(identifier).build());
        reviewItem.put(SCORE_KEY, AttributeValue.builder().n(review.getScore().toString()).build());
        reviewItem.put(TITLE_KEY, AttributeValue.builder().s(review.getTitle()).build());
        reviewItem.put(BODY_KEY, AttributeValue.builder().s(review.getBody()).build());
        reviewItem.put(IS_REVIEW_KEY, AttributeValue.builder().s(IS_REVIEW_VALUE).build());

        if (review.getIsoDateTime() != null) {
            reviewItem.put(ISO_DATE_TIME, AttributeValue.builder().s(review.getIsoDateTime()).build());
        }
        if (review.getAccountId() != null) {
            reviewItem.put(ACCOUNT_ID_KEY, AttributeValue.builder().s(review.getAccountId()).build());
        }

        final Map<String, AttributeValue> aggregateKey = Map.of(
                RESTAURANT_ID_KEY, AttributeValue.builder().s(review.getRestaurantId()).build(),
                IDENTIFIER_KEY, AttributeValue.builder().s(AGGREGATE_IDENTIFIER).build()
        );

        final GetItemRequest getAggregateRequest = GetItemRequest.builder()
                .tableName(TABLE_NAME)
                .key(aggregateKey)
                .build();

        final GetItemResponse aggregateResponse = dynamoDb.getItem(getAggregateRequest);
        final Map<String, AttributeValue> existingAggregate = aggregateResponse.item();

        // Build the aggregate item
        final Map<String, AttributeValue> aggregateItem = new HashMap<>();
        aggregateItem.put(RESTAURANT_ID_KEY, AttributeValue.builder().s(review.getRestaurantId()).build());
        aggregateItem.put(IDENTIFIER_KEY, AttributeValue.builder().s(AGGREGATE_IDENTIFIER).build());
        aggregateItem.put(ISO_DATE_TIME, AttributeValue.builder().s(AGGREGATE_TIMESTAMP).build());

        double newTotalScore;
        int newReviewCount;
        double newAverageScore;

        if (existingAggregate == null || existingAggregate.isEmpty()) {
            newTotalScore = review.getScore();
            newReviewCount = 1;
            newAverageScore = review.getScore();
            log.info("Creating new aggregate for restaurantId: {}", review.getRestaurantId());
        } else {
            double existingTotalScore = Double.parseDouble(existingAggregate.get(TOTAL_SCORE_KEY).n());
            int existingReviewCount = Integer.parseInt(existingAggregate.get(REVIEW_COUNT_KEY).n());

            newTotalScore = existingTotalScore + review.getScore();
            newReviewCount = existingReviewCount + 1;
            newAverageScore = newTotalScore / newReviewCount;
            log.info("Updating aggregate for restaurantId: {} (reviewCount: {} -> {})",
                    review.getRestaurantId(), existingReviewCount, newReviewCount);
        }

        aggregateItem.put(TOTAL_SCORE_KEY, AttributeValue.builder().n(String.valueOf(newTotalScore)).build());
        aggregateItem.put(REVIEW_COUNT_KEY, AttributeValue.builder().n(String.valueOf(newReviewCount)).build());
        aggregateItem.put(AVERAGE_SCORE_KEY, AttributeValue.builder().n(String.valueOf(newAverageScore)).build());

        // Use TransactWriteItems to write both the review and aggregate atomically
        final TransactWriteItemsRequest transactRequest = TransactWriteItemsRequest.builder()
                .transactItems(
                        TransactWriteItem.builder()
                                .put(Put.builder()
                                        .tableName(TABLE_NAME)
                                        .item(reviewItem)
                                        .build())
                                .build(),
                        TransactWriteItem.builder()
                                .put(Put.builder()
                                        .tableName(TABLE_NAME)
                                        .item(aggregateItem)
                                        .build())
                                .build()
                )
                .build();

        dynamoDb.transactWriteItems(transactRequest);

        // Return the review with the generated reviewId
        final String reviewId = review.getRestaurantId() + ":" + identifier;
        return new Review(
                reviewId,
                review.getRestaurantId(),
                review.getScore(),
                review.getTitle(),
                review.getBody(),
                review.getIsoDateTime(),
                review.getAccountId(),
                review.getUserMetadata()
        );
    }

    @Override
    public boolean deleteUserReview(@NonNull final DeleteReviewRequest delReviewRequest) {
        String reviewId = delReviewRequest.reviewId();

        final Query query = new Query().addCriteria(Criteria.where("_id").is(reviewId));
        Review deletedReview = mongoTemplate.findAndRemove(query, Review.class);
        
        return deletedReview != null;
    }

    /**
     * Maps a DynamoDB item to a Review object and fetches associated user metadata.
     */
    private Review mapItemToReviewWithUserMetadata(Map<String, AttributeValue> item) {
        final String accountId = getStringAttribute(item, ACCOUNT_ID_KEY);
        final String restaurantId = getStringAttribute(item, RESTAURANT_ID_KEY);
        final String identifier = getStringAttribute(item, IDENTIFIER_KEY);
        PublicUserMetadata userMetadata = null;

        if (accountId != null) {
            userMetadata = fetchUserMetadata(accountId);
        }

        // Construct reviewId from composite key
        final String reviewId = restaurantId + ":" + identifier;

        return new Review(
                reviewId,
                restaurantId,
                getDoubleAttribute(item, SCORE_KEY),
                getStringAttribute(item, TITLE_KEY),
                getStringAttribute(item, BODY_KEY),
                getStringAttribute(item, ISO_DATE_TIME),
                accountId,
                userMetadata
        );
    }

    /**
     * Fetches user metadata from the user metadata table.
     */
    private PublicUserMetadata fetchUserMetadata(String accountId) {
        final GetItemRequest request = GetItemRequest.builder()
                .tableName(USER_METADATA_TABLE_NAME)
                .key(Map.of(ACCOUNT_ID_KEY, AttributeValue.builder().s(accountId).build()))
                .build();

        final GetItemResponse response = dynamoDb.getItem(request);
        final Map<String, AttributeValue> userItem = response.item();

        if (userItem == null || userItem.isEmpty()) {
            return null;
        }

        return new PublicUserMetadata(
                accountId,
                getStringAttribute(userItem, USERNAME_KEY)
        );
    }

    private String getStringAttribute(Map<String, AttributeValue> item, String key) {
        AttributeValue attr = item.get(key);
        return (attr != null && attr.s() != null) ? attr.s() : null;
    }

    private Double getDoubleAttribute(Map<String, AttributeValue> item, String key) {
        AttributeValue attr = item.get(key);
        return (attr != null && attr.n() != null) ? Double.parseDouble(attr.n()) : null;
    }
}

