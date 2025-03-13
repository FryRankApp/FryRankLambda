package com.fryrank.domain;

import com.fryrank.dal.ReviewDAL;
import com.fryrank.model.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import static com.fryrank.TestConstants.TEST_ACCOUNT_ID;
import static com.fryrank.TestConstants.TEST_RESTAURANT_ID;
import static com.fryrank.TestConstants.TEST_REVIEWS;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.when;


@RunWith(MockitoJUnitRunner.class)
public class ReviewDomainTests {
    @Mock
    ReviewDAL reviewDAL;

    @InjectMocks
    ReviewDomain controller;

    // /api/reviews endpoint tests
    @Test
    public void testGetAllReviewsForRestaurant() throws Exception {
        final GetAllReviewsOutput expectedOutput = new GetAllReviewsOutput(TEST_REVIEWS);
        when(reviewDAL.getAllReviewsByRestaurantId(TEST_RESTAURANT_ID)).thenReturn(expectedOutput);

        final GetAllReviewsOutput actualOutput = controller.getAllReviews(TEST_RESTAURANT_ID, null);
        assertEquals(expectedOutput, actualOutput);
    }

    @Test
    public void testGetAllReviewsForAccount() throws Exception {
        final GetAllReviewsOutput expectedOutput = new GetAllReviewsOutput(TEST_REVIEWS);
        when(reviewDAL.getAllReviewsByAccountId(TEST_ACCOUNT_ID)).thenReturn(expectedOutput);

        final GetAllReviewsOutput actualOutput = controller.getAllReviews(null, TEST_ACCOUNT_ID);
        assertEquals(expectedOutput, actualOutput);
    }

    @Test
    public void testGetAllReviewsNoParameter() throws Exception {
        assertThrows(NullPointerException.class, () -> controller.getAllReviews(null, null));
    }
}
