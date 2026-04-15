package com.fryrank.handler;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPResponse;
import com.fryrank.domain.ReviewDomain;
import com.fryrank.domain.UserMetadataDomain;
import com.fryrank.model.GetAllReviewsOutput;
import com.fryrank.model.Review;
import com.fryrank.util.Authorizer;
import com.fryrank.validator.APIGatewayRequestValidator;
import com.fryrank.validator.DeleteReviewRequestValidator;
import com.fryrank.validator.ReviewValidator;
import com.google.gson.Gson;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.Map;

import static com.fryrank.Constants.DEFAULT_PAGE_LIMIT;
import static com.fryrank.Constants.GET_ALL_REVIEWS_HANDLER;
import static com.fryrank.TestConstants.TEST_ACCOUNT_ID;
import static com.fryrank.TestConstants.TEST_AUTHORIZED_ACCOUNT_ID;
import static com.fryrank.TestConstants.TEST_BODY_1;
import static com.fryrank.TestConstants.TEST_RESTAURANT_ID;
import static com.fryrank.TestConstants.TEST_REVIEW_ID_1;
import static com.fryrank.TestConstants.TEST_REVIEWS;
import static com.fryrank.TestConstants.TEST_TITLE_1;
import static com.fryrank.TestConstants.TEST_VALID_TOKEN;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class ServiceRequestHandlerTests {

    @Mock
    private ReviewDomain reviewDomain;

    @Mock
    private UserMetadataDomain userMetadataDomain;

    @Mock
    private APIGatewayRequestValidator requestValidator;

    @Mock
    private ReviewValidator reviewValidator;

    @Mock
    private DeleteReviewRequestValidator deleteReviewRequestValidator;

    @Mock
    private Authorizer authorizer;

    @Mock
    private Context context;

    private Gson gson;
    private ServiceRequestHandler handler;

    @BeforeEach
    void setUp() {
        gson = new Gson();
        handler = new ServiceRequestHandler(
                reviewDomain,
                userMetadataDomain,
                requestValidator,
                reviewValidator,
                deleteReviewRequestValidator,
                authorizer,
                gson
        );
    }

    @Test
    void handleRequest_getAllReviews_routesByRouteKey() throws Exception {
        final Map<String, Object> event = new HashMap<>();
        event.put("routeKey", "GET /api/reviews");
        event.put("queryStringParameters", Map.of("restaurantId", TEST_RESTAURANT_ID));
        event.put("headers", new HashMap<>());

        doNothing().when(requestValidator).validateRequest(any(), any());
        final GetAllReviewsOutput output = new GetAllReviewsOutput(TEST_REVIEWS, null);
        when(reviewDomain.getAllReviews(eq(TEST_RESTAURANT_ID), isNull(), eq(DEFAULT_PAGE_LIMIT), isNull()))
                .thenReturn(output);

        final APIGatewayV2HTTPResponse response = handler.handleRequest(event, context);

        assertEquals(200, response.getStatusCode());
        assertNotNull(response.getBody());
        verify(requestValidator).validateRequest(eq(GET_ALL_REVIEWS_HANDLER), any(APIGatewayV2HTTPEvent.class));
        verify(reviewDomain).getAllReviews(eq(TEST_RESTAURANT_ID), isNull(), eq(DEFAULT_PAGE_LIMIT), isNull());
    }

    @Test
    void handleRequest_addReview_routesByRouteKey_andAuthorizes() throws Exception {
        final Review inputReview = Review.builder()
                .restaurantId(TEST_RESTAURANT_ID)
                .score(5.0)
                .title(TEST_TITLE_1)
                .body(TEST_BODY_1)
                .accountId(TEST_ACCOUNT_ID)
                .build();

        final Review outputReview = Review.builder()
                .reviewId(TEST_REVIEW_ID_1)
                .restaurantId(TEST_RESTAURANT_ID)
                .score(5.0)
                .title(TEST_TITLE_1)
                .body(TEST_BODY_1)
                .accountId(TEST_AUTHORIZED_ACCOUNT_ID)
                .isoDateTime("2026-01-01T00:00:00Z")
                .build();

        final Map<String, Object> event = new HashMap<>();
        event.put("routeKey", "POST /api/reviews");
        event.put("body", gson.toJson(inputReview));
        event.put("headers", Map.of("Authorization", "Bearer " + TEST_VALID_TOKEN));

        doNothing().when(requestValidator).validateRequest(any(), any());
        when(authorizer.authorizeAndGetAccountId(TEST_VALID_TOKEN)).thenReturn(TEST_AUTHORIZED_ACCOUNT_ID);
        when(reviewDomain.addNewReviewForRestaurant(any(Review.class))).thenReturn(outputReview);

        final APIGatewayV2HTTPResponse response = handler.handleRequest(event, context);

        assertEquals(200, response.getStatusCode());
        assertNotNull(response.getBody());

        final Review responseReview = gson.fromJson(response.getBody(), Review.class);
        assertEquals(TEST_REVIEW_ID_1, responseReview.getReviewId());
        assertEquals(TEST_AUTHORIZED_ACCOUNT_ID, responseReview.getAccountId());

        verify(authorizer).authorizeAndGetAccountId(TEST_VALID_TOKEN);
        verify(reviewValidator).validate(any(), any());

        final ArgumentCaptor<Review> reviewCaptor = ArgumentCaptor.forClass(Review.class);
        verify(reviewDomain).addNewReviewForRestaurant(reviewCaptor.capture());
        assertEquals(TEST_AUTHORIZED_ACCOUNT_ID, reviewCaptor.getValue().getAccountId());
        assertNotNull(reviewCaptor.getValue().getIsoDateTime());
    }

    @Test
    void handleRequest_unknownRoute_returns404() {
        final Map<String, Object> event = new HashMap<>();
        event.put("routeKey", "GET /api/does-not-exist");
        event.put("headers", new HashMap<>());

        final APIGatewayV2HTTPResponse response = handler.handleRequest(event, context);

        assertEquals(404, response.getStatusCode());
        assertEquals("Not Found", response.getBody());
    }
}
