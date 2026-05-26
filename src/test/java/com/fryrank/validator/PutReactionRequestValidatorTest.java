package com.fryrank.validator;

import com.fryrank.model.PutReactionRequest;
import com.fryrank.model.enums.ReactionAction;
import com.fryrank.model.enums.ReactionType;
import org.junit.jupiter.api.Test;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.Errors;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PutReactionRequestValidatorTest {

    private final PutReactionRequestValidator validator = new PutReactionRequestValidator();

    @Test
    void validate_allFieldsPresent_noErrors() {
        PutReactionRequest req = new PutReactionRequest(
                "acc", "rest:REVIEW:acc", ReactionType.THUMBS_UP, ReactionAction.ADD);
        Errors errors = new BeanPropertyBindingResult(req, "putReactionRequest");

        validator.validate(req, errors);

        assertFalse(errors.hasErrors());
    }

    @Test
    void validate_nullAccountId_rejects() {
        PutReactionRequest req = new PutReactionRequest(null, "r:REVIEW:a", ReactionType.HEART, ReactionAction.REMOVE);
        Errors errors = new BeanPropertyBindingResult(req, "putReactionRequest");

        validator.validate(req, errors);

        assertTrue(errors.hasFieldErrors(PutReactionRequestValidator.ACCOUNT_ID));
    }

    @Test
    void validate_blankReviewId_rejects() {
        PutReactionRequest req = new PutReactionRequest("acc", "  ", ReactionType.THUMBS_DOWN, ReactionAction.ADD);
        Errors errors = new BeanPropertyBindingResult(req, "putReactionRequest");

        validator.validate(req, errors);

        assertTrue(errors.hasFieldErrors(PutReactionRequestValidator.REVIEW_ID));
    }

    @Test
    void validate_nullReactionType_rejects() {
        PutReactionRequest req = new PutReactionRequest("acc", "r:REVIEW:a", null, ReactionAction.ADD);
        Errors errors = new BeanPropertyBindingResult(req, "putReactionRequest");

        validator.validate(req, errors);

        assertTrue(errors.hasFieldErrors(PutReactionRequestValidator.REACTION_TYPE));
    }

    @Test
    void validate_nullAction_rejects() {
        PutReactionRequest req = new PutReactionRequest("acc", "r:REVIEW:a", ReactionType.THUMBS_UP, null);
        Errors errors = new BeanPropertyBindingResult(req, "putReactionRequest");

        validator.validate(req, errors);

        assertTrue(errors.hasFieldErrors(PutReactionRequestValidator.ACTION));
    }
}
