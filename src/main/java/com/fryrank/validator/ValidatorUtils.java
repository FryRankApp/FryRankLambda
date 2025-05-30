package com.fryrank.validator;

import static com.fryrank.Constants.GENERIC_VALIDATOR_ERROR_MESSAGE;

import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.BindingResult;
import org.springframework.validation.Validator;

import lombok.NonNull;

/**
 * Utility class for common validation operations.
 */
public class ValidatorUtils {

    /**
     * Validates an object using the specified validator and object name.
     * Throws ValidatorException if validation fails.
     *
     * @param target The object to validate
     * @param objectName The name of the object for error reporting
     * @param validator The validator to use
     * @throws ValidatorException if validation fails
     */
    public static void validateAndThrow(@NonNull Object target, @NonNull String objectName, 
                                       @NonNull Validator validator) throws ValidatorException {
        BindingResult bindingResult = new BeanPropertyBindingResult(target, objectName);
        validator.validate(target, bindingResult);

        if (bindingResult.hasErrors()) {
            throw new ValidatorException(bindingResult.getAllErrors(), GENERIC_VALIDATOR_ERROR_MESSAGE);
        }
    }
}
