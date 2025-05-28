package com.fryrank.handler;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPResponse;
import com.fryrank.dal.ReviewDALImpl;
import com.fryrank.domain.ReviewDomain;
import com.fryrank.model.GetAllReviewsOutput;
import com.fryrank.model.enums.QueryParam;
import com.fryrank.util.APIGatewayResponseBuilder;
import com.fryrank.validator.APIGatewayRequestValidator;
import lombok.extern.log4j.Log4j2;

import java.util.Map;

@Log4j2
public class GetAllReviewsHandler implements RequestHandler<APIGatewayV2HTTPEvent, APIGatewayV2HTTPResponse> {

    private final ReviewDALImpl reviewDAL;
    private final ReviewDomain reviewDomain;
    private final APIGatewayRequestValidator requestValidator;

    public GetAllReviewsHandler() {
        reviewDAL = new ReviewDALImpl();
        reviewDomain = new ReviewDomain(reviewDAL);
        requestValidator = new APIGatewayRequestValidator();
    }

    @Override
    public APIGatewayV2HTTPResponse handleRequest(APIGatewayV2HTTPEvent input, Context context) {
        log.info("Handling request: {}", input);

        final String handlerName = getClass().getSimpleName();
        return APIGatewayResponseBuilder.handleRequest(handlerName, () -> {
            requestValidator.validateRequest(handlerName, input);

            Map<String, String> params = input.getQueryStringParameters();
            final GetAllReviewsOutput output = reviewDomain.getAllReviews(
                    params.get(QueryParam.RESTAURANT_ID.getValue()),
                    params.get(QueryParam.ACCOUNT_ID.getValue()));

            log.info("Request processed successfully");
            return APIGatewayResponseBuilder.buildSuccessResponse(output);
        });
    }
}