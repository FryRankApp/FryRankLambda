package com.fryrank.util;

import static com.fryrank.TestConstants.TEST_INVALID_TOKEN;
import static com.fryrank.TestConstants.TEST_VALID_TOKEN;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.security.GeneralSecurityException;

import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mockStatic;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fryrank.model.exceptions.NotAuthorizedException;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;

@ExtendWith(MockitoExtension.class)
public class AuthorizerTests {

    private Authorizer authorizer;

    @Mock
    private GoogleIdTokenVerifier verifier;

    @Mock
    private GoogleIdToken idToken;

    private MockedStatic<SSMParameterStore> mockedSSM;

    @BeforeEach
    public void setUp() {
        mockedSSM = mockStatic(SSMParameterStore.class);
        mockedSSM.when(SSMParameterStore::getGoogleClientIdFromSSM).thenReturn("test-client-id");
        authorizer = new Authorizer(verifier);
    }

    @AfterEach
    public void tearDown() {
        if (mockedSSM != null) {
            mockedSSM.close();
        }
    }

    @Test
    public void testIsValidToken_WithValidToken_ReturnsTrue() throws NotAuthorizedException, GeneralSecurityException, IOException {
        // Arrange
        final String validToken = TEST_VALID_TOKEN;
        doReturn(idToken).when(verifier).verify(validToken);

        // Act
        authorizer.authorizeToken(validToken);

        // Assert - if we get here without exception, the test passes
        // No assertion needed - success is completing without exception
    }

    @Test
    public void testIsValidToken_WithInvalidToken_ReturnsFalse() throws GeneralSecurityException, IOException {
        // Arrange
        final String invalidToken = TEST_INVALID_TOKEN;
        doReturn(null).when(verifier).verify(invalidToken);

        // Act & Assert
        final NotAuthorizedException exception = org.junit.jupiter.api.Assertions.assertThrows(
            NotAuthorizedException.class,
            () -> authorizer.authorizeToken(invalidToken)
        );
        assertEquals("Unauthorized: Invalid token", exception.getMessage());
    }
}
