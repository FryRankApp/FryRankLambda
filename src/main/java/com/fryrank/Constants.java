package com.fryrank;

import java.util.Set;

/**
 * Constants used throughout the application.
 */
public class Constants {

    public static final String ISO_DATE_TIME = "isoDateTime";

    // SSM Parameter Store
    public static final String DATABASE_URI_PARAMETER_NAME_ENV_VAR = "SSM_DATABASE_URI_PARAMETER_KEY";
    public static final String GOOGLE_CLIENT_ID_PARAMETER_NAME_ENV_VAR = "SSM_GOOGLE_CLIENT_ID_PARAMETER_KEY";
    public static final String SSM_DISABLE_AUTH_PARAMETER_NAME_ENV_VAR = "SSM_DISABLE_AUTH_PARAMETER_KEY";

    // Input Validator
    public static final String GENERIC_VALIDATOR_ERROR_MESSAGE = "Encountered error while validating API input.";
    public static final String REVIEW_VALIDATOR_ERRORS_OBJECT_NAME = "review";
    public static final String DELETE_REVIEW_REQUEST_VALIDATOR_ERRORS_OBJECT_NAME = "DeleteReviewRequest";
    public static final String USER_METADATA_VALIDATOR_ERRORS_OBJECT_NAME = "userMetadata";
    public static final String REJECTION_REQUIRED_CODE = "field.required";
    public static final String REJECTION_FORMAT_CODE = "field.invalidFormat";

    // MongoDB keys
    public static final String ACCOUNT_ID_KEY = "accountId";

    // DynamoDB attribute names
    public static final String RESTAURANT_ID_KEY = "restaurantId";
    public static final String IDENTIFIER_KEY = "identifier";
    public static final String ISO_DATE_TIME_KEY = "isoDateTime";
    public static final String TOTAL_SCORE_KEY = "totalScore";
    public static final String REVIEW_COUNT_KEY = "reviewCount";
    public static final String AVERAGE_SCORE_KEY = "averageScore";
    public static final String SCORE_KEY = "score";
    public static final String TITLE_KEY = "title";
    public static final String BODY_KEY = "body";
    public static final String USERNAME_KEY = "username";
    public static final String IS_REVIEW_KEY = "isReview";
    public static final String IS_REVIEW_VALUE = "true";

    // DynamoDB GSI names
    public static final String RESTAURANT_ID_TIME_INDEX = "restaurantId-time-index";
    public static final String ACCOUNT_ID_TIME_INDEX = "accountId-time-index";
    public static final String RECENT_REVIEWS_INDEX = "recent-reviews-index";

    // Handler class names
    public static final String ADD_NEW_REVIEW_HANDLER = "AddNewReviewForRestaurantHandler";
    public static final String DELETE_EXISTING_REVIEW_HANDLER = "DeleteReviewHandler";
    public static final String GET_ALL_REVIEWS_HANDLER = "GetAllReviewsHandler";
    public static final String GET_AGGREGATE_REVIEW_HANDLER = "GetAggregateReviewInformationHandler";
    public static final String GET_RECENT_REVIEWS_HANDLER = "GetRecentReviewsHandler";
    public static final String GET_PUBLIC_USER_METADATA_HANDLER = "GetPublicUserMetadataHandler";
    public static final String PUT_PUBLIC_USER_METADATA_HANDLER = "PutPublicUserMetadataHandler";
    public static final String UPSERT_PUBLIC_USER_METADATA_HANDLER = "UpsertPublicUserMetadataHandler";

    // Allowed Origins
    public static final String LOCALHOST = "http://localhost:3000";
    public static final String FRYRANK_STAGE_OXYSERVER = "https://fryrank-beta-stage.oxyserver.com";
    public static final String FRYRANK_STAGE_CLOUDFRONT = "https://d1jqnaqp1g7d1o.cloudfront.net";
    public static final String FRYRANK_BETA = "https://beta.fryrank.app";
    public static final String FRYRANK_BETA_STAGE_ALT_URL = "https://pure-temple-61679-beta-stage-84eefac76015.herokuapp.com";
    public static final String FRYRANK_PROD_OXYSERVER = "https://fryrank.oxyserver.com";
    public static final String FRYRANK_PROD = "https://fryrank.app";
    public static final String FRYRANK_PROD_WWW = "https://www.fryrank.app";
    public static final String FRYRANK_PROD_ALT_URL = "https://pure-temple-61679-98a4d5c2d04e.herokuapp.com";
    public static final String FRYRANK_PROD_CLOUDFRONT = "https://d3h6a05rzfj3y8.cloudfront.net";
    public static final Set<String> ALLOWED_ORIGINS = Set.of(
        LOCALHOST, 
        FRYRANK_STAGE_OXYSERVER,
        FRYRANK_BETA_STAGE_ALT_URL,
        FRYRANK_BETA,
        FRYRANK_STAGE_CLOUDFRONT,
        FRYRANK_PROD,
        FRYRANK_PROD_ALT_URL,
        FRYRANK_PROD_CLOUDFRONT,
        FRYRANK_PROD_OXYSERVER,
        FRYRANK_PROD_WWW
    );

    // Authorization Error Messages
    public static final String AUTH_ERROR_MISSING_OR_INVALID_HEADER = "Unauthorized: Missing or invalid authorization header";
    public static final String AUTH_ERROR_INVALID_TOKEN = "Unauthorized: Invalid token";
    public static final String AUTH_ERROR_VERIFICATION_FAILED = "Unauthorized: Token verification failed";

    // Headers
    public static final String HEADER_ACCESS_CONTROL_ALLOW_ORIGIN = "Access-Control-Allow-Origin";
    public static final String HEADER_ACCESS_CONTROL_ALLOW_METHODS = "Access-Control-Allow-Methods";
    public static final String HEADER_ACCESS_CONTROL_ALLOW_HEADERS = "Access-Control-Allow-Headers";
    public static final String ALLOWED_METHODS = "GET, POST, PUT, OPTIONS";
    public static final String ORIGIN = "origin";
    public static final String CONTENT_TYPE = "Content-Type";
}
