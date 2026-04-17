package com.fryrank.handler;

import static com.fryrank.Constants.DEFAULT_PAGE_LIMIT;
import static com.fryrank.Constants.MAX_PAGE_LIMIT;
import static com.fryrank.TestConstants.TEST_ACCOUNT_ID;
import static com.fryrank.TestConstants.TEST_RESTAURANT_ID;
import static com.fryrank.TestConstants.TEST_REVIEWS;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPResponse;
import com.fryrank.domain.ReviewDomain;
import com.fryrank.model.GetAllReviewsOutput;
import com.fryrank.util.Authorizer;
import com.fryrank.validator.APIGatewayRequestValidator;

@ExtendWith(MockitoExtension.class)
public class GetAllReviewsHandlerTests {

    @Mock
    private ReviewDomain reviewDomain;

    @Mock
    private APIGatewayRequestValidator requestValidator;

    @Mock
    private Authorizer authorizer;

    @Mock
    private Context context;

    @InjectMocks
    private GetAllReviewsHandler handler;

    private GetAllReviewsOutput defaultOutput;

    @BeforeEach
    public void setUp() {
        defaultOutput = new GetAllReviewsOutput(TEST_REVIEWS, null);
    }

    @Test
    public void testHandleRequest_WithNoLimitParam_UsesDefaultLimit() throws Exception {
        doNothing().when(requestValidator).validateRequest(any(), any());
        when(reviewDomain.getAllReviews(eq(TEST_RESTAURANT_ID), isNull(), eq(DEFAULT_PAGE_LIMIT), isNull(), isNull()))
                .thenReturn(defaultOutput);

        final APIGatewayV2HTTPResponse response = handler.handleRequest(createEvent(TEST_RESTAURANT_ID, null, null), context);

        assertEquals(200, response.getStatusCode());
        verify(reviewDomain).getAllReviews(eq(TEST_RESTAURANT_ID), isNull(), eq(DEFAULT_PAGE_LIMIT), isNull(), isNull());
    }

    @Test
    public void testHandleRequest_WithEmptyLimitParam_UsesDefaultLimit() throws Exception {
        doNothing().when(requestValidator).validateRequest(any(), any());
        when(reviewDomain.getAllReviews(eq(TEST_RESTAURANT_ID), isNull(), eq(DEFAULT_PAGE_LIMIT), isNull(), isNull()))
                .thenReturn(defaultOutput);

        final APIGatewayV2HTTPResponse response = handler.handleRequest(createEvent(TEST_RESTAURANT_ID, null, ""), context);

        assertEquals(200, response.getStatusCode());
        verify(reviewDomain).getAllReviews(eq(TEST_RESTAURANT_ID), isNull(), eq(DEFAULT_PAGE_LIMIT), isNull(), isNull());
    }

    @Test
    public void testHandleRequest_WithNonNumericLimitParam_UsesDefaultLimit() throws Exception {
        doNothing().when(requestValidator).validateRequest(any(), any());
        when(reviewDomain.getAllReviews(eq(TEST_RESTAURANT_ID), isNull(), eq(DEFAULT_PAGE_LIMIT), isNull(), isNull()))
                .thenReturn(defaultOutput);

        final APIGatewayV2HTTPResponse response = handler.handleRequest(createEvent(TEST_RESTAURANT_ID, null, "abc"), context);

        assertEquals(200, response.getStatusCode());
        verify(reviewDomain).getAllReviews(eq(TEST_RESTAURANT_ID), isNull(), eq(DEFAULT_PAGE_LIMIT), isNull(), isNull());
    }

    @Test
    public void testHandleRequest_WithZeroLimitParam_ClampsToOne() throws Exception {
        doNothing().when(requestValidator).validateRequest(any(), any());
        when(reviewDomain.getAllReviews(eq(TEST_RESTAURANT_ID), isNull(), eq(1), isNull(), isNull()))
                .thenReturn(defaultOutput);

        final APIGatewayV2HTTPResponse response = handler.handleRequest(createEvent(TEST_RESTAURANT_ID, null, "0"), context);

        assertEquals(200, response.getStatusCode());
        verify(reviewDomain).getAllReviews(eq(TEST_RESTAURANT_ID), isNull(), eq(1), isNull(), isNull());
    }

