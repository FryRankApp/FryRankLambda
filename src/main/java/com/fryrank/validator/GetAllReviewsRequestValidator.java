package com.fryrank.validator;

import com.fryrank.model.GetAllReviewsRequest;
import lombok.NonNull;
import org.springframework.validation.Errors;
import org.springframework.validation.Validator;

import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;

import static com.fryrank.Constants.REJECTION_FORMAT_CODE;
import static com.fryrank.Constants.REJECTION_REQUIRED_CODE;

public class GetAllReviewsRequestValidator implements Validator {

    public static final String LIMIT = "limit";
    public static final String CURSOR = "cursor";
    public static final String LIMIT_REJECTION_REQUIRED_REASON = "The limit query parameter is required.";
    public static final String LIMIT_REJECTION_FORMAT_REASON = "Limit must be a positive integer.";
    public static final String CURSOR_REJECTION_FORMAT_REASON = "Cursor must be a valid ISO-8601 datetime.";

    @Override
    public boolean supports(@NonNull Class<?> clazz) {
        return GetAllReviewsRequest.class.isAssignableFrom(clazz);
    }

    @Override
    public void validate(@NonNull Object target, @NonNull Errors errors) {
        GetAllReviewsRequest request = (GetAllReviewsRequest) target;

        // Validate limit is present
        String limitStr = request.limit();
        if (limitStr == null || limitStr.isEmpty()) {
            errors.rejectValue(LIMIT, REJECTION_REQUIRED_CODE, LIMIT_REJECTION_REQUIRED_REASON);
            return;
        }

        // Validate limit is a positive integer
        int limit;
        try {
            limit = Integer.parseInt(limitStr);
        } catch (NumberFormatException e) {
            errors.rejectValue(LIMIT, REJECTION_FORMAT_CODE, LIMIT_REJECTION_FORMAT_REASON);
            return;
        }
        if (limit <= 0) {
            errors.rejectValue(LIMIT, REJECTION_FORMAT_CODE, LIMIT_REJECTION_FORMAT_REASON);
        }

        // Validate cursor format if present
        String cursor = request.cursor();
        if (cursor != null && !cursor.isEmpty()) {
            try {
                OffsetDateTime.parse(cursor);
            } catch (DateTimeParseException e) {
                errors.rejectValue(CURSOR, REJECTION_FORMAT_CODE, CURSOR_REJECTION_FORMAT_REASON);
            }
        }
    }
}
