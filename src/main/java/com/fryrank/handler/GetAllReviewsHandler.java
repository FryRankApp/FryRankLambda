package com.fryrank.handler;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPResponse;
import com.fryrank.dagger.Dependencies;
import com.fryrank.domain.ReviewDomain;
import com.fryrank.model.GetAllReviewsOutput;
import com.fryrank.model.GetAllReviewsRequest;
import com.fryrank.model.enums.QueryParam;
import com.fryrank.model.exceptions.AuthorizationDisabledException;
import com.fryrank.model.exceptions.NotAuthorizedException;
import com.fryrank.util.APIGatewayResponseBuilder;
import com.fryrank.util.Authorizer;
import com.fryrank.util.HeaderUtils;
import com.fryrank.validator.APIGatewayRequestValidator;
import lombok.extern.log4j.Log4j2;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static com.fryrank.Constants.DEFAULT_PAGE_LIMIT;
import static com.fryrank.Constants.MAX_PAGE_LIMIT;
import static com.fryrank.util.HeaderUtils.createCorsHeaders;

@Log4j2
public class GetAllReviewsHandler implements RequestHandler<APIGatewayV2HTTPEvent, APIGatewayV2HTTPResponse> {

    private final ReviewDomain reviewDomain;
    private final APIGatewayRequestValidator requestValidator;
    private final Authorizer authorizer;

    public GetAllReviewsHandler() {
        final var component = Dependencies.appComponent();
        reviewDomain = component.reviewDomain();
        requestValidator = component.apiGatewayRequestValidator();
        authorizer = component.authorizer();
    }

    public GetAllReviewsHandler(
            final ReviewDomain reviewDomain,
            final APIGatewayRequestValidator requestValidator,
            final Authorizer authorizer
    ) {
        this.reviewDomain = reviewDomain;
        this.requestValidator = requestValidator;
        this.authorizer = authorizer;
    }

    private String decodeCursor(final String cursor) {
        if (cursor == null || cursor.isEmpty()) {
            return cursor;
        }
        return URLDecoder.decode(cursor, StandardCharsets.UTF_8);
    }

    @Override
    public APIGatewayV2HTTPResponse handleRequest(APIGatewayV2HTTPEvent input, Context context) {
        log.info("Handling request: {}", input);

        final String handlerName = getClass().getSimpleName();
        return APIGatewayResponseBuilder.handleRequest(handlerName, input, () -> {
            requestValidator.validateRequest(handlerName, input);

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
            final String viewerAccountId;
            try {
                viewerAccountId = resolveViewerAccountIdFromAuthorizationHeader(input);
            } catch (NotAuthorizedException e) {
                return APIGatewayResponseBuilder.buildErrorResponse(401, e.getMessage(), createCorsHeaders(input));
            }

			final GetAllReviewsOutput output = reviewDomain.getAllReviews(
					request.restaurantId(),
					request.accountId(),
					limit,
					request.cursor(),
                    viewerAccountId);

            log.info("Request processed successfully");
            return APIGatewayResponseBuilder.buildSuccessResponse(output, createCorsHeaders(input));
        });
    }
    /**
     * Verified Google {@code sub} when a Bearer token is present and valid; {@code null} when there is no token
     * (anonymous listing). Throws {@link NotAuthorizedException} if a token is present but invalid.
     */
    private String resolveViewerAccountIdFromAuthorizationHeader(APIGatewayV2HTTPEvent input) throws NotAuthorizedException {
        final String token = HeaderUtils.extractBearerToken(input);
        if (token == null || token.isBlank()) {
            return null;
        }
        try {
            return authorizer.authorizeAndGetAccountId(token);
        } catch (AuthorizationDisabledException e) {
            log.info("Authorization disabled; listing reviews without viewer-specific myReactions");
            return null;
        }
    }
}
