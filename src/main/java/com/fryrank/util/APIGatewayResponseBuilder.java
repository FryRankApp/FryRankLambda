package com.fryrank.util;

import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPResponse;
import lombok.extern.log4j.Log4j2;

@Log4j2
public class APIGatewayResponseBuilder {
    
    public static APIGatewayV2HTTPResponse handleRequest(String handlerName, RequestHandler handler) {
        try {
            return handler.execute();
        } catch (IllegalArgumentException e) {
            log.error("IllegalArgumentException caught in handler: {}", handlerName, e);
            return buildErrorResponse(400, "Bad Request: " + e.getMessage());
        } catch (Exception e) {
            log.error("Exception caught in handler: {}", handlerName, e);
            return buildErrorResponse(500, "Internal Server Error: " + e.getMessage());
        }
    }

    public static APIGatewayV2HTTPResponse buildSuccessResponse(Object body) {
        APIGatewayV2HTTPResponse response = new APIGatewayV2HTTPResponse();
        response.setStatusCode(200);
        response.setBody(body != null ? body.toString() : "");
        return response;
    }

    public static APIGatewayV2HTTPResponse buildErrorResponse(int statusCode, String message) {
        APIGatewayV2HTTPResponse response = new APIGatewayV2HTTPResponse();
        response.setStatusCode(statusCode);
        response.setBody(message);
        return response;
    }

    @FunctionalInterface
    public interface RequestHandler {
        APIGatewayV2HTTPResponse execute() throws Exception;
    }
}
