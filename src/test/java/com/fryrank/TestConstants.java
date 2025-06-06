package com.fryrank;

import com.fryrank.model.Review;
import com.fryrank.model.PublicUserMetadata;
import com.fryrank.model.PublicUserMetadataOutput;
import com.fryrank.model.enums.QueryParam;

import java.util.ArrayList;
import java.util.List;

public class TestConstants {

    public static final String TEST_RESTAURANT_ID = "1";
    public static final String TEST_RESTAURANT_ID_1 = "ChIJl8BSSgfsj4ARi9qijghUAH0";
    public static final String TEST_REVIEW_ID_1 = "review_id_1";
    public static final String TEST_REVIEW_ID_2 = "review_id_2";
    public static final String TEST_TITLE_1 = "title_1";
    public static final String TEST_TITLE_2 = "title_2";
    public static final String TEST_BODY_1 = "body_1";
    public static final String TEST_BODY_2 = "body_2";
    public static final String TEST_ISO_DATE_TIME_1 = "1970-01-01T00:00:00Z";
    public static final String TEST_ACCOUNT_ID = "test_account_id";
    public static final String TEST_USERNAME = "testflush";

    public static final PublicUserMetadata TEST_USER_METADATA_1 = new PublicUserMetadata(
            TEST_ACCOUNT_ID,
            TEST_USERNAME
    );

    public static final PublicUserMetadata TEST_USER_METADATA_VALID = new PublicUserMetadata(
            "test-account-id", 
            "test-username"
    );

    public static final PublicUserMetadata TEST_USER_METADATA_NULL_USERNAME = new PublicUserMetadata(
            "test-account-id", 
            null
    );

    public static final PublicUserMetadata TEST_USER_METADATA_NULL_ACCOUNT_ID = new PublicUserMetadata(
            null, 
            "test-username"
    );

    public static final PublicUserMetadata TEST_USER_METADATA_BOTH_NULL = new PublicUserMetadata(
            null, 
            null
    );

    public static final Review TEST_REVIEW_1 = new Review(
            TEST_REVIEW_ID_1,
            TEST_RESTAURANT_ID,
            5.0 ,
            TEST_TITLE_1,
            TEST_BODY_1,
            TEST_ISO_DATE_TIME_1,
            TEST_ACCOUNT_ID,
            null
    );

    public static final Review TEST_REVIEW_NULL_ISO_DATETIME = new Review(
            TEST_REVIEW_ID_1,
            TEST_RESTAURANT_ID_1,
            5.0,
            TEST_TITLE_1,
            TEST_BODY_1,
            null,
            TEST_ACCOUNT_ID,
            TEST_USER_METADATA_1
    );

    public static final Review TEST_REVIEW_BAD_ISO_DATETIME = new Review(
            TEST_REVIEW_ID_1,
            TEST_RESTAURANT_ID_1,
            5.0,
            TEST_TITLE_1,
            TEST_BODY_1,
            "not-a-real-date",
            TEST_ACCOUNT_ID,
            TEST_USER_METADATA_1
    );

    public static final Review TEST_REVIEW_NULL_ACCOUNT_ID = new Review(
            TEST_REVIEW_ID_1,
            TEST_RESTAURANT_ID,
            5.0 ,
            TEST_TITLE_1,
            TEST_BODY_1,
            TEST_ISO_DATE_TIME_1,
            null,
            TEST_USER_METADATA_1
    );

    public static final List<Review> TEST_REVIEWS = new ArrayList<>() {
        {
            add(TEST_REVIEW_1);
            add(new Review(
                    TEST_REVIEW_ID_2,
                    TEST_RESTAURANT_ID,
                    7.0 ,
                    TEST_TITLE_2,
                    TEST_BODY_2,
                    TEST_ISO_DATE_TIME_1,
                    TEST_ACCOUNT_ID,
                    TEST_USER_METADATA_1)
            );
        }
    };

    public static final String TEST_REVIEWS_JSON_STRING = "{\"reviews\":[{\"reviewId\":\"review_id_1\",\"restaurantId\":\"1\",\"score\":5.0,\"title\":\"title_1\",\"body\":\"body_1\",\"isoDateTime\":\"1970-01-01T00:00:00Z\",\"accountId\":\"test_account_id\"},{\"reviewId\":\"review_id_2\",\"restaurantId\":\"1\",\"score\":7.0,\"title\":\"title_2\",\"body\":\"body_2\",\"isoDateTime\":\"1970-01-01T00:00:00Z\",\"accountId\":\"test_account_id\",\"userMetadata\":{\"accountId\":\"test_account_id\",\"username\":\"testflush\"}}]}";

    public static final String TEST_RESTAURANT_ID_2 = "ChIJ1wHcROHNj4ARmNwmP2PcUWw";
    public static final String TEST_ACCOUNT_ID_NO_USER_METADATA = "test_account_id_no_user_metadata";

    public static final String RESTAURANT_ID_ACCOUNT_ID_REQUIRED_PARAMETERS_ERROR_STRING = "'" + QueryParam.RESTAURANT_ID.getValue() + "' or '" + QueryParam.ACCOUNT_ID.getValue() + "'";

    public static final PublicUserMetadataOutput TEST_USER_METADATA_OUTPUT_1 = new PublicUserMetadataOutput(
        TEST_USERNAME
    );

    public static final String TEST_DEFAULT_NAME = "test user name";
    public static final PublicUserMetadataOutput TEST_PUBLIC_USER_METADATA_OUTPUT_WITH_DEFAULT_NAME = new PublicUserMetadataOutput(TEST_DEFAULT_NAME);

    public static final PublicUserMetadataOutput TEST_PUBLIC_USER_METADATA_OUTPUT_EMPTY = new PublicUserMetadataOutput(null);

    public static final List<PublicUserMetadata> TEST_PUBLIC_USER_METADATA_LIST = new ArrayList<>() {
        {
            add(new PublicUserMetadata(TEST_ACCOUNT_ID, TEST_USERNAME));
        }
    };
}