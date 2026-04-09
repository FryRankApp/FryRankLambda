package com.fryrank.validator;

import com.fryrank.model.ToggleReactionRequest;
import com.fryrank.model.enums.ReactionType;
import org.junit.jupiter.api.Test;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.Errors;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ToggleReactionRequestValidatorTest {

    private final ToggleReactionRequestValidator validator = new ToggleReactionRequestValidator();

    @Test
    void validate_allFieldsPresent_noErrors() {
        ToggleReactionRequest req = new ToggleReactionRequest("acc", "rest:REVIEW:acc", ReactionType.THUMBS_UP);
        Errors errors = new BeanPropertyBindingResult(req, "toggleReactionRequest");

        validator.validate(req, errors);

        assertFalse(errors.hasErrors());
    }

    @Test
    void validate_nullAccountId_rejects() {
        ToggleReactionRequest req = new ToggleReactionRequest(null, "r:REVIEW:a", ReactionType.HEART);
        Errors errors = new BeanPropertyBindingResult(req, "toggleReactionRequest");

        validator.validate(req, errors);

        assertTrue(errors.hasFieldErrors(ToggleReactionRequestValidator.ACCOUNT_ID));
    }

    @Test
    void validate_blankReviewId_rejects() {
        ToggleReactionRequest req = new ToggleReactionRequest("acc", "  ", ReactionType.THUMBS_DOWN);
        Errors errors = new BeanPropertyBindingResult(req, "toggleReactionRequest");

        validator.validate(req, errors);

        assertTrue(errors.hasFieldErrors(ToggleReactionRequestValidator.REVIEW_ID));
    }

    @Test
    void validate_nullReactionType_rejects() {
        ToggleReactionRequest req = new ToggleReactionRequest("acc", "r:REVIEW:a", null);
        Errors errors = new BeanPropertyBindingResult(req, "toggleReactionRequest");

        validator.validate(req, errors);

        assertTrue(errors.hasFieldErrors(ToggleReactionRequestValidator.REACTION_TYPE));
    }
}
