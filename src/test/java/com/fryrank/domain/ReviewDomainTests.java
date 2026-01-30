package com.fryrank.domain;

import com.fryrank.dal.ReviewDAL;
import com.fryrank.model.*;
import com.fryrank.validator.ValidatorException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static com.fryrank.TestConstants.TEST_ACCOUNT_ID;
import static com.fryrank.TestConstants.TEST_BODY_1;
import static com.fryrank.TestConstants.TEST_ISO_DATE_TIME_1;
import static com.fryrank.TestConstants.TEST_RESTAURANT_ID;
import static com.fryrank.TestConstants.TEST_REVIEWS;
import static com.fryrank.TestConstants.TEST_REVIEW_1;
import static com.fryrank.TestConstants.TEST_REVIEW_BAD_ISO_DATETIME;
import static com.fryrank.TestConstants.TEST_REVIEW_ID_1;
import static com.fryrank.TestConstants.TEST_REVIEW_NULL_ACCOUNT_ID;
import static com.fryrank.TestConstants.TEST_REVIEW_NULL_ISO_DATETIME;
import static com.fryrank.TestConstants.TEST_TITLE_1;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.fryrank.TestConstants.TEST_RESTAURANT_ID_1;
import static com.fryrank.TestConstants.TEST_RESTAURANT_ID_2;

@ExtendWith(MockitoExtension.class)
public class ReviewDomainTests {
    @Mock
    ReviewDAL reviewDAL;

    @InjectMocks
    ReviewDomain domain;

    // /api/reviews endpoint tests
    @Test
    public void testGetAllReviewsForRestaurant() throws Exception {
        final GetAllReviewsOutput expectedOutput = new GetAllReviewsOutput(TEST_REVIEWS);
        when(reviewDAL.getAllReviewsByRestaurantId(TEST_RESTAURANT_ID)).thenReturn(expectedOutput);

        final GetAllReviewsOutput actualOutput = domain.getAllReviews(TEST_RESTAURANT_ID, null);
        assertEquals(expectedOutput, actualOutput);
    }

    @Test
    public void testGetAllReviewsForAccount() throws Exception {
        final GetAllReviewsOutput expectedOutput = new GetAllReviewsOutput(TEST_REVIEWS);
        when(reviewDAL.getAllReviewsByAccountId(TEST_ACCOUNT_ID)).thenReturn(expectedOutput);

        final GetAllReviewsOutput actualOutput = domain.getAllReviews(null, TEST_ACCOUNT_ID);
        assertEquals(expectedOutput, actualOutput);
    }

    @Test
    public void testGetAllReviewsNoParameter() throws Exception {
        assertThrows(NullPointerException.class, () -> domain.getAllReviews(null, null));
    }

    @Test
    public void testGetRecentReviews() throws Exception {
        final GetAllReviewsOutput expectedOutput = new GetAllReviewsOutput(TEST_REVIEWS);
        when(reviewDAL.getRecentReviews(TEST_REVIEWS.size())).thenReturn(expectedOutput);

        final GetAllReviewsOutput actualOutput = domain.getRecentReviews(TEST_REVIEWS.size());
        assertEquals(expectedOutput.getReviews().size(), actualOutput.getReviews().size());
    }

    // /api/reviews/aggregateInformation endpoint tests
    @Test
    public void testGetSingleRestaurantAllAggregateInformation() throws Exception {
        final Map<String, AggregateReviewInformation> expectedAggregateReviewInformation = Map.of(
            TEST_RESTAURANT_ID_1, new AggregateReviewInformation(TEST_RESTAURANT_ID_1, 5.0F)
        );
        final GetAggregateReviewInformationOutput expectedOutput = new GetAggregateReviewInformationOutput(expectedAggregateReviewInformation);
        final List<String> restaurantIds = new ArrayList<String>(){
            {
                add(TEST_RESTAURANT_ID_1);
            }
        };
        final AggregateReviewFilter aggregateReviewFilter = new AggregateReviewFilter(true);

        when(reviewDAL.getAggregateReviewInformationForRestaurants(restaurantIds, aggregateReviewFilter)).thenReturn(expectedOutput);

        final GetAggregateReviewInformationOutput actualOutput = domain.getAggregateReviewInformationForRestaurants(TEST_RESTAURANT_ID_1, true);
        assertEquals(expectedOutput, actualOutput);
    }

    @Test
    public void testGetMultipleRestaurantAllAggregateInformation() throws Exception {
        final Map<String, AggregateReviewInformation> expectedAggregateReviewInformation = Map.of(
            TEST_RESTAURANT_ID_1, new AggregateReviewInformation(TEST_RESTAURANT_ID_1, 5.0F),
                TEST_RESTAURANT_ID_2, new AggregateReviewInformation(TEST_RESTAURANT_ID_2, 7.0F)
        );
        final GetAggregateReviewInformationOutput expectedOutput = new GetAggregateReviewInformationOutput(expectedAggregateReviewInformation);
        final List<String> restaurantIds = new ArrayList<String>(){
            {
                add(TEST_RESTAURANT_ID_1);
                add(TEST_RESTAURANT_ID_2);
            }
        };
        final AggregateReviewFilter aggregateReviewFilter = new AggregateReviewFilter(true);

        when(reviewDAL.getAggregateReviewInformationForRestaurants(restaurantIds.stream().sorted().collect(Collectors.toList()), aggregateReviewFilter)).thenReturn(expectedOutput);

        final GetAggregateReviewInformationOutput inOrderOutput = domain.getAggregateReviewInformationForRestaurants(TEST_RESTAURANT_ID_1 + "," + TEST_RESTAURANT_ID_2, true);
        assertEquals(expectedOutput, inOrderOutput);

        final GetAggregateReviewInformationOutput reversedOutput = domain.getAggregateReviewInformationForRestaurants(TEST_RESTAURANT_ID_2 + "," + TEST_RESTAURANT_ID_1, true);
        assertEquals(expectedOutput, reversedOutput);
    }

    @Test
    public void testGetSingleRestaurantNoAggregateInformation() throws Exception {
        final Map<String, AggregateReviewInformation> expectedAggregateReviewInformation = Map.of(
            TEST_RESTAURANT_ID_1, new AggregateReviewInformation(TEST_RESTAURANT_ID_1, null)
        );
        final GetAggregateReviewInformationOutput expectedOutput = new GetAggregateReviewInformationOutput(expectedAggregateReviewInformation);
        final List<String> restaurantIds = new ArrayList<String>(){
            {
                add(TEST_RESTAURANT_ID_1);
            }
        };
        final AggregateReviewFilter aggregateReviewFilter = new AggregateReviewFilter(false);

        when(reviewDAL.getAggregateReviewInformationForRestaurants(restaurantIds, aggregateReviewFilter)).thenReturn(expectedOutput);

        final GetAggregateReviewInformationOutput actualOutput = domain.getAggregateReviewInformationForRestaurants(TEST_RESTAURANT_ID_1, false);
        assertEquals(expectedOutput, actualOutput);
    }

    @Test
    public void testGetMultipleRestaurantNoAggregateInformationReverseOrdering() throws Exception {
        final Map<String, AggregateReviewInformation> expectedAggregateReviewInformation = Map.of(
            TEST_RESTAURANT_ID_1, new AggregateReviewInformation(TEST_RESTAURANT_ID_1, null),
                TEST_RESTAURANT_ID_2, new AggregateReviewInformation(TEST_RESTAURANT_ID_2, null)
        );
        final GetAggregateReviewInformationOutput expectedOutput = new GetAggregateReviewInformationOutput(expectedAggregateReviewInformation);
        final List<String> restaurantIds = new ArrayList<String>(){
            {
                add(TEST_RESTAURANT_ID_1);
                add(TEST_RESTAURANT_ID_2);
            }
        };
        final AggregateReviewFilter aggregateReviewFilter = new AggregateReviewFilter(false);

        when(reviewDAL.getAggregateReviewInformationForRestaurants(restaurantIds.stream().sorted().collect(Collectors.toList()), aggregateReviewFilter)).thenReturn(expectedOutput);

        final GetAggregateReviewInformationOutput actualOutput = domain.getAggregateReviewInformationForRestaurants(TEST_RESTAURANT_ID_2 + "," + TEST_RESTAURANT_ID_1, false);
        assertEquals(expectedOutput, actualOutput);
    }

