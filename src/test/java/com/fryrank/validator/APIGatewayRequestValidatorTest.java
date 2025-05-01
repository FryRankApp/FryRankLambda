package com.fryrank.validator;

import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Map;

class APIGatewayRequestValidatorTest {

    private static final String ADD_NEW_REVIEW_HANDLER = "AddNewReviewForRestaurantHandler";
    private static final String GET_AGGREGATE_REVIEW_INFORMATION_HANDLER = "GetAggregateReviewInformationHandler";
    private static final String GET_RECENT_REVIEWS_HANDLER = "GetRecentReviewsHandler";
    private static final String GET_ALL_REVIEWS_HANDLER = "GetAllReviewsHandler";

    private APIGatewayRequestValidator validator;
    private APIGatewayV2HTTPEvent event;

    @BeforeEach
    void setUp() {
        validator = new APIGatewayRequestValidator();
        event = new APIGatewayV2HTTPEvent();
    }

    @Test
    void validateRequest_AddNewReviewHandler_WithValidBody_Succeeds() {
        // Arrange
        event.setBody("{ \"valid\": \"json\" }");

        // Act & Assert
        assertDoesNotThrow(() -> 
            validator.validateRequest(ADD_NEW_REVIEW_HANDLER, event)
        );
    }

    @Test
    void validateRequest_AddNewReviewHandler_WithNullBody_ThrowsException() {
        // Arrange
        event.setBody(null);

        // Act & Assert
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
            validator.validateRequest(ADD_NEW_REVIEW_HANDLER, event)
        );
        assertTrue(exception.getMessage().contains("Request body is required"));
    }

    @Test
    void validateRequest_AddNewReviewHandler_WithEmptyBody_ThrowsException() {
        // Arrange
        event.setBody("");

        // Act & Assert
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
            validator.validateRequest(ADD_NEW_REVIEW_HANDLER, event)
        );
        assertTrue(exception.getMessage().contains("Request body is required"));
    }

    @Test
    void validateRequest_GetAggregateReviewInformationHandler_WithValidParams_Succeeds() {
        // Arrange
        Map<String, String> queryParams = new HashMap<>();
        queryParams.put("ids", "1,2,3");
        queryParams.put("includeRating", "true");
        event.setQueryStringParameters(queryParams);

        // Act & Assert
        assertDoesNotThrow(() ->
            validator.validateRequest(GET_AGGREGATE_REVIEW_INFORMATION_HANDLER, event)
        );
    }

    @Test
    void validateRequest_GetAggregateReviewInformationHandler_WithMissingParams_ThrowsException() {
        // Arrange
        event.setQueryStringParameters(null);

        // Act & Assert
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
            validator.validateRequest(GET_AGGREGATE_REVIEW_INFORMATION_HANDLER, event)
        );
        assertTrue(exception.getMessage().contains("Query parameters are required"));
    }

    @Test
    void validateRequest_GetRecentReviewsHandler_WithValidParams_Succeeds() {
        // Arrange
        Map<String, String> queryParams = new HashMap<>();
        queryParams.put("count", "5");
        event.setQueryStringParameters(queryParams);

        // Act & Assert
        assertDoesNotThrow(() ->
            validator.validateRequest(GET_RECENT_REVIEWS_HANDLER, event)
        );
    }

    @Test
    void validateRequest_GetRecentReviewsHandler_WithMissingParams_ThrowsException() {
        // Arrange
        event.setQueryStringParameters(null);

        // Act & Assert
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
            validator.validateRequest(GET_RECENT_REVIEWS_HANDLER, event)
        );
        assertTrue(exception.getMessage().contains("Query parameters are required"));
    }

    @Test
    void validateRequest_GetAllReviewsHandler_WithOptionalParams_Succeeds() {
        // Arrange
        Map<String, String> queryParams = new HashMap<>();
        queryParams.put("restaurantId", "123");
        queryParams.put("accountId", "456");
        event.setQueryStringParameters(queryParams);

        // Act & Assert
        assertDoesNotThrow(() ->
            validator.validateRequest(GET_ALL_REVIEWS_HANDLER, event)
        );
    }

    @Test
    void validateRequest_GetAllReviewsHandler_WithNoParams_ThrowsException() {
        // Arrange
        event.setQueryStringParameters(new HashMap<>());

        // Act & Assert
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
            validator.validateRequest(GET_RECENT_REVIEWS_HANDLER, event)
        );
        assertTrue(exception.getMessage().contains("Query parameters are required"));
    }
}
