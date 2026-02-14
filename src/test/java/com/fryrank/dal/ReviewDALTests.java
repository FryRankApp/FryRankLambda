
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
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.GetItemResponse;
import software.amazon.awssdk.services.dynamodb.model.QueryRequest;
import software.amazon.awssdk.services.dynamodb.model.QueryResponse;
import software.amazon.awssdk.services.dynamodb.model.TransactWriteItem;
import software.amazon.awssdk.services.dynamodb.model.TransactWriteItemsRequest;
import software.amazon.awssdk.services.dynamodb.model.TransactWriteItemsResponse;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.fryrank.Constants.ACCOUNT_ID_KEY;
import static com.fryrank.Constants.ISO_DATE_TIME;
import static com.fryrank.TestConstants.TEST_ACCOUNT_ID;
import static com.fryrank.TestConstants.TEST_RESTAURANT_ID;
import static com.fryrank.TestConstants.TEST_REVIEWS;
import static com.fryrank.TestConstants.TEST_REVIEW_1;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
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

        // Capture and verify the transaction request
        ArgumentCaptor<TransactWriteItemsRequest> transactCaptor = ArgumentCaptor.forClass(TransactWriteItemsRequest.class);
        verify(dynamoDb).transactWriteItems(transactCaptor.capture());

        TransactWriteItemsRequest capturedRequest = transactCaptor.getValue();
        List<TransactWriteItem> transactItems = capturedRequest.transactItems();

        // Should have 2 items: review and aggregate
        assertEquals(2, transactItems.size());

        // Find the aggregate item (identifier = "AGGREGATE")
        Map<String, AttributeValue> aggregateItem = transactItems.stream()
                .map(item -> item.put().item())
                .filter(item -> "AGGREGATE".equals(item.get("identifier").s()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Aggregate item not found in transaction"));

        // Verify aggregate values for first review
        assertEquals("1", aggregateItem.get("reviewCount").n());
        assertEquals(TEST_REVIEW_1.getScore().toString(), aggregateItem.get("totalScore").n());
        assertEquals(TEST_REVIEW_1.getScore().toString(), aggregateItem.get("averageScore").n());
    }

    @Test
    public void testAddNewReview_withExistingAggregate() throws Exception {
        // Existing aggregate: totalScore=50, reviewCount=5, averageScore=10.0
        double existingTotalScore = 50.0;
        int existingReviewCount = 5;

        Map<String, AttributeValue> existingAggregate = new HashMap<>();
        existingAggregate.put("restaurantId", AttributeValue.builder().s(TEST_REVIEW_1.getRestaurantId()).build());
        existingAggregate.put("identifier", AttributeValue.builder().s("AGGREGATE").build());
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

        // Capture and verify the transaction request
        ArgumentCaptor<TransactWriteItemsRequest> transactCaptor = ArgumentCaptor.forClass(TransactWriteItemsRequest.class);
        verify(dynamoDb).transactWriteItems(transactCaptor.capture());

        TransactWriteItemsRequest capturedRequest = transactCaptor.getValue();
        List<TransactWriteItem> transactItems = capturedRequest.transactItems();

        // Should have 2 items: review and aggregate
        assertEquals(2, transactItems.size());

        // Find the aggregate item
        Map<String, AttributeValue> aggregateItem = transactItems.stream()
                .map(item -> item.put().item())
                .filter(item -> "AGGREGATE".equals(item.get("identifier").s()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Aggregate item not found in transaction"));

        // Verify updated aggregate values
        double expectedNewTotalScore = existingTotalScore + TEST_REVIEW_1.getScore();
        int expectedNewReviewCount = existingReviewCount + 1;
        double expectedNewAverageScore = expectedNewTotalScore / expectedNewReviewCount;

        assertEquals(String.valueOf(expectedNewReviewCount), aggregateItem.get("reviewCount").n());
        assertEquals(String.valueOf(expectedNewTotalScore), aggregateItem.get("totalScore").n());
        assertEquals(String.valueOf(expectedNewAverageScore), aggregateItem.get("averageScore").n());

        // Verify the review item has REVIEW: prefix
        Map<String, AttributeValue> reviewItem = transactItems.stream()
                .map(item -> item.put().item())
                .filter(item -> item.get("identifier").s().startsWith("REVIEW:"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Review item not found in transaction"));

        assertTrue(reviewItem.get("identifier").s().startsWith("REVIEW:"));
        assertEquals(TEST_REVIEW_1.getScore().toString(), reviewItem.get("score").n());
    }

    @Test
    public void testAddNewReview_nullReview() {
        assertThrows(NullPointerException.class, () -> reviewDAL.addNewReview(null));
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