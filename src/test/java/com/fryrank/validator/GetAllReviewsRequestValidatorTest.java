package com.fryrank.validator;

import com.fryrank.model.GetAllReviewsRequest;
import org.junit.jupiter.api.Test;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.Errors;

import static com.fryrank.TestConstants.TEST_CURSOR_1;
import static com.fryrank.TestConstants.TEST_ISO_DATE_TIME_1;
import static com.fryrank.TestConstants.TEST_RESTAURANT_ID;
import static com.fryrank.validator.GetAllReviewsRequestValidator.CURSOR_REJECTION_FORMAT_REASON;
import static com.fryrank.validator.GetAllReviewsRequestValidator.LIMIT_REJECTION_FORMAT_REASON;
import static com.fryrank.validator.GetAllReviewsRequestValidator.LIMIT_REJECTION_REQUIRED_REASON;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class GetAllReviewsRequestValidatorTest {

    private final GetAllReviewsRequestValidator validator = new GetAllReviewsRequestValidator();

    private Errors validate(GetAllReviewsRequest request) {
        Errors errors = new BeanPropertyBindingResult(request, "getAllReviewsRequest");
        validator.validate(request, errors);
        return errors;
    }

    @Test
    public void testValidate_validLimitNoCursor_noErrors() {
        Errors errors = validate(new GetAllReviewsRequest(TEST_RESTAURANT_ID, null, "10", null));
        assertFalse(errors.hasErrors());
    }

    @Test
    public void testValidate_validLimitAndCompositeCursor_noErrors() {
        Errors errors = validate(new GetAllReviewsRequest(TEST_RESTAURANT_ID, null, "10", TEST_CURSOR_1));
        assertFalse(errors.hasErrors());
    }

    @Test
    public void testValidate_rawDatetimeCursor_rejected() {
        Errors errors = validate(new GetAllReviewsRequest(TEST_RESTAURANT_ID, null, "10", TEST_ISO_DATE_TIME_1));
        assertTrue(errors.hasErrors());
        assertEquals(1, errors.getErrorCount());
        assertEquals("cursor", errors.getFieldError().getField());
        assertEquals(CURSOR_REJECTION_FORMAT_REASON, errors.getFieldError().getDefaultMessage());
    }

    @Test
    public void testValidate_garbageCursor_rejected() {
        Errors errors = validate(new GetAllReviewsRequest(TEST_RESTAURANT_ID, null, "10", "not-a-valid-cursor"));
        assertTrue(errors.hasErrors());
        assertEquals("cursor", errors.getFieldError().getField());
        assertEquals(CURSOR_REJECTION_FORMAT_REASON, errors.getFieldError().getDefaultMessage());
    }

    @Test
    public void testValidate_nullLimit_rejected() {
        Errors errors = validate(new GetAllReviewsRequest(TEST_RESTAURANT_ID, null, null, null));
        assertTrue(errors.hasErrors());
        assertEquals("limit", errors.getFieldError().getField());
        assertEquals(LIMIT_REJECTION_REQUIRED_REASON, errors.getFieldError().getDefaultMessage());
    }

    @Test
    public void testValidate_negativeLimit_rejected() {
        Errors errors = validate(new GetAllReviewsRequest(TEST_RESTAURANT_ID, null, "-1", null));
        assertTrue(errors.hasErrors());
        assertEquals("limit", errors.getFieldError().getField());
        assertEquals(LIMIT_REJECTION_FORMAT_REASON, errors.getFieldError().getDefaultMessage());
    }

    @Test
    public void testValidate_nonIntegerLimit_rejected() {
        Errors errors = validate(new GetAllReviewsRequest(TEST_RESTAURANT_ID, null, "abc", null));
        assertTrue(errors.hasErrors());
        assertEquals("limit", errors.getFieldError().getField());
        assertEquals(LIMIT_REJECTION_FORMAT_REASON, errors.getFieldError().getDefaultMessage());
    }

    @Test
    public void testSupports_withGetAllReviewsRequest_returnsTrue() {
        assertTrue(validator.supports(GetAllReviewsRequest.class));
    }
}
