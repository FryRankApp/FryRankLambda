package com.fryrank.util;

import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPResponse;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class APIGatewayResponseBuilderTest {

    @Test
    void handleRequest_SuccessfulExecution_ReturnsSuccessResponse() {
        // Arrange
        String handlerName = "TestHandler";
        String testData = "Test Response";

        // Act
        APIGatewayV2HTTPResponse response = APIGatewayResponseBuilder.handleRequest(handlerName, () -> {
            return APIGatewayResponseBuilder.buildSuccessResponse(testData);
        });

        // Assert
        assertNotNull(response);
        assertEquals(200, response.getStatusCode());
        assertEquals(testData.toString(), response.getBody());
    }

    @Test
    void handleRequest_ThrowsIllegalArgumentException_ReturnsBadRequestResponse() {
        // Arrange
        String handlerName = "TestHandler";
        String errorMessage = "Invalid input";

        // Act
        APIGatewayV2HTTPResponse response = APIGatewayResponseBuilder.handleRequest(handlerName, () -> {
            throw new IllegalArgumentException(errorMessage);
        });

        // Assert
        assertNotNull(response);
        assertEquals(400, response.getStatusCode());
        assertTrue(response.getBody().contains(errorMessage));
    }

    @Test
    void handleRequest_ThrowsRuntimeException_ReturnsInternalServerErrorResponse() {
        // Arrange
        String handlerName = "TestHandler";
        String errorMessage = "Internal error";

        // Act
        APIGatewayV2HTTPResponse response = APIGatewayResponseBuilder.handleRequest(handlerName, () -> {
            throw new RuntimeException(errorMessage);
        });

        // Assert
        assertNotNull(response);
        assertEquals(500, response.getStatusCode());
        assertTrue(response.getBody().contains("Internal Server Error"));
    }

    @Test
    void buildSuccessResponse_WithValidData_ReturnsCorrectResponse() {
        // Arrange
        TestData testData = new TestData("test value");

        // Act
        APIGatewayV2HTTPResponse response = APIGatewayResponseBuilder.buildSuccessResponse(testData);

        // Assert
        assertNotNull(response);
        assertEquals(200, response.getStatusCode());
        assertTrue(response.getBody().contains("test value"));
    }

    @Test
    void buildErrorResponse_WithStatusAndMessage_ReturnsCorrectResponse() {
        // Arrange
        int statusCode = 400;
        String message = "Bad Request";

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
