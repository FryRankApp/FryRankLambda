package com.fryrank;

import java.util.Set;

/**
 * Constants used throughout the application.
 */
public class Constants {

    public static final String ISO_DATE_TIME = "isoDateTime";

    // SSM Parameter Store
    public static final String DATABASE_URI_PARAMETER_NAME_ENV_VAR = "SSM_DATABASE_URI_PARAMETER_KEY";

    // Input Validator
    public static final String GENERIC_VALIDATOR_ERROR_MESSAGE = "Encountered error while validating API input.";
    public static final String REVIEW_VALIDATOR_ERRORS_OBJECT_NAME = "review";
    public static final String USER_METADATA_VALIDATOR_ERRORS_OBJECT_NAME = "userMetadata";
    public static final String DELETE_REVIEW_INFO_VALIDATOR_ERRORS_OBJECT_NAME = "deleteReviewInfo";
    public static final String REJECTION_REQUIRED_CODE = "field.required";
    public static final String REJECTION_FORMAT_CODE = "field.invalidFormat";

    // Output field names
    public static final String USER_METADATA_OUTPUT_FIELD_NAME = "userMetadata";

    // MongoDB keys
    public static final String ACCOUNT_ID_KEY = "accountId";
    public static final String PRIMARY_KEY = "_id";

    // MongoDB collection names
    public static final String REVIEW_COLLECTION_NAME = "review";
    public static final String PUBLIC_USER_METADATA_COLLECTION_NAME = "user-metadata";

    // Handler class names
    public static final String ADD_NEW_REVIEW_HANDLER = "AddNewReviewForRestaurantHandler";
    public static final String DELETE_EXISTING_REVIEW_HANDLER = "DeleteExistingReviewForRestaurantHandler";
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

    // Headers
    public static final String HEADER_ACCESS_CONTROL_ALLOW_ORIGIN = "Access-Control-Allow-Origin";
    public static final String HEADER_ACCESS_CONTROL_ALLOW_METHODS = "Access-Control-Allow-Methods";
    public static final String HEADER_ACCESS_CONTROL_ALLOW_HEADERS = "Access-Control-Allow-Headers";
    public static final String ALLOWED_METHODS = "GET, POST, PUT, OPTIONS";
    public static final String ORIGIN = "origin";
    public static final String CONTENT_TYPE = "Content-Type";
}