    @Test
    public void testAddNewReviewForRestaurant() throws Exception {
        when(reviewDAL.addNewReview(TEST_REVIEW_1)).thenReturn(TEST_REVIEW_1);

        Review actualReview = domain.addNewReviewForRestaurant(TEST_REVIEW_1);

        assertEquals(TEST_REVIEW_1, actualReview);
    }

    @Test
    public void testAddNewReviewForNullRestaurant() throws Exception {
        assertThrows(NullPointerException.class, () -> domain.addNewReviewForRestaurant(null));
    }

    @Test
    public void testAddNewReviewNullReviewID() throws Exception {
        Review expectedReview = Review.builder()
            .reviewId(null)
            .restaurantId(TEST_RESTAURANT_ID_1)
            .score(5.0)
            .title(TEST_TITLE_1)
            .body(TEST_BODY_1)
            .isoDateTime(TEST_ISO_DATE_TIME_1)
            .accountId(TEST_ACCOUNT_ID)
            .build();

        when(reviewDAL.addNewReview(expectedReview)).thenReturn(expectedReview);

        Review actualReview = domain.addNewReviewForRestaurant(expectedReview);

        assertEquals(expectedReview, actualReview);
    }

    @Test
    public void testAddNewReviewNullRestaurantID() throws Exception {
        assertThrows(NullPointerException.class, () -> 
            Review.builder()
                .reviewId(TEST_REVIEW_ID_1)
                .restaurantId(null)
                .score(5.0)
                .title(TEST_TITLE_1)
                .body(TEST_BODY_1)
                .isoDateTime(TEST_ISO_DATE_TIME_1)
                .accountId(TEST_ACCOUNT_ID)
                .build()
        );
    }

    @Test
    public void testAddNewReviewNullScore() throws Exception {
        assertThrows(NullPointerException.class, () -> 
            Review.builder()
                .reviewId(TEST_REVIEW_ID_1)
                .restaurantId(TEST_RESTAURANT_ID_1)
                .score(null)
                .title(TEST_TITLE_1)
                .body(TEST_BODY_1)
                .isoDateTime(TEST_ISO_DATE_TIME_1)
                .accountId(TEST_ACCOUNT_ID)
                .build()
        );
    }

    @Test
    public void testAddNewReviewNullTitle() throws Exception {
        assertThrows(NullPointerException.class, () -> 
            Review.builder()
                .reviewId(TEST_REVIEW_ID_1)
                .restaurantId(TEST_RESTAURANT_ID_1)
                .score(5.0)
                .title(null)
                .body(TEST_BODY_1)
                .isoDateTime(TEST_ISO_DATE_TIME_1)
                .accountId(TEST_ACCOUNT_ID)
                .build()
        );
    }

    @Test
    public void testAddNewReviewNullBody() throws Exception {
        assertThrows(NullPointerException.class, () -> 
            Review.builder()
                .reviewId(TEST_REVIEW_ID_1)
                .restaurantId(TEST_RESTAURANT_ID_1)
                .score(5.0)
                .title(TEST_TITLE_1)
                .body(null)
                .isoDateTime(TEST_ISO_DATE_TIME_1)
                .accountId(TEST_ACCOUNT_ID)
                .build()
        );
    }

    @Test
    public void testAddNewReviewNullISODateTime() throws Exception {
        assertThrows(ValidatorException.class, () -> domain.addNewReviewForRestaurant(TEST_REVIEW_NULL_ISO_DATETIME));
    }

    @Test
    public void testAddNewBadFormatISODateTime() throws Exception {
        assertThrows(ValidatorException.class, () -> domain.addNewReviewForRestaurant(TEST_REVIEW_BAD_ISO_DATETIME));
    }

    @Test
    public void testAddNewReviewNullAccountId() throws Exception {
        assertThrows(ValidatorException.class, () -> domain.addNewReviewForRestaurant(TEST_REVIEW_NULL_ACCOUNT_ID));
    }
}
