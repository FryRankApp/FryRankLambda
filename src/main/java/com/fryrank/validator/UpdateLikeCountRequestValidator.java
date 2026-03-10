package com.fryrank.validator;

import com.fryrank.model.UpdateLikeCountRequest;
import lombok.NonNull;
import org.springframework.validation.Errors;
import org.springframework.validation.Validator;

import static com.fryrank.Constants.REJECTION_FORMAT_CODE;
import static com.fryrank.Constants.REJECTION_REQUIRED_CODE;

public class UpdateLikeCountRequestValidator implements Validator {
    public static final String REVIEW_ID = "reviewId";
    public static final String LIKE_COUNT = "likeCount";

    public static final String REVIEW_ID_REJECTION_REQUIRED_REASON = "The review ID is required.";
    public static final String REVIEW_ID_REJECTION_FORMAT_REASON = "The review ID must be in the format 'restaurantId:accountId'.";
    public static final String LIKE_COUNT_REJECTION_REQUIRED_REASON = "The likeCount is required.";
    public static final String LIKE_COUNT_REJECTION_FORMAT_REASON = "The likeCount must be >= 0.";

    @Override
    public boolean supports(@NonNull Class<?> clazz) {
        return UpdateLikeCountRequest.class.isAssignableFrom(clazz);
    }

    @Override
    public void validate(@NonNull Object target, @NonNull Errors errors) {
        UpdateLikeCountRequest req = (UpdateLikeCountRequest) target;

        String reviewId = req.reviewId();
        if (reviewId == null || reviewId.isEmpty()) {
            errors.rejectValue(REVIEW_ID, REJECTION_REQUIRED_CODE, REVIEW_ID_REJECTION_REQUIRED_REASON);
        } else if (!reviewId.contains(":")) {
            errors.rejectValue(REVIEW_ID, REJECTION_FORMAT_CODE, REVIEW_ID_REJECTION_FORMAT_REASON);
        }

        Integer likeCount = req.likeCount();
        if (likeCount == null) {
            errors.rejectValue(LIKE_COUNT, REJECTION_REQUIRED_CODE, LIKE_COUNT_REJECTION_REQUIRED_REASON);
        } else if (likeCount < 0) {
            errors.rejectValue(LIKE_COUNT, REJECTION_FORMAT_CODE, LIKE_COUNT_REJECTION_FORMAT_REASON);
        }
    }
}

