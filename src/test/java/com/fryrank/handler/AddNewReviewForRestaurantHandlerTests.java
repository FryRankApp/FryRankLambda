package com.fryrank.handler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPResponse;
import com.fryrank.Constants;

import static com.fryrank.TestConstants.TEST_ACCOUNT_ID;
import static com.fryrank.TestConstants.TEST_BODY_1;
import static com.fryrank.TestConstants.TEST_INVALID_TOKEN;
import static com.fryrank.TestConstants.TEST_ISO_DATE_TIME_1;
import static com.fryrank.TestConstants.TEST_MALFORMED_TOKEN;
import static com.fryrank.TestConstants.TEST_REVIEW_ID_1;
import static com.fryrank.TestConstants.TEST_RESTAURANT_ID;
import static com.fryrank.TestConstants.TEST_TITLE_1;
import static com.fryrank.TestConstants.TEST_VALID_TOKEN;
import com.fryrank.dal.ReviewDALImpl;
import com.fryrank.domain.ReviewDomain;
import com.fryrank.model.Review;
import com.fryrank.model.exceptions.AuthorizationDisabledException;
import com.fryrank.model.exceptions.NotAuthorizedException;
import com.fryrank.util.Authorizer;
import com.fryrank.validator.APIGatewayRequestValidator;
import com.fryrank.validator.ReviewValidator;
import com.google.gson.Gson;

@ExtendWith(MockitoExtension.class)
public class AddNewReviewForRestaurantHandlerTests {

    @Mock
    private ReviewDALImpl reviewDAL;
    
    @Mock
    private ReviewDomain reviewDomain;
    
    @Mock
    private APIGatewayRequestValidator requestValidator;
    
    @Mock
    private ReviewValidator reviewValidator;
    
    @Mock
    private Authorizer authorizer;
    
    @Mock
    private Context context;
    
    @InjectMocks
    private AddNewReviewForRestaurantHandler handler;
    
    private Gson gson;

    @BeforeEach
    public void setUp() {
        // @InjectMocks will automatically create handler with mocked dependencies
        gson = new Gson();
    }

    @Test
    public void testHandleRequest_WithValidTokenAndReview_ReturnsSuccess() throws Exception {
        // Arrange
        final Review inputReview = Review.builder()
            .restaurantId(TEST_RESTAURANT_ID)
            .score(5.0)
            .title(TEST_TITLE_1)
            .body(TEST_BODY_1)
            .build();
        
        final Review outputReview = Review.builder()
            .reviewId(TEST_REVIEW_ID_1)
            .restaurantId(TEST_RESTAURANT_ID)
            .score(5.0)
            .title(TEST_TITLE_1)
            .body(TEST_BODY_1)
            .accountId(TEST_ACCOUNT_ID)
            .isoDateTime(TEST_ISO_DATE_TIME_1)
            .build();
        
        final APIGatewayV2HTTPEvent event = createTestEvent(createBearerToken(TEST_VALID_TOKEN), gson.toJson(inputReview));
        
        // Setup mocks - mock Authorizer and domain layer
        doNothing().when(requestValidator).validateRequest(any(), any());
        when(authorizer.authorizeAndGetAccountId(TEST_VALID_TOKEN)).thenReturn(TEST_ACCOUNT_ID);
        when(reviewDomain.addNewReviewForRestaurant(any(Review.class))).thenReturn(outputReview);
        
        // Act
        final APIGatewayV2HTTPResponse response = handler.handleRequest(event, context);
        
        // Assert
        assertEquals(200, response.getStatusCode());
        assertNotNull(response.getBody());
        
        Review responseReview = gson.fromJson(response.getBody(), Review.class);
        assertEquals(TEST_REVIEW_ID_1, responseReview.getReviewId());
        assertEquals(TEST_RESTAURANT_ID, responseReview.getRestaurantId());
        assertEquals(5.0, responseReview.getScore());
        assertEquals(TEST_BODY_1, responseReview.getBody());
        
        verify(authorizer).authorizeAndGetAccountId(TEST_VALID_TOKEN);

        // Capture the Review object passed to the domain layer
        final ArgumentCaptor<Review> reviewCaptor = ArgumentCaptor.forClass(Review.class);
        verify(reviewDomain).addNewReviewForRestaurant(reviewCaptor.capture());
        
        final Review capturedReview = reviewCaptor.getValue();
        assertNotNull(capturedReview.getAccountId(), "Account ID should not be null when authorization succeeds");
        assertEquals(TEST_ACCOUNT_ID, capturedReview.getAccountId(), "Account ID should match the authorized user's ID");
        assertNotNull(capturedReview.getIsoDateTime(), "Timestamp should not be null when creating a review");
    }