    @Test
    public void testHandleRequest_WithNegativeLimitParam_ClampsToOne() throws Exception {
        doNothing().when(requestValidator).validateRequest(any(), any());
        when(reviewDomain.getAllReviews(eq(TEST_RESTAURANT_ID), isNull(), eq(1), isNull(), isNull()))
                .thenReturn(defaultOutput);

        final APIGatewayV2HTTPResponse response = handler.handleRequest(createEvent(TEST_RESTAURANT_ID, null, "-5"), context);

        assertEquals(200, response.getStatusCode());
        verify(reviewDomain).getAllReviews(eq(TEST_RESTAURANT_ID), isNull(), eq(1), isNull(), isNull());
    }

    @Test
    public void testHandleRequest_WithLimitAboveMax_ClampsToMaxPageLimit() throws Exception {
        doNothing().when(requestValidator).validateRequest(any(), any());
        when(reviewDomain.getAllReviews(eq(TEST_RESTAURANT_ID), isNull(), eq(MAX_PAGE_LIMIT), isNull(), isNull()))
                .thenReturn(defaultOutput);

        final APIGatewayV2HTTPResponse response = handler.handleRequest(createEvent(TEST_RESTAURANT_ID, null, "999"), context);

        assertEquals(200, response.getStatusCode());
        verify(reviewDomain).getAllReviews(eq(TEST_RESTAURANT_ID), isNull(), eq(MAX_PAGE_LIMIT), isNull(), isNull());
    }

    @Test
    public void testHandleRequest_WithValidLimitParam_UsesProvidedLimit() throws Exception {
        doNothing().when(requestValidator).validateRequest(any(), any());
        when(reviewDomain.getAllReviews(eq(TEST_RESTAURANT_ID), isNull(), eq(25), isNull(), isNull()))
                .thenReturn(defaultOutput);

        final APIGatewayV2HTTPResponse response = handler.handleRequest(createEvent(TEST_RESTAURANT_ID, null, "25"), context);

        assertEquals(200, response.getStatusCode());
        verify(reviewDomain).getAllReviews(eq(TEST_RESTAURANT_ID), isNull(), eq(25), isNull(), isNull());
    }

    @Test
    public void testHandleRequest_WithMaxLimitParam_UsesMaxPageLimit() throws Exception {
        doNothing().when(requestValidator).validateRequest(any(), any());
        when(reviewDomain.getAllReviews(eq(TEST_RESTAURANT_ID), isNull(), eq(MAX_PAGE_LIMIT), isNull(), isNull()))
                .thenReturn(defaultOutput);

        final APIGatewayV2HTTPResponse response = handler.handleRequest(createEvent(TEST_RESTAURANT_ID, null, String.valueOf(MAX_PAGE_LIMIT)), context);

        assertEquals(200, response.getStatusCode());
        verify(reviewDomain).getAllReviews(eq(TEST_RESTAURANT_ID), isNull(), eq(MAX_PAGE_LIMIT), isNull(), isNull());
    }

    @Test
    public void testHandleRequest_WithAccountIdAndValidLimit_PassesLimitToReviewDomain() throws Exception {
        doNothing().when(requestValidator).validateRequest(any(), any());
        when(reviewDomain.getAllReviews(isNull(), eq(TEST_ACCOUNT_ID), eq(5), isNull(), isNull()))
                .thenReturn(defaultOutput);

        final APIGatewayV2HTTPResponse response = handler.handleRequest(createEvent(null, TEST_ACCOUNT_ID, "5"), context);

        assertEquals(200, response.getStatusCode());
        verify(reviewDomain).getAllReviews(isNull(), eq(TEST_ACCOUNT_ID), eq(5), isNull(), isNull());
    }

    private APIGatewayV2HTTPEvent createEvent(String restaurantId, String accountId, String limit) {
        final APIGatewayV2HTTPEvent event = new APIGatewayV2HTTPEvent();
        final Map<String, String> params = new HashMap<>();
        if (restaurantId != null) {
            params.put("restaurantId", restaurantId);
        }
        if (accountId != null) {
            params.put("accountId", accountId);
        }
        if (limit != null) {
            params.put("limit", limit);
        }
        event.setQueryStringParameters(params.isEmpty() ? null : params);
        return event;
    }
}
