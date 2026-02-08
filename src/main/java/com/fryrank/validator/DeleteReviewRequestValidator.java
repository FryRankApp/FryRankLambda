package com.fryrank.validator;

import lombok.NonNull;
import com.fryrank.model.DeleteReviewRequest;
import org.springframework.validation.Errors;
import org.springframework.validation.Validator;

import static com.fryrank.Constants.REJECTION_FORMAT_CODE;
import static com.fryrank.Constants.REJECTION_REQUIRED_CODE;

public class DeleteReviewRequestValidator implements Validator {
    public static final String REVIEW_ID = "reviewId";
    public static final String REVIEW_ID_REJECTION_REQUIRED_REASON = "The review ID is required.";
    public static final String REVIEW_ID_REJECTION_FORMAT_REASON = "The review ID must be in the format 'restaurantId:accountId'.";

    @Override
    public boolean supports(@NonNull Class<?> clazz) {
        return DeleteReviewRequest.class.isAssignableFrom(clazz);
    }

    @Override
    public void validate(@NonNull Object target, @NonNull Errors errors) {
        DeleteReviewRequest deleteReviewInfo = (DeleteReviewRequest) target;
        String reviewId = deleteReviewInfo.getReviewId();

        // Validate review ID is not null or empty
        if (reviewId == null || reviewId.isEmpty()) {
            errors.rejectValue(REVIEW_ID, REJECTION_REQUIRED_CODE, REVIEW_ID_REJECTION_REQUIRED_REASON);
            return;
        }

        // Validate format: should contain a colon (restaurantId:accountId format)
        if (!reviewId.contains(":")) {
            errors.rejectValue(REVIEW_ID, REJECTION_FORMAT_CODE, REVIEW_ID_REJECTION_FORMAT_REASON);
        }
    }
}
