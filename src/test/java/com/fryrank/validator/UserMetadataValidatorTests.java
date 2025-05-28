package com.fryrank.validator;

import com.fryrank.model.PublicUserMetadata;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.Errors;
import org.springframework.validation.ObjectError;

import java.util.List;

import static com.fryrank.Constants.REJECTION_REQUIRED_CODE;
import static com.fryrank.Constants.USER_METADATA_VALIDATOR_ERRORS_OBJECT_NAME;
import static com.fryrank.TestConstants.TEST_USER_METADATA_BOTH_NULL;
import static com.fryrank.TestConstants.TEST_USER_METADATA_NULL_ACCOUNT_ID;
import static com.fryrank.TestConstants.TEST_USER_METADATA_NULL_USERNAME;
import static com.fryrank.TestConstants.TEST_USER_METADATA_VALID;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
public class UserMetadataValidatorTests {

    private final UserMetadataValidator userMetadataValidator = new UserMetadataValidator();

    @Test
    public void testValidateUserMetadataSuccess() {
        Errors errors = new BeanPropertyBindingResult(TEST_USER_METADATA_VALID, USER_METADATA_VALIDATOR_ERRORS_OBJECT_NAME);
        userMetadataValidator.validate(TEST_USER_METADATA_VALID, errors);
        
        assertFalse(errors.hasErrors());
    }

    @Test
    public void testValidateUserMetadataNullUsername() {
        Errors errors = new BeanPropertyBindingResult(TEST_USER_METADATA_NULL_USERNAME, USER_METADATA_VALIDATOR_ERRORS_OBJECT_NAME);
        userMetadataValidator.validate(TEST_USER_METADATA_NULL_USERNAME, errors);
        
        assertTrue(errors.hasErrors());
        List<ObjectError> allErrors = errors.getAllErrors();
        assertEquals(1, allErrors.size());
        assertEquals(REJECTION_REQUIRED_CODE, allErrors.get(0).getCode());
        assertEquals(UserMetadataValidator.USERNAME, errors.getFieldError().getField());
    }

    @Test
    public void testValidateUserMetadataNullAccountId() {
        Errors errors = new BeanPropertyBindingResult(TEST_USER_METADATA_NULL_ACCOUNT_ID, USER_METADATA_VALIDATOR_ERRORS_OBJECT_NAME);
        userMetadataValidator.validate(TEST_USER_METADATA_NULL_ACCOUNT_ID, errors);
        
        assertTrue(errors.hasErrors());
        List<ObjectError> allErrors = errors.getAllErrors();
        assertEquals(1, allErrors.size());
        assertEquals(REJECTION_REQUIRED_CODE, allErrors.get(0).getCode());
        assertEquals(UserMetadataValidator.ACCOUNT_ID, errors.getFieldError().getField());
    }

    @Test
    public void testValidateUserMetadataBothNull() {
        Errors errors = new BeanPropertyBindingResult(TEST_USER_METADATA_BOTH_NULL, USER_METADATA_VALIDATOR_ERRORS_OBJECT_NAME);
        userMetadataValidator.validate(TEST_USER_METADATA_BOTH_NULL, errors);
        
        assertTrue(errors.hasErrors());
        List<ObjectError> allErrors = errors.getAllErrors();
        assertEquals(2, allErrors.size());
        
        assertTrue(errors.hasFieldErrors(UserMetadataValidator.USERNAME));
        assertTrue(errors.hasFieldErrors(UserMetadataValidator.ACCOUNT_ID));
    }
}
