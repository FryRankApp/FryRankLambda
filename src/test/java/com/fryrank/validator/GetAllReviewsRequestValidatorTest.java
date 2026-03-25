package com.fryrank.validator;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.Errors;

import com.fryrank.model.GetAllReviewsRequest;

import static com.fryrank.validator.GetAllReviewsRequestValidator.LIMIT;
import static com.fryrank.validator.GetAllReviewsRequestValidator.LIMIT_REJECTION_FORMAT_REASON;

public class GetAllReviewsRequestValidatorTest {

    private final GetAllReviewsRequestValidator validator = new GetAllReviewsRequestValidator();

    @Test
    public void testValidate_WithNullLimit_NoErrors() {
        // Arrange
        GetAllReviewsRequest request = new GetAllReviewsRequest(null, null, null, null);
        Errors errors = new BeanPropertyBindingResult(request, "getAllReviewsRequest");

        // Act
        validator.validate(request, errors);

        // Assert
        assertFalse(errors.hasErrors());
    }

    @Test
    public void testValidate_WithEmptyLimit_NoErrors() {
        // Arrange
        GetAllReviewsRequest request = new GetAllReviewsRequest(null, null, "", null);
        Errors errors = new BeanPropertyBindingResult(request, "getAllReviewsRequest");

        // Act
        validator.validate(request, errors);

        // Assert
        assertFalse(errors.hasErrors());
    }

    @Test
    public void testValidate_WithValidPositiveLimit_NoErrors() {
        // Arrange
        GetAllReviewsRequest request = new GetAllReviewsRequest(null, null, "10", null);
        Errors errors = new BeanPropertyBindingResult(request, "getAllReviewsRequest");

        // Act
        validator.validate(request, errors);

        // Assert
        assertFalse(errors.hasErrors());
    }

    @Test
    public void testValidate_WithLimitOfOne_NoErrors() {
        // Arrange
        GetAllReviewsRequest request = new GetAllReviewsRequest(null, null, "1", null);
        Errors errors = new BeanPropertyBindingResult(request, "getAllReviewsRequest");

        // Act
        validator.validate(request, errors);

        // Assert
        assertFalse(errors.hasErrors());
    }

    @Test
    public void testValidate_WithLimitOfZero_AddsError() {
        // Arrange
        GetAllReviewsRequest request = new GetAllReviewsRequest(null, null, "0", null);
        Errors errors = new BeanPropertyBindingResult(request, "getAllReviewsRequest");

        // Act
        validator.validate(request, errors);

        // Assert
        assertTrue(errors.hasErrors());
        assertEquals(1, errors.getErrorCount());
        assertEquals(LIMIT, errors.getFieldError().getField());
        assertEquals(LIMIT_REJECTION_FORMAT_REASON, errors.getFieldError().getDefaultMessage());
    }

    @Test
    public void testValidate_WithNegativeLimit_AddsError() {
        // Arrange
        GetAllReviewsRequest request = new GetAllReviewsRequest(null, null, "-1", null);
        Errors errors = new BeanPropertyBindingResult(request, "getAllReviewsRequest");

        // Act
        validator.validate(request, errors);

        // Assert
        assertTrue(errors.hasErrors());
        assertEquals(1, errors.getErrorCount());
        assertEquals(LIMIT, errors.getFieldError().getField());
        assertEquals(LIMIT_REJECTION_FORMAT_REASON, errors.getFieldError().getDefaultMessage());
    }

    @Test
    public void testValidate_WithNonNumericLimit_AddsError() {
        // Arrange
        GetAllReviewsRequest request = new GetAllReviewsRequest(null, null, "abc", null);
        Errors errors = new BeanPropertyBindingResult(request, "getAllReviewsRequest");

        // Act
        validator.validate(request, errors);

        // Assert
        assertTrue(errors.hasErrors());
        assertEquals(1, errors.getErrorCount());
        assertEquals(LIMIT, errors.getFieldError().getField());
        assertEquals(LIMIT_REJECTION_FORMAT_REASON, errors.getFieldError().getDefaultMessage());
    }

    @Test
    public void testValidate_WithDecimalLimit_AddsError() {
        // Arrange
        GetAllReviewsRequest request = new GetAllReviewsRequest(null, null, "5.5", null);
        Errors errors = new BeanPropertyBindingResult(request, "getAllReviewsRequest");

        // Act
        validator.validate(request, errors);

        // Assert
        assertTrue(errors.hasErrors());
        assertEquals(1, errors.getErrorCount());
        assertEquals(LIMIT, errors.getFieldError().getField());
        assertEquals(LIMIT_REJECTION_FORMAT_REASON, errors.getFieldError().getDefaultMessage());
    }

    @Test
    public void testValidate_WithLimitAboveMax_NoErrors() {
        // Arrange
        GetAllReviewsRequest request = new GetAllReviewsRequest(null, null, "200", null);
        Errors errors = new BeanPropertyBindingResult(request, "getAllReviewsRequest");

        // Act
        validator.validate(request, errors);

        // Assert
        assertFalse(errors.hasErrors());
    }

    @Test
    public void testSupports_WithGetAllReviewsRequest_ReturnsTrue() {
        assertTrue(validator.supports(GetAllReviewsRequest.class));
    }

    @Test
    public void testSupports_WithOtherClass_ReturnsFalse() {
        assertFalse(validator.supports(String.class));
    }
}
