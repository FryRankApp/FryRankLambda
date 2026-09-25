package com.fryrank.domain;

import com.fryrank.dal.UserMetadataDAL;
import com.fryrank.model.PublicUserMetadataOutput;
import com.fryrank.validator.UserMetadataValidator;
import com.fryrank.validator.ValidatorException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import static com.fryrank.TestConstants.TEST_ACCOUNT_ID;
import static com.fryrank.TestConstants.TEST_ACCOUNT_ID_NO_USER_METADATA;
import static com.fryrank.TestConstants.TEST_USERNAME;
import static com.fryrank.TestConstants.TEST_USER_METADATA_1;
import static com.fryrank.TestConstants.TEST_USER_METADATA_OUTPUT_1;
import static com.fryrank.TestConstants.TEST_PUBLIC_USER_METADATA_OUTPUT_EMPTY;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class PublicUserMetadataDomainTests {
    @Mock
    UserMetadataDAL userMetadataDAL;

    @Spy
    UserMetadataValidator userMetadataValidator = new UserMetadataValidator();

    @InjectMocks
    UserMetadataDomain domain;

    @Test
    public void testGetPublicUserMetadata_happyPath() throws Exception {
        when(userMetadataDAL.getPublicUserMetadataForAccountId(TEST_ACCOUNT_ID))
            .thenReturn(TEST_USER_METADATA_OUTPUT_1);

        final PublicUserMetadataOutput actualOutput = domain.getPublicUserMetadata(TEST_ACCOUNT_ID);
        assertEquals(TEST_USER_METADATA_OUTPUT_1, actualOutput);
    }

    @Test
    public void testGetPublicUserMetadata_emptyResult() throws Exception {
        when(userMetadataDAL.getPublicUserMetadataForAccountId(TEST_ACCOUNT_ID_NO_USER_METADATA))
            .thenReturn(TEST_PUBLIC_USER_METADATA_OUTPUT_EMPTY);

        final PublicUserMetadataOutput actualOutput = domain.getPublicUserMetadata(TEST_ACCOUNT_ID_NO_USER_METADATA);
        assertEquals(TEST_PUBLIC_USER_METADATA_OUTPUT_EMPTY, actualOutput);
    }

    @Test
    public void testGetPublicUserMetadata_nullAccountId() {
        assertThrows(NullPointerException.class, () -> domain.getPublicUserMetadata(null));
    }

    @Test
    public void testPutPublicUserMetadata_happyPath() throws Exception {
        when(userMetadataDAL.putPublicUserMetadata(TEST_USER_METADATA_1))
            .thenReturn(TEST_USER_METADATA_OUTPUT_1);

        final PublicUserMetadataOutput actualOutput = domain.putPublicUserMetadata(TEST_ACCOUNT_ID, TEST_USERNAME);
        assertEquals(TEST_USER_METADATA_OUTPUT_1, actualOutput);
    }

    @Test
    public void testPutPublicUserMetadata_nullAccountId_throwsValidatorException() {
        assertThrows(ValidatorException.class, () -> domain.putPublicUserMetadata(null, TEST_USERNAME));
        verifyNoInteractions(userMetadataDAL);
    }

    @Test
    public void testPutPublicUserMetadata_nullUsername_throwsValidatorException() {
        assertThrows(ValidatorException.class, () -> domain.putPublicUserMetadata(TEST_ACCOUNT_ID, null));
        verifyNoInteractions(userMetadataDAL);
    }
}
