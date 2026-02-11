package com.fryrank.validator;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.Errors;

import com.fryrank.model.DeleteReviewRequest;

import static com.fryrank.TestConstants.TEST_DELETE_REVIEW_ID;
import static com.fryrank.TestConstants.TEST_DELETE_REVIEW_ID_NO_COLON;

public class DeleteReviewRequestValidatorTest {

    private final DeleteReviewRequestValidator deleteReviewRequestValidator = new DeleteReviewRequestValidator();

    @Test
    public void testValidate_WithValidReviewId_NoErrors() {
        // Arrange
        DeleteReviewRequest request = new DeleteReviewRequest(TEST_DELETE_REVIEW_ID);
        Errors errors = new BeanPropertyBindingResult(request, "deleteReviewRequest");

        // Act
        deleteReviewRequestValidator.validate(request, errors);

        // Assert
        assertFalse(errors.hasErrors());
    }

    @Test
    public void testValidate_WithNullReviewId_AddsError() {
        // Arrange
        DeleteReviewRequest request = new DeleteReviewRequest(null);
        Errors errors = new BeanPropertyBindingResult(request, "deleteReviewRequest");

        // Act
        deleteReviewRequestValidator.validate(request, errors);

        // Assert
        assertTrue(errors.hasErrors());
        assertEquals(1, errors.getErrorCount());
        assertEquals("reviewId", errors.getFieldError().getField());
        assertEquals("The review ID is required.", errors.getFieldError().getDefaultMessage());
    }

    @Test
    public void testValidate_WithEmptyReviewId_AddsError() {
        // Arrange
        DeleteReviewRequest request = new DeleteReviewRequest("");
        Errors errors = new BeanPropertyBindingResult(request, "deleteReviewRequest");

        // Act
        deleteReviewRequestValidator.validate(request, errors);

        // Assert
        assertTrue(errors.hasErrors());
        assertEquals("reviewId", errors.getFieldError().getField());
        assertEquals("The review ID is required.", errors.getFieldError().getDefaultMessage());
    }

    @Test
    public void testValidate_WithMissingColon_AddsError() {
        // Arrange
        DeleteReviewRequest request = new DeleteReviewRequest(TEST_DELETE_REVIEW_ID_NO_COLON);
        Errors errors = new BeanPropertyBindingResult(request, "deleteReviewRequest");

        // Act
        deleteReviewRequestValidator.validate(request, errors);

        // Assert
        assertTrue(errors.hasErrors());
        assertEquals("reviewId", errors.getFieldError().getField());
        assertEquals("The review ID must be in the format 'restaurantId:accountId'.", 
                     errors.getFieldError().getDefaultMessage());
    }

    @Test
    public void testSupports_WithDeleteReviewRequest_ReturnsTrue() {
        assertTrue(deleteReviewRequestValidator.supports(DeleteReviewRequest.class));
    }
}
