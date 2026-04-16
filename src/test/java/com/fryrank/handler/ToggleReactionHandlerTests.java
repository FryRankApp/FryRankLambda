package com.fryrank.handler;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPResponse;
import com.fryrank.Constants;
import com.fryrank.dal.ReviewDALImpl;
import com.fryrank.domain.ReviewDomain;
import com.fryrank.model.MyReactions;
import com.fryrank.model.ReactionCounts;
import com.fryrank.model.ToggleReactionRequest;
import com.fryrank.model.ToggleReactionResult;
import com.fryrank.model.enums.ReactionAction;
import com.fryrank.model.enums.ReactionType;
import com.fryrank.model.exceptions.AuthorizationDisabledException;
import com.fryrank.model.exceptions.NotAuthorizedException;
import com.fryrank.util.Authorizer;
import com.fryrank.validator.APIGatewayRequestValidator;
import com.google.gson.Gson;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.Map;

import static com.fryrank.TestConstants.TEST_ACCOUNT_ID;
import static com.fryrank.TestConstants.TEST_AUTHORIZED_ACCOUNT_ID;
import static com.fryrank.TestConstants.TEST_INVALID_TOKEN;
import static com.fryrank.TestConstants.TEST_REVIEW_ID_1;
import static com.fryrank.TestConstants.TEST_VALID_TOKEN;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ToggleReactionHandlerTests {

    @Mock
    private ReviewDALImpl reviewDAL;

    @Mock
    private ReviewDomain reviewDomain;

    @Mock
    private APIGatewayRequestValidator requestValidator;

    @Mock
    private Authorizer authorizer;

    @Mock
    private Context context;

    @InjectMocks
    private ToggleReactionHandler handler;

    private Gson gson;

    @BeforeEach
    void setUp() {
        gson = new Gson();
    }

    @Test
    void handleRequest_validToken_callsDomainAndReturnsSuccess() throws Exception {
        ToggleReactionRequest request = new ToggleReactionRequest(
                TEST_ACCOUNT_ID,
                TEST_REVIEW_ID_1,
                ReactionType.THUMBS_UP,
                ReactionAction.ADD);
        ToggleReactionResult result = new ToggleReactionResult(
                TEST_REVIEW_ID_1,
                ReactionCounts.builder().thumbsUp(1).build(),
                MyReactions.builder().thumbsUp(true).build());
        APIGatewayV2HTTPEvent event = createTestEvent(createBearerToken(TEST_VALID_TOKEN), gson.toJson(request));

        doNothing().when(requestValidator).validateRequest(any(), any());
        when(authorizer.authorizeAndGetAccountId(TEST_VALID_TOKEN)).thenReturn(TEST_AUTHORIZED_ACCOUNT_ID);
        when(reviewDomain.toggleReaction(any(), any())).thenReturn(result);

        APIGatewayV2HTTPResponse response = handler.handleRequest(event, context);

        assertEquals(200, response.getStatusCode());
        assertNotNull(response.getBody());
        verify(authorizer).authorizeAndGetAccountId(TEST_VALID_TOKEN);

        ArgumentCaptor<String> accountIdCaptor = ArgumentCaptor.forClass(String.class);
        verify(reviewDomain).toggleReaction(accountIdCaptor.capture(), any(ToggleReactionRequest.class));
        assertEquals(TEST_AUTHORIZED_ACCOUNT_ID, accountIdCaptor.getValue());
    }

    @Test
    void handleRequest_invalidToken_returnsUnauthorized() throws Exception {
        ToggleReactionRequest request = new ToggleReactionRequest(
                TEST_ACCOUNT_ID,
                TEST_REVIEW_ID_1,
                ReactionType.HEART,
                ReactionAction.ADD);
        APIGatewayV2HTTPEvent event = createTestEvent(createBearerToken(TEST_INVALID_TOKEN), gson.toJson(request));

        doNothing().when(requestValidator).validateRequest(any(), any());
        doThrow(new NotAuthorizedException(Constants.AUTH_ERROR_INVALID_TOKEN))
                .when(authorizer).authorizeAndGetAccountId(TEST_INVALID_TOKEN);

        APIGatewayV2HTTPResponse response = handler.handleRequest(event, context);

        assertEquals(401, response.getStatusCode());
        assertEquals(Constants.AUTH_ERROR_INVALID_TOKEN, response.getBody());
    }

    @Test
    void handleRequest_authDisabled_usesRequestAccountIdAsViewer() throws Exception {
        ToggleReactionRequest request = new ToggleReactionRequest(
                TEST_ACCOUNT_ID,
                TEST_REVIEW_ID_1,
                ReactionType.THUMBS_DOWN,
                ReactionAction.REMOVE);
        ToggleReactionResult result = new ToggleReactionResult(
                TEST_REVIEW_ID_1,
                ReactionCounts.zero(),
                MyReactions.none());
        APIGatewayV2HTTPEvent event = createTestEvent(null, gson.toJson(request));

        doNothing().when(requestValidator).validateRequest(any(), any());
        doThrow(new AuthorizationDisabledException("Authorization is disabled"))
                .when(authorizer).authorizeAndGetAccountId(null);
        when(reviewDomain.toggleReaction(any(), any())).thenReturn(result);

        APIGatewayV2HTTPResponse response = handler.handleRequest(event, context);

        assertEquals(200, response.getStatusCode());
        ArgumentCaptor<String> accountIdCaptor = ArgumentCaptor.forClass(String.class);
        verify(reviewDomain).toggleReaction(accountIdCaptor.capture(), any(ToggleReactionRequest.class));
        assertEquals(TEST_ACCOUNT_ID, accountIdCaptor.getValue());
    }

    private APIGatewayV2HTTPEvent createTestEvent(String authHeader, String body) {
        APIGatewayV2HTTPEvent event = new APIGatewayV2HTTPEvent();
        event.setBody(body);

        Map<String, String> headers = new HashMap<>();
        if (authHeader != null) {
            headers.put("Authorization", authHeader);
        }
        event.setHeaders(headers);
        return event;
    }

    private String createBearerToken(String token) {
        return "Bearer " + token;
    }
}
