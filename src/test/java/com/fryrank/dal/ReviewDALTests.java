package com.fryrank.dal;

import com.fryrank.model.GetAllReviewsOutput;
import com.fryrank.model.Review;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.FindAndReplaceOptions;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.util.List;

import static com.fryrank.TestConstants.TEST_ACCOUNT_ID;
import static com.fryrank.TestConstants.TEST_RESTAURANT_ID;
import static com.fryrank.TestConstants.TEST_REVIEWS;
import static com.fryrank.TestConstants.TEST_REVIEW_1;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class ReviewDALTests {
    @Mock
    MongoTemplate mongoTemplate;

    @InjectMocks
    ReviewDALImpl reviewDAL;

    @Test
    public void testGetAllReviewsByRestaurantId_happyPath() throws Exception {
        AggregationResults<Review> aggregationResults = new AggregationResults<>(TEST_REVIEWS, new Document());
        when(mongoTemplate.aggregate(Mockito.any(Aggregation.class), Mockito.anyString(), Mockito.eq(Review.class))).thenReturn(aggregationResults);

        final GetAllReviewsOutput expectedOutput = new GetAllReviewsOutput(TEST_REVIEWS);
        final GetAllReviewsOutput actualOutput = reviewDAL.getAllReviewsByRestaurantId(TEST_RESTAURANT_ID);
        assertEquals(actualOutput, expectedOutput);
    }

    @Test
    public void testGetAllReviewsByRestaurantId_noReviews() throws Exception {
        final List<Review> expectedReviews = List.of();

        AggregationResults<Review> aggregationResults = new AggregationResults<>(expectedReviews, new Document());
        when(mongoTemplate.aggregate(Mockito.any(Aggregation.class), Mockito.anyString(), Mockito.eq(Review.class))).thenReturn(aggregationResults);

        final GetAllReviewsOutput expectedOutput = new GetAllReviewsOutput(expectedReviews);
        final GetAllReviewsOutput actualOutput = reviewDAL.getAllReviewsByRestaurantId(TEST_RESTAURANT_ID);
        assertEquals(actualOutput, expectedOutput);
    }

    @Test
    public void testGetAllReviewsByRestaurantId_nullRestaurantId() {
        assertThrows(NullPointerException.class, () -> reviewDAL.getAllReviewsByRestaurantId(null));
    }

    @Test
    public void testGetAllReviewsByAccountId_happyPath() throws Exception {
        AggregationResults<Review> aggregationResults = new AggregationResults<>(TEST_REVIEWS, new Document());
        when(mongoTemplate.aggregate(Mockito.any(Aggregation.class), Mockito.anyString(), Mockito.eq(Review.class))).thenReturn(aggregationResults);

        final GetAllReviewsOutput expectedOutput = new GetAllReviewsOutput(TEST_REVIEWS);
        final GetAllReviewsOutput actualOutput = reviewDAL.getAllReviewsByAccountId(TEST_ACCOUNT_ID);
        assertEquals(actualOutput, expectedOutput);
    }

    @Test
    public void testGetAllReviewsByAccountId_noReviews() throws Exception {
        final List<Review> expectedReviews = List.of();

        AggregationResults<Review> aggregationResults = new AggregationResults<>(expectedReviews, new Document());
        when(mongoTemplate.aggregate(Mockito.any(Aggregation.class), Mockito.anyString(), Mockito.eq(Review.class))).thenReturn(aggregationResults);

        final GetAllReviewsOutput expectedOutput = new GetAllReviewsOutput(expectedReviews);
        final GetAllReviewsOutput actualOutput = reviewDAL.getAllReviewsByAccountId(TEST_ACCOUNT_ID);
        assertEquals(actualOutput, expectedOutput);
    }

    @Test
    public void testGetAllReviewsByAccountId_nullAccountId() {
        assertThrows(NullPointerException.class, () -> reviewDAL.getAllReviewsByAccountId(null));
    }

    @Test public void testGetTopMostRecentReviews() throws Exception {
        AggregationResults<Review> aggregationResults = new AggregationResults<>(TEST_REVIEWS, new Document());
        when(mongoTemplate.aggregate(Mockito.any(Aggregation.class), Mockito.anyString(), Mockito.eq(Review.class))).thenReturn(aggregationResults);

        final GetAllReviewsOutput expectedOutput = new GetAllReviewsOutput(TEST_REVIEWS);
        final GetAllReviewsOutput actualOutput = reviewDAL.getTopMostRecentReviews(TEST_REVIEWS.size());
        assertEquals(actualOutput, expectedOutput);
    }

    @Test
    public void testAddNewReview() throws Exception {
        when(mongoTemplate.findAndReplace(any(Query.class), eq(TEST_REVIEW_1), any(FindAndReplaceOptions.class))).thenReturn(TEST_REVIEW_1);

        final Review expectedReview = TEST_REVIEW_1;
        final Review actualReview = reviewDAL.addNewReview(expectedReview);
        assertEquals(expectedReview, actualReview);
    }

    @Test
    public void testAddNewReview_nullReview() {
        assertThrows(NullPointerException.class, () -> reviewDAL.addNewReview(null));
    }
    
}
