package com.fryrank.validator;

import lombok.Getter;
import org.springframework.validation.ObjectError;

import java.util.List;

@Getter
public class ValidatorException extends Exception {
    private final List<ObjectError> errors;
    private final String errorsString;

    public ValidatorException(List<ObjectError> errors, String message) {
        super(message);
        this.errors = errors;

        StringBuilder messageBuilder = new StringBuilder(message + "\n" + "Errors:\n");
        for(ObjectError error : errors) {
            messageBuilder.append("\t").append(error.toString()).append("\n");
        }
        errorsString = messageBuilder.toString();
    }
}