package com.fryrank.util;

import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPResponse;
import com.google.gson.Gson;
import lombok.extern.log4j.Log4j2;

import java.util.HashMap;
import java.util.Map;

@Log4j2
public class APIGatewayResponseBuilder {

    private final APIGatewayV2HTTPResponse response;
    private final Gson gson;

    private APIGatewayResponseBuilder() {
        this.response = new APIGatewayV2HTTPResponse();
        this.gson = new Gson();
    }

    public static APIGatewayResponseBuilder builder() {
        return new APIGatewayResponseBuilder();
    }

    public APIGatewayResponseBuilder statusCode(int statusCode) {
        response.setStatusCode(statusCode);
        return this;
    }

    public APIGatewayResponseBuilder body(Object body) {
        if (body != null) {
            String jsonString = gson.toJson(body);
            response.setBody(jsonString);
        } else {
            response.setBody("{}");
        }
        return this;
    }

    public APIGatewayResponseBuilder rawBody(String body) {
        response.setBody(body);
        return this;
    }

    public APIGatewayResponseBuilder headers(Map<String, String> headers) {
        response.setHeaders(headers);
        return this;
    }

    public APIGatewayResponseBuilder addHeader(String key, String value) {
        Map<String, String> currentHeaders = response.getHeaders();
        if (currentHeaders == null) {
            currentHeaders = new HashMap<>();
        }
        currentHeaders.put(key, value);
        response.setHeaders(currentHeaders);
        return this;
    }

    public APIGatewayV2HTTPResponse build() {
        return response;
    }

    // Utility methods to maintain backward compatibility
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
        return buildSuccessResponse(body, new HashMap<>());
    }

    public static APIGatewayV2HTTPResponse buildSuccessResponse(Object body, Map<String, String> headers) {
        return builder()
                .statusCode(200)
                .body(body)
                .headers(headers)
                .build();
    }

    public static APIGatewayV2HTTPResponse buildErrorResponse(int statusCode, String message) {
        return builder()
                .statusCode(statusCode)
                .rawBody(message)
                .build();
    }

    @FunctionalInterface
    public interface RequestHandler {
        APIGatewayV2HTTPResponse execute() throws Exception;
    }
}