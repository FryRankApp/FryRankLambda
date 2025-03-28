package com.fryrank.handler;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPResponse;
import com.fryrank.dal.ReviewDAL;
import com.fryrank.dal.ReviewDALImpl;
import com.fryrank.domain.ReviewDomain;
import com.fryrank.model.GetAllReviewsOutput;

public class ReviewHandler implements RequestHandler<APIGatewayV2HTTPEvent, APIGatewayV2HTTPResponse> {

    final ReviewDAL reviewDAL;
    final ReviewDomain reviewDomain;

    public ReviewHandler() {
        this.reviewDAL = new ReviewDALImpl();
        this.reviewDomain = new ReviewDomain(reviewDAL);
    }

    @Override
    public APIGatewayV2HTTPResponse handleRequest(APIGatewayV2HTTPEvent input, Context context) {

        GetAllReviewsOutput output = reviewDomain.getAllReviews(
                input.getQueryStringParameters().getOrDefault("restaurantId", null),
                input.getQueryStringParameters().getOrDefault("accountId", null));

        APIGatewayV2HTTPResponse response = new APIGatewayV2HTTPResponse();
        response.setBody(output.toString());

        return response;
    }
}