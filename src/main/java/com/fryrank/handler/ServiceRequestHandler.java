package com.fryrank.handler;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPResponse;
import com.fryrank.dagger.AppComponent;
import com.fryrank.dagger.Dependencies;
import com.fryrank.domain.ReviewDomain;
import com.fryrank.domain.UserMetadataDomain;
import com.fryrank.util.APIGatewayResponseBuilder;
import com.fryrank.util.Authorizer;
import com.fryrank.validator.APIGatewayRequestValidator;
import com.fryrank.validator.DeleteReviewRequestValidator;
import com.fryrank.validator.ReviewValidator;
import com.google.gson.Gson;
import lombok.extern.log4j.Log4j2;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static com.fryrank.util.HeaderUtils.createCorsHeaders;

/**
 * Unified Lambda entrypoint that routes API Gateway requests to the existing per-endpoint handlers.
 *
 * Accepts both API Gateway REST (v1) and HTTP API (v2) proxy events via a generic Map input.
 * Routing prefers {@code routeKey} (e.g. {@code "GET /api/reviews"}), then falls back to
 * {@code requestContext.http.{method,path}} or {@code httpMethod}+{@code path}.
 */
@Log4j2
public class ServiceRequestHandler implements RequestHandler<Map<String, Object>, APIGatewayV2HTTPResponse> {

    private static final List<String> KNOWN_PATHS = List.of(
            "/api/reviews/aggregateInformation",
            "/api/reviews/recentReviews",
            "/api/reviews/recent",
            "/api/reviews",
            "/api/publicUserMetadata",
            "/api/userMetadata");

    private final Map<String, RequestHandler<APIGatewayV2HTTPEvent, APIGatewayV2HTTPResponse>> routes;

    public ServiceRequestHandler() {
        this(Dependencies.appComponent());
    }

    ServiceRequestHandler(final AppComponent component) {
        this(createRoutes(component));
    }

    ServiceRequestHandler(final Map<String, RequestHandler<APIGatewayV2HTTPEvent, APIGatewayV2HTTPResponse>> routes) {
        this.routes = routes;
    }

    @Override
    public APIGatewayV2HTTPResponse handleRequest(final Map<String, Object> rawInput, final Context context) {
        log.info("Handling request: {}", rawInput == null ? null : rawInput.keySet());

        final APIGatewayV2HTTPEvent event = getAPIGatewayEvent(rawInput);
        final String routeKey = resolveRouteKey(rawInput);
        log.info("Resolved routeKey={}", routeKey);

        if (routeKey != null && routeKey.startsWith("OPTIONS ")) {
            return APIGatewayResponseBuilder.buildSuccessNoContentResponse(createCorsHeaders(event));
        }

        final RequestHandler<APIGatewayV2HTTPEvent, APIGatewayV2HTTPResponse> handler =
                routeKey == null ? null : routes.get(routeKey);
        if (handler == null) {
            return APIGatewayResponseBuilder.buildErrorResponse(404, "Not Found", createCorsHeaders(event));
        }

        return handler.handleRequest(event, context);
    }

    private static Map<String, RequestHandler<APIGatewayV2HTTPEvent, APIGatewayV2HTTPResponse>> createRoutes(
            final AppComponent component) {
        final ReviewDomain reviewDomain = component.reviewDomain();
        final UserMetadataDomain userMetadataDomain = component.userMetadataDomain();
        final APIGatewayRequestValidator requestValidator = component.apiGatewayRequestValidator();
        final ReviewValidator reviewValidator = component.reviewValidator();
        final DeleteReviewRequestValidator deleteReviewRequestValidator = component.deleteReviewRequestValidator();
        final Authorizer authorizer = component.authorizer();
        final Gson gson = component.gson();

        final GetAllReviewsHandler getAllReviews = new GetAllReviewsHandler(reviewDomain, requestValidator);
        final AddNewReviewForRestaurantHandler addReview =
                new AddNewReviewForRestaurantHandler(reviewDomain, requestValidator, reviewValidator, authorizer);
        final DeleteReviewHandler deleteReview =
                new DeleteReviewHandler(reviewDomain, requestValidator, deleteReviewRequestValidator);
        final GetAggregateReviewInformationHandler getAggregate =
                new GetAggregateReviewInformationHandler(reviewDomain, requestValidator);
        final GetRecentReviewsHandler getRecent = new GetRecentReviewsHandler(reviewDomain, requestValidator);
        final GetPublicUserMetadataHandler getPublicUserMetadata =
                new GetPublicUserMetadataHandler(userMetadataDomain, requestValidator);
        final PutPublicUserMetadataHandler putPublicUserMetadata =
                new PutPublicUserMetadataHandler(userMetadataDomain, requestValidator);
        final UpsertPublicUserMetadataHandler upsertPublicUserMetadata =
                new UpsertPublicUserMetadataHandler(userMetadataDomain, requestValidator, gson);

        final Map<String, RequestHandler<APIGatewayV2HTTPEvent, APIGatewayV2HTTPResponse>> routes = new HashMap<>();
        routes.put("GET /api/reviews", getAllReviews);
        routes.put("POST /api/reviews", addReview);
        routes.put("DELETE /api/reviews", deleteReview);
        routes.put("GET /api/reviews/aggregateInformation", getAggregate);
        routes.put("GET /api/reviews/recent", getRecent);
        routes.put("GET /api/reviews/recentReviews", getRecent);
        routes.put("GET /api/publicUserMetadata", getPublicUserMetadata);
        routes.put("GET /api/userMetadata", getPublicUserMetadata);
        routes.put("PUT /api/publicUserMetadata", putPublicUserMetadata);
        routes.put("PUT /api/userMetadata", putPublicUserMetadata);
        routes.put("POST /api/publicUserMetadata", upsertPublicUserMetadata);
        routes.put("POST /api/userMetadata", upsertPublicUserMetadata);
        return Map.copyOf(routes);
    }

