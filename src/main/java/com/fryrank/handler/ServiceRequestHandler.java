package com.fryrank.handler;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPResponse;
import com.fryrank.dagger.Dependencies;
import com.fryrank.domain.ReviewDomain;
import com.fryrank.domain.UserMetadataDomain;
import com.fryrank.model.DeleteReviewRequest;
import com.fryrank.model.GetAggregateReviewInformationOutput;
import com.fryrank.model.GetAllReviewsOutput;
import com.fryrank.model.GetAllReviewsRequest;
import com.fryrank.model.PublicUserMetadata;
import com.fryrank.model.PublicUserMetadataOutput;
import com.fryrank.model.Review;
import com.fryrank.model.enums.QueryParam;
import com.fryrank.model.exceptions.AuthorizationDisabledException;
import com.fryrank.model.exceptions.NotAuthorizedException;
import com.fryrank.util.APIGatewayResponseBuilder;
import com.fryrank.util.Authorizer;
import com.fryrank.util.HeaderUtils;
import com.fryrank.validator.APIGatewayRequestValidator;
import com.fryrank.validator.DeleteReviewRequestValidator;
import com.fryrank.validator.ReviewValidator;
import com.fryrank.validator.ValidatorUtils;
import com.google.gson.Gson;
import lombok.extern.log4j.Log4j2;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import static com.fryrank.Constants.ADD_NEW_REVIEW_HANDLER;
import static com.fryrank.Constants.DEFAULT_PAGE_LIMIT;
import static com.fryrank.Constants.DELETE_EXISTING_REVIEW_HANDLER;
import static com.fryrank.Constants.DELETE_REVIEW_REQUEST_VALIDATOR_ERRORS_OBJECT_NAME;
import static com.fryrank.Constants.GET_AGGREGATE_REVIEW_HANDLER;
import static com.fryrank.Constants.GET_ALL_REVIEWS_HANDLER;
import static com.fryrank.Constants.GET_PUBLIC_USER_METADATA_HANDLER;
import static com.fryrank.Constants.GET_RECENT_REVIEWS_HANDLER;
import static com.fryrank.Constants.MAX_PAGE_LIMIT;
import static com.fryrank.Constants.PUT_PUBLIC_USER_METADATA_HANDLER;
import static com.fryrank.Constants.REVIEW_VALIDATOR_ERRORS_OBJECT_NAME;
import static com.fryrank.Constants.UPSERT_PUBLIC_USER_METADATA_HANDLER;
import static com.fryrank.util.HeaderUtils.createCorsHeaders;

/**
 * Unified Lambda handler that serves as a single entrypoint for all API requests.
 *
 * Accepts both API Gateway REST (v1) and HTTP API (v2) proxy events by using a generic Map input.
 * Routing prefers "routeKey" (e.g. "GET /api/reviews"). If missing, it falls back to
 * requestContext.http.{method,path}, then httpMethod+path.
 */
@Log4j2
public class ServiceRequestHandler implements RequestHandler<Map<String, Object>, APIGatewayV2HTTPResponse> {

    private static final String PATH_API_REVIEWS = "/api/reviews";
    private static final String PATH_API_REVIEWS_AGGREGATE = "/api/reviews/aggregateInformation";
    private static final String PATH_API_REVIEWS_RECENT = "/api/reviews/recent";
    private static final String PATH_API_REVIEWS_RECENT_REVIEWS = "/api/reviews/recentReviews";
    private static final String PATH_API_PUBLIC_USER_METADATA = "/api/publicUserMetadata";
    private static final String PATH_API_USER_METADATA = "/api/userMetadata";

    private final ReviewDomain reviewDomain;
    private final UserMetadataDomain userMetadataDomain;
    private final APIGatewayRequestValidator requestValidator;
    private final ReviewValidator reviewValidator;
    private final DeleteReviewRequestValidator deleteReviewRequestValidator;
    private final Authorizer authorizer;
    private final Gson gson;

    public ServiceRequestHandler() {
        final var component = Dependencies.appComponent();
        reviewDomain = component.reviewDomain();
        userMetadataDomain = component.userMetadataDomain();
        requestValidator = component.apiGatewayRequestValidator();
        reviewValidator = component.reviewValidator();
        deleteReviewRequestValidator = component.deleteReviewRequestValidator();
        authorizer = component.authorizer();
        gson = component.gson();
    }

    public ServiceRequestHandler(
            final ReviewDomain reviewDomain,
            final UserMetadataDomain userMetadataDomain,
            final APIGatewayRequestValidator requestValidator,
            final ReviewValidator reviewValidator,
            final DeleteReviewRequestValidator deleteReviewRequestValidator,
            final Authorizer authorizer,
            final Gson gson
    ) {
        this.reviewDomain = reviewDomain;
        this.userMetadataDomain = userMetadataDomain;
        this.requestValidator = requestValidator;
        this.reviewValidator = reviewValidator;
        this.deleteReviewRequestValidator = deleteReviewRequestValidator;
        this.authorizer = authorizer;
        this.gson = gson;
    }

    private record ResolvedRoute(String method, String path) {}

    @Override
    public APIGatewayV2HTTPResponse handleRequest(final Map<String, Object> rawInput, final Context context) {
        log.info("Handling request: {}", rawInput == null ? null : rawInput.keySet());

        final APIGatewayV2HTTPEvent input = toV2EventForValidationAndHeaders(rawInput);

        final ResolvedRoute route = resolveRoute(rawInput);
        log.info("Resolved route method={} path={} routeKey={}", route.method, route.path, rawInput == null ? null : rawInput.get("routeKey"));

        // Handle CORS preflight early.
        if ("OPTIONS".equals(route.method)) {
            return APIGatewayResponseBuilder.buildSuccessNoContentResponse(createCorsHeaders(input));
        }

        final String handlerName = getClass().getSimpleName();
        return APIGatewayResponseBuilder.handleRequest(handlerName, input, () -> {
            final Map<String, String> corsHeaders = createCorsHeaders(input);

            final String canonicalPath = canonicalizePath(route.path);
            final String routingKey = route.method + " " + canonicalPath;

            return switch (routingKey) {
                case "GET " + PATH_API_REVIEWS -> {
                    requestValidator.validateRequest(GET_ALL_REVIEWS_HANDLER, input);

                    final Map<String, String> params = input.getQueryStringParameters() != null
                            ? input.getQueryStringParameters()
                            : Map.of();
                    final GetAllReviewsRequest request = new GetAllReviewsRequest(
                            params.get(QueryParam.RESTAURANT_ID.getValue()),
                            params.get(QueryParam.ACCOUNT_ID.getValue()),
                            params.get(QueryParam.LIMIT.getValue()),
                            decodeCursor(params.get(QueryParam.CURSOR.getValue())));

                    final String limitParam = request.limit();
                    int limit = DEFAULT_PAGE_LIMIT;
                    if (limitParam != null && !limitParam.isEmpty()) {
                        try {
                            limit = Math.min(Math.max(Integer.parseInt(limitParam), 1), MAX_PAGE_LIMIT);
                        } catch (NumberFormatException e) {
                            log.warn("Invalid limit param '{}', using default: {}", limitParam, DEFAULT_PAGE_LIMIT);
                        }
                    }
                    log.debug("Using limit: {}", limit);

                    final GetAllReviewsOutput output = reviewDomain.getAllReviews(
                            request.restaurantId(),
                            request.accountId(),
                            limit,
                            request.cursor());
                    yield APIGatewayResponseBuilder.buildSuccessResponse(output, corsHeaders);
                }
                case "POST " + PATH_API_REVIEWS -> {
                    requestValidator.validateRequest(ADD_NEW_REVIEW_HANDLER, input);

                    final Review review = gson.fromJson(input.getBody(), Review.class);

                    try {
                        final String token = HeaderUtils.extractBearerToken(input);
                        final String authorizedAccountId = authorizer.authorizeAndGetAccountId(token);
                        review.setAccountId(authorizedAccountId);
                    } catch (NotAuthorizedException e) {
                        yield APIGatewayResponseBuilder.buildErrorResponse(401, e.getMessage(), corsHeaders);
                    } catch (AuthorizationDisabledException e) {
                        log.info("Authorization disabled, using accountId from request body");
                    }

                    review.setIsoDateTime(Instant.now().toString());
                    ValidatorUtils.validateAndThrow(review, REVIEW_VALIDATOR_ERRORS_OBJECT_NAME, reviewValidator);

                    final Review output = reviewDomain.addNewReviewForRestaurant(review);
                    yield APIGatewayResponseBuilder.buildSuccessResponse(output, corsHeaders);
                }
                case "DELETE " + PATH_API_REVIEWS -> {
                    requestValidator.validateRequest(DELETE_EXISTING_REVIEW_HANDLER, input);

                    final DeleteReviewRequest deleteRequest = gson.fromJson(input.getBody(), DeleteReviewRequest.class);
                    ValidatorUtils.validateAndThrow(
                            deleteRequest,
                            DELETE_REVIEW_REQUEST_VALIDATOR_ERRORS_OBJECT_NAME,
                            deleteReviewRequestValidator);

                    reviewDomain.deleteReview(deleteRequest);
                    yield APIGatewayResponseBuilder.buildSuccessNoContentResponse(corsHeaders);
                }
                case "GET " + PATH_API_REVIEWS_AGGREGATE -> {
                    requestValidator.validateRequest(GET_AGGREGATE_REVIEW_HANDLER, input);

                    final Map<String, String> params = input.getQueryStringParameters();
                    final GetAggregateReviewInformationOutput output = reviewDomain.getAggregateReviewInformationForRestaurants(
                            params.get(QueryParam.IDS.getValue()),
                            Boolean.parseBoolean(params.getOrDefault(QueryParam.INCLUDE_RATING.getValue(), "false")));
                    yield APIGatewayResponseBuilder.buildSuccessResponse(output, corsHeaders);
                }
                case "GET " + PATH_API_REVIEWS_RECENT, "GET " + PATH_API_REVIEWS_RECENT_REVIEWS -> {
                    requestValidator.validateRequest(GET_RECENT_REVIEWS_HANDLER, input);

                    final String countParam = input.getQueryStringParameters().get(QueryParam.COUNT.getValue());
                    final int count;
                    try {
                        count = Integer.parseInt(countParam);
                    } catch (NumberFormatException e) {
                        throw new IllegalArgumentException("Invalid count: " + countParam);
                    }

                    final GetAllReviewsOutput output = reviewDomain.getRecentReviews(count);
                    yield APIGatewayResponseBuilder.buildSuccessResponse(output, corsHeaders);
                }
                case "GET " + PATH_API_PUBLIC_USER_METADATA, "GET " + PATH_API_USER_METADATA -> {
                    requestValidator.validateRequest(GET_PUBLIC_USER_METADATA_HANDLER, input);

                    final PublicUserMetadataOutput output = userMetadataDomain.getPublicUserMetadata(
                            input.getQueryStringParameters().getOrDefault(QueryParam.ACCOUNT_ID.getValue(), null));
                    yield APIGatewayResponseBuilder.buildSuccessResponse(output, corsHeaders);
                }
                case "PUT " + PATH_API_PUBLIC_USER_METADATA, "PUT " + PATH_API_USER_METADATA -> {
                    requestValidator.validateRequest(PUT_PUBLIC_USER_METADATA_HANDLER, input);

                    final Map<String, String> params = input.getQueryStringParameters();
                    final PublicUserMetadataOutput output = userMetadataDomain.putPublicUserMetadata(
                            params.get(QueryParam.ACCOUNT_ID.getValue()),
                            params.get(QueryParam.USERNAME.getValue()));
                    yield APIGatewayResponseBuilder.buildSuccessResponse(output, corsHeaders);
                }
                case "POST " + PATH_API_PUBLIC_USER_METADATA, "POST " + PATH_API_USER_METADATA -> {
                    requestValidator.validateRequest(UPSERT_PUBLIC_USER_METADATA_HANDLER, input);

                    final PublicUserMetadata userMetadata = gson.fromJson(input.getBody(), PublicUserMetadata.class);
                    final PublicUserMetadataOutput output = userMetadataDomain.upsertPublicUserMetadata(userMetadata);
                    yield APIGatewayResponseBuilder.buildSuccessResponse(output, corsHeaders);
                }
                default -> APIGatewayResponseBuilder.buildErrorResponse(404, "Not Found", corsHeaders);
            };
        });
    }

    private ResolvedRoute resolveRoute(final Map<String, Object> event) {
        final ResolvedRoute fromRouteKey = Optional.ofNullable(event)
                .map(e -> e.get("routeKey"))
                .map(Object::toString)
                .flatMap(ServiceRequestHandler::parseRouteKey)
                .orElse(null);

        final String method = Optional.ofNullable(fromRouteKey)
                .map(ResolvedRoute::method)
                .or(() -> Optional.ofNullable(event)
                        .map(e -> getNestedString(e, "requestContext", "http", "method")))
                .or(() -> Optional.ofNullable(event).map(e -> (String) e.get("httpMethod")))
                .map(m -> m.trim().toUpperCase(Locale.ROOT))
                .orElse(null);

        final String path = Optional.ofNullable(fromRouteKey)
                .map(ResolvedRoute::path)
                .or(() -> Optional.ofNullable(event).map(e -> getNestedString(e, "requestContext", "http", "path")))
                .or(() -> Optional.ofNullable(event).map(e -> (String) e.get("rawPath")))
                .or(() -> Optional.ofNullable(event).map(e -> (String) e.get("path")))
                .map(p -> normalizePath(getStage(event), p))
                .orElse(null);

        return new ResolvedRoute(method, path);
    }

    private static Optional<ResolvedRoute> parseRouteKey(final String routeKey) {
        if (routeKey == null || !routeKey.contains(" ")) {
            return Optional.empty();
        }
        final String[] parts = routeKey.split(" ", 2);
        final String method = parts[0];
        final String path = parts[1];
        if (method == null || method.isBlank() || path == null || path.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(new ResolvedRoute(method, path));
    }

    private String canonicalizePath(final String path) {
        if (path == null) {
            return null;
        }
        if (path.equals(PATH_API_REVIEWS) || path.endsWith(PATH_API_REVIEWS)) {
            return PATH_API_REVIEWS;
        }
        if (path.equals(PATH_API_REVIEWS_AGGREGATE) || path.endsWith(PATH_API_REVIEWS_AGGREGATE)) {
            return PATH_API_REVIEWS_AGGREGATE;
        }
        if (path.equals(PATH_API_REVIEWS_RECENT) || path.endsWith(PATH_API_REVIEWS_RECENT)) {
            return PATH_API_REVIEWS_RECENT;
        }
        if (path.equals(PATH_API_REVIEWS_RECENT_REVIEWS) || path.endsWith(PATH_API_REVIEWS_RECENT_REVIEWS)) {
            return PATH_API_REVIEWS_RECENT_REVIEWS;
        }
        if (path.equals(PATH_API_PUBLIC_USER_METADATA) || path.endsWith(PATH_API_PUBLIC_USER_METADATA)) {
            return PATH_API_PUBLIC_USER_METADATA;
        }
        if (path.equals(PATH_API_USER_METADATA) || path.endsWith(PATH_API_USER_METADATA)) {
            return PATH_API_USER_METADATA;
        }
        return path;
    }

    private String normalizePath(final String stage, final String inputPath) {
        if (inputPath == null) {
            return null;
        }

        final String trimmed = inputPath.trim();
        final String withoutTrailingSlash =
                (trimmed.length() > 1 && trimmed.endsWith("/"))
                        ? trimmed.substring(0, trimmed.length() - 1)
                        : trimmed;

        if (stage == null || stage.isBlank()) {
            return withoutTrailingSlash;
        }

        final String stagePrefix = "/" + stage + "/";
        if (!withoutTrailingSlash.startsWith(stagePrefix)) {
            return withoutTrailingSlash;
        }

        return "/" + withoutTrailingSlash.substring(stagePrefix.length());
    }

    private String decodeCursor(final String cursor) {
        if (cursor == null || cursor.isEmpty()) {
            return cursor;
        }
        return URLDecoder.decode(cursor, StandardCharsets.UTF_8);
    }

    private APIGatewayV2HTTPEvent toV2EventForValidationAndHeaders(final Map<String, Object> rawInput) {
        final APIGatewayV2HTTPEvent event = new APIGatewayV2HTTPEvent();
        if (rawInput == null) {
            return event;
        }

        event.setBody((String) rawInput.get("body"));
        event.setHeaders(castStringMap(rawInput.get("headers")));
        event.setQueryStringParameters(castStringMap(rawInput.get("queryStringParameters")));
        return event;
    }

    @SuppressWarnings("unchecked")
    private Map<String, String> castStringMap(final Object obj) {
        if (!(obj instanceof Map<?, ?> rawMap)) {
            return new HashMap<>();
        }
        final Map<String, String> result = new HashMap<>();
        for (final Map.Entry<?, ?> entry : rawMap.entrySet()) {
            if (entry.getKey() == null) {
                continue;
            }
            final String key = entry.getKey().toString();
            final Object valueObj = entry.getValue();
            final String value = valueObj == null ? null : valueObj.toString();
            result.put(key, value);
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
        final String stage = getNestedString(event, "requestContext", "stage");
        if (stage != null && !stage.isBlank()) {
            return stage;
        }
        return getNestedString(event, "requestContext", "stageName");
    }
}
