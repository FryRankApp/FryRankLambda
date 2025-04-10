package com.fryrank;

/**
 * Constants used throughout the application.
 */
public class Constants {

    public static final String ISO_DATE_TIME = "isoDateTime";

    // Input Validator
    public static final String REVIEW_VALIDATOR_ERRORS_OBJECT_NAME = "review";
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

    /**
     * Environment variable name for the MongoDB connection URI.
     */
    public static final String DATABASE_URI_ENV_VAR = "DATABASE_URI";

    // Query parameters
    public static final String RESTAURANT_ID_QUERY_PARAM = "restaurantId";
    public static final String ACCOUNT_ID_QUERY_PARAM = "accountId";
    public static final String COUNT_QUERY_PARAM = "count";
}
