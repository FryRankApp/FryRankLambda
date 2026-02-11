package com.fryrank.handler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import java.util.HashMap;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPResponse;
import static com.fryrank.TestConstants.TEST_DELETE_REVIEW_ID;
import com.fryrank.dal.ReviewDALImpl;
import com.fryrank.domain.ReviewDomain;
import com.fryrank.model.DeleteReviewRequest;
import com.fryrank.model.exceptions.NotFoundException;
import com.fryrank.validator.APIGatewayRequestValidator;
import com.fryrank.validator.DeleteReviewRequestValidator;
import com.google.gson.Gson;

@ExtendWith(MockitoExtension.class)
public class DeleteReviewHandlerTests {

    @Mock
    private ReviewDALImpl reviewDAL;

    @Mock
    private ReviewDomain reviewDomain;

    @Mock
    private APIGatewayRequestValidator requestValidator;

    @Mock
    private DeleteReviewRequestValidator deleteReviewRequestValidator;

    @Mock
    private Context context;

    @InjectMocks
    private DeleteReviewHandler handler;

    private Gson gson;

    @BeforeEach
    public void setUp() {
        gson = new Gson();
    }

    @Test
    public void testHandleRequest_WithValidReviewId_Returns204NoContent() throws Exception {
        // Arrange
        final DeleteReviewRequest deleteRequest = new DeleteReviewRequest(TEST_DELETE_REVIEW_ID);
        final APIGatewayV2HTTPEvent event = createTestEvent(gson.toJson(deleteRequest));

        // Setup mocks
        doNothing().when(requestValidator).validateRequest(any(), any());
        doNothing().when(reviewDomain).deleteReview(any(DeleteReviewRequest.class));

        // Act
        final APIGatewayV2HTTPResponse response = handler.handleRequest(event, context);

        // Assert
        assertEquals(204, response.getStatusCode());
        verify(requestValidator).validateRequest(eq("DeleteReviewHandler"), any());
        verify(reviewDomain).deleteReview(any(DeleteReviewRequest.class));
    }

    @Test
    public void testHandleRequest_WithReviewNotFound_Returns404() throws Exception {
        // Arrange
        final DeleteReviewRequest deleteRequest = new DeleteReviewRequest(TEST_DELETE_REVIEW_ID);
        final APIGatewayV2HTTPEvent event = createTestEvent(gson.toJson(deleteRequest));
        final String errorMessage = "Review not found in database.";

        // Setup mocks
        doNothing().when(requestValidator).validateRequest(any(), any());
        doThrow(new NotFoundException(errorMessage)).when(reviewDomain).deleteReview(any(DeleteReviewRequest.class));

        // Act
        final APIGatewayV2HTTPResponse response = handler.handleRequest(event, context);

        // Assert
        assertEquals(404, response.getStatusCode());
        assertEquals(errorMessage, response.getBody());
        verify(requestValidator).validateRequest(eq("DeleteReviewHandler"), any());
        verify(reviewDomain).deleteReview(any(DeleteReviewRequest.class));
    }

    @Test
    public void testHandleRequest_WithMissingRequestBody_Returns400() throws Exception {
        // Arrange
        final APIGatewayV2HTTPEvent event = createTestEvent(null);

        // Setup mocks - requestValidator throws IllegalArgumentException for missing body
        doThrow(new IllegalArgumentException("Request body is required"))
            .when(requestValidator).validateRequest(any(), any());

        // Act
        final APIGatewayV2HTTPResponse response = handler.handleRequest(event, context);

        // Assert
        assertEquals(400, response.getStatusCode());
        assertEquals("Bad Request: Request body is required", response.getBody());
        verify(requestValidator).validateRequest(eq("DeleteReviewHandler"), any());
    }

    private APIGatewayV2HTTPEvent createTestEvent(String body) {
        final APIGatewayV2HTTPEvent event = new APIGatewayV2HTTPEvent();
        event.setBody(body);
        event.setHeaders(new HashMap<>());
        return event;
    }
}
