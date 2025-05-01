package com.fryrank.util;

import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPResponse;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class APIGatewayResponseBuilderTest {

    private static final String TEST_HANDLER_NAME = "TestHandler";
    private static final String TEST_RESPONSE_DATA = "Test Response";
    private static final String TEST_ERROR_MESSAGE = "Invalid input";
    private static final String INTERNAL_ERROR_MESSAGE = "Internal error";
    private static final String INTERNAL_SERVER_ERROR = "Internal Server Error";
    private static final String BAD_REQUEST_MESSAGE = "Bad Request";
    private static final String TEST_VALUE = "test value";
    
    private static final int HTTP_OK = 200;
    private static final int HTTP_BAD_REQUEST = 400;
    private static final int HTTP_INTERNAL_ERROR = 500;

    @Test
    void handleRequest_SuccessfulExecution_ReturnsSuccessResponse() {
        // Arrange
        String handlerName = TEST_HANDLER_NAME;
        String testData = TEST_RESPONSE_DATA;

        // Act
        APIGatewayV2HTTPResponse response = APIGatewayResponseBuilder.handleRequest(handlerName, () -> {
            return APIGatewayResponseBuilder.buildSuccessResponse(testData);
        });

        // Assert
        assertNotNull(response);
        assertEquals(HTTP_OK, response.getStatusCode());
        assertEquals(testData.toString(), response.getBody());
    }

    @Test
    void handleRequest_ThrowsIllegalArgumentException_ReturnsBadRequestResponse() {
        // Arrange
        String handlerName = TEST_HANDLER_NAME;
        String errorMessage = TEST_ERROR_MESSAGE;

        // Act
        APIGatewayV2HTTPResponse response = APIGatewayResponseBuilder.handleRequest(handlerName, () -> {
            throw new IllegalArgumentException(errorMessage);
        });

        // Assert
        assertNotNull(response);
        assertEquals(HTTP_BAD_REQUEST, response.getStatusCode());
        assertTrue(response.getBody().contains(errorMessage));
    }

    @Test
    void handleRequest_ThrowsRuntimeException_ReturnsInternalServerErrorResponse() {
        // Arrange
        String handlerName = TEST_HANDLER_NAME;
        String errorMessage = INTERNAL_ERROR_MESSAGE;

        // Act
        APIGatewayV2HTTPResponse response = APIGatewayResponseBuilder.handleRequest(handlerName, () -> {
            throw new RuntimeException(errorMessage);
        });

        // Assert
        assertNotNull(response);
        assertEquals(HTTP_INTERNAL_ERROR, response.getStatusCode());
        assertTrue(response.getBody().contains(INTERNAL_SERVER_ERROR));
    }

    @Test
    void buildSuccessResponse_WithValidData_ReturnsCorrectResponse() {
        // Arrange
        TestData testData = new TestData(TEST_VALUE);

        // Act
        APIGatewayV2HTTPResponse response = APIGatewayResponseBuilder.buildSuccessResponse(testData);

        // Assert
        assertNotNull(response);
        assertEquals(HTTP_OK, response.getStatusCode());
        assertTrue(response.getBody().contains(TEST_VALUE));
    }

    @Test
    void buildErrorResponse_WithStatusAndMessage_ReturnsCorrectResponse() {
        // Arrange
        int statusCode = HTTP_BAD_REQUEST;
        String message = BAD_REQUEST_MESSAGE;

        // Act
        APIGatewayV2HTTPResponse response = APIGatewayResponseBuilder.buildErrorResponse(statusCode, message);

        // Assert
        assertNotNull(response);
        assertEquals(statusCode, response.getStatusCode());
        assertTrue(response.getBody().contains(message));
    }

    // Helper class for testing
    private static class TestData {
        private final String value;

        TestData(String value) {
            this.value = value;
        }

        @Override
        public String toString() {
            return value;
        }
    }
}
