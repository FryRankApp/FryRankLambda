package com.fryrank.validator;

import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPEvent;
import com.fryrank.model.enums.QueryParam;
import lombok.extern.log4j.Log4j2;

import java.util.Map;

import static com.fryrank.Constants.ADD_NEW_REVIEW_HANDLER;
import static com.fryrank.Constants.GET_ALL_REVIEWS_HANDLER;
import static com.fryrank.Constants.GET_AGGREGATE_REVIEW_HANDLER;
import static com.fryrank.Constants.GET_RECENT_REVIEWS_HANDLER;
import static com.fryrank.Constants.GET_PUBLIC_USER_METADATA_HANDLER;
import static com.fryrank.Constants.PUT_PUBLIC_USER_METADATA_HANDLER;
import static com.fryrank.Constants.UPSERT_PUBLIC_USER_METADATA_HANDLER;

@Log4j2
public class APIGatewayRequestValidator {

    public static final String REQUEST_BODY_REQUIRED_ERROR_MESSAGE = "Request body is required";
    public static final String QUERY_PARAMS_REQUIRED_ERROR_MESSAGE = "Query parameters are required";
    public static final String ACCOUNT_ID_REQUIRED_ERROR_MESSAGE = "Account ID is required";
    public static final String USERNAME_REQUIRED_ERROR_MESSAGE = "Username is required";
    public static final String UNSUPPORTED_HANDLER_ERROR_MESSAGE = "Validation for handler '%s' is not supported";

    /**
     * Validates if the request body is null or empty.
     * @param request The API Gateway request
     * @throws IllegalArgumentException if the request body is null or empty
     */
    private void validateRequestBodyExists(APIGatewayV2HTTPEvent request) {
        if (request.getBody() == null || request.getBody().isEmpty()) {
            throw new IllegalArgumentException(REQUEST_BODY_REQUIRED_ERROR_MESSAGE);
        }
    }

    /**
     * Validates if query parameters exist in the request.
     * @param request The API Gateway request
     * @return The query parameters map if it exists
     * @throws IllegalArgumentException if the query parameters are null
     */
    private Map<String, String> getQueryParamsFromRequest(APIGatewayV2HTTPEvent request) {
        Map<String, String> params = request.getQueryStringParameters();
        if (params == null) {
            throw new IllegalArgumentException(QUERY_PARAMS_REQUIRED_ERROR_MESSAGE);
        }
        return params;
    }

    /**
     * Validates if a specific query parameter exists.
     * @param params The query parameters map
     * @param param The parameter to check
     * @param errorMessage The error message to throw if the parameter doesn't exist
     * @throws IllegalArgumentException if the parameter doesn't exist
     */
    private void validateQueryParamExists(Map<String, String> params, QueryParam param, String errorMessage) {
        if (!params.containsKey(param.getValue())) {
            throw new IllegalArgumentException(errorMessage);
        }
    }

    /**
     * Validates an API Gateway request based on the handler class name.
     *
     * @param className The name of the handler class
     * @param request The API Gateway request to validate
     * @throws IllegalArgumentException if the request is invalid or the handler is not supported
     */
    public void validateRequest(String className, APIGatewayV2HTTPEvent request) throws IllegalArgumentException {
        log.info("Validating API Gateway Request for handler class: {}", className);

        // switch on the class name to determine the validation rules
        switch (className) {
            case ADD_NEW_REVIEW_HANDLER:
                validateRequestBodyExists(request);
                break;
            case GET_ALL_REVIEWS_HANDLER:
                Map<String, String> reviewParams = getQueryParamsFromRequest(request);
                validateQueryParamExists(reviewParams, QueryParam.RESTAURANT_ID, QUERY_PARAMS_REQUIRED_ERROR_MESSAGE);
                validateQueryParamExists(reviewParams, QueryParam.ACCOUNT_ID, QUERY_PARAMS_REQUIRED_ERROR_MESSAGE);
                break;
            case GET_AGGREGATE_REVIEW_HANDLER:
                Map<String, String> aggregateParams = getQueryParamsFromRequest(request);
                validateQueryParamExists(aggregateParams, QueryParam.IDS, QUERY_PARAMS_REQUIRED_ERROR_MESSAGE);
                break;
            case GET_RECENT_REVIEWS_HANDLER:
                Map<String, String> recentParams = getQueryParamsFromRequest(request);
                validateQueryParamExists(recentParams, QueryParam.COUNT, QUERY_PARAMS_REQUIRED_ERROR_MESSAGE);
                break;
            case GET_PUBLIC_USER_METADATA_HANDLER:
                Map<String, String> getMetadataParams = getQueryParamsFromRequest(request);
                validateQueryParamExists(getMetadataParams, QueryParam.ACCOUNT_ID, ACCOUNT_ID_REQUIRED_ERROR_MESSAGE);
                break;
            case PUT_PUBLIC_USER_METADATA_HANDLER:
                Map<String, String> putMetadataParams = getQueryParamsFromRequest(request);
                validateQueryParamExists(putMetadataParams, QueryParam.ACCOUNT_ID, ACCOUNT_ID_REQUIRED_ERROR_MESSAGE);
                validateQueryParamExists(putMetadataParams, QueryParam.USERNAME, USERNAME_REQUIRED_ERROR_MESSAGE);
                break;
            case UPSERT_PUBLIC_USER_METADATA_HANDLER:
                validateRequestBodyExists(request);
                break;
            default:
                throw new IllegalArgumentException(String.format(UNSUPPORTED_HANDLER_ERROR_MESSAGE, className));
        }
    }
}