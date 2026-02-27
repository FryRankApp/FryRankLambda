package com.fryrank.dal;

import com.fryrank.model.*;
import com.fryrank.util.DynamoDbUtils;
import lombok.AllArgsConstructor;
import lombok.NonNull;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.BatchGetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.BatchGetItemResponse;
import software.amazon.awssdk.services.dynamodb.model.DeleteItemRequest;
import software.amazon.awssdk.services.dynamodb.model.DeleteItemResponse;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.GetItemResponse;
import software.amazon.awssdk.services.dynamodb.model.KeysAndAttributes;
import software.amazon.awssdk.services.dynamodb.model.Put;
import software.amazon.awssdk.services.dynamodb.model.QueryRequest;
import software.amazon.awssdk.services.dynamodb.model.QueryResponse;
import software.amazon.awssdk.services.dynamodb.model.ReturnValue;
import software.amazon.awssdk.services.dynamodb.model.TransactWriteItem;
import software.amazon.awssdk.services.dynamodb.model.TransactWriteItemsRequest;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import static com.fryrank.Constants.ACCOUNT_ID_KEY;
import static com.fryrank.Constants.ACCOUNT_ID_TIME_INDEX;
import static com.fryrank.Constants.AVERAGE_SCORE_KEY;
import static com.fryrank.Constants.BODY_KEY;
import static com.fryrank.Constants.IDENTIFIER_KEY;
import static com.fryrank.Constants.ISO_DATE_TIME;
import static com.fryrank.Constants.IS_REVIEW_KEY;
import static com.fryrank.Constants.IS_REVIEW_VALUE;
import static com.fryrank.Constants.RECENT_REVIEWS_INDEX;
import static com.fryrank.Constants.RESTAURANT_ID_KEY;
import static com.fryrank.Constants.RESTAURANT_ID_TIME_INDEX;
import static com.fryrank.Constants.SCORE_KEY;
import static com.fryrank.Constants.TITLE_KEY;
import static com.fryrank.Constants.USERNAME_KEY;
import static com.fryrank.util.EnvironmentUtils.getRequiredEnv;

@Repository
@Log4j2
@AllArgsConstructor
public class ReviewDALImpl implements ReviewDAL {
    /**
     * The Rankings table combines Rankings with Aggregate data about rankings for a restaurant. This is all included
     * in the same table to simplify queries and streamline amount of DB calls.
     *
     * **Rankings Table**
     * restaurantId (PK) | identifier (SK) | timestamp | isReview | accountId | username | totalScore | reviewCount | averageScore | etc attributes
     * -- | -- | -- | -- | -- | -- | -- | -- | -- | --
     * res1 | REVIEW:acc1 | 2024-07-15 | true | acc1 | user1 | | | |
     * res3 | REVIEW:acc1 | 2024-08-10 | true | acc1 | user1 | | | |
     * res2 | REVIEW:acc2 | 2024-07-18 | true | acc2 | user2 | | | |
     * res3 | REVIEW:acc3 | 2024-07-03 | true | acc3 | user3 | | | |
     * res1 | AGGREGATE | AGGREGATE | | | | 100 | 10 | 10
     * res2 | AGGREGATE | AGGREGATE | | | | 6 | 10 | 0.6
     *
     */

    private static final String TABLE_NAME = getRequiredEnv("REVIEW_TABLE_NAME");
    private static final String USER_METADATA_TABLE_NAME = getRequiredEnv("PUBLIC_USER_METADATA_TABLE_NAME");

    // Ranking identifiers
    public static final String REVIEW_IDENTIFIER_PREFIX = "REVIEW:";
    public static final String AGGREGATE_IDENTIFIER = "AGGREGATE";

    private final DynamoDbClient dynamoDb;

    public ReviewDALImpl() {
        this.dynamoDb = DynamoDbUtils.client();
    }

    @Override
    public GetAllReviewsOutput getAllReviewsByRestaurantId(@NonNull final String restaurantId) {
        log.info("Getting all reviews for restaurantId: {}", restaurantId);
        return queryReviews(RESTAURANT_ID_TIME_INDEX, RESTAURANT_ID_KEY, restaurantId);
    }

    @Override
    public GetAllReviewsOutput getAllReviewsByAccountId(@NonNull final String accountId) {
        log.info("Getting all reviews for accountId: {}", accountId);
        return queryReviews(ACCOUNT_ID_TIME_INDEX, ACCOUNT_ID_KEY, accountId);
    }

    private GetAllReviewsOutput queryReviews(String indexName, String keyAttribute, String keyValue) {
        final QueryRequest request = QueryRequest.builder()
                .tableName(TABLE_NAME)
                .indexName(indexName)
                .keyConditionExpression("#key = :value")
                // TODO(FRY-114): Temporary filter expression because we have not yet converted over outputs to use the
                //  new Ranking model objects. Once we convert outputs to use Ranking objects, we can remove this
                .filterExpression("attribute_exists(isReview)")
                .expressionAttributeNames(Map.of("#key", keyAttribute))
                .expressionAttributeValues(Map.of(
                        ":value", AttributeValue.builder().s(keyValue).build()
                ))
                .scanIndexForward(false)  // Most recent first
                .build();

        final QueryResponse response = dynamoDb.query(request);
        return mapItemsToReviewsWithUserMetadata(response.items());
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
        return mapItemsToReviewsWithUserMetadata(response.items());
    }

    @Override
    public GetAggregateReviewInformationOutput getAggregateReviewInformationForRestaurants(
            @NonNull final List<String> restaurantIds,
            @NonNull final AggregateReviewFilter aggregateReviewFilter
    ) {
        log.info("Getting aggregate review information for {} restaurants", restaurantIds.size());

        Map<String, AggregateReviewInformation> restaurantIdToAggregateReviewInformation = new HashMap<>();

        // Build keys for batch get - each key is (restaurantId, "AGGREGATE")
        List<Map<String, AttributeValue>> keys = restaurantIds.stream()
                .map(restaurantId -> Map.of(
                        RESTAURANT_ID_KEY, AttributeValue.builder().s(restaurantId).build(),
                        IDENTIFIER_KEY, AttributeValue.builder().s(AGGREGATE_IDENTIFIER).build()
                ))
                .collect(Collectors.toList());

        // BatchGetItem has a limit of 100 items per request. The likely use case for this is only for 1-10 restaurants,
        // but batching causes 1 call per 100 restaurants instead of N calls per N restaurants from using GetItem in a loop.
        int batchSize = 100;
        for (int i = 0; i < keys.size(); i += batchSize) {
            List<Map<String, AttributeValue>> batchKeys = keys.subList(i, Math.min(i + batchSize, keys.size()));

            KeysAndAttributes keysAndAttributes = KeysAndAttributes.builder()
                    .keys(batchKeys)
                    .build();

            BatchGetItemRequest batchRequest = BatchGetItemRequest.builder()
                    .requestItems(Map.of(TABLE_NAME, keysAndAttributes))
                    .build();

            BatchGetItemResponse batchResponse = dynamoDb.batchGetItem(batchRequest);
            List<Map<String, AttributeValue>> items = batchResponse.responses().get(TABLE_NAME);

            if (items != null) {
                for (Map<String, AttributeValue> item : items) {
                    String restaurantId = item.get(RESTAURANT_ID_KEY).s();

                    final Float averageScore;
                    if (aggregateReviewFilter.getIncludeRating()) {
                        double avgScore = Double.parseDouble(item.get(AVERAGE_SCORE_KEY).n());
                        averageScore = BigDecimal.valueOf(avgScore)
                                .setScale(1, RoundingMode.DOWN)
                                .floatValue();
                    } else {
                        averageScore = null;
                    }

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
        final String restaurantId = review.getRestaurantId();

        log.info("Adding new review for restaurantId: {} and accountId: {}",
                restaurantId, review.getAccountId());

        final String identifier = REVIEW_IDENTIFIER_PREFIX + review.getAccountId();

        final Map<String, AttributeValue> reviewItem = new HashMap<>();
        reviewItem.put(RESTAURANT_ID_KEY, AttributeValue.builder().s(restaurantId).build());
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
                RESTAURANT_ID_KEY, AttributeValue.builder().s(restaurantId).build(),
                IDENTIFIER_KEY, AttributeValue.builder().s(AGGREGATE_IDENTIFIER).build()
        );

        final GetItemRequest getAggregateRequest = GetItemRequest.builder()
                .tableName(TABLE_NAME)
                .key(aggregateKey)
                .build();

        final GetItemResponse aggregateResponse = dynamoDb.getItem(getAggregateRequest);
        final Map<String, AttributeValue> existingAggregate = aggregateResponse.item();

        final AggregateRanking aggregateRanking;

        if (existingAggregate == null || existingAggregate.isEmpty()) {
            aggregateRanking = AggregateRanking.forFirstReview(restaurantId, review.getScore());
            log.info("Creating new aggregate for restaurantId: {}", review.getRestaurantId());
        } else {
            AggregateRanking existingAggregateRanking = AggregateRanking.fromMap(existingAggregate);
            aggregateRanking = existingAggregateRanking.withNewReview(review.getScore());
            log.info("Updating aggregate for restaurantId: {} (reviewCount: {} -> {})",
                    review.getRestaurantId(), existingAggregateRanking.getReviewCount(), aggregateRanking.getReviewCount());
        }

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
                                        .item(aggregateRanking.toMap())
                                        .build())
                                .build()
                )
                .build();

        dynamoDb.transactWriteItems(transactRequest);

        // Return the review with the generated reviewId
        // TODO(FRY-114): Once we standardize the Review model, we will no longer need to generate a reviewId which
        // does not exist in dyanmoDB
        final String reviewId = review.getRestaurantId() + ":" + identifier;
        return Review.builder()
                .reviewId(reviewId)
                .restaurantId(review.getRestaurantId())
                .score(review.getScore())
                .title(review.getTitle())
                .body(review.getBody())
                .isoDateTime(review.getIsoDateTime())
                .accountId(review.getAccountId())
                .userMetadata(review.getUserMetadata())
                .build();
    }

