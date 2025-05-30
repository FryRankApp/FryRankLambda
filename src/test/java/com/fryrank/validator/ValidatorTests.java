package com.fryrank.validator;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.Errors;
import org.springframework.validation.ObjectError;

import java.util.List;

import static com.fryrank.Constants.REJECTION_FORMAT_CODE;
import static com.fryrank.Constants.REJECTION_REQUIRED_CODE;
import static com.fryrank.Constants.REVIEW_VALIDATOR_ERRORS_OBJECT_NAME;

import static com.fryrank.TestConstants.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(MockitoExtension.class)
public class ValidatorTests {

    private final ReviewValidator reviewValidator = new ReviewValidator();

    @Test
    public void testValidateReviewSuccess() {
        Errors errors = new BeanPropertyBindingResult(TEST_REVIEW_1, REVIEW_VALIDATOR_ERRORS_OBJECT_NAME);
        reviewValidator.validate(TEST_REVIEW_1, errors);

        assertFalse(errors.hasErrors());
    }

    @Test
    public void testValidateReviewNullISODateTime() {
        Errors errors = new BeanPropertyBindingResult(TEST_REVIEW_NULL_ISO_DATETIME, REVIEW_VALIDATOR_ERRORS_OBJECT_NAME);
        reviewValidator.validate(TEST_REVIEW_NULL_ISO_DATETIME, errors);

        assertTrue(errors.hasErrors());
        List<ObjectError> allErrors = errors.getAllErrors();
        assertEquals(1, allErrors.size());
        assertEquals(REJECTION_REQUIRED_CODE, allErrors.get(0).getCode());
    }

    @Test
    public void testValidateReviewBadFormatISODateTime() {
        Errors errors = new BeanPropertyBindingResult(TEST_REVIEW_BAD_ISO_DATETIME, REVIEW_VALIDATOR_ERRORS_OBJECT_NAME);
        reviewValidator.validate(TEST_REVIEW_BAD_ISO_DATETIME, errors);

        assertTrue(errors.hasErrors());
        List<ObjectError> allErrors = errors.getAllErrors();
        assertEquals(1, allErrors.size());
        assertEquals(REJECTION_FORMAT_CODE, allErrors.get(0).getCode());
    }

    @Test
    public void testValidateReviewNullAccountId() {
        Errors errors = new BeanPropertyBindingResult(TEST_REVIEW_NULL_ACCOUNT_ID, REVIEW_VALIDATOR_ERRORS_OBJECT_NAME);
        reviewValidator.validate(TEST_REVIEW_NULL_ACCOUNT_ID, errors);

        assertTrue(errors.hasErrors());
        List<ObjectError> allErrors = errors.getAllErrors();
        assertEquals(1, allErrors.size());
        assertEquals(REJECTION_REQUIRED_CODE, allErrors.get(0).getCode());
    }
}