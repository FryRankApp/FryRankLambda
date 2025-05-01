package com.fryrank.validator;

import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPEvent;
import com.fryrank.model.enums.QueryParam;
import lombok.extern.log4j.Log4j2;

import static com.fryrank.Constants.ADD_NEW_REVIEW_HANDLER;
import static com.fryrank.Constants.GET_ALL_REVIEWS_HANDLER;
import static com.fryrank.Constants.GET_AGGREGATE_REVIEW_HANDLER;
import static com.fryrank.Constants.GET_RECENT_REVIEWS_HANDLER;

@Log4j2
public class APIGatewayRequestValidator {

    public static final String REQUEST_BODY_REQUIRED_ERROR_MESSAGE = "Request body is required";
    public static final String QUERY_PARAMS_REQUIRED_ERROR_MESSAGE = "Query parameters are required";

    public void validateRequest(String className, APIGatewayV2HTTPEvent request) {
        log.info("Validating API Gateway Request for handler class: {}", className);

        // switch on the class name to determine the validation rules
        switch (className) {
            case ADD_NEW_REVIEW_HANDLER:
                if (request.getBody() == null || request.getBody().isEmpty()) {
                    throw new IllegalArgumentException(REQUEST_BODY_REQUIRED_ERROR_MESSAGE);
                }
                break;
            case GET_ALL_REVIEWS_HANDLER:
                if (request.getQueryStringParameters() == null
                        || !request.getQueryStringParameters().containsKey(QueryParam.RESTAURANT_ID.getValue())
                        || !request.getQueryStringParameters().containsKey(QueryParam.ACCOUNT_ID.getValue())) {
                    throw new IllegalArgumentException(QUERY_PARAMS_REQUIRED_ERROR_MESSAGE);
                }
                break;
            case GET_AGGREGATE_REVIEW_HANDLER:
                if (request.getQueryStringParameters() == null
                        || !request.getQueryStringParameters().containsKey(QueryParam.IDS.getValue())) {
                    throw new IllegalArgumentException(QUERY_PARAMS_REQUIRED_ERROR_MESSAGE);
                }
                break;
            case GET_RECENT_REVIEWS_HANDLER:
                if (request.getQueryStringParameters() == null
                        || !request.getQueryStringParameters().containsKey(QueryParam.COUNT.getValue())) {
                    throw new IllegalArgumentException(QUERY_PARAMS_REQUIRED_ERROR_MESSAGE);
                }
                break;
        }
    }
}
