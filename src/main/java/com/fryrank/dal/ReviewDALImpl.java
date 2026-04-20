package com.fryrank.dal;

import com.fryrank.model.AggregateRanking;
import com.fryrank.model.AggregateReviewFilter;
import com.fryrank.model.AggregateReviewInformation;
import com.fryrank.model.DeleteReviewRequest;
import com.fryrank.model.GetAggregateReviewInformationOutput;
import com.fryrank.model.GetAllReviewsOutput;
import com.fryrank.model.PublicUserMetadata;
import com.fryrank.model.MyReactions;
import com.fryrank.model.ReactionCounts;
import com.fryrank.model.Review;
import com.fryrank.model.ToggleReactionResult;
import com.fryrank.model.enums.ReactionAction;
import com.fryrank.model.enums.ReactionType;
import lombok.NonNull;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.BatchGetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.BatchGetItemResponse;
import software.amazon.awssdk.services.dynamodb.model.Delete;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.GetItemResponse;
import software.amazon.awssdk.services.dynamodb.model.KeysAndAttributes;
import software.amazon.awssdk.services.dynamodb.model.Put;
import software.amazon.awssdk.services.dynamodb.model.DeleteItemRequest;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;
import software.amazon.awssdk.services.dynamodb.model.QueryRequest;
import software.amazon.awssdk.services.dynamodb.model.QueryResponse;
import software.amazon.awssdk.services.dynamodb.model.TransactWriteItem;
import software.amazon.awssdk.services.dynamodb.model.UpdateItemRequest;
import software.amazon.awssdk.services.dynamodb.model.TransactWriteItemsRequest;
import software.amazon.awssdk.services.dynamodb.model.TransactionCanceledException;
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import static com.fryrank.Constants.ACCOUNT_ID_KEY;
import static com.fryrank.Constants.ACCOUNT_ID_TIME_INDEX;
import static com.fryrank.Constants.AGGREGATE_IDENTIFIER;
import static com.fryrank.Constants.AVERAGE_SCORE_KEY;
import static com.fryrank.Constants.BODY_KEY;
import static com.fryrank.Constants.HEART_KEY;
import static com.fryrank.Constants.IDENTIFIER_KEY;
import static com.fryrank.Constants.ISO_DATE_TIME;
import static com.fryrank.Constants.IS_REVIEW_KEY;
import static com.fryrank.Constants.IS_REVIEW_VALUE;
import static com.fryrank.Constants.RANKINGS_TABLE_NAME;
import static com.fryrank.Constants.REACTIONS_TABLE_NAME;
import static com.fryrank.Constants.REACTION_COUNTS_KEY;
import static com.fryrank.Constants.RECENT_REVIEWS_INDEX;
import static com.fryrank.Constants.RESTAURANT_ID_KEY;
import static com.fryrank.Constants.RESTAURANT_ID_TIME_INDEX;
import static com.fryrank.Constants.REVIEW_COUNT_KEY;
import static com.fryrank.Constants.REVIEW_IDENTIFIER_PREFIX;
import static com.fryrank.Constants.REVIEW_ID_KEY;
import static com.fryrank.Constants.SCORE_KEY;
import static com.fryrank.Constants.THUMBS_DOWN_KEY;
import static com.fryrank.Constants.THUMBS_UP_KEY;
import static com.fryrank.Constants.TITLE_KEY;
import static com.fryrank.Constants.VIEWER_ACCOUNT_ID_KEY;
import static com.fryrank.Constants.USERNAME_KEY;
import static com.fryrank.Constants.USER_METADATA_TABLE_NAME;

@Repository
@Log4j2
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

    private static final int MAX_AGGREGATE_UPDATE_RETRIES = 3;

    private static final int MAX_TOGGLE_REACTION_RETRIES = 5;

    private final DynamoDbClient dynamoDb;

    public ReviewDALImpl(final DynamoDbClient dynamoDb) {
        this.dynamoDb = dynamoDb;
    }

    @Override
    public GetAllReviewsOutput getAllReviewsByRestaurantId(@NonNull final String restaurantId, final Integer limit, final String cursor) {
        log.info("Getting reviews for restaurantId: {} with limit: {} and cursor: {}", restaurantId, limit, cursor);
        return queryReviews(RESTAURANT_ID_TIME_INDEX, RESTAURANT_ID_KEY, restaurantId, limit, cursor);
    }

    @Override
    public GetAllReviewsOutput getAllReviewsByAccountId(@NonNull final String accountId, final Integer limit, final String cursor) {
        log.info("Getting reviews for accountId: {} with limit: {} and cursor: {}", accountId, limit, cursor);
        return queryReviews(ACCOUNT_ID_TIME_INDEX, ACCOUNT_ID_KEY, accountId, limit, cursor);
    }

    private GetAllReviewsOutput queryReviews(String indexName, String keyAttribute, String keyValue, Integer limit, String cursor) {
        final Map<String, String> exprAttrNames = new HashMap<>();
        exprAttrNames.put("#key", keyAttribute);

        final Map<String, AttributeValue> exprAttrValues = new HashMap<>();
        exprAttrValues.put(":value", AttributeValue.builder().s(keyValue).build());

        final String keyCondition;
        if (cursor != null && !cursor.isEmpty()) {
            exprAttrNames.put("#dt", ISO_DATE_TIME);
            exprAttrValues.put(":cursor", AttributeValue.builder().s(cursor).build());
            keyCondition = "#key = :value AND #dt < :cursor";
        } else {
            keyCondition = "#key = :value";
        }

        final QueryRequest.Builder requestBuilder = QueryRequest.builder()
                .tableName(RANKINGS_TABLE_NAME)
                .indexName(indexName)
                .keyConditionExpression(keyCondition)
                // TODO(FRY-114): Temporary filter expression because we have not yet converted over outputs to use the
                //  new Ranking model objects. Once we convert outputs to use Ranking objects, we can remove this
                .filterExpression("attribute_exists(isReview)")
                .expressionAttributeNames(exprAttrNames)
                .expressionAttributeValues(exprAttrValues)
                .scanIndexForward(false);  // Most recent first

        if (limit != null) {
            requestBuilder.limit(limit);
        }

        final QueryResponse response = dynamoDb.query(requestBuilder.build());

        final List<Map<String, AttributeValue>> items = response.items();
        final Map<String, AttributeValue> lek = response.lastEvaluatedKey();
        String nextCursor = null;
        if (lek != null && !lek.isEmpty() && !items.isEmpty()) {
            final AttributeValue lastDateTime = items.getLast().get(ISO_DATE_TIME);
            if (lastDateTime != null) {
                nextCursor = URLEncoder.encode(lastDateTime.s(), StandardCharsets.UTF_8);
            }
        }

        return mapItemsToReviewsWithUserMetadata(items, nextCursor);
    }

    @Override
    public GetAllReviewsOutput mergeViewerReactions(final String restaurantId, final String accountId, final String viewerAccountId) {

        final GetAllReviewsOutput reviewsOutput;
        if (restaurantId != null) {
            reviewsOutput = getAllReviewsByRestaurantId(restaurantId, null, null);
        } else if (accountId != null) {
            reviewsOutput = getAllReviewsByAccountId(accountId, null, null);
        } else {
            throw new NullPointerException("At least one of restaurantId and accountId must not be null.");
        }
        if (reviewsOutput == null || viewerAccountId == null || viewerAccountId.isBlank()) {
            return reviewsOutput;
        }

        final List<Review> reviews = reviewsOutput.getReviews();
        if (reviews.isEmpty()) {
            return reviewsOutput;
        }
        final Map<String, MyReactions> byReviewId = batchGetMyReactionsForViewer(viewerAccountId, reviews);
        final List<Review> merged = reviews.parallelStream()
                .map(r -> withMyReactions(r, byReviewId.getOrDefault(r.getReviewId(), MyReactions.none())))
                .collect(Collectors.toList());
        return new GetAllReviewsOutput(merged);
    }

    @Override
    public GetAllReviewsOutput getRecentReviews(@NonNull final Integer count) {
        log.info("Getting {} recent reviews", count);

        final QueryRequest request = QueryRequest.builder()
                .tableName(RANKINGS_TABLE_NAME)
                .indexName(RECENT_REVIEWS_INDEX)
                // #ir is an expression attribute placeholder for the isReview attribute.
                .keyConditionExpression("#ir = :isReview")
                .expressionAttributeNames(Map.of("#ir", IS_REVIEW_KEY))
                .expressionAttributeValues(Map.of(
                        ":isReview", AttributeValue.builder().s(IS_REVIEW_VALUE).build()
                ))
                .scanIndexForward(false)  // Descending by isoDateTime (most recent first)
                .limit(count)
                .build();

        final QueryResponse response = dynamoDb.query(request);
        return mapItemsToReviewsWithUserMetadata(response.items(), null);
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
                    .requestItems(Map.of(RANKINGS_TABLE_NAME, keysAndAttributes))
                    .build();

            BatchGetItemResponse batchResponse = dynamoDb.batchGetItem(batchRequest);
            List<Map<String, AttributeValue>> items = batchResponse.responses().get(RANKINGS_TABLE_NAME);

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
        reviewItem.put(REACTION_COUNTS_KEY, zeroReactionCountsAttribute());

        if (review.getIsoDateTime() != null) {
            reviewItem.put(ISO_DATE_TIME, AttributeValue.builder().s(review.getIsoDateTime()).build());
        }
        if (review.getAccountId() != null) {
            reviewItem.put(ACCOUNT_ID_KEY, AttributeValue.builder().s(review.getAccountId()).build());
        }

        // Use transactional write with optimistic locking retries
        addReviewWithTransactionalAggregate(restaurantId, reviewItem, review.getScore());

        // Return the review with the generated reviewId
        final String reviewId = review.getRestaurantId() + ":" + identifier;
        return Review.builder()
                .reviewId(reviewId)
                .restaurantId(review.getRestaurantId())
                .score(review.getScore())
                .title(review.getTitle())
                .body(review.getBody())
                .isoDateTime(review.getIsoDateTime())
                .accountId(review.getAccountId())
                .reactionCounts(ReactionCounts.zero())
                .build();
    }

    private static AttributeValue zeroReactionCountsAttribute() {
        return AttributeValue.builder()
                .m(Map.of(
                        THUMBS_UP_KEY, AttributeValue.builder().n("0").build(),
                        THUMBS_DOWN_KEY, AttributeValue.builder().n("0").build(),
                        HEART_KEY, AttributeValue.builder().n("0").build()
                ))
                .build();
    }

    private Map<String, AttributeValue> getRestaurantAggregate(String restaurantId) {
        final Map<String, AttributeValue> rankingsTablePrimaryKey = Map.of(
                RESTAURANT_ID_KEY, AttributeValue.builder().s(restaurantId).build(),
                IDENTIFIER_KEY, AttributeValue.builder().s(AGGREGATE_IDENTIFIER).build()
        );

        final GetItemRequest getAggregateRequest = GetItemRequest.builder()
                .tableName(RANKINGS_TABLE_NAME)
                .key(rankingsTablePrimaryKey)
                .build();

        return dynamoDb.getItem(getAggregateRequest).item();
    }

    void handleAggregateUpdateOptimisticLockingConflict(String restaurantId, int attempt, Exception e) {
        log.warn("Transaction conflict for restaurantId: {} on attempt {}/{}, retrying...",
                restaurantId, attempt + 1, MAX_AGGREGATE_UPDATE_RETRIES);

        if (attempt == MAX_AGGREGATE_UPDATE_RETRIES - 1) {
            throw new RuntimeException(
                    "Failed to add/delete review for restaurantId: " + restaurantId +
                            " after " + MAX_AGGREGATE_UPDATE_RETRIES + " attempts due to concurrent modifications", e);
        }

        try {
            Thread.sleep((long) (Math.pow(2, attempt) * 10));
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted during retry backoff", ie);
        }
    }

    /**
     * Atomically writes a review and updates the aggregate using DynamoDB transactions.
     * Uses optimistic locking on the aggregate with retries for concurrent modifications.
     */
    private void addReviewWithTransactionalAggregate(String restaurantId, Map<String, AttributeValue> reviewItem, Double newScore) {
        for (int attempt = 0; attempt < MAX_AGGREGATE_UPDATE_RETRIES; attempt++) {
            try {
                final Map<String, AttributeValue> existingAggregate = getRestaurantAggregate(restaurantId);

                // Build the review Put
                final Put reviewPut = Put.builder()
                        .tableName(RANKINGS_TABLE_NAME)
                        .item(reviewItem)
                        .build();

                // Build the aggregate Put with condition
                final Put aggregatePut;
                if (existingAggregate == null || existingAggregate.isEmpty()) {
                    // First review for this restaurant
                    AggregateRanking aggregateRanking = AggregateRanking.forFirstReview(restaurantId, newScore);
                    aggregatePut = Put.builder()
                            .tableName(RANKINGS_TABLE_NAME)
                            .item(aggregateRanking.toMap())
                            .conditionExpression("attribute_not_exists(#pk)")
                            .expressionAttributeNames(Map.of("#pk", RESTAURANT_ID_KEY))
                            .build();
                    log.info("Creating new aggregate for restaurantId: {}", restaurantId);
                } else {
                    // Update existing aggregate
                    AggregateRanking existingAggregateRanking = AggregateRanking.fromMap(existingAggregate);
                    AggregateRanking newAggregateRanking = existingAggregateRanking.withNewReview(newScore);
                    aggregatePut = Put.builder()
                            .tableName(RANKINGS_TABLE_NAME)
                            .item(newAggregateRanking.toMap())
                            .conditionExpression("#reviewCount = :expectedCount")
                            .expressionAttributeNames(Map.of("#reviewCount", REVIEW_COUNT_KEY))
                            .expressionAttributeValues(Map.of(
                                    ":expectedCount", AttributeValue.builder()
                                            .n(String.valueOf(existingAggregateRanking.getReviewCount()))
                                            .build()
                            ))
                            .build();
                    log.info("Updating aggregate for restaurantId: {} (reviewCount: {} -> {})",
                            restaurantId, existingAggregateRanking.getReviewCount(), newAggregateRanking.getReviewCount());
                }

                // Execute transaction - both writes succeed or both fail
                TransactWriteItemsRequest transactRequest = TransactWriteItemsRequest.builder()
                        .transactItems(
                                TransactWriteItem.builder().put(reviewPut).build(),
                                TransactWriteItem.builder().put(aggregatePut).build()
                        )
                        .build();

                dynamoDb.transactWriteItems(transactRequest);
                log.info("Successfully added review and updated aggregate for restaurantId: {}", restaurantId);
                return;

            } catch (TransactionCanceledException e) {
                handleAggregateUpdateOptimisticLockingConflict(restaurantId, attempt, e);
            }
        }
    }

    // TODO(FRY-114): Once we standardize the Review model, we can refactor this API to require a restaurantId and an
    // accountId instead.
    @Override
    public boolean deleteUserReview(@NonNull final DeleteReviewRequest delReviewRequest) {
        String reviewId = delReviewRequest.reviewId();

        String[] keyParts = reviewId.split(":");
        String restaurantId = keyParts[0];
        String identifier = REVIEW_IDENTIFIER_PREFIX + keyParts[1];

        // First, get the review to find its score (needed for aggregate update)
        final Map<String, AttributeValue> reviewKey = Map.of(
                RESTAURANT_ID_KEY, AttributeValue.builder().s(restaurantId).build(),
                IDENTIFIER_KEY, AttributeValue.builder().s(identifier).build()
        );

        final GetItemRequest getReviewRequest = GetItemRequest.builder()
                .tableName(RANKINGS_TABLE_NAME)
                .key(reviewKey)
                .build();

        final GetItemResponse reviewResponse = dynamoDb.getItem(getReviewRequest);
        final Map<String, AttributeValue> existingReview = reviewResponse.item();

        if (existingReview == null || existingReview.isEmpty()) {
            log.warn("Review with reviewId: {} does not exist, skipping delete", reviewId);
            return false;
        }

        final Double reviewScore = getDoubleAttribute(existingReview, SCORE_KEY);

        for (int attempt = 0; attempt < MAX_AGGREGATE_UPDATE_RETRIES; attempt++) {
            try {
                List<TransactWriteItem> transactWriteItems = new ArrayList<>();

                final Map<String, AttributeValue> existingAggregate = getRestaurantAggregate(restaurantId);

                if (existingAggregate == null || existingAggregate.isEmpty()) {
                    log.warn("Aggregate for restaurantId: {} does not exist, deleting review without aggregate update", restaurantId);
                    // Just delete the review without updating aggregate
                } else if (reviewScore == null) {
                    log.warn("Review with reviewId: {} has no score, deleting review without aggregate update", reviewId);
                    // Just delete the review without updating aggregate
                } else {
                    AggregateRanking existingAggregateRanking = AggregateRanking.fromMap(existingAggregate);

                    if (existingAggregateRanking.getReviewCount() <= 1) {
                        // Last review in aggregate, delete the aggregate
                        final Delete aggregateDelete = Delete.builder()
                                .tableName(RANKINGS_TABLE_NAME)
                                .key(Map.of(
                                        RESTAURANT_ID_KEY, AttributeValue.builder().s(restaurantId).build(),
                                        IDENTIFIER_KEY, AttributeValue.builder().s(AGGREGATE_IDENTIFIER).build()
                                ))
                                .conditionExpression("#reviewCount = :expectedCount")
                                .expressionAttributeNames(Map.of("#reviewCount", REVIEW_COUNT_KEY))
                                .expressionAttributeValues(Map.of(
                                        ":expectedCount", AttributeValue.builder()
                                                .n(String.valueOf(existingAggregateRanking.getReviewCount()))
                                                .build()
                                ))
                                .build();
                        transactWriteItems.add(TransactWriteItem.builder().delete(aggregateDelete).build());
                    } else {
                        // Update aggregate by removing this review's score
                        AggregateRanking newAggregateRanking = existingAggregateRanking.withoutReview(reviewScore);
                        final Put aggregatePut = Put.builder()
                                .tableName(RANKINGS_TABLE_NAME)
                                .item(newAggregateRanking.toMap())
                                .conditionExpression("#reviewCount = :expectedCount")
                                .expressionAttributeNames(Map.of("#reviewCount", REVIEW_COUNT_KEY))
                                .expressionAttributeValues(Map.of(
                                        ":expectedCount", AttributeValue.builder()
                                                .n(String.valueOf(existingAggregateRanking.getReviewCount()))
                                                .build()
                                ))
                                .build();
                        transactWriteItems.add(TransactWriteItem.builder().put(aggregatePut).build());
                    }
                }

                final Delete reviewDelete = Delete.builder()
                        .tableName(RANKINGS_TABLE_NAME)
                        .key(reviewKey)
                        .conditionExpression("attribute_exists(#pk)")
                        .expressionAttributeNames(Map.of("#pk", RESTAURANT_ID_KEY))
                        .build();
                transactWriteItems.add(TransactWriteItem.builder().delete(reviewDelete).build());

                TransactWriteItemsRequest transactRequest = TransactWriteItemsRequest.builder()
                        .transactItems(transactWriteItems)
                        .build();

                dynamoDb.transactWriteItems(transactRequest);
                log.info("Successfully deleted review and updated aggregate for restaurantId: {}", restaurantId);
                return true;

            } catch (TransactionCanceledException e) {
                handleAggregateUpdateOptimisticLockingConflict(restaurantId, attempt, e);
            }
        }

        // Should not reach here, but return false if all retries exhausted without throwing
        return false;
    }

    /**
     * Adds or removes one reaction for the viewer: updates {@code reactionCounts} on the review row and
     * {@code PutItem} / {@code DeleteItem} on {@link com.fryrank.Constants#REACTIONS_TABLE_NAME}.
     * If the viewer already matches the requested state, returns without writing (idempotent).
     * <p>
     * Uses {@link UpdateItemRequest#conditionExpression()} on {@code reactionCounts} so concurrent updates
     * do not overwrite each other; retries with backoff on {@link ConditionalCheckFailedException}.
     */
    @Override
    public ToggleReactionResult toggleReaction(
            @NonNull final String viewerAccountId,
            @NonNull final String reviewId,
            @NonNull final ReactionType reactionType,
            @NonNull final ReactionAction action
    ) {
        ConditionalCheckFailedException lastConflict = null;
        // Retry: another request may have changed reactionCounts between our read and write (optimistic lock miss).
        for (int attempt = 0; attempt < MAX_TOGGLE_REACTION_RETRIES; attempt++) {
            try {
                return toggleReactionOnce(viewerAccountId, reviewId, reactionType, action);
            } catch (ConditionalCheckFailedException e) {
                lastConflict = e;
                log.warn("toggleReaction optimistic lock failed for review {} (attempt {}/{})",
                        reviewId, attempt + 1, MAX_TOGGLE_REACTION_RETRIES);
                if (attempt < MAX_TOGGLE_REACTION_RETRIES - 1) {
                    try {
                        // Exponential backoff before re-reading the item and trying again.
                        Thread.sleep((long) (Math.pow(2, attempt) * 10L));
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException("Interrupted during toggle reaction retry", ie);
                    }
                }
            }
        }
        throw new IllegalStateException(
                "Could not update reactionCounts for review " + reviewId + " after concurrent modifications",
                lastConflict);
    }

    private ToggleReactionResult toggleReactionOnce(
            @NonNull final String viewerAccountId,
            @NonNull final String reviewId,
            @NonNull final ReactionType reactionType,
            @NonNull final ReactionAction action
    ) {
        final String[] rk = splitReviewId(reviewId);
        final String restaurantId = rk[0];
        final String identifier = rk[1];

        final Map<String, AttributeValue> reviewKey = Map.of(
                RESTAURANT_ID_KEY, AttributeValue.builder().s(restaurantId).build(),
                IDENTIFIER_KEY, AttributeValue.builder().s(identifier).build()
        );

        final Map<String, AttributeValue> reviewItem = dynamoDb.getItem(
                GetItemRequest.builder().tableName(RANKINGS_TABLE_NAME).key(reviewKey).build()
        ).item();

        if (reviewItem == null || reviewItem.isEmpty()) {
            throw new IllegalArgumentException("Review not found: " + reviewId);
        }

        final ReactionCounts counts = mapReactionCountsOrZero(reviewItem);

        final Map<String, AttributeValue> reactionKey = Map.of(
                VIEWER_ACCOUNT_ID_KEY, AttributeValue.builder().s(viewerAccountId).build(),
                REVIEW_ID_KEY, AttributeValue.builder().s(reviewId).build()
        );

        final Map<String, AttributeValue> existingReaction = dynamoDb.getItem(
                GetItemRequest.builder().tableName(REACTIONS_TABLE_NAME).key(reactionKey).build()
        ).item();

        final MyReactions previous = (existingReaction == null || existingReaction.isEmpty())
                ? MyReactions.none()
                : mapItemToMyReactions(existingReaction);

        final boolean currentlyOn = reactionFlag(previous, reactionType);
        final boolean requestedOn = (action == ReactionAction.ADD);
        if (currentlyOn == requestedOn) {
            return new ToggleReactionResult(reviewId, counts, previous);
        }

        // Snapshot of public totals before this request (used to detect concurrent writers via ConditionExpression).
        final int snapshotThumbsUp = counts.getThumbsUp();
        final int snapshotThumbsDown = counts.getThumbsDown();
        final int snapshotHeart = counts.getHeart();
        final boolean reactionCountsMissingOnItem = !reviewItem.containsKey(REACTION_COUNTS_KEY);

        applyActionToPublicCounts(counts, reactionType, action);

        final MyReactions next = setReactionFlag(previous, reactionType, requestedOn);

        final Map<String, AttributeValue> updateValues = new HashMap<>();
        updateValues.put(":rc", reactionCountsToAttribute(counts));

        final UpdateItemRequest.Builder updateBuilder = UpdateItemRequest.builder()
                .tableName(RANKINGS_TABLE_NAME)
                .key(reviewKey)
                .updateExpression("SET reactionCounts = :rc");

        final boolean snapshotWasAllZeros =
                snapshotThumbsUp == 0 && snapshotThumbsDown == 0 && snapshotHeart == 0;
        // First write of reactionCounts on this item: only succeed if the attribute still does not exist.
        if (reactionCountsMissingOnItem && snapshotWasAllZeros) {
            updateBuilder.conditionExpression("attribute_not_exists(reactionCounts)");
        } else {
            // Compare-and-set: apply new totals only if the three public counts still match what we read (no lost updates).
            updateValues.put(":etu", AttributeValue.builder().n(String.valueOf(snapshotThumbsUp)).build());
            updateValues.put(":etd", AttributeValue.builder().n(String.valueOf(snapshotThumbsDown)).build());
            updateValues.put(":eh", AttributeValue.builder().n(String.valueOf(snapshotHeart)).build());
            updateBuilder
                    .conditionExpression(
                            "reactionCounts.#tu = :etu AND reactionCounts.#td = :etd AND reactionCounts.#h = :eh")
                    .expressionAttributeNames(Map.of(
                            "#tu", THUMBS_UP_KEY,
                            "#td", THUMBS_DOWN_KEY,
                            "#h", HEART_KEY));
        }

        updateBuilder.expressionAttributeValues(updateValues);
        dynamoDb.updateItem(updateBuilder.build());

        // Per-viewer reaction row (separate item); rankings update above must succeed first.
        if (isAllReactionsOff(next)) {
            dynamoDb.deleteItem(DeleteItemRequest.builder()
                    .tableName(REACTIONS_TABLE_NAME)
                    .key(reactionKey)
                    .build());
        } else {
            final Map<String, AttributeValue> reactionRow = new HashMap<>();
            reactionRow.put(VIEWER_ACCOUNT_ID_KEY, AttributeValue.builder().s(viewerAccountId).build());
            reactionRow.put(REVIEW_ID_KEY, AttributeValue.builder().s(reviewId).build());
            reactionRow.put(THUMBS_UP_KEY, AttributeValue.builder().bool(next.isThumbsUp()).build());
            reactionRow.put(THUMBS_DOWN_KEY, AttributeValue.builder().bool(next.isThumbsDown()).build());
            reactionRow.put(HEART_KEY, AttributeValue.builder().bool(next.isHeart()).build());
            dynamoDb.putItem(PutItemRequest.builder()
                    .tableName(REACTIONS_TABLE_NAME)
                    .item(reactionRow)
                    .build());
        }

        return new ToggleReactionResult(reviewId, counts, next);
    }

    private static String[] splitReviewId(String reviewId) {
        final int idx = reviewId.indexOf(':');
        if (idx <= 0 || idx >= reviewId.length() - 1) {
            throw new IllegalArgumentException("Invalid reviewId: " + reviewId);
        }
        return new String[]{reviewId.substring(0, idx), reviewId.substring(idx + 1)};
    }

    /** Whether the viewer currently has this reaction type turned on. */
    private static boolean reactionFlag(MyReactions r, ReactionType type) {
        return switch (type) {
            case THUMBS_UP -> r.isThumbsUp();
            case THUMBS_DOWN -> r.isThumbsDown();
            case HEART -> r.isHeart();
        };
    }

    private static MyReactions setReactionFlag(MyReactions r, ReactionType type, boolean value) {
        return MyReactions.builder()
                .thumbsUp(type == ReactionType.THUMBS_UP ? value : r.isThumbsUp())
                .thumbsDown(type == ReactionType.THUMBS_DOWN ? value : r.isThumbsDown())
                .heart(type == ReactionType.HEART ? value : r.isHeart())
                .build();
    }

    /** Adjusts public totals for the review row: ADD increments, REMOVE decrements (call only when viewer state changes). */
    private static void applyActionToPublicCounts(ReactionCounts counts, ReactionType type, ReactionAction action) {
        switch (action) {
            case ADD -> {
                switch (type) {
                    case THUMBS_UP -> counts.setThumbsUp(counts.getThumbsUp() + 1);
                    case THUMBS_DOWN -> counts.setThumbsDown(counts.getThumbsDown() + 1);
                    case HEART -> counts.setHeart(counts.getHeart() + 1);
                }
            }
            case REMOVE -> {
                switch (type) {
                    case THUMBS_UP -> counts.setThumbsUp(Math.max(0, counts.getThumbsUp() - 1));
                    case THUMBS_DOWN -> counts.setThumbsDown(Math.max(0, counts.getThumbsDown() - 1));
                    case HEART -> counts.setHeart(Math.max(0, counts.getHeart() - 1));
                }
            }
        }
    }

    private static boolean isAllReactionsOff(MyReactions r) {
        return !r.isThumbsUp() && !r.isThumbsDown() && !r.isHeart();
    }

    private static AttributeValue reactionCountsToAttribute(ReactionCounts c) {
        return AttributeValue.builder()
                .m(Map.of(
                        THUMBS_UP_KEY, AttributeValue.builder().n(String.valueOf(c.getThumbsUp())).build(),
                        THUMBS_DOWN_KEY, AttributeValue.builder().n(String.valueOf(c.getThumbsDown())).build(),
                        HEART_KEY, AttributeValue.builder().n(String.valueOf(c.getHeart())).build()
                ))
                .build();
    }

    /**
     * Maps DynamoDB items to Review objects with batched user metadata fetching.
     */
    private GetAllReviewsOutput mapItemsToReviewsWithUserMetadata(List<Map<String, AttributeValue>> items, String nextCursor) {
        final List<String> accountIds = items.parallelStream()
                .map(item -> getStringAttribute(item, ACCOUNT_ID_KEY))
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());

        final Map<String, PublicUserMetadata> userMetadataMap = batchFetchUserMetadata(accountIds);

        final List<Review> reviews = items.parallelStream()
                .map(item -> mapItemToReview(item, userMetadataMap))
                .collect(Collectors.toList());

        return new GetAllReviewsOutput(reviews, nextCursor);
    }

    private static Review withMyReactions(Review review, MyReactions myReactions) {
        review.setMyReactions(myReactions);
        return review;
    }

    /**
     * Batch-gets reaction rows for this viewer for the given reviews (simple prototype; chunks of 100).
     */
    private Map<String, MyReactions> batchGetMyReactionsForViewer(String viewerAccountId, List<Review> reviews) {
        final List<String> reviewIds = reviews.parallelStream().map(Review::getReviewId).collect(Collectors.toList());
        if (reviewIds.isEmpty()) {
            return Map.of();
        }
        final Map<String, MyReactions> out = new HashMap<>();
        final int batchSize = 100;
        for (int i = 0; i < reviewIds.size(); i += batchSize) {
            final List<String> batch = reviewIds.subList(i, Math.min(i + batchSize, reviewIds.size()));
            final List<Map<String, AttributeValue>> keys = batch.stream()
                    .map(rid -> Map.of(
                            VIEWER_ACCOUNT_ID_KEY, AttributeValue.builder().s(viewerAccountId).build(),
                            REVIEW_ID_KEY, AttributeValue.builder().s(rid).build()
                    ))
                    .collect(Collectors.toList());

            final BatchGetItemRequest req = BatchGetItemRequest.builder()
                    .requestItems(Map.of(REACTIONS_TABLE_NAME, KeysAndAttributes.builder().keys(keys).build()))
                    .build();
            final BatchGetItemResponse resp = dynamoDb.batchGetItem(req);
            final List<Map<String, AttributeValue>> items = resp.responses().get(REACTIONS_TABLE_NAME);
            if (items != null) {
                for (Map<String, AttributeValue> item : items) {
                    final String rid = item.get(REVIEW_ID_KEY).s();
                    out.put(rid, mapItemToMyReactions(item));
                }
            }
        }
        return out;
    }

    private static MyReactions mapItemToMyReactions(Map<String, AttributeValue> item) {
        return MyReactions.builder()
                .thumbsUp(boolAttr(item, THUMBS_UP_KEY))
                .thumbsDown(boolAttr(item, THUMBS_DOWN_KEY))
                .heart(boolAttr(item, HEART_KEY))
                .build();
    }

    private static boolean boolAttr(Map<String, AttributeValue> item, String key) {
        final AttributeValue v = item.get(key);
        return v != null && Boolean.TRUE.equals(v.bool());
    }

    /**
     * Maps a DynamoDB item to a Review object using pre-fetched user metadata.
     */
    private Review mapItemToReview(Map<String, AttributeValue> item, Map<String, PublicUserMetadata> userMetadataMap) {
        final String accountId = getStringAttribute(item, ACCOUNT_ID_KEY);
        final String restaurantId = getStringAttribute(item, RESTAURANT_ID_KEY);
        final String identifierValue = Objects.requireNonNull(getStringAttribute(item, IDENTIFIER_KEY));

        final PublicUserMetadata userMetadata = accountId != null ? userMetadataMap.get(accountId) : null;

        // Align with addNewReview: reviewId = restaurantId + ":" + identifier (e.g. res:REVIEW:accountId)
        final String reviewId = restaurantId + ":" + identifierValue;

        assert restaurantId != null;
        return Review.builder()
                .reviewId(reviewId)
                .restaurantId(restaurantId)
                .score(Objects.requireNonNull(getDoubleAttribute(item, SCORE_KEY)))
                .title(Objects.requireNonNull(getStringAttribute(item, TITLE_KEY)))
                .body(Objects.requireNonNull(getStringAttribute(item, BODY_KEY)))
                .isoDateTime(getStringAttribute(item, ISO_DATE_TIME))
                .accountId(accountId)
                .userMetadata(userMetadata)
                .reactionCounts(mapReactionCountsOrZero(item))
                .build();
    }

    private ReactionCounts mapReactionCountsOrZero(Map<String, AttributeValue> item) {
        final AttributeValue rc = item.get(REACTION_COUNTS_KEY);
        if (rc == null || rc.m() == null) {
            return ReactionCounts.zero();
        }
        final Map<String, AttributeValue> reactionCountsByKey = rc.m();
        return ReactionCounts.builder()
                .thumbsUp(intAttr(reactionCountsByKey, THUMBS_UP_KEY))
                .thumbsDown(intAttr(reactionCountsByKey, THUMBS_DOWN_KEY))
                .heart(intAttr(reactionCountsByKey, HEART_KEY))
                .build();
    }

    private static int intAttr(Map<String, AttributeValue> countAttributes, String key) {
        final AttributeValue v = countAttributes.get(key);
        if (v == null || v.n() == null) {
            return 0;
        }
        return (int) Double.parseDouble(v.n());
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

    private String getStringAttribute(Map<String, AttributeValue> item, String key) {
        AttributeValue attr = item.get(key);
        return (attr != null && attr.s() != null) ? attr.s() : null;
    }

    private Double getDoubleAttribute(Map<String, AttributeValue> item, String key) {
        AttributeValue attr = item.get(key);
        return (attr != null && attr.n() != null) ? Double.parseDouble(attr.n()) : null;
    }
}

