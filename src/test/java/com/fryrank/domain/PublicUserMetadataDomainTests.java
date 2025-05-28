package com.fryrank.domain;

import com.fryrank.dal.UserMetadataDAL;
import com.fryrank.model.PublicUserMetadata;
import com.fryrank.model.PublicUserMetadataOutput;
import com.fryrank.validator.ValidatorException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static com.fryrank.TestConstants.TEST_ACCOUNT_ID;
import static com.fryrank.TestConstants.TEST_ACCOUNT_ID_NO_USER_METADATA;
import static com.fryrank.TestConstants.TEST_DEFAULT_NAME;
import static com.fryrank.TestConstants.TEST_USERNAME;
import static com.fryrank.TestConstants.TEST_USER_METADATA_1;
import static com.fryrank.TestConstants.TEST_USER_METADATA_OUTPUT_1;
import static com.fryrank.TestConstants.TEST_PUBLIC_USER_METADATA_OUTPUT_EMPTY;
import static com.fryrank.TestConstants.TEST_PUBLIC_USER_METADATA_OUTPUT_WITH_DEFAULT_NAME;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class PublicUserMetadataDomainTests {
    @Mock
    UserMetadataDAL userMetadataDAL;

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
        when(userMetadataDAL.putPublicUserMetadataForAccountId(TEST_ACCOUNT_ID, TEST_DEFAULT_NAME))
            .thenReturn(TEST_USER_METADATA_OUTPUT_1);

        final PublicUserMetadataOutput actualOutput = domain.putPublicUserMetadata(TEST_ACCOUNT_ID, TEST_DEFAULT_NAME);
        assertEquals(TEST_USER_METADATA_OUTPUT_1, actualOutput);
    }

    @Test
    public void testPutPublicUserMetadata_noExistingMetadata() throws Exception {
        when(userMetadataDAL.putPublicUserMetadataForAccountId(TEST_ACCOUNT_ID_NO_USER_METADATA, TEST_DEFAULT_NAME))
            .thenReturn(TEST_PUBLIC_USER_METADATA_OUTPUT_WITH_DEFAULT_NAME);

        final PublicUserMetadataOutput actualOutput = domain.putPublicUserMetadata(TEST_ACCOUNT_ID_NO_USER_METADATA, TEST_DEFAULT_NAME);
        assertEquals(TEST_PUBLIC_USER_METADATA_OUTPUT_WITH_DEFAULT_NAME, actualOutput);
    }

    @Test
    public void testPutPublicUserMetadata_nullAccountId() {
        assertThrows(NullPointerException.class, () -> domain.putPublicUserMetadata(null, TEST_DEFAULT_NAME));
    }

    @Test
    public void testPutPublicUserMetadata_nullDefaultUserName() {
        assertThrows(NullPointerException.class, () -> domain.putPublicUserMetadata(TEST_ACCOUNT_ID, null));
    }

    @Test
    public void testPutPublicUserMetadata_bothNull() {
        assertThrows(NullPointerException.class, () -> domain.putPublicUserMetadata(null, null));
    }

    @Test
    public void testUpsertPublicUserMetadata_happyPath() throws Exception {
        when(userMetadataDAL.upsertPublicUserMetadata(TEST_USER_METADATA_1))
            .thenReturn(TEST_USER_METADATA_OUTPUT_1);

        final PublicUserMetadataOutput actualOutput = domain.upsertPublicUserMetadata(TEST_USER_METADATA_1);
        assertEquals(TEST_USER_METADATA_OUTPUT_1, actualOutput);
    }

    @Test
    public void testUpsertPublicUserMetadata_nullUserMetadata() {
        assertThrows(NullPointerException.class, () -> domain.upsertPublicUserMetadata(null));
    }

    @Test
    public void testUpsertPublicUserMetadata_invalidUserMetadata() throws Exception {
        PublicUserMetadata invalidMetadata = new PublicUserMetadata(null, TEST_USERNAME);
        assertThrows(ValidatorException.class, () -> domain.upsertPublicUserMetadata(invalidMetadata));
    }

    @Test
    public void testUpsertPublicUserMetadata_nullUsername() throws Exception {
        PublicUserMetadata invalidMetadata = new PublicUserMetadata(TEST_ACCOUNT_ID, null);
        assertThrows(ValidatorException.class, () -> domain.upsertPublicUserMetadata(invalidMetadata));
    }
}