    // TODO(FRY-114): Once we standardize the Review model, we can refactor this API to require a restaurantId and an
    // accountId instead.
    @Override
    public boolean deleteUserReview(@NonNull final DeleteReviewRequest delReviewRequest) {
        String reviewId = delReviewRequest.reviewId();

        String[] keyParts = reviewId.split(":");
        String restaurantId = keyParts[0];
        String identifier = "REVIEW:" + keyParts[1];

        DeleteItemRequest deleteRequest = DeleteItemRequest.builder()
                .tableName(System.getenv("RANKINGS_TABLE_NAME"))
                .key(Map.of(
                        "restaurantId", AttributeValue.builder().s(restaurantId).build(),
                        "identifier", AttributeValue.builder().s(identifier).build()
                ))
                .returnValues(ReturnValue.ALL_OLD)
                .build();

        DeleteItemResponse response = dynamoDb.deleteItem(deleteRequest);

        return response.attributes() != null && !response.attributes().isEmpty();
    }

    /**
     * Maps DynamoDB items to Review objects with batched user metadata fetching.
     */
    private GetAllReviewsOutput mapItemsToReviewsWithUserMetadata(List<Map<String, AttributeValue>> items) {
        final List<String> accountIds = items.stream()
                .map(item -> getStringAttribute(item, ACCOUNT_ID_KEY))
                .filter(accountId -> accountId != null)
                .distinct()
                .collect(Collectors.toList());

        final Map<String, PublicUserMetadata> userMetadataMap = batchFetchUserMetadata(accountIds);

        final List<Review> reviews = items.stream()
                .map(item -> mapItemToReview(item, userMetadataMap))
                .collect(Collectors.toList());

        return new GetAllReviewsOutput(reviews);
    }

    /**
     * Maps a DynamoDB item to a Review object using pre-fetched user metadata.
     */
    private Review mapItemToReview(Map<String, AttributeValue> item, Map<String, PublicUserMetadata> userMetadataMap) {
        final String accountId = getStringAttribute(item, ACCOUNT_ID_KEY);
        final String restaurantId = getStringAttribute(item, RESTAURANT_ID_KEY);
        final String identifier = getStringAttribute(item, IDENTIFIER_KEY);

        final PublicUserMetadata userMetadata = accountId != null ? userMetadataMap.get(accountId) : null;

        // TODO(FRY-114): Once we standardize the Review model, we will no longer need to generate a reviewId which
        // does not exist in dyanmoDB
        final String reviewId = restaurantId + ":" + identifier;

        assert restaurantId != null;
        return Review.builder()
                .reviewId(reviewId)
                .restaurantId(restaurantId)
                .score(Objects.requireNonNull(getDoubleAttribute(item, SCORE_KEY)))
                .title(Objects.requireNonNull(getStringAttribute(item, TITLE_KEY)))
                .body(Objects.requireNonNull(getStringAttribute(item, BODY_KEY)))
                // TODO(FRY-108): Once we update isoDateTime to non-optional we can add a requirement here for non null.
                .isoDateTime(getStringAttribute(item, ISO_DATE_TIME))
                .accountId(accountId)
                .userMetadata(userMetadata)
                .build();
    }

    /**
     * Batch fetches user metadata for multiple account IDs.
     */
    private Map<String, PublicUserMetadata> batchFetchUserMetadata(List<String> accountIds) {
        if (accountIds.isEmpty()) {
            return Map.of();
        }

        final Map<String, PublicUserMetadata> result = new HashMap<>();

        // BatchGetItem has a limit of 100 items per request
        final int batchSize = 100;
        for (int i = 0; i < accountIds.size(); i += batchSize) {
            final List<String> batchAccountIds = accountIds.subList(i, Math.min(i + batchSize, accountIds.size()));

            final List<Map<String, AttributeValue>> keys = batchAccountIds.stream()
                    .map(accountId -> Map.of(ACCOUNT_ID_KEY, AttributeValue.builder().s(accountId).build()))
                    .collect(Collectors.toList());

            final BatchGetItemRequest batchRequest = BatchGetItemRequest.builder()
                    .requestItems(Map.of(USER_METADATA_TABLE_NAME, KeysAndAttributes.builder().keys(keys).build()))
                    .build();

            final BatchGetItemResponse batchResponse = dynamoDb.batchGetItem(batchRequest);
            final List<Map<String, AttributeValue>> items = batchResponse.responses().get(USER_METADATA_TABLE_NAME);

            if (items != null) {
                for (Map<String, AttributeValue> item : items) {
                    final String accountId = getStringAttribute(item, ACCOUNT_ID_KEY);
                    result.put(accountId, new PublicUserMetadata(
                            accountId,
                            getStringAttribute(item, USERNAME_KEY)
                    ));
                }
            }
        }

        return result;
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

