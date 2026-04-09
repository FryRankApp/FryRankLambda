package com.fryrank.validator;

import com.fryrank.model.ToggleReactionRequest;
import lombok.NonNull;
import org.springframework.validation.Errors;
import org.springframework.validation.Validator;

import static com.fryrank.Constants.REJECTION_REQUIRED_CODE;

/**
 * Validates {@link ToggleReactionRequest} bodies for reaction toggle.
 */
public class ToggleReactionRequestValidator implements Validator {

    public static final String ACCOUNT_ID = "accountId";
    public static final String REVIEW_ID = "reviewId";
    public static final String REACTION_TYPE = "reactionType";

    public static final String ACCOUNT_ID_REQUIRED = "The account ID is required.";
    public static final String REVIEW_ID_REQUIRED = "The review ID is required.";
    public static final String REACTION_TYPE_REQUIRED = "The reaction type is required.";

    @Override
    public boolean supports(@NonNull Class<?> clazz) {
        return ToggleReactionRequest.class.isAssignableFrom(clazz);
    }

    @Override
    public void validate(@NonNull Object target, @NonNull Errors errors) {
        ToggleReactionRequest req = (ToggleReactionRequest) target;

        if (req.accountId() == null || req.accountId().isBlank()) {
            errors.rejectValue(ACCOUNT_ID, REJECTION_REQUIRED_CODE, ACCOUNT_ID_REQUIRED);
        }
        if (req.reviewId() == null || req.reviewId().isBlank()) {
            errors.rejectValue(REVIEW_ID, REJECTION_REQUIRED_CODE, REVIEW_ID_REQUIRED);
        }
        if (req.reactionType() == null) {
            errors.rejectValue(REACTION_TYPE, REJECTION_REQUIRED_CODE, REACTION_TYPE_REQUIRED);
        }
    }
}
