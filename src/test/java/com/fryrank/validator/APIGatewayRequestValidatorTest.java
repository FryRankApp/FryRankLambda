package com.fryrank.validator;

import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPEvent;
import com.fryrank.model.enums.QueryParam;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static com.fryrank.validator.APIGatewayRequestValidator.REQUEST_BODY_REQUIRED_ERROR_MESSAGE;
import static com.fryrank.validator.APIGatewayRequestValidator.QUERY_PARAMS_REQUIRED_ERROR_MESSAGE;

import static com.fryrank.Constants.ADD_NEW_REVIEW_HANDLER;
import static com.fryrank.Constants.GET_ALL_REVIEWS_HANDLER;
import static com.fryrank.Constants.GET_AGGREGATE_REVIEW_HANDLER;
import static com.fryrank.Constants.GET_RECENT_REVIEWS_HANDLER;

import java.util.HashMap;
import java.util.Map;

class APIGatewayRequestValidatorTest {

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
        assertTrue(exception.getMessage().contains(REQUEST_BODY_REQUIRED_ERROR_MESSAGE));
    }

    @Test
    void validateRequest_AddNewReviewHandler_WithEmptyBody_ThrowsException() {
        // Arrange
        event.setBody("");

        // Act & Assert
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
            validator.validateRequest(ADD_NEW_REVIEW_HANDLER, event)
        );
        assertTrue(exception.getMessage().contains(REQUEST_BODY_REQUIRED_ERROR_MESSAGE));
    }

    @Test
    void validateRequest_GetAggregateReviewHandler_WithValidParams_Succeeds() {
        // Arrange
        Map<String, String> queryParams = new HashMap<>();
        queryParams.put(QueryParam.IDS.getValue(), "1,2,3");
        queryParams.put(QueryParam.INCLUDE_RATING.getValue(), "true");
        event.setQueryStringParameters(queryParams);

        // Act & Assert
        assertDoesNotThrow(() ->
            validator.validateRequest(GET_AGGREGATE_REVIEW_HANDLER, event)
        );
    }

    @Test
    void validateRequest_GetAggregateReviewHandler_WithMissingParams_ThrowsException() {
        // Arrange
        event.setQueryStringParameters(null);

        // Act & Assert
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
            validator.validateRequest(GET_AGGREGATE_REVIEW_HANDLER, event)
        );
        assertTrue(exception.getMessage().contains(QUERY_PARAMS_REQUIRED_ERROR_MESSAGE));
    }

    @Test
    void validateRequest_GetRecentReviewsHandler_WithValidParams_Succeeds() {
        // Arrange
        Map<String, String> queryParams = new HashMap<>();
        queryParams.put(QueryParam.COUNT.getValue(), "5");
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
        assertTrue(exception.getMessage().contains(QUERY_PARAMS_REQUIRED_ERROR_MESSAGE));
    }

    @Test
    void validateRequest_GetAllReviewsHandler_WithOptionalParams_Succeeds() {
        // Arrange
        Map<String, String> queryParams = new HashMap<>();
        queryParams.put(QueryParam.RESTAURANT_ID.getValue(), "123");
        queryParams.put(QueryParam.ACCOUNT_ID.getValue(), "456");
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
            validator.validateRequest(GET_ALL_REVIEWS_HANDLER, event)
        );
        assertTrue(exception.getMessage().contains(QUERY_PARAMS_REQUIRED_ERROR_MESSAGE));
    }
}
