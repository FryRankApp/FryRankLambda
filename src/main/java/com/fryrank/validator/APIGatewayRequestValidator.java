package com.fryrank.validator;

import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPEvent;
import com.fryrank.model.enums.QueryParam;
import lombok.extern.log4j.Log4j2;

import java.util.List;
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
    public static final String UNSUPPORTED_HANDLER_ERROR_MESSAGE = "Validation for handler '%s' is not supported";
    public static final String QUERY_PARAM_MISSING_ERROR_FORMAT = "Required query parameter '%s' is missing";
    public static final String AT_LEAST_ONE_PARAM_REQUIRED_ERROR_FORMAT = "At least one of these query parameters is required: %s";

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
         * @throws IllegalArgumentException if the parameter doesn't exist
         */
        private void validateQueryParamExists(Map<String, String> params, QueryParam param) {
            if (!params.containsKey(param.getValue())) {
                throw new IllegalArgumentException(String.format(QUERY_PARAM_MISSING_ERROR_FORMAT, param.getValue()));
        }
    }

    /**
         * Validates if at least one of the specified query parameters exists.
         * @param params The query parameters map
         * @param queryParams The parameters to check (at least one must exist)
         * @throws IllegalArgumentException if none of the parameters exist
         */
        private void validateAtLeastOneQueryParamExists(Map<String, String> params, List<QueryParam> queryParams) {
            for (QueryParam param : queryParams) {
                if (params.containsKey(param.getValue())) {
                    return; // At least one parameter exists, validation passes
                }
            }

            // Build a comma-separated list of parameter names
            StringBuilder paramNames = new StringBuilder();
            for (int i = 0; i < queryParams.size(); i++) {
                paramNames.append("'").append(queryParams.get(i).getValue()).append("'");
                if (i < queryParams.size() - 1) {
                    paramNames.append(" or ");
                }
            }

            throw new IllegalArgumentException(String.format(AT_LEAST_ONE_PARAM_REQUIRED_ERROR_FORMAT, paramNames));
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
                    validateAtLeastOneQueryParamExists(
                        reviewParams,
                        List.of(QueryParam.RESTAURANT_ID, QueryParam.ACCOUNT_ID)
                    );
                break;

            case GET_AGGREGATE_REVIEW_HANDLER:
                Map<String, String> aggregateParams = getQueryParamsFromRequest(request);
                validateQueryParamExists(aggregateParams, QueryParam.IDS);
                break;
            case GET_RECENT_REVIEWS_HANDLER:
                Map<String, String> recentParams = getQueryParamsFromRequest(request);
                validateQueryParamExists(recentParams, QueryParam.COUNT);
                break;
            case GET_PUBLIC_USER_METADATA_HANDLER:
                Map<String, String> getMetadataParams = getQueryParamsFromRequest(request);
                validateQueryParamExists(getMetadataParams, QueryParam.ACCOUNT_ID);
                break;
            case PUT_PUBLIC_USER_METADATA_HANDLER:
                Map<String, String> putMetadataParams = getQueryParamsFromRequest(request);
                validateQueryParamExists(putMetadataParams, QueryParam.ACCOUNT_ID);
                validateQueryParamExists(putMetadataParams, QueryParam.USERNAME);
                break;
            case UPSERT_PUBLIC_USER_METADATA_HANDLER:
                validateRequestBodyExists(request);
                break;
            default:
                throw new IllegalArgumentException(String.format(UNSUPPORTED_HANDLER_ERROR_MESSAGE, className));
        }
    }
}