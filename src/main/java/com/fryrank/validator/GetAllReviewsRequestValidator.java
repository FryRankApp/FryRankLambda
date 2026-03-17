package com.fryrank.validator;

import com.fryrank.model.GetAllReviewsRequest;
import lombok.NonNull;
import org.springframework.validation.Errors;
import org.springframework.validation.Validator;

import com.fryrank.util.CursorUtils;

import static com.fryrank.Constants.REJECTION_FORMAT_CODE;

public class GetAllReviewsRequestValidator implements Validator {

    public static final String LIMIT = "limit";
    public static final String CURSOR = "cursor";
    public static final String LIMIT_REJECTION_FORMAT_REASON = "Limit must be a positive integer.";
    public static final String CURSOR_REJECTION_FORMAT_REASON = "Cursor must be a valid Base64-encoded composite cursor.";

    @Override
    public boolean supports(@NonNull Class<?> clazz) {
        return GetAllReviewsRequest.class.isAssignableFrom(clazz);
    }

    @Override
    public void validate(@NonNull Object target, @NonNull Errors errors) {
        GetAllReviewsRequest request = (GetAllReviewsRequest) target;

        // Validate limit format if provided (defaults to DEFAULT_PAGE_LIMIT if absent)
        String limitStr = request.limit();
        if (limitStr != null && !limitStr.isEmpty()) {
            try {
                int limit = Integer.parseInt(limitStr);
                if (limit <= 0) {
                    errors.rejectValue(LIMIT, REJECTION_FORMAT_CODE, LIMIT_REJECTION_FORMAT_REASON);
                }
            } catch (NumberFormatException e) {
                errors.rejectValue(LIMIT, REJECTION_FORMAT_CODE, LIMIT_REJECTION_FORMAT_REASON);
            }
        }

        // Validate cursor decodes to 3 non-empty parts
        String cursor = request.cursor();
        if (cursor != null && !cursor.isEmpty()) {
            try {
                CursorUtils.decode(cursor);
            } catch (IllegalArgumentException e) {
                errors.rejectValue(CURSOR, REJECTION_FORMAT_CODE, CURSOR_REJECTION_FORMAT_REASON);
            }
        }
    }
}
