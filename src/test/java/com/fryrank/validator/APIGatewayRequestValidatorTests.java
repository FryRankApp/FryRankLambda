package com.fryrank.validator;

import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPEvent;
import com.fryrank.model.enums.QueryParam;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.Map;

import static com.fryrank.Constants.*;
import static com.fryrank.TestConstants.ACCOUNT_ID_REQUIRED_ERROR;
import static com.fryrank.TestConstants.QUERY_PARAMS_REQUIRED_ERROR;
import static com.fryrank.TestConstants.REQUEST_BODY_REQUIRED_ERROR;
import static com.fryrank.TestConstants.TEST_ACCOUNT_ID_PARAM;
import static com.fryrank.TestConstants.TEST_COUNT_PARAM;
import static com.fryrank.TestConstants.TEST_EMPTY_BODY;
import static com.fryrank.TestConstants.TEST_IDS_PARAM;
import static com.fryrank.TestConstants.TEST_RESTAURANT_ID_PARAM;
import static com.fryrank.TestConstants.TEST_UNSUPPORTED_HANDLER;
import static com.fryrank.TestConstants.TEST_USERNAME_PARAM;
import static com.fryrank.TestConstants.TEST_VALID_BODY;
import static com.fryrank.TestConstants.UNSUPPORTED_HANDLER_ERROR;
import static com.fryrank.TestConstants.USERNAME_REQUIRED_ERROR;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@ExtendWith(MockitoExtension.class)
public class APIGatewayRequestValidatorTests {

    private APIGatewayRequestValidator validator;
    private APIGatewayV2HTTPEvent request;

    @BeforeEach
    public void setup() {
        validator = new APIGatewayRequestValidator();
        request = new APIGatewayV2HTTPEvent();
    }

    @Test
    public void testValidateAddNewReviewHandlerValidBody() {
        request.setBody(TEST_VALID_BODY);
        assertDoesNotThrow(() -> validator.validateRequest(ADD_NEW_REVIEW_HANDLER, request));
    }

    @Test
    public void testValidateAddNewReviewHandlerNullBody() {
        request.setBody(null);
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, 
            () -> validator.validateRequest(ADD_NEW_REVIEW_HANDLER, request));
        assertEquals(REQUEST_BODY_REQUIRED_ERROR, exception.getMessage());
    }

    @Test
    public void testValidateAddNewReviewHandlerEmptyBody() {
        request.setBody(TEST_EMPTY_BODY);
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, 
            () -> validator.validateRequest(ADD_NEW_REVIEW_HANDLER, request));
        assertEquals(REQUEST_BODY_REQUIRED_ERROR, exception.getMessage());
    }

    @Test
    public void testValidateUpsertPublicUserMetadataHandlerValidBody() {
        request.setBody(TEST_VALID_BODY);
        assertDoesNotThrow(() -> validator.validateRequest(UPSERT_PUBLIC_USER_METADATA_HANDLER, request));
    }

    @Test
    public void testValidateUpsertPublicUserMetadataHandlerNullBody() {
        request.setBody(null);
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, 
            () -> validator.validateRequest(UPSERT_PUBLIC_USER_METADATA_HANDLER, request));
        assertEquals(REQUEST_BODY_REQUIRED_ERROR, exception.getMessage());
    }

    @Test
    public void testValidateGetAllReviewsHandlerValidParams() {
        Map<String, String> params = new HashMap<>();
        params.put(QueryParam.RESTAURANT_ID.getValue(), TEST_RESTAURANT_ID_PARAM);
        params.put(QueryParam.ACCOUNT_ID.getValue(), TEST_ACCOUNT_ID_PARAM);
        request.setQueryStringParameters(params);
        
        assertDoesNotThrow(() -> validator.validateRequest(GET_ALL_REVIEWS_HANDLER, request));
    }

    @Test
    public void testValidateGetAllReviewsHandlerNullParams() {
        request.setQueryStringParameters(null);
        
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, 
            () -> validator.validateRequest(GET_ALL_REVIEWS_HANDLER, request));
        assertEquals(QUERY_PARAMS_REQUIRED_ERROR, exception.getMessage());
    }

    @Test
    public void testValidateGetAllReviewsHandlerMissingParams() {
        Map<String, String> params = new HashMap<>();
        params.put(QueryParam.RESTAURANT_ID.getValue(), TEST_RESTAURANT_ID_PARAM);
        request.setQueryStringParameters(params);
        
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, 
            () -> validator.validateRequest(GET_ALL_REVIEWS_HANDLER, request));
        assertEquals(QUERY_PARAMS_REQUIRED_ERROR, exception.getMessage());
    }

    @Test
    public void testValidateGetAggregateReviewHandlerValidParams() {
        Map<String, String> params = new HashMap<>();
        params.put(QueryParam.IDS.getValue(), TEST_IDS_PARAM);
        request.setQueryStringParameters(params);
        
        assertDoesNotThrow(() -> validator.validateRequest(GET_AGGREGATE_REVIEW_HANDLER, request));
    }

    @Test
    public void testValidateGetRecentReviewsHandlerValidParams() {
        Map<String, String> params = new HashMap<>();
        params.put(QueryParam.COUNT.getValue(), TEST_COUNT_PARAM);
        request.setQueryStringParameters(params);
        
        assertDoesNotThrow(() -> validator.validateRequest(GET_RECENT_REVIEWS_HANDLER, request));
    }

    @Test
    public void testValidateGetPublicUserMetadataHandlerValidParams() {
        Map<String, String> params = new HashMap<>();
        params.put(QueryParam.ACCOUNT_ID.getValue(), TEST_ACCOUNT_ID_PARAM);
        request.setQueryStringParameters(params);
        
        assertDoesNotThrow(() -> validator.validateRequest(GET_PUBLIC_USER_METADATA_HANDLER, request));
    }

    @Test
    public void testValidateGetPublicUserMetadataHandlerMissingAccountId() {
        Map<String, String> params = new HashMap<>();
        request.setQueryStringParameters(params);
        
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, 
            () -> validator.validateRequest(GET_PUBLIC_USER_METADATA_HANDLER, request));
        assertEquals(ACCOUNT_ID_REQUIRED_ERROR, exception.getMessage());
    }

    @Test
    public void testValidatePutPublicUserMetadataHandlerValidParams() {
        Map<String, String> params = new HashMap<>();
        params.put(QueryParam.ACCOUNT_ID.getValue(), TEST_ACCOUNT_ID_PARAM);
        params.put(QueryParam.USERNAME.getValue(), TEST_USERNAME_PARAM);
        request.setQueryStringParameters(params);
        
        assertDoesNotThrow(() -> validator.validateRequest(PUT_PUBLIC_USER_METADATA_HANDLER, request));
    }

    @Test
    public void testValidatePutPublicUserMetadataHandlerMissingUsername() {
        Map<String, String> params = new HashMap<>();
        params.put(QueryParam.ACCOUNT_ID.getValue(), TEST_ACCOUNT_ID_PARAM);
        request.setQueryStringParameters(params);
        
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, 
            () -> validator.validateRequest(PUT_PUBLIC_USER_METADATA_HANDLER, request));
        assertEquals(USERNAME_REQUIRED_ERROR, exception.getMessage());
    }

    @Test
    public void testValidateUnsupportedHandler() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, 
            () -> validator.validateRequest(TEST_UNSUPPORTED_HANDLER, request));
        assertEquals(UNSUPPORTED_HANDLER_ERROR, exception.getMessage());
    }
}