    @Test
    public void testHandleRequest_WithInvalidToken_ReturnsUnauthorized() throws Exception {
        // Arrange
        final Review inputReview = Review.builder()
            .restaurantId(TEST_RESTAURANT_ID)
            .score(5.0)
            .title(TEST_TITLE_1)
            .body(TEST_BODY_1)
            .build();
        
        final APIGatewayV2HTTPEvent event = createTestEvent(createBearerToken(TEST_INVALID_TOKEN), gson.toJson(inputReview));
        
        // Setup mocks - mock Authorizer to throw exception for invalid token
        doNothing().when(requestValidator).validateRequest(any(), any());
        doThrow(new NotAuthorizedException(Constants.AUTH_ERROR_INVALID_TOKEN)).when(authorizer).authorizeAndGetAccountId(TEST_INVALID_TOKEN);
        
        // Act
        final APIGatewayV2HTTPResponse response = handler.handleRequest(event, context);
        
        // Assert
        assertEquals(401, response.getStatusCode());
        assertEquals(Constants.AUTH_ERROR_INVALID_TOKEN, response.getBody());
        
        verify(authorizer).authorizeAndGetAccountId(TEST_INVALID_TOKEN);
    }

    @Test
    public void testHandleRequest_WithMissingToken_ReturnsUnauthorized() throws Exception {
        // Arrange
        final Review inputReview = Review.builder()
            .restaurantId(TEST_RESTAURANT_ID)
            .score(5.0)
            .title(TEST_TITLE_1)
            .body(TEST_BODY_1)
            .build();
        
        final APIGatewayV2HTTPEvent event = createTestEvent(null, gson.toJson(inputReview));
        
        // Setup mocks - mock Authorizer to throw exception for null token (missing header)
        doNothing().when(requestValidator).validateRequest(any(), any());
        doThrow(new NotAuthorizedException(Constants.AUTH_ERROR_MISSING_OR_INVALID_HEADER)).when(authorizer).authorizeAndGetAccountId(null);
        
        // Act
        final APIGatewayV2HTTPResponse response = handler.handleRequest(event, context);
        
        // Assert
        assertEquals(401, response.getStatusCode());
        assertEquals(Constants.AUTH_ERROR_MISSING_OR_INVALID_HEADER, response.getBody());
    }

    @Test
    public void testHandleRequest_WithMalformedToken_ReturnsUnauthorized() throws Exception {
        // Arrange
        final Review inputReview = Review.builder()
            .restaurantId(TEST_RESTAURANT_ID)
            .score(5.0)
            .title(TEST_TITLE_1)
            .body(TEST_BODY_1)
            .build();
        
        final APIGatewayV2HTTPEvent event = createTestEvent(TEST_MALFORMED_TOKEN, gson.toJson(inputReview));
        
        // Setup mocks - mock Authorizer to throw exception for malformed token
        doNothing().when(requestValidator).validateRequest(any(), any());
        doThrow(new NotAuthorizedException(Constants.AUTH_ERROR_MISSING_OR_INVALID_HEADER)).when(authorizer).authorizeAndGetAccountId(null);
        
        // Act
        final APIGatewayV2HTTPResponse response = handler.handleRequest(event, context);
        
        // Assert
        assertEquals(401, response.getStatusCode());
        assertEquals(Constants.AUTH_ERROR_MISSING_OR_INVALID_HEADER, response.getBody());
    }

    @Test
    public void testHandleRequest_WithAuthDisabled_UsesAccountIdFromRequestBody() throws Exception {
        // Arrange - include accountId in the request body
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
            .accountId(TEST_ACCOUNT_ID)
            .isoDateTime(TEST_ISO_DATE_TIME_1)
            .build();
        
        final APIGatewayV2HTTPEvent event = createTestEvent(null, gson.toJson(inputReview));
        
        // Setup mocks - mock Authorizer to throw exception (auth disabled)
        doNothing().when(requestValidator).validateRequest(any(), any());
        doThrow(new AuthorizationDisabledException("Authorization is disabled")).when(authorizer).authorizeAndGetAccountId(null);
        when(reviewDomain.addNewReviewForRestaurant(any(Review.class))).thenReturn(outputReview);
        
        // Act
        final APIGatewayV2HTTPResponse response = handler.handleRequest(event, context);
        
        // Assert
        assertEquals(200, response.getStatusCode());
        
        // Capture the Review object passed to the domain layer
        final ArgumentCaptor<Review> reviewCaptor = ArgumentCaptor.forClass(Review.class);
        verify(reviewDomain).addNewReviewForRestaurant(reviewCaptor.capture());
        
        final Review capturedReview = reviewCaptor.getValue();
        assertEquals(TEST_ACCOUNT_ID, capturedReview.getAccountId(), "Account ID should be used from request body when auth is disabled");
    }

    private APIGatewayV2HTTPEvent createTestEvent(String authHeader, String body) {
        final APIGatewayV2HTTPEvent event = new APIGatewayV2HTTPEvent();
        event.setBody(body);
        
        final Map<String, String> headers = new HashMap<>();
        if (authHeader != null) {
            headers.put("Authorization", authHeader);
        }
        event.setHeaders(headers);
        
        return event;
    }

    private String createBearerToken(String token) {
        return "Bearer " + token;
    }
}
