package com.fryrank.handler;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPResponse;
import com.fryrank.domain.ReviewDomain;
import com.fryrank.model.GetAllReviewsOutput;
import lombok.AllArgsConstructor;

@AllArgsConstructor
public class ReviewHandler implements RequestHandler<APIGatewayV2HTTPEvent, APIGatewayV2HTTPResponse> {

    ReviewDomain reviewDomain;

    @Override
    public APIGatewayV2HTTPResponse handleRequest(APIGatewayV2HTTPEvent input, Context context) {

        GetAllReviewsOutput output = reviewDomain.getAllReviews(
                input.getPathParameters().get("restaurantId"),
                input.getPathParameters().get("accountId"));

        APIGatewayV2HTTPResponse response = new APIGatewayV2HTTPResponse();
        response.setBody(output.toString());

        return response;
    }
}