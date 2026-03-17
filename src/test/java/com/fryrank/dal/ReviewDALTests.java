
package com.fryrank.dal;

import com.fryrank.model.GetAllReviewsOutput;
import com.fryrank.model.Review;
import com.fryrank.model.DeleteReviewRequest;
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
import software.amazon.awssdk.services.dynamodb.model.DeleteItemRequest;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.GetItemResponse;
import software.amazon.awssdk.services.dynamodb.model.KeysAndAttributes;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;
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
import static com.fryrank.Constants.AVERAGE_SCORE_KEY;
import static com.fryrank.Constants.BODY_KEY;
import static com.fryrank.Constants.IDENTIFIER_KEY;
import static com.fryrank.Constants.ISO_DATE_TIME;
import static com.fryrank.Constants.ISO_DATE_TIME_KEY;
import static com.fryrank.Constants.RESTAURANT_ID_KEY;
import static com.fryrank.Constants.REVIEW_COUNT_KEY;
import static com.fryrank.Constants.REVIEW_IDENTIFIER_PREFIX;
import static com.fryrank.Constants.SCORE_KEY;
import static com.fryrank.Constants.TITLE_KEY;
import static com.fryrank.Constants.TOTAL_SCORE_KEY;
import static com.fryrank.Constants.USERNAME_KEY;
import static com.fryrank.Constants.USER_METADATA_TABLE_NAME;
import static com.fryrank.TestConstants.TEST_ACCOUNT_ID;
import static com.fryrank.TestConstants.TEST_ISO_DATE_TIME_1;
import static com.fryrank.TestConstants.TEST_RESTAURANT_ID;
import static com.fryrank.TestConstants.TEST_REVIEWS;
import static com.fryrank.TestConstants.TEST_REVIEW_1;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
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
        assertTrue(reviewItem.get(IDENTIFIER_KEY).s().startsWith(REVIEW_IDENTIFIER_PREFIX));
        assertEquals(TEST_REVIEW_1.getScore().toString(), reviewItem.get(SCORE_KEY).n());

        // Second item should be the aggregate with condition expression for new aggregate
        var aggregatePut = capturedRequest.transactItems().get(1).put();
        Map<String, AttributeValue> aggregateItem = aggregatePut.item();
        assertEquals("AGGREGATE", aggregateItem.get(IDENTIFIER_KEY).s());
        assertEquals("1", aggregateItem.get(REVIEW_COUNT_KEY).n());
        assertEquals(TEST_REVIEW_1.getScore().toString(), aggregateItem.get(TOTAL_SCORE_KEY).n());
        assertEquals(TEST_REVIEW_1.getScore().toString(), aggregateItem.get(AVERAGE_SCORE_KEY).n());

        // Verify condition expression for new aggregate
        assertEquals("attribute_not_exists(#pk)", aggregatePut.conditionExpression());
    }

    @Test
    public void testAddNewReview_withExistingAggregate() throws Exception {
        // Existing aggregate: totalScore=50, reviewCount=5, averageScore=10.0
        double existingTotalScore = 50.0;
        int existingReviewCount = 5;

        Map<String, AttributeValue> existingAggregate = new HashMap<>();
        existingAggregate.put(RESTAURANT_ID_KEY, AttributeValue.builder().s(TEST_REVIEW_1.getRestaurantId()).build());
        existingAggregate.put(IDENTIFIER_KEY, AttributeValue.builder().s("AGGREGATE").build());
        existingAggregate.put(ISO_DATE_TIME_KEY, AttributeValue.builder().s("AGGREGATE").build());
        existingAggregate.put(TOTAL_SCORE_KEY, AttributeValue.builder().n(String.valueOf(existingTotalScore)).build());
        existingAggregate.put(REVIEW_COUNT_KEY, AttributeValue.builder().n(String.valueOf(existingReviewCount)).build());
        existingAggregate.put(AVERAGE_SCORE_KEY, AttributeValue.builder().n("10.0").build());

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

        assertEquals(String.valueOf(expectedNewReviewCount), aggregateItem.get(REVIEW_COUNT_KEY).n());
        assertEquals(String.valueOf(expectedNewTotalScore), aggregateItem.get(TOTAL_SCORE_KEY).n());
        assertEquals(String.valueOf(expectedNewAverageScore), aggregateItem.get(AVERAGE_SCORE_KEY).n());

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
        existingAggregate.put(RESTAURANT_ID_KEY, AttributeValue.builder().s(TEST_REVIEW_1.getRestaurantId()).build());
        existingAggregate.put(IDENTIFIER_KEY, AttributeValue.builder().s("AGGREGATE").build());
        existingAggregate.put(ISO_DATE_TIME_KEY, AttributeValue.builder().s("AGGREGATE").build());
        existingAggregate.put(TOTAL_SCORE_KEY, AttributeValue.builder().n("50.0").build());
        existingAggregate.put(REVIEW_COUNT_KEY, AttributeValue.builder().n("5").build());
        existingAggregate.put(AVERAGE_SCORE_KEY, AttributeValue.builder().n("10.0").build());

        // Updated aggregate (simulating concurrent update)
        Map<String, AttributeValue> updatedAggregate = new HashMap<>();
        updatedAggregate.put(RESTAURANT_ID_KEY, AttributeValue.builder().s(TEST_REVIEW_1.getRestaurantId()).build());
        updatedAggregate.put(IDENTIFIER_KEY, AttributeValue.builder().s("AGGREGATE").build());
        updatedAggregate.put(ISO_DATE_TIME_KEY, AttributeValue.builder().s("AGGREGATE").build());
        updatedAggregate.put(TOTAL_SCORE_KEY, AttributeValue.builder().n("58.0").build());
        updatedAggregate.put(REVIEW_COUNT_KEY, AttributeValue.builder().n("6").build());
        updatedAggregate.put(AVERAGE_SCORE_KEY, AttributeValue.builder().n("9.666666666666666").build());

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
        existingAggregate.put(RESTAURANT_ID_KEY, AttributeValue.builder().s(TEST_REVIEW_1.getRestaurantId()).build());
        existingAggregate.put(IDENTIFIER_KEY, AttributeValue.builder().s("AGGREGATE").build());
        existingAggregate.put(ISO_DATE_TIME_KEY, AttributeValue.builder().s("AGGREGATE").build());
        existingAggregate.put(TOTAL_SCORE_KEY, AttributeValue.builder().n("50.0").build());
        existingAggregate.put(REVIEW_COUNT_KEY, AttributeValue.builder().n("5").build());
        existingAggregate.put(AVERAGE_SCORE_KEY, AttributeValue.builder().n("10.0").build());

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

        assertTrue(exception.getMessage().contains("Failed to add/delete review"));
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
        existingAggregate.put(RESTAURANT_ID_KEY, AttributeValue.builder().s(TEST_REVIEW_1.getRestaurantId()).build());
        existingAggregate.put(IDENTIFIER_KEY, AttributeValue.builder().s("AGGREGATE").build());
        existingAggregate.put(ISO_DATE_TIME_KEY, AttributeValue.builder().s("AGGREGATE").build());
        existingAggregate.put(TOTAL_SCORE_KEY, AttributeValue.builder().n("8.0").build());
        existingAggregate.put(REVIEW_COUNT_KEY, AttributeValue.builder().n("1").build());
        existingAggregate.put(AVERAGE_SCORE_KEY, AttributeValue.builder().n("8.0").build());
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
        assertEquals("2", secondAggregatePut.item().get(REVIEW_COUNT_KEY).n());
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
            reviewItem.put(RESTAURANT_ID_KEY, AttributeValue.builder().s(TEST_RESTAURANT_ID).build());
            reviewItem.put(IDENTIFIER_KEY, AttributeValue.builder().s(REVIEW_IDENTIFIER_PREFIX + accountId).build());
            reviewItem.put(SCORE_KEY, AttributeValue.builder().n("5.0").build());
            reviewItem.put(TITLE_KEY, AttributeValue.builder().s("Title " + i).build());
            reviewItem.put(BODY_KEY, AttributeValue.builder().s("Body " + i).build());
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
            reviewItem.put(RESTAURANT_ID_KEY, AttributeValue.builder().s(TEST_RESTAURANT_ID).build());
            reviewItem.put(IDENTIFIER_KEY, AttributeValue.builder().s(REVIEW_IDENTIFIER_PREFIX + accountId).build());
            reviewItem.put(SCORE_KEY, AttributeValue.builder().n("5.0").build());
            reviewItem.put(TITLE_KEY, AttributeValue.builder().s("Title " + i).build());
            reviewItem.put(BODY_KEY, AttributeValue.builder().s("Body " + i).build());
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
            reviewItem.put(RESTAURANT_ID_KEY, AttributeValue.builder().s(TEST_RESTAURANT_ID).build());
            reviewItem.put(IDENTIFIER_KEY, AttributeValue.builder().s(REVIEW_IDENTIFIER_PREFIX + accountId + "_" + i).build());
            reviewItem.put(SCORE_KEY, AttributeValue.builder().n("5.0").build());
            reviewItem.put(TITLE_KEY, AttributeValue.builder().s("Title " + i).build());
            reviewItem.put(BODY_KEY, AttributeValue.builder().s("Body " + i).build());
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

    // ==================== Delete User Review Tests ====================

    @Test
    public void testDeleteUserReview_happyPath_updatesAggregate() throws Exception {
        // Review ID format is "restaurantId:accountId"
        String restaurantId = "res123";
        String accountId = "acc456";
        String reviewId = restaurantId + ":" + accountId;
        Double reviewScore = 8.0;

        DeleteReviewRequest deleteRequest = new DeleteReviewRequest(reviewId);

        // Mock getting the existing review
        Map<String, AttributeValue> existingReview = new HashMap<>();
        existingReview.put(RESTAURANT_ID_KEY, AttributeValue.builder().s(restaurantId).build());
        existingReview.put(IDENTIFIER_KEY, AttributeValue.builder().s(REVIEW_IDENTIFIER_PREFIX + accountId).build());
        existingReview.put(SCORE_KEY, AttributeValue.builder().n(reviewScore.toString()).build());
        existingReview.put(TITLE_KEY, AttributeValue.builder().s("Test Title").build());
        existingReview.put(BODY_KEY, AttributeValue.builder().s("Test Body").build());

        // Existing aggregate: totalScore=40, reviewCount=5, averageScore=8.0
        Map<String, AttributeValue> existingAggregate = new HashMap<>();
        existingAggregate.put(RESTAURANT_ID_KEY, AttributeValue.builder().s(restaurantId).build());
        existingAggregate.put(IDENTIFIER_KEY, AttributeValue.builder().s("AGGREGATE").build());
        existingAggregate.put(ISO_DATE_TIME_KEY, AttributeValue.builder().s("AGGREGATE").build());
        existingAggregate.put(TOTAL_SCORE_KEY, AttributeValue.builder().n("40.0").build());
        existingAggregate.put(REVIEW_COUNT_KEY, AttributeValue.builder().n("5").build());
        existingAggregate.put(AVERAGE_SCORE_KEY, AttributeValue.builder().n("8.0").build());

        GetItemResponse reviewResponse = GetItemResponse.builder().item(existingReview).build();
        GetItemResponse aggregateResponse = GetItemResponse.builder().item(existingAggregate).build();

        // First call gets the review, second call gets the aggregate
        when(dynamoDb.getItem(any(GetItemRequest.class)))
                .thenReturn(reviewResponse)
                .thenReturn(aggregateResponse);

        when(dynamoDb.transactWriteItems(any(TransactWriteItemsRequest.class)))
                .thenReturn(TransactWriteItemsResponse.builder().build());

        boolean result = reviewDAL.deleteUserReview(deleteRequest);

        assertTrue(result);

        // Verify transactWriteItems was called
        ArgumentCaptor<TransactWriteItemsRequest> transactCaptor = ArgumentCaptor.forClass(TransactWriteItemsRequest.class);
        verify(dynamoDb, times(1)).transactWriteItems(transactCaptor.capture());

        TransactWriteItemsRequest capturedRequest = transactCaptor.getValue();
        assertEquals(2, capturedRequest.transactItems().size());

        // First item should be aggregate update (Put)
        var aggregatePut = capturedRequest.transactItems().get(0).put();
        assertNotNull(aggregatePut);
        Map<String, AttributeValue> aggregateItem = aggregatePut.item();
        assertEquals("4", aggregateItem.get(REVIEW_COUNT_KEY).n()); // 5 - 1
        assertEquals("32.0", aggregateItem.get(TOTAL_SCORE_KEY).n()); // 40 - 8

        // Second item should be review delete
        var reviewDelete = capturedRequest.transactItems().get(1).delete();
        assertNotNull(reviewDelete);
    }

    @Test
    public void testDeleteUserReview_lastReview_deletesAggregate() throws Exception {
        String restaurantId = "res123";
        String accountId = "acc456";
        String reviewId = restaurantId + ":" + accountId;
        Double reviewScore = 8.0;

        DeleteReviewRequest deleteRequest = new DeleteReviewRequest(reviewId);

        // Mock getting the existing review
        Map<String, AttributeValue> existingReview = new HashMap<>();
        existingReview.put(RESTAURANT_ID_KEY, AttributeValue.builder().s(restaurantId).build());
        existingReview.put(IDENTIFIER_KEY, AttributeValue.builder().s(REVIEW_IDENTIFIER_PREFIX + accountId).build());
        existingReview.put(SCORE_KEY, AttributeValue.builder().n(reviewScore.toString()).build());

        // Existing aggregate with only 1 review
        Map<String, AttributeValue> existingAggregate = new HashMap<>();
        existingAggregate.put(RESTAURANT_ID_KEY, AttributeValue.builder().s(restaurantId).build());
        existingAggregate.put(IDENTIFIER_KEY, AttributeValue.builder().s("AGGREGATE").build());
        existingAggregate.put(ISO_DATE_TIME_KEY, AttributeValue.builder().s("AGGREGATE").build());
        existingAggregate.put(TOTAL_SCORE_KEY, AttributeValue.builder().n("8.0").build());
        existingAggregate.put(REVIEW_COUNT_KEY, AttributeValue.builder().n("1").build());
        existingAggregate.put(AVERAGE_SCORE_KEY, AttributeValue.builder().n("8.0").build());

        GetItemResponse reviewResponse = GetItemResponse.builder().item(existingReview).build();
        GetItemResponse aggregateResponse = GetItemResponse.builder().item(existingAggregate).build();

        when(dynamoDb.getItem(any(GetItemRequest.class)))
                .thenReturn(reviewResponse)
                .thenReturn(aggregateResponse);

        when(dynamoDb.transactWriteItems(any(TransactWriteItemsRequest.class)))
                .thenReturn(TransactWriteItemsResponse.builder().build());

        boolean result = reviewDAL.deleteUserReview(deleteRequest);

        assertTrue(result);

        ArgumentCaptor<TransactWriteItemsRequest> transactCaptor = ArgumentCaptor.forClass(TransactWriteItemsRequest.class);
        verify(dynamoDb, times(1)).transactWriteItems(transactCaptor.capture());

        TransactWriteItemsRequest capturedRequest = transactCaptor.getValue();
        assertEquals(2, capturedRequest.transactItems().size());

        // First item should be aggregate DELETE (not Put)
        var aggregateDelete = capturedRequest.transactItems().get(0).delete();
        assertNotNull(aggregateDelete);
        assertEquals("#reviewCount = :expectedCount", aggregateDelete.conditionExpression());

        // Second item should be review delete
        var reviewDelete = capturedRequest.transactItems().get(1).delete();
        assertNotNull(reviewDelete);
    }

    @Test
    public void testDeleteUserReview_reviewDoesNotExist_returnsFalse() throws Exception {
        String restaurantId = "res123";
        String accountId = "acc456";
        String reviewId = restaurantId + ":" + accountId;

        DeleteReviewRequest deleteRequest = new DeleteReviewRequest(reviewId);

        // Mock getting empty review (doesn't exist)
        GetItemResponse emptyResponse = GetItemResponse.builder().item(Map.of()).build();
        when(dynamoDb.getItem(any(GetItemRequest.class))).thenReturn(emptyResponse);

        boolean result = reviewDAL.deleteUserReview(deleteRequest);

        assertFalse(result);

        // Verify no transaction was attempted
        verify(dynamoDb, times(0)).transactWriteItems(any(TransactWriteItemsRequest.class));
    }

    @Test
    public void testDeleteUserReview_aggregateDoesNotExist_deletesReviewOnly() throws Exception {
        String restaurantId = "res123";
        String accountId = "acc456";
        String reviewId = restaurantId + ":" + accountId;
        Double reviewScore = 8.0;

        DeleteReviewRequest deleteRequest = new DeleteReviewRequest(reviewId);

        // Mock getting the existing review
        Map<String, AttributeValue> existingReview = new HashMap<>();
        existingReview.put(RESTAURANT_ID_KEY, AttributeValue.builder().s(restaurantId).build());
        existingReview.put(IDENTIFIER_KEY, AttributeValue.builder().s(REVIEW_IDENTIFIER_PREFIX + accountId).build());
        existingReview.put(SCORE_KEY, AttributeValue.builder().n(reviewScore.toString()).build());

        GetItemResponse reviewResponse = GetItemResponse.builder().item(existingReview).build();
        GetItemResponse emptyAggregateResponse = GetItemResponse.builder().item(Map.of()).build();

        when(dynamoDb.getItem(any(GetItemRequest.class)))
                .thenReturn(reviewResponse)
                .thenReturn(emptyAggregateResponse);

        when(dynamoDb.transactWriteItems(any(TransactWriteItemsRequest.class)))
                .thenReturn(TransactWriteItemsResponse.builder().build());

        boolean result = reviewDAL.deleteUserReview(deleteRequest);

        assertTrue(result);

        // Verify transaction was called with only the review delete (no aggregate update)
        ArgumentCaptor<TransactWriteItemsRequest> transactCaptor = ArgumentCaptor.forClass(TransactWriteItemsRequest.class);
        verify(dynamoDb, times(1)).transactWriteItems(transactCaptor.capture());

        TransactWriteItemsRequest capturedRequest = transactCaptor.getValue();
        assertEquals(1, capturedRequest.transactItems().size());

        // Should only be review delete
        var reviewDelete = capturedRequest.transactItems().get(0).delete();
        assertNotNull(reviewDelete);
    }

    @Test
    public void testDeleteUserReview_nullRequest() {
        assertThrows(NullPointerException.class, () -> reviewDAL.deleteUserReview(null));
    }

    @Test
    public void testDeleteUserReview_transactionConflict_retriesSuccessfully() throws Exception {
        String restaurantId = "res123";
        String accountId = "acc456";
        String reviewId = restaurantId + ":" + accountId;
        Double reviewScore = 8.0;

        DeleteReviewRequest deleteRequest = new DeleteReviewRequest(reviewId);

        // Mock getting the existing review
        Map<String, AttributeValue> existingReview = new HashMap<>();
        existingReview.put(RESTAURANT_ID_KEY, AttributeValue.builder().s(restaurantId).build());
        existingReview.put(IDENTIFIER_KEY, AttributeValue.builder().s(REVIEW_IDENTIFIER_PREFIX + accountId).build());
        existingReview.put(SCORE_KEY, AttributeValue.builder().n(reviewScore.toString()).build());

        // First aggregate state
        Map<String, AttributeValue> existingAggregate = new HashMap<>();
        existingAggregate.put(RESTAURANT_ID_KEY, AttributeValue.builder().s(restaurantId).build());
        existingAggregate.put(IDENTIFIER_KEY, AttributeValue.builder().s("AGGREGATE").build());
        existingAggregate.put(ISO_DATE_TIME_KEY, AttributeValue.builder().s("AGGREGATE").build());
        existingAggregate.put(TOTAL_SCORE_KEY, AttributeValue.builder().n("40.0").build());
        existingAggregate.put(REVIEW_COUNT_KEY, AttributeValue.builder().n("5").build());
        existingAggregate.put(AVERAGE_SCORE_KEY, AttributeValue.builder().n("8.0").build());

        // Updated aggregate (simulating concurrent update)
        Map<String, AttributeValue> updatedAggregate = new HashMap<>();
        updatedAggregate.put(RESTAURANT_ID_KEY, AttributeValue.builder().s(restaurantId).build());
        updatedAggregate.put(IDENTIFIER_KEY, AttributeValue.builder().s("AGGREGATE").build());
        updatedAggregate.put(ISO_DATE_TIME_KEY, AttributeValue.builder().s("AGGREGATE").build());
        updatedAggregate.put(TOTAL_SCORE_KEY, AttributeValue.builder().n("48.0").build());
        updatedAggregate.put(REVIEW_COUNT_KEY, AttributeValue.builder().n("6").build());
        updatedAggregate.put(AVERAGE_SCORE_KEY, AttributeValue.builder().n("8.0").build());

        GetItemResponse reviewResponse = GetItemResponse.builder().item(existingReview).build();
        GetItemResponse aggregateResponse1 = GetItemResponse.builder().item(existingAggregate).build();
        GetItemResponse aggregateResponse2 = GetItemResponse.builder().item(updatedAggregate).build();

        // First call gets review, second gets aggregate (first attempt), third gets aggregate (retry)
        when(dynamoDb.getItem(any(GetItemRequest.class)))
                .thenReturn(reviewResponse)
                .thenReturn(aggregateResponse1)
                .thenReturn(aggregateResponse2);

        when(dynamoDb.transactWriteItems(any(TransactWriteItemsRequest.class)))
                .thenThrow(TransactionCanceledException.builder()
                        .message("Transaction cancelled")
                        .cancellationReasons(
                                CancellationReason.builder().code("ConditionalCheckFailed").build(),
                                CancellationReason.builder().code("None").build()
                        )
                        .build())
                .thenReturn(TransactWriteItemsResponse.builder().build());

        boolean result = reviewDAL.deleteUserReview(deleteRequest);

        assertTrue(result);

        // Verify getItem was called 3 times (1 for review + 2 for aggregate attempts)
        verify(dynamoDb, times(3)).getItem(any(GetItemRequest.class));

        // Verify transactWriteItems was called twice (failed + successful)
        verify(dynamoDb, times(2)).transactWriteItems(any(TransactWriteItemsRequest.class));
    }

    @Test
    public void testDeleteUserReview_transactionConflict_exhaustsRetries() throws Exception {
        String restaurantId = "res123";
        String accountId = "acc456";
        String reviewId = restaurantId + ":" + accountId;
        Double reviewScore = 8.0;

        DeleteReviewRequest deleteRequest = new DeleteReviewRequest(reviewId);

        // Mock getting the existing review
        Map<String, AttributeValue> existingReview = new HashMap<>();
        existingReview.put(RESTAURANT_ID_KEY, AttributeValue.builder().s(restaurantId).build());
        existingReview.put(IDENTIFIER_KEY, AttributeValue.builder().s(REVIEW_IDENTIFIER_PREFIX + accountId).build());
        existingReview.put(SCORE_KEY, AttributeValue.builder().n(reviewScore.toString()).build());

        Map<String, AttributeValue> existingAggregate = new HashMap<>();
        existingAggregate.put(RESTAURANT_ID_KEY, AttributeValue.builder().s(restaurantId).build());
        existingAggregate.put(IDENTIFIER_KEY, AttributeValue.builder().s("AGGREGATE").build());
        existingAggregate.put(ISO_DATE_TIME_KEY, AttributeValue.builder().s("AGGREGATE").build());
        existingAggregate.put(TOTAL_SCORE_KEY, AttributeValue.builder().n("40.0").build());
        existingAggregate.put(REVIEW_COUNT_KEY, AttributeValue.builder().n("5").build());
        existingAggregate.put(AVERAGE_SCORE_KEY, AttributeValue.builder().n("8.0").build());

        GetItemResponse reviewResponse = GetItemResponse.builder().item(existingReview).build();
        GetItemResponse aggregateResponse = GetItemResponse.builder().item(existingAggregate).build();

        when(dynamoDb.getItem(any(GetItemRequest.class)))
                .thenReturn(reviewResponse)
                .thenReturn(aggregateResponse);

        // All transaction attempts fail
        when(dynamoDb.transactWriteItems(any(TransactWriteItemsRequest.class)))
                .thenThrow(TransactionCanceledException.builder()
                        .message("Transaction cancelled")
                        .cancellationReasons(
                                CancellationReason.builder().code("ConditionalCheckFailed").build(),
                                CancellationReason.builder().code("None").build()
                        )
                        .build());

        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> reviewDAL.deleteUserReview(deleteRequest));

        assertTrue(exception.getMessage().contains("Failed to add/delete review"));
        assertTrue(exception.getMessage().contains("concurrent modifications"));

        // Verify retries happened (MAX_AGGREGATE_UPDATE_RETRIES = 3)
        verify(dynamoDb, times(3)).transactWriteItems(any(TransactWriteItemsRequest.class));
    }

    @Test
    public void testDeleteUserReview_transactionAtomicity_bothItemsInSameTransaction() throws Exception {
        String restaurantId = "res123";
        String accountId = "acc456";
        String reviewId = restaurantId + ":" + accountId;
        Double reviewScore = 8.0;

        DeleteReviewRequest deleteRequest = new DeleteReviewRequest(reviewId);

        Map<String, AttributeValue> existingReview = new HashMap<>();
        existingReview.put(RESTAURANT_ID_KEY, AttributeValue.builder().s(restaurantId).build());
        existingReview.put(IDENTIFIER_KEY, AttributeValue.builder().s(REVIEW_IDENTIFIER_PREFIX + accountId).build());
        existingReview.put(SCORE_KEY, AttributeValue.builder().n(reviewScore.toString()).build());

        Map<String, AttributeValue> existingAggregate = new HashMap<>();
        existingAggregate.put(RESTAURANT_ID_KEY, AttributeValue.builder().s(restaurantId).build());
        existingAggregate.put(IDENTIFIER_KEY, AttributeValue.builder().s("AGGREGATE").build());
        existingAggregate.put(ISO_DATE_TIME_KEY, AttributeValue.builder().s("AGGREGATE").build());
        existingAggregate.put(TOTAL_SCORE_KEY, AttributeValue.builder().n("40.0").build());
        existingAggregate.put(REVIEW_COUNT_KEY, AttributeValue.builder().n("5").build());
        existingAggregate.put(AVERAGE_SCORE_KEY, AttributeValue.builder().n("8.0").build());

        GetItemResponse reviewResponse = GetItemResponse.builder().item(existingReview).build();
        GetItemResponse aggregateResponse = GetItemResponse.builder().item(existingAggregate).build();

        when(dynamoDb.getItem(any(GetItemRequest.class)))
                .thenReturn(reviewResponse)
                .thenReturn(aggregateResponse);

        when(dynamoDb.transactWriteItems(any(TransactWriteItemsRequest.class)))
                .thenReturn(TransactWriteItemsResponse.builder().build());

        reviewDAL.deleteUserReview(deleteRequest);

        // Verify that both aggregate update and review delete are in the same transaction
        ArgumentCaptor<TransactWriteItemsRequest> transactCaptor = ArgumentCaptor.forClass(TransactWriteItemsRequest.class);
        verify(dynamoDb, times(1)).transactWriteItems(transactCaptor.capture());

        TransactWriteItemsRequest capturedRequest = transactCaptor.getValue();
        assertEquals(2, capturedRequest.transactItems().size());

        // Verify no separate deleteItem calls were made
        verify(dynamoDb, times(0)).deleteItem(any(DeleteItemRequest.class));
    }

    @Test
    public void testMapItemToReview_happyPath_allFieldsPopulated() throws Exception {
        String accountId = "acc123";
        String restaurantId = "res456";
        String identifier = REVIEW_IDENTIFIER_PREFIX + accountId;
        Double score = 7.5;
        String title = "Great food";
        String body = "Really enjoyed the meal";
        String isoDateTime = "2024-03-15T10:30:00Z";
        String username = "testuser";

        Map<String, AttributeValue> reviewItem = new HashMap<>();
        reviewItem.put(RESTAURANT_ID_KEY, AttributeValue.builder().s(restaurantId).build());
        reviewItem.put(IDENTIFIER_KEY, AttributeValue.builder().s(identifier).build());
        reviewItem.put(SCORE_KEY, AttributeValue.builder().n(score.toString()).build());
        reviewItem.put(TITLE_KEY, AttributeValue.builder().s(title).build());
        reviewItem.put(BODY_KEY, AttributeValue.builder().s(body).build());
        reviewItem.put(ACCOUNT_ID_KEY, AttributeValue.builder().s(accountId).build());
        reviewItem.put(ISO_DATE_TIME, AttributeValue.builder().s(isoDateTime).build());

        QueryResponse queryResponse = QueryResponse.builder()
                .items(List.of(reviewItem))
                .build();
        when(dynamoDb.query(any(QueryRequest.class))).thenReturn(queryResponse);

        // Mock user metadata lookup
        List<Map<String, AttributeValue>> metadataItems = new ArrayList<>();
        Map<String, AttributeValue> metadata = new HashMap<>();
        metadata.put(ACCOUNT_ID_KEY, AttributeValue.builder().s(accountId).build());
        metadata.put(USERNAME_KEY, AttributeValue.builder().s(username).build());
        metadataItems.add(metadata);

        BatchGetItemResponse batchResponse = BatchGetItemResponse.builder()
                .responses(Map.of(USER_METADATA_TABLE_NAME, metadataItems))
                .build();
        when(dynamoDb.batchGetItem(any(BatchGetItemRequest.class))).thenReturn(batchResponse);

        final GetAllReviewsOutput actualOutput = reviewDAL.getAllReviewsByRestaurantId(restaurantId);

        assertNotNull(actualOutput);
        assertEquals(1, actualOutput.getReviews().size());

        Review review = actualOutput.getReviews().get(0);
        assertEquals(restaurantId + ":" + accountId, review.getReviewId());
        assertEquals(restaurantId, review.getRestaurantId());
        assertEquals(score, review.getScore());
        assertEquals(title, review.getTitle());
        assertEquals(body, review.getBody());
        assertEquals(isoDateTime, review.getIsoDateTime());
        assertEquals(accountId, review.getAccountId());
        assertNotNull(review.getUserMetadata());
        assertEquals(accountId, review.getUserMetadata().getAccountId());
        assertEquals(username, review.getUserMetadata().getUsername());
    }

    @Test
    public void testMapItemToReview_withNullAccountId() throws Exception {
        String restaurantId = "res456";
        String identifier = "REVIEW:someuser";
        Double score = 7.5;
        String title = "Great food";
        String body = "Really enjoyed the meal";

        Map<String, AttributeValue> reviewItem = new HashMap<>();
        reviewItem.put(RESTAURANT_ID_KEY, AttributeValue.builder().s(restaurantId).build());
        reviewItem.put(IDENTIFIER_KEY, AttributeValue.builder().s(identifier).build());
        reviewItem.put(SCORE_KEY, AttributeValue.builder().n(score.toString()).build());
        reviewItem.put(TITLE_KEY, AttributeValue.builder().s(title).build());
        reviewItem.put(BODY_KEY, AttributeValue.builder().s(body).build());
        // No accountId attribute

        QueryResponse queryResponse = QueryResponse.builder()
                .items(List.of(reviewItem))
                .build();
        when(dynamoDb.query(any(QueryRequest.class))).thenReturn(queryResponse);

        final GetAllReviewsOutput actualOutput = reviewDAL.getAllReviewsByRestaurantId(restaurantId);

        assertNotNull(actualOutput);
        assertEquals(1, actualOutput.getReviews().size());

        Review review = actualOutput.getReviews().get(0);
        assertNull(review.getAccountId());
    }

    @Test
    public void testMapItemToReview_reviewIdGeneration_removesPrefix() throws Exception {
        String accountId = "user123";
        String restaurantId = "res456";
        String identifier = REVIEW_IDENTIFIER_PREFIX + accountId;

        Map<String, AttributeValue> reviewItem = new HashMap<>();
        reviewItem.put(RESTAURANT_ID_KEY, AttributeValue.builder().s(restaurantId).build());
        reviewItem.put(IDENTIFIER_KEY, AttributeValue.builder().s(identifier).build());
        reviewItem.put(SCORE_KEY, AttributeValue.builder().n("5.0").build());
        reviewItem.put(TITLE_KEY, AttributeValue.builder().s(TITLE_KEY).build());
        reviewItem.put(BODY_KEY, AttributeValue.builder().s(BODY_KEY).build());
        reviewItem.put(ACCOUNT_ID_KEY, AttributeValue.builder().s(accountId).build());

        QueryResponse queryResponse = QueryResponse.builder()
                .items(List.of(reviewItem))
                .build();
        when(dynamoDb.query(any(QueryRequest.class))).thenReturn(queryResponse);

        mockUserMetadataLookup();

        final GetAllReviewsOutput actualOutput = reviewDAL.getAllReviewsByRestaurantId(restaurantId);

        assertNotNull(actualOutput);
        assertEquals(1, actualOutput.getReviews().size());

        Review review = actualOutput.getReviews().get(0);
        // reviewId should be restaurantId:accountId (without REVIEW: prefix)
        assertEquals("res456:user123", review.getReviewId());
    }


    /**
     * Helper method to convert a Review to DynamoDB attribute map
     */
    private Map<String, AttributeValue> reviewToAttributeMap(Review review) {
        Map<String, AttributeValue> map = new HashMap<>();
        map.put(RESTAURANT_ID_KEY, AttributeValue.builder().s(review.getRestaurantId()).build());
        map.put(IDENTIFIER_KEY, AttributeValue.builder().s(REVIEW_IDENTIFIER_PREFIX + review.getAccountId()).build());
        map.put(SCORE_KEY, AttributeValue.builder().n(review.getScore().toString()).build());
        map.put(TITLE_KEY, AttributeValue.builder().s(review.getTitle()).build());
        map.put(BODY_KEY, AttributeValue.builder().s(review.getBody()).build());
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