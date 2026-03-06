
package com.fryrank.dal;

import com.fryrank.model.GetAllReviewsOutput;
import com.fryrank.model.Review;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.BatchGetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.BatchGetItemResponse;
import software.amazon.awssdk.services.dynamodb.model.CancellationReason;
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException;
import software.amazon.awssdk.services.dynamodb.model.DeleteItemRequest;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.GetItemResponse;
import software.amazon.awssdk.services.dynamodb.model.KeysAndAttributes;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;
import software.amazon.awssdk.services.dynamodb.model.PutItemResponse;
import software.amazon.awssdk.services.dynamodb.model.QueryRequest;
import software.amazon.awssdk.services.dynamodb.model.QueryResponse;
import software.amazon.awssdk.services.dynamodb.model.TransactionCanceledException;
import software.amazon.awssdk.services.dynamodb.model.TransactWriteItemsRequest;
import software.amazon.awssdk.services.dynamodb.model.TransactWriteItemsResponse;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.fryrank.Constants.ACCOUNT_ID_KEY;
import static com.fryrank.Constants.ISO_DATE_TIME;
import static com.fryrank.Constants.USERNAME_KEY;
import static com.fryrank.Constants.USER_METADATA_TABLE_NAME;
import static com.fryrank.TestConstants.TEST_ACCOUNT_ID;
import static com.fryrank.TestConstants.TEST_ISO_DATE_TIME_1;
import static com.fryrank.TestConstants.TEST_RESTAURANT_ID;
import static com.fryrank.TestConstants.TEST_REVIEWS;
import static com.fryrank.TestConstants.TEST_REVIEW_1;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class ReviewDALTests {
    @Mock
    DynamoDbClient dynamoDb;

    @InjectMocks
    ReviewDALImpl reviewDAL;

    @Test
    public void testGetAllReviewsByRestaurantId_happyPath() throws Exception {
        // Mock the query response with review items
        QueryResponse queryResponse = QueryResponse.builder()
                .items(TEST_REVIEWS.stream().map(this::reviewToAttributeMap).toList())
                .build();
        when(dynamoDb.query(any(QueryRequest.class))).thenReturn(queryResponse);

        // Mock user metadata lookup for each review
        mockUserMetadataLookup();

        final GetAllReviewsOutput actualOutput = reviewDAL.getAllReviewsByRestaurantId(TEST_RESTAURANT_ID);
        assertNotNull(actualOutput);
        assertEquals(TEST_REVIEWS.size(), actualOutput.getReviews().size());
    }

    @Test
    public void testGetAllReviewsByRestaurantId_noReviews() throws Exception {
        QueryResponse queryResponse = QueryResponse.builder()
                .items(List.of())
                .build();
        when(dynamoDb.query(any(QueryRequest.class))).thenReturn(queryResponse);

        final GetAllReviewsOutput expectedOutput = new GetAllReviewsOutput(List.of());
        final GetAllReviewsOutput actualOutput = reviewDAL.getAllReviewsByRestaurantId(TEST_RESTAURANT_ID);
        assertEquals(expectedOutput, actualOutput);
    }

    @Test
    public void testGetAllReviewsByRestaurantId_nullRestaurantId() {
        assertThrows(NullPointerException.class, () -> reviewDAL.getAllReviewsByRestaurantId(null));
    }

    @Test
    public void testGetAllReviewsByAccountId_happyPath() throws Exception {
        QueryResponse queryResponse = QueryResponse.builder()
                .items(TEST_REVIEWS.stream().map(this::reviewToAttributeMap).toList())
                .build();
        when(dynamoDb.query(any(QueryRequest.class))).thenReturn(queryResponse);

        mockUserMetadataLookup();

        final GetAllReviewsOutput actualOutput = reviewDAL.getAllReviewsByAccountId(TEST_ACCOUNT_ID);
        assertNotNull(actualOutput);
        assertEquals(TEST_REVIEWS.size(), actualOutput.getReviews().size());
    }

    @Test
    public void testGetAllReviewsByAccountId_noReviews() throws Exception {
        QueryResponse queryResponse = QueryResponse.builder()
                .items(List.of())
                .build();
        when(dynamoDb.query(any(QueryRequest.class))).thenReturn(queryResponse);

        final GetAllReviewsOutput expectedOutput = new GetAllReviewsOutput(List.of());
        final GetAllReviewsOutput actualOutput = reviewDAL.getAllReviewsByAccountId(TEST_ACCOUNT_ID);
        assertEquals(expectedOutput, actualOutput);
    }

    @Test
    public void testGetAllReviewsByAccountId_nullAccountId() {
        assertThrows(NullPointerException.class, () -> reviewDAL.getAllReviewsByAccountId(null));
    }

    @Test
    public void testGetRecentReviews() throws Exception {
        QueryResponse queryResponse = QueryResponse.builder()
                .items(TEST_REVIEWS.stream().map(this::reviewToAttributeMap).toList())
                .build();
        when(dynamoDb.query(any(QueryRequest.class))).thenReturn(queryResponse);

        mockUserMetadataLookup();

        final GetAllReviewsOutput actualOutput = reviewDAL.getRecentReviews(TEST_REVIEWS.size());
        assertNotNull(actualOutput);
        assertEquals(TEST_REVIEWS.size(), actualOutput.getReviews().size());
    }

    @Test
    public void testAddNewReview_noExistingAggregate() throws Exception {
        // Mock getItem to return empty (no existing aggregate)
        GetItemResponse emptyAggregateResponse = GetItemResponse.builder()
                .item(Map.of())
                .build();
        when(dynamoDb.getItem(any(GetItemRequest.class))).thenReturn(emptyAggregateResponse);

        // Mock transactWriteItems
        when(dynamoDb.transactWriteItems(any(TransactWriteItemsRequest.class)))
                .thenReturn(TransactWriteItemsResponse.builder().build());

        final Review actualReview = reviewDAL.addNewReview(TEST_REVIEW_1);

        // Verify the review is returned correctly
        assertNotNull(actualReview);
        assertEquals(TEST_REVIEW_1.getRestaurantId(), actualReview.getRestaurantId());
        assertEquals(TEST_REVIEW_1.getScore(), actualReview.getScore());
        assertEquals(TEST_REVIEW_1.getTitle(), actualReview.getTitle());
        assertEquals(TEST_REVIEW_1.getBody(), actualReview.getBody());

        // Capture and verify the transactWriteItems request
        ArgumentCaptor<TransactWriteItemsRequest> transactCaptor = ArgumentCaptor.forClass(TransactWriteItemsRequest.class);
        verify(dynamoDb, times(1)).transactWriteItems(transactCaptor.capture());

        TransactWriteItemsRequest capturedRequest = transactCaptor.getValue();
        assertEquals(2, capturedRequest.transactItems().size());

        // First item should be the review
        Map<String, AttributeValue> reviewItem = capturedRequest.transactItems().get(0).put().item();
        assertTrue(reviewItem.get("identifier").s().startsWith("REVIEW:"));
        assertEquals(TEST_REVIEW_1.getScore().toString(), reviewItem.get("score").n());

        // Second item should be the aggregate with condition expression for new aggregate
        var aggregatePut = capturedRequest.transactItems().get(1).put();
        Map<String, AttributeValue> aggregateItem = aggregatePut.item();
        assertEquals("AGGREGATE", aggregateItem.get("identifier").s());
        assertEquals("1", aggregateItem.get("reviewCount").n());
        assertEquals(TEST_REVIEW_1.getScore().toString(), aggregateItem.get("totalScore").n());
        assertEquals(TEST_REVIEW_1.getScore().toString(), aggregateItem.get("averageScore").n());

        // Verify condition expression for new aggregate
        assertEquals("attribute_not_exists(#pk)", aggregatePut.conditionExpression());
    }

    @Test
    public void testAddNewReview_withExistingAggregate() throws Exception {
        // Existing aggregate: totalScore=50, reviewCount=5, averageScore=10.0
        double existingTotalScore = 50.0;
        int existingReviewCount = 5;

        Map<String, AttributeValue> existingAggregate = new HashMap<>();
        existingAggregate.put("restaurantId", AttributeValue.builder().s(TEST_REVIEW_1.getRestaurantId()).build());
        existingAggregate.put("identifier", AttributeValue.builder().s("AGGREGATE").build());
        existingAggregate.put("isoDateTime", AttributeValue.builder().s("AGGREGATE").build());
        existingAggregate.put("totalScore", AttributeValue.builder().n(String.valueOf(existingTotalScore)).build());
        existingAggregate.put("reviewCount", AttributeValue.builder().n(String.valueOf(existingReviewCount)).build());
        existingAggregate.put("averageScore", AttributeValue.builder().n("10.0").build());

        GetItemResponse aggregateResponse = GetItemResponse.builder()
                .item(existingAggregate)
                .build();
        when(dynamoDb.getItem(any(GetItemRequest.class))).thenReturn(aggregateResponse);

        // Mock transactWriteItems
        when(dynamoDb.transactWriteItems(any(TransactWriteItemsRequest.class)))
                .thenReturn(TransactWriteItemsResponse.builder().build());

        final Review actualReview = reviewDAL.addNewReview(TEST_REVIEW_1);

        // Verify the review is returned correctly
        assertNotNull(actualReview);
        assertEquals(TEST_REVIEW_1.getRestaurantId(), actualReview.getRestaurantId());

        // Capture and verify the transactWriteItems request
        ArgumentCaptor<TransactWriteItemsRequest> transactCaptor = ArgumentCaptor.forClass(TransactWriteItemsRequest.class);
        verify(dynamoDb, times(1)).transactWriteItems(transactCaptor.capture());

        TransactWriteItemsRequest capturedRequest = transactCaptor.getValue();
        var aggregatePut = capturedRequest.transactItems().get(1).put();
        Map<String, AttributeValue> aggregateItem = aggregatePut.item();

        // Verify updated aggregate values
        double expectedNewTotalScore = existingTotalScore + TEST_REVIEW_1.getScore();
        int expectedNewReviewCount = existingReviewCount + 1;
        double expectedNewAverageScore = expectedNewTotalScore / expectedNewReviewCount;

        assertEquals(String.valueOf(expectedNewReviewCount), aggregateItem.get("reviewCount").n());
        assertEquals(String.valueOf(expectedNewTotalScore), aggregateItem.get("totalScore").n());
        assertEquals(String.valueOf(expectedNewAverageScore), aggregateItem.get("averageScore").n());

        // Verify condition expression checks expected count
        assertEquals("#reviewCount = :expectedCount", aggregatePut.conditionExpression());
        assertEquals(String.valueOf(existingReviewCount),
                aggregatePut.expressionAttributeValues().get(":expectedCount").n());
    }

    @Test
    public void testAddNewReview_nullReview() {
        assertThrows(NullPointerException.class, () -> reviewDAL.addNewReview(null));
    }

    @Test
    public void testAddNewReview_transactionConflict_retriesSuccessfully() throws Exception {
        // Existing aggregate
        Map<String, AttributeValue> existingAggregate = new HashMap<>();
        existingAggregate.put("restaurantId", AttributeValue.builder().s(TEST_REVIEW_1.getRestaurantId()).build());
        existingAggregate.put("identifier", AttributeValue.builder().s("AGGREGATE").build());
        existingAggregate.put("isoDateTime", AttributeValue.builder().s("AGGREGATE").build());
        existingAggregate.put("totalScore", AttributeValue.builder().n("50.0").build());
        existingAggregate.put("reviewCount", AttributeValue.builder().n("5").build());
        existingAggregate.put("averageScore", AttributeValue.builder().n("10.0").build());

        // Updated aggregate (simulating concurrent update)
        Map<String, AttributeValue> updatedAggregate = new HashMap<>();
        updatedAggregate.put("restaurantId", AttributeValue.builder().s(TEST_REVIEW_1.getRestaurantId()).build());
        updatedAggregate.put("identifier", AttributeValue.builder().s("AGGREGATE").build());
        updatedAggregate.put("isoDateTime", AttributeValue.builder().s("AGGREGATE").build());
        updatedAggregate.put("totalScore", AttributeValue.builder().n("58.0").build());
        updatedAggregate.put("reviewCount", AttributeValue.builder().n("6").build());
        updatedAggregate.put("averageScore", AttributeValue.builder().n("9.666666666666666").build());

        GetItemResponse firstResponse = GetItemResponse.builder().item(existingAggregate).build();
        GetItemResponse secondResponse = GetItemResponse.builder().item(updatedAggregate).build();

        // First getItem returns original, second getItem returns updated (after conflict)
        when(dynamoDb.getItem(any(GetItemRequest.class)))
                .thenReturn(firstResponse)
                .thenReturn(secondResponse);

        // First transaction fails with conflict, second succeeds
        when(dynamoDb.transactWriteItems(any(TransactWriteItemsRequest.class)))
                .thenThrow(TransactionCanceledException.builder()
                        .message("Transaction cancelled")
                        .cancellationReasons(
                                CancellationReason.builder().code("None").build(),
                                CancellationReason.builder().code("ConditionalCheckFailed").build()
                        )
                        .build())
                .thenReturn(TransactWriteItemsResponse.builder().build());

        final Review actualReview = reviewDAL.addNewReview(TEST_REVIEW_1);

        assertNotNull(actualReview);
        assertEquals(TEST_REVIEW_1.getRestaurantId(), actualReview.getRestaurantId());

        // Verify getItem was called twice (initial + retry)
        verify(dynamoDb, times(2)).getItem(any(GetItemRequest.class));

        // Verify transactWriteItems was called twice (failed + successful)
        verify(dynamoDb, times(2)).transactWriteItems(any(TransactWriteItemsRequest.class));
    }

    @Test
    public void testAddNewReview_transactionConflict_exhaustsRetries() throws Exception {
        // Existing aggregate
        Map<String, AttributeValue> existingAggregate = new HashMap<>();
        existingAggregate.put("restaurantId", AttributeValue.builder().s(TEST_REVIEW_1.getRestaurantId()).build());
        existingAggregate.put("identifier", AttributeValue.builder().s("AGGREGATE").build());
        existingAggregate.put("isoDateTime", AttributeValue.builder().s("AGGREGATE").build());
        existingAggregate.put("totalScore", AttributeValue.builder().n("50.0").build());
        existingAggregate.put("reviewCount", AttributeValue.builder().n("5").build());
        existingAggregate.put("averageScore", AttributeValue.builder().n("10.0").build());

        GetItemResponse aggregateResponse = GetItemResponse.builder().item(existingAggregate).build();
        when(dynamoDb.getItem(any(GetItemRequest.class))).thenReturn(aggregateResponse);

        // All transaction attempts fail
        when(dynamoDb.transactWriteItems(any(TransactWriteItemsRequest.class)))
                .thenThrow(TransactionCanceledException.builder()
                        .message("Transaction cancelled")
                        .cancellationReasons(
                                CancellationReason.builder().code("None").build(),
                                CancellationReason.builder().code("ConditionalCheckFailed").build()
                        )
                        .build());

        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> reviewDAL.addNewReview(TEST_REVIEW_1));

        assertTrue(exception.getMessage().contains("Failed to add review"));
        assertTrue(exception.getMessage().contains("concurrent modifications"));

        // Verify retries happened (MAX_AGGREGATE_UPDATE_RETRIES = 3)
        verify(dynamoDb, times(3)).transactWriteItems(any(TransactWriteItemsRequest.class));
        verify(dynamoDb, times(3)).getItem(any(GetItemRequest.class));
    }

    @Test
    public void testAddNewReview_newAggregateConflict_retriesWithExistingAggregate() throws Exception {
        // First read returns empty (no aggregate), second read returns existing aggregate
        // This simulates: two concurrent first reviews, one succeeds first
        GetItemResponse emptyResponse = GetItemResponse.builder().item(Map.of()).build();

        Map<String, AttributeValue> existingAggregate = new HashMap<>();
        existingAggregate.put("restaurantId", AttributeValue.builder().s(TEST_REVIEW_1.getRestaurantId()).build());
        existingAggregate.put("identifier", AttributeValue.builder().s("AGGREGATE").build());
        existingAggregate.put("isoDateTime", AttributeValue.builder().s("AGGREGATE").build());
        existingAggregate.put("totalScore", AttributeValue.builder().n("8.0").build());
        existingAggregate.put("reviewCount", AttributeValue.builder().n("1").build());
        existingAggregate.put("averageScore", AttributeValue.builder().n("8.0").build());
        GetItemResponse existingResponse = GetItemResponse.builder().item(existingAggregate).build();

        when(dynamoDb.getItem(any(GetItemRequest.class)))
                .thenReturn(emptyResponse)
                .thenReturn(existingResponse);

        when(dynamoDb.transactWriteItems(any(TransactWriteItemsRequest.class)))
                .thenThrow(TransactionCanceledException.builder()
                        .message("Transaction cancelled")
                        .cancellationReasons(
                                CancellationReason.builder().code("None").build(),
                                CancellationReason.builder().code("ConditionalCheckFailed").build()
                        )
                        .build())
                .thenReturn(TransactWriteItemsResponse.builder().build());

        final Review actualReview = reviewDAL.addNewReview(TEST_REVIEW_1);

        assertNotNull(actualReview);

        // Capture transaction requests to verify transition from "new" to "update" mode
        ArgumentCaptor<TransactWriteItemsRequest> transactCaptor = ArgumentCaptor.forClass(TransactWriteItemsRequest.class);
        verify(dynamoDb, times(2)).transactWriteItems(transactCaptor.capture());

        List<TransactWriteItemsRequest> capturedRequests = transactCaptor.getAllValues();

        // First attempt should use attribute_not_exists (new aggregate)
        var firstAggregatePut = capturedRequests.get(0).transactItems().get(1).put();
        assertEquals("attribute_not_exists(#pk)", firstAggregatePut.conditionExpression());

        // Second attempt should use reviewCount check (update existing)
        var secondAggregatePut = capturedRequests.get(1).transactItems().get(1).put();
        assertEquals("#reviewCount = :expectedCount", secondAggregatePut.conditionExpression());

        // Final aggregate should have reviewCount=2 (existing 1 + new review)
        assertEquals("2", secondAggregatePut.item().get("reviewCount").n());
    }

    @Test
    public void testAddNewReview_transactionAtomicity_bothItemsInSameTransaction() throws Exception {
        // Mock getItem to return empty (no existing aggregate)
        GetItemResponse emptyAggregateResponse = GetItemResponse.builder()
                .item(Map.of())
                .build();
        when(dynamoDb.getItem(any(GetItemRequest.class))).thenReturn(emptyAggregateResponse);

        // Mock transactWriteItems
        when(dynamoDb.transactWriteItems(any(TransactWriteItemsRequest.class)))
                .thenReturn(TransactWriteItemsResponse.builder().build());

        reviewDAL.addNewReview(TEST_REVIEW_1);

        // Verify that both review and aggregate are in the same transaction
        ArgumentCaptor<TransactWriteItemsRequest> transactCaptor = ArgumentCaptor.forClass(TransactWriteItemsRequest.class);
        verify(dynamoDb, times(1)).transactWriteItems(transactCaptor.capture());

        TransactWriteItemsRequest capturedRequest = transactCaptor.getValue();

        // Should have exactly 2 items in the transaction
        assertEquals(2, capturedRequest.transactItems().size());

        // Both should be Put operations
        assertNotNull(capturedRequest.transactItems().get(0).put());
        assertNotNull(capturedRequest.transactItems().get(1).put());

        // Verify no separate putItem calls were made (everything is in the transaction)
        verify(dynamoDb, times(0)).putItem(any(PutItemRequest.class));
    }

    @Test
    public void testAddNewReview_transactionFailure_noReviewWritten() throws Exception {
        // Mock getItem to return empty (no existing aggregate)
        GetItemResponse emptyAggregateResponse = GetItemResponse.builder()
                .item(Map.of())
                .build();
        when(dynamoDb.getItem(any(GetItemRequest.class))).thenReturn(emptyAggregateResponse);

        // All transaction attempts fail with a non-conditional error
        when(dynamoDb.transactWriteItems(any(TransactWriteItemsRequest.class)))
                .thenThrow(TransactionCanceledException.builder()
                        .message("Transaction cancelled")
                        .build());

        // Verify exception is thrown
        assertThrows(RuntimeException.class, () -> reviewDAL.addNewReview(TEST_REVIEW_1));

        // Verify no separate putItem was called - the review is never written outside the transaction
        verify(dynamoDb, times(0)).putItem(any(PutItemRequest.class));

        // Verify no deleteItem was called - no rollback needed since transaction is atomic
        verify(dynamoDb, times(0)).deleteItem(any(DeleteItemRequest.class));
    }

    // ==================== Batch Fetch User Metadata Tests ====================

    @Test
    public void testGetAllReviewsByRestaurantId_withMoreThan100UniqueAccounts_batchesCalls() throws Exception {
        // Create 150 reviews with unique account IDs
        int totalReviews = 150;
        List<Map<String, AttributeValue>> reviewItems = new ArrayList<>();

        for (int i = 0; i < totalReviews; i++) {
            String accountId = "account_" + i;
            Map<String, AttributeValue> reviewItem = new HashMap<>();
            reviewItem.put("restaurantId", AttributeValue.builder().s(TEST_RESTAURANT_ID).build());
            reviewItem.put("identifier", AttributeValue.builder().s("REVIEW:" + accountId).build());
            reviewItem.put("score", AttributeValue.builder().n("5.0").build());
            reviewItem.put("title", AttributeValue.builder().s("Title " + i).build());
            reviewItem.put("body", AttributeValue.builder().s("Body " + i).build());
            reviewItem.put(ACCOUNT_ID_KEY, AttributeValue.builder().s(accountId).build());
            reviewItem.put(ISO_DATE_TIME, AttributeValue.builder().s(TEST_ISO_DATE_TIME_1).build());
            reviewItems.add(reviewItem);
        }

        QueryResponse queryResponse = QueryResponse.builder()
                .items(reviewItems)
                .build();
        when(dynamoDb.query(any(QueryRequest.class))).thenReturn(queryResponse);

        // Mock batch get responses - should be called twice (100 + 50)
        // First batch: accounts 0-99
        List<Map<String, AttributeValue>> firstBatchMetadata = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            String accountId = "account_" + i;
            Map<String, AttributeValue> metadata = new HashMap<>();
            metadata.put(ACCOUNT_ID_KEY, AttributeValue.builder().s(accountId).build());
            metadata.put(USERNAME_KEY, AttributeValue.builder().s("user_" + i).build());
            firstBatchMetadata.add(metadata);
        }

        // Second batch: accounts 100-149
        List<Map<String, AttributeValue>> secondBatchMetadata = new ArrayList<>();
        for (int i = 100; i < totalReviews; i++) {
            String accountId = "account_" + i;
            Map<String, AttributeValue> metadata = new HashMap<>();
            metadata.put(ACCOUNT_ID_KEY, AttributeValue.builder().s(accountId).build());
            metadata.put(USERNAME_KEY, AttributeValue.builder().s("user_" + i).build());
            secondBatchMetadata.add(metadata);
        }

        BatchGetItemResponse firstBatchResponse = BatchGetItemResponse.builder()
                .responses(Map.of(USER_METADATA_TABLE_NAME, firstBatchMetadata))
                .build();
        BatchGetItemResponse secondBatchResponse = BatchGetItemResponse.builder()
                .responses(Map.of(USER_METADATA_TABLE_NAME, secondBatchMetadata))
                .build();

        when(dynamoDb.batchGetItem(any(BatchGetItemRequest.class)))
                .thenReturn(firstBatchResponse)
                .thenReturn(secondBatchResponse);

        final GetAllReviewsOutput actualOutput = reviewDAL.getAllReviewsByRestaurantId(TEST_RESTAURANT_ID);

        // Verify results
        assertNotNull(actualOutput);
        assertEquals(totalReviews, actualOutput.getReviews().size());

        // Verify batchGetItem was called exactly twice (100 + 50)
        ArgumentCaptor<BatchGetItemRequest> batchCaptor = ArgumentCaptor.forClass(BatchGetItemRequest.class);
        verify(dynamoDb, times(2)).batchGetItem(batchCaptor.capture());

        List<BatchGetItemRequest> capturedRequests = batchCaptor.getAllValues();

        // First batch should have 100 keys
        KeysAndAttributes firstKeys = capturedRequests.get(0).requestItems().get(USER_METADATA_TABLE_NAME);
        assertEquals(100, firstKeys.keys().size());

        // Second batch should have 50 keys
        KeysAndAttributes secondKeys = capturedRequests.get(1).requestItems().get(USER_METADATA_TABLE_NAME);
        assertEquals(50, secondKeys.keys().size());
    }

    @Test
    public void testGetAllReviewsByRestaurantId_withExactly100UniqueAccounts_singleBatch() throws Exception {
        // Create exactly 100 reviews with unique account IDs
        int totalReviews = 100;
        List<Map<String, AttributeValue>> reviewItems = new ArrayList<>();

        for (int i = 0; i < totalReviews; i++) {
            String accountId = "account_" + i;
            Map<String, AttributeValue> reviewItem = new HashMap<>();
            reviewItem.put("restaurantId", AttributeValue.builder().s(TEST_RESTAURANT_ID).build());
            reviewItem.put("identifier", AttributeValue.builder().s("REVIEW:" + accountId).build());
            reviewItem.put("score", AttributeValue.builder().n("5.0").build());
            reviewItem.put("title", AttributeValue.builder().s("Title " + i).build());
            reviewItem.put("body", AttributeValue.builder().s("Body " + i).build());
            reviewItem.put(ACCOUNT_ID_KEY, AttributeValue.builder().s(accountId).build());
            reviewItem.put(ISO_DATE_TIME, AttributeValue.builder().s(TEST_ISO_DATE_TIME_1).build());
            reviewItems.add(reviewItem);
        }

        QueryResponse queryResponse = QueryResponse.builder()
                .items(reviewItems)
                .build();
        when(dynamoDb.query(any(QueryRequest.class))).thenReturn(queryResponse);

        // Mock batch get response - should be called once with 100 items
        List<Map<String, AttributeValue>> batchMetadata = new ArrayList<>();
        for (int i = 0; i < totalReviews; i++) {
            String accountId = "account_" + i;
            Map<String, AttributeValue> metadata = new HashMap<>();
            metadata.put(ACCOUNT_ID_KEY, AttributeValue.builder().s(accountId).build());
            metadata.put(USERNAME_KEY, AttributeValue.builder().s("user_" + i).build());
            batchMetadata.add(metadata);
        }

        BatchGetItemResponse batchResponse = BatchGetItemResponse.builder()
                .responses(Map.of(USER_METADATA_TABLE_NAME, batchMetadata))
                .build();

        when(dynamoDb.batchGetItem(any(BatchGetItemRequest.class))).thenReturn(batchResponse);

        final GetAllReviewsOutput actualOutput = reviewDAL.getAllReviewsByRestaurantId(TEST_RESTAURANT_ID);

        assertNotNull(actualOutput);
        assertEquals(totalReviews, actualOutput.getReviews().size());

        // Verify batchGetItem was called exactly once
        verify(dynamoDb, times(1)).batchGetItem(any(BatchGetItemRequest.class));
    }

    @Test
    public void testGetAllReviewsByRestaurantId_withDuplicateAccounts_deduplicatesBeforeBatch() throws Exception {
        // Create 150 reviews but only 50 unique account IDs (3 reviews per account)
        int totalReviews = 150;
        int uniqueAccounts = 50;
        List<Map<String, AttributeValue>> reviewItems = new ArrayList<>();

        for (int i = 0; i < totalReviews; i++) {
            String accountId = "account_" + (i % uniqueAccounts);  // Reuse account IDs
            Map<String, AttributeValue> reviewItem = new HashMap<>();
            reviewItem.put("restaurantId", AttributeValue.builder().s(TEST_RESTAURANT_ID).build());
            reviewItem.put("identifier", AttributeValue.builder().s("REVIEW:" + accountId + "_" + i).build());
            reviewItem.put("score", AttributeValue.builder().n("5.0").build());
            reviewItem.put("title", AttributeValue.builder().s("Title " + i).build());
            reviewItem.put("body", AttributeValue.builder().s("Body " + i).build());
            reviewItem.put(ACCOUNT_ID_KEY, AttributeValue.builder().s(accountId).build());
            reviewItem.put(ISO_DATE_TIME, AttributeValue.builder().s(TEST_ISO_DATE_TIME_1).build());
            reviewItems.add(reviewItem);
        }

        QueryResponse queryResponse = QueryResponse.builder()
                .items(reviewItems)
                .build();
        when(dynamoDb.query(any(QueryRequest.class))).thenReturn(queryResponse);

        // Mock batch get response - should only need 50 unique account lookups
        List<Map<String, AttributeValue>> batchMetadata = new ArrayList<>();
        for (int i = 0; i < uniqueAccounts; i++) {
            String accountId = "account_" + i;
            Map<String, AttributeValue> metadata = new HashMap<>();
            metadata.put(ACCOUNT_ID_KEY, AttributeValue.builder().s(accountId).build());
            metadata.put(USERNAME_KEY, AttributeValue.builder().s("user_" + i).build());
            batchMetadata.add(metadata);
        }

        BatchGetItemResponse batchResponse = BatchGetItemResponse.builder()
                .responses(Map.of(USER_METADATA_TABLE_NAME, batchMetadata))
                .build();

        when(dynamoDb.batchGetItem(any(BatchGetItemRequest.class))).thenReturn(batchResponse);

        final GetAllReviewsOutput actualOutput = reviewDAL.getAllReviewsByRestaurantId(TEST_RESTAURANT_ID);

        assertNotNull(actualOutput);
        assertEquals(totalReviews, actualOutput.getReviews().size());

        // Verify batchGetItem was called exactly once (50 unique accounts < 100 batch limit)
        ArgumentCaptor<BatchGetItemRequest> batchCaptor = ArgumentCaptor.forClass(BatchGetItemRequest.class);
        verify(dynamoDb, times(1)).batchGetItem(batchCaptor.capture());

        // Verify only 50 unique keys were requested
        KeysAndAttributes keys = batchCaptor.getValue().requestItems().get(USER_METADATA_TABLE_NAME);
        assertEquals(uniqueAccounts, keys.keys().size());
    }


    /**
     * Helper method to convert a Review to DynamoDB attribute map
     */
    private Map<String, AttributeValue> reviewToAttributeMap(Review review) {
        Map<String, AttributeValue> map = new HashMap<>();
        map.put("restaurantId", AttributeValue.builder().s(review.getRestaurantId()).build());
        map.put("identifier", AttributeValue.builder().s("REVIEW:" + review.getAccountId()).build());
        map.put("score", AttributeValue.builder().n(review.getScore().toString()).build());
        map.put("title", AttributeValue.builder().s(review.getTitle()).build());
        map.put("body", AttributeValue.builder().s(review.getBody()).build());
        map.put(ACCOUNT_ID_KEY, AttributeValue.builder().s(review.getAccountId()).build());
        if (review.getIsoDateTime() != null) {
            map.put(ISO_DATE_TIME, AttributeValue.builder().s(review.getIsoDateTime()).build());
        }
        return map;
    }

    /**
     * Helper to mock user metadata lookups
     */
    private void mockUserMetadataLookup() {
        // Mock batch get for user metadata
        BatchGetItemResponse batchResponse = BatchGetItemResponse.builder()
                .responses(Map.of())  // Empty map - no user metadata found
                .build();
        when(dynamoDb.batchGetItem(any(BatchGetItemRequest.class))).thenReturn(batchResponse);
    }
}