package com.fryrank.handler;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPResponse;
import com.fryrank.dal.ReviewDALImpl;
import com.fryrank.domain.ReviewDomain;
import com.fryrank.model.GetAllReviewsOutput;
import com.fryrank.model.GetAllReviewsRequest;
import com.fryrank.model.enums.QueryParam;
import com.fryrank.util.APIGatewayResponseBuilder;
import com.fryrank.validator.APIGatewayRequestValidator;
import com.fryrank.validator.GetAllReviewsRequestValidator;
import com.fryrank.validator.ValidatorUtils;
import lombok.extern.log4j.Log4j2;

import java.util.Map;

import static com.fryrank.Constants.GET_ALL_REVIEWS_REQUEST_VALIDATOR_ERRORS_OBJECT_NAME;
import static com.fryrank.util.HeaderUtils.createCorsHeaders;

@Log4j2
public class GetAllReviewsHandler implements RequestHandler<APIGatewayV2HTTPEvent, APIGatewayV2HTTPResponse> {

    private final ReviewDALImpl reviewDAL;
    private final ReviewDomain reviewDomain;
    private final APIGatewayRequestValidator requestValidator;
    private final GetAllReviewsRequestValidator getAllReviewsRequestValidator;

    public GetAllReviewsHandler() {
        reviewDAL = new ReviewDALImpl();
        reviewDomain = new ReviewDomain(reviewDAL);
        requestValidator = new APIGatewayRequestValidator();
        getAllReviewsRequestValidator = new GetAllReviewsRequestValidator();
    }

    @Override
    public APIGatewayV2HTTPResponse handleRequest(APIGatewayV2HTTPEvent input, Context context) {
        log.info("Handling request: {}", input);

        final String handlerName = getClass().getSimpleName();
        return APIGatewayResponseBuilder.handleRequest(handlerName, input, () -> {
            requestValidator.validateRequest(handlerName, input);

			Map<String, String> params = input.getQueryStringParameters();
			final GetAllReviewsRequest request = new GetAllReviewsRequest(
					params.get(QueryParam.RESTAURANT_ID.getValue()),
					params.get(QueryParam.ACCOUNT_ID.getValue()),
					params.get(QueryParam.LIMIT.getValue()),
					params.get(QueryParam.CURSOR.getValue()));
			ValidatorUtils.validateAndThrow(request, GET_ALL_REVIEWS_REQUEST_VALIDATOR_ERRORS_OBJECT_NAME, getAllReviewsRequestValidator);

			final int limit = Integer.parseInt(request.limit());
			final GetAllReviewsOutput output = reviewDomain.getAllReviews(
					request.restaurantId(),
					request.accountId(),
					limit,
					request.cursor());

            log.info("Request processed successfully");
            return APIGatewayResponseBuilder.buildSuccessResponse(output, createCorsHeaders(input));
        });
    }
}