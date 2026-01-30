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
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPResponse;
import com.fryrank.Constants;
import com.fryrank.TestConstants;
import com.fryrank.dal.ReviewDALImpl;
import com.fryrank.domain.ReviewDomain;
import com.fryrank.model.Review;
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
            .restaurantId(TestConstants.TEST_RESTAURANT_ID)
            .score(5.0)
            .title(TestConstants.TEST_TITLE_1)
            .body(TestConstants.TEST_BODY_1)
            .build();
        
        final Review outputReview = Review.builder()
            .reviewId(TestConstants.TEST_REVIEW_ID_1)
            .restaurantId(TestConstants.TEST_RESTAURANT_ID)
            .score(5.0)
            .title(TestConstants.TEST_TITLE_1)
            .body(TestConstants.TEST_BODY_1)
            .build();
        
        final APIGatewayV2HTTPEvent event = createTestEvent("Bearer " + TestConstants.TEST_VALID_TOKEN, gson.toJson(inputReview));
        
        // Setup mocks - mock Authorizer and domain layer
        doNothing().when(requestValidator).validateRequest(any(), any());
        doNothing().when(authorizer).authorizeToken(TestConstants.TEST_VALID_TOKEN); // No exception for valid token
        when(reviewDomain.addNewReviewForRestaurant(any(Review.class))).thenReturn(outputReview);
        
        // Act
        APIGatewayV2HTTPResponse response = handler.handleRequest(event, context);
        
        // Assert
        assertEquals(200, response.getStatusCode());
        assertNotNull(response.getBody());
        
        Review responseReview = gson.fromJson(response.getBody(), Review.class);
        assertEquals(TestConstants.TEST_REVIEW_ID_1, responseReview.getReviewId());
        assertEquals(TestConstants.TEST_RESTAURANT_ID, responseReview.getRestaurantId());
        assertEquals(5.0, responseReview.getScore());
        assertEquals(TestConstants.TEST_BODY_1, responseReview.getBody());
        
        verify(authorizer).authorizeToken(TestConstants.TEST_VALID_TOKEN);
        verify(reviewDomain).addNewReviewForRestaurant(inputReview);
    }

    @Test
    public void testHandleRequest_WithInvalidToken_ReturnsUnauthorized() throws Exception {
        // Arrange
        final Review inputReview = Review.builder()
            .restaurantId(TestConstants.TEST_RESTAURANT_ID)
            .score(5.0)
            .title(TestConstants.TEST_TITLE_1)
            .body(TestConstants.TEST_BODY_1)
            .build();
        
        final APIGatewayV2HTTPEvent event = createTestEvent("Bearer " + TestConstants.TEST_INVALID_TOKEN, gson.toJson(inputReview));
        
        // Setup mocks - mock Authorizer to throw exception for invalid token
        doNothing().when(requestValidator).validateRequest(any(), any());
        doThrow(new NotAuthorizedException(Constants.AUTH_ERROR_INVALID_TOKEN)).when(authorizer).authorizeToken(TestConstants.TEST_INVALID_TOKEN);
        
        // Act
        final APIGatewayV2HTTPResponse response = handler.handleRequest(event, context);
        
        // Assert
        assertEquals(401, response.getStatusCode());
        assertEquals(Constants.AUTH_ERROR_INVALID_TOKEN, response.getBody());
        
        verify(authorizer).authorizeToken(TestConstants.TEST_INVALID_TOKEN);
    }

    @Test
    public void testHandleRequest_WithMissingToken_ReturnsUnauthorized() throws Exception {
        // Arrange
        final Review inputReview = Review.builder()
            .restaurantId(TestConstants.TEST_RESTAURANT_ID)
            .score(5.0)
            .title(TestConstants.TEST_TITLE_1)
            .body(TestConstants.TEST_BODY_1)
            .build();
        
        final APIGatewayV2HTTPEvent event = createTestEvent(null, gson.toJson(inputReview));
        
        // Setup mocks - mock Authorizer to throw exception for null token (missing header)
        doNothing().when(requestValidator).validateRequest(any(), any());
        doThrow(new NotAuthorizedException(Constants.AUTH_ERROR_MISSING_OR_INVALID_HEADER)).when(authorizer).authorizeToken(null);
        
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
            .restaurantId(TestConstants.TEST_RESTAURANT_ID)
            .score(5.0)
            .title(TestConstants.TEST_TITLE_1)
            .body(TestConstants.TEST_BODY_1)
            .build();
        
        final APIGatewayV2HTTPEvent event = createTestEvent(TestConstants.TEST_MALFORMED_TOKEN, gson.toJson(inputReview));
        
        // Setup mocks - mock Authorizer to throw exception for malformed token
        doNothing().when(requestValidator).validateRequest(any(), any());
        doThrow(new NotAuthorizedException(Constants.AUTH_ERROR_MISSING_OR_INVALID_HEADER)).when(authorizer).authorizeToken(null);
        
        // Act
        final APIGatewayV2HTTPResponse response = handler.handleRequest(event, context);
        
        // Assert
        assertEquals(401, response.getStatusCode());
        assertEquals(Constants.AUTH_ERROR_MISSING_OR_INVALID_HEADER, response.getBody());
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
}