    private String resolveRouteKey(final Map<String, Object> event) {
        if (event == null) {
            return null;
        }

        final Object routeKey = event.get("routeKey");
        if (routeKey != null) {
            return routeKey.toString();
        }

        final String method = resolveMethod(event);
        final String path = canonicalizePath(resolvePath(event));
        if (method == null || path == null) {
            return null;
        }
        return method + " " + path;
    }

    private String resolveMethod(final Map<String, Object> event) {
        final String method = firstNonBlank(
                getNestedString(event, "requestContext", "http", "method"),
                (String) event.get("httpMethod"));
        return method == null ? null : method.trim().toUpperCase(Locale.ROOT);
    }

    private String resolvePath(final Map<String, Object> event) {
        final String path = firstNonBlank(
                getNestedString(event, "requestContext", "http", "path"),
                (String) event.get("rawPath"),
                (String) event.get("path"));
        return normalizePath(getStage(event), path);
    }

    private static String firstNonBlank(final String... values) {
        for (final String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private String canonicalizePath(final String path) {
        if (path == null) {
            return null;
        }
        for (final String knownPath : KNOWN_PATHS) {
            if (path.equals(knownPath) || path.endsWith(knownPath)) {
                return knownPath;
            }
        }
        return path;
    }

    private String normalizePath(final String stage, final String inputPath) {
        if (inputPath == null) {
            return null;
        }

        final String trimmed = inputPath.trim();
        final String withoutTrailingSlash =
                trimmed.length() > 1 && trimmed.endsWith("/") ? trimmed.substring(0, trimmed.length() - 1) : trimmed;

        if (stage == null || stage.isBlank() || !withoutTrailingSlash.startsWith("/" + stage + "/")) {
            return withoutTrailingSlash;
        }

        return "/" + withoutTrailingSlash.substring(("/" + stage + "/").length());
    }

    private APIGatewayV2HTTPEvent getAPIGatewayEvent(final Map<String, Object> rawInput) {
        final APIGatewayV2HTTPEvent event = new APIGatewayV2HTTPEvent();
        if (rawInput == null) {
            return event;
        }

        event.setBody((String) rawInput.get("body"));
        event.setHeaders(castStringMap(rawInput.get("headers")));
        event.setQueryStringParameters(castStringMap(rawInput.get("queryStringParameters")));
        return event;
    }

    private Map<String, String> castStringMap(final Object obj) {
        if (!(obj instanceof Map<?, ?> rawMap)) {
            return new HashMap<>();
        }
        final Map<String, String> result = new HashMap<>();
        for (final Map.Entry<?, ?> entry : rawMap.entrySet()) {
            if (entry.getKey() == null) {
                continue;
            }
            final Object valueObj = entry.getValue();
            result.put(entry.getKey().toString(), valueObj == null ? null : valueObj.toString());
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private static String getNestedString(final Map<String, Object> root, final String... path) {
        Object cursor = root;
        for (final String segment : path) {
            if (!(cursor instanceof Map<?, ?> map)) {
                return null;
            }
            cursor = ((Map<String, Object>) map).get(segment);
            if (cursor == null) {
                return null;
            }
        }
        return cursor.toString();
    }

    private static String getStage(final Map<String, Object> event) {
        if (event == null) {
            return null;
        }
        return firstNonBlank(
                getNestedString(event, "requestContext", "stage"),
                getNestedString(event, "requestContext", "stageName"));
    }
}
