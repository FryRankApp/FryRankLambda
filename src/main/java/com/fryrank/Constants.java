package com.fryrank;

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
    public static final String GET_ALL_REVIEWS_HANDLER = "GetAllReviewsHandler";
    public static final String GET_AGGREGATE_REVIEW_HANDLER = "GetAggregateReviewInformationHandler";
    public static final String GET_RECENT_REVIEWS_HANDLER = "GetRecentReviewsHandler";
    public static final String GET_PUBLIC_USER_METADATA_HANDLER = "GetPublicUserMetadataHandler";
    public static final String PUT_PUBLIC_USER_METADATA_HANDLER = "PutPublicUserMetadataHandler";
    public static final String UPSERT_PUBLIC_USER_METADATA_HANDLER = "UpsertPublicUserMetadataHandler";

}
