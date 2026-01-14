package com.fryrank.handler;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPResponse;
import com.fryrank.dal.ReviewDAL;
import com.fryrank.dal.ReviewDALImpl;
import com.fryrank.domain.ReviewDomain;
import com.fryrank.model.GetAggregateReviewInformationOutput;
import com.fryrank.model.enums.QueryParam;
import com.fryrank.util.APIGatewayResponseBuilder;
import com.fryrank.validator.APIGatewayRequestValidator;
import lombok.extern.log4j.Log4j2;

import java.util.Map;

import static com.fryrank.util.HeaderUtils.createCorsHeaders;

@Log4j2
public class GetAggregateReviewInformationHandler implements RequestHandler<APIGatewayV2HTTPEvent, APIGatewayV2HTTPResponse> {

    private final ReviewDomain reviewDomain;
    private final ReviewDAL reviewDAL;
    private final APIGatewayRequestValidator requestValidator;

    public GetAggregateReviewInformationHandler() {
        reviewDAL = new ReviewDALImpl();
        reviewDomain = new ReviewDomain(reviewDAL);
        requestValidator = new APIGatewayRequestValidator();
    }

    @Override
    public APIGatewayV2HTTPResponse handleRequest(APIGatewayV2HTTPEvent input, Context context) {
        log.info("Handling request: {}", input);

        final String handlerName = getClass().getSimpleName();
        return APIGatewayResponseBuilder.handleRequest(handlerName, input, () -> {
            requestValidator.validateRequest(handlerName, input);

            Map<String, String> params = input.getQueryStringParameters();
            final GetAggregateReviewInformationOutput output = reviewDomain.getAggregateReviewInformationForRestaurants(
                params.get(QueryParam.IDS.getValue()),
                Boolean.parseBoolean(params.getOrDefault(QueryParam.INCLUDE_RATING.getValue(), "false"))
            );

            log.info("Request processed successfully");
            return APIGatewayResponseBuilder.buildSuccessResponse(output, createCorsHeaders(input));
        });
    }
}
