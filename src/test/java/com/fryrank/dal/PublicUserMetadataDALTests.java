package com.fryrank.dal;
import com.fryrank.model.PublicUserMetadata;
import com.fryrank.model.PublicUserMetadataOutput;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.FindAndReplaceOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;

import java.util.List;

import static com.fryrank.Constants.ACCOUNT_ID_KEY;
import static com.fryrank.TestConstants.TEST_ACCOUNT_ID;
import static com.fryrank.TestConstants.TEST_ACCOUNT_ID_NO_USER_METADATA;
import static com.fryrank.TestConstants.TEST_DEFAULT_NAME;
import static com.fryrank.TestConstants.TEST_USER_METADATA_1;
import static com.fryrank.TestConstants.TEST_PUBLIC_USER_METADATA_LIST;
import static com.fryrank.TestConstants.TEST_USER_METADATA_OUTPUT_1;
import static com.fryrank.TestConstants.TEST_PUBLIC_USER_METADATA_OUTPUT_EMPTY;
import static com.fryrank.TestConstants.TEST_PUBLIC_USER_METADATA_OUTPUT_WITH_DEFAULT_NAME;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.data.mongodb.core.query.Criteria.where;

@ExtendWith(MockitoExtension.class)
public class PublicUserMetadataDALTests {
    @Mock
    MongoTemplate mongoTemplate;

    @InjectMocks
    UserMetadataDALImpl userMetadataDAL;

    @Test
    public void testPutPublicUserMetadataForAccountId_happyPath() throws Exception {
        final Query query = new Query();
        query.addCriteria(where(ACCOUNT_ID_KEY).is(TEST_ACCOUNT_ID));

        when(mongoTemplate.find(query, PublicUserMetadata.class)).thenReturn(TEST_PUBLIC_USER_METADATA_LIST);

        final PublicUserMetadataOutput actualOutput = userMetadataDAL.putPublicUserMetadataForAccountId(TEST_ACCOUNT_ID, TEST_DEFAULT_NAME);
        assertEquals(actualOutput, TEST_USER_METADATA_OUTPUT_1);
    }

    @Test
    public void testPutPublicUserMetadataForAccountId_noUserMetadata() throws Exception {
        final Query query = new Query();
        query.addCriteria(where(ACCOUNT_ID_KEY).is(TEST_ACCOUNT_ID_NO_USER_METADATA));

        final List<PublicUserMetadata> expectedUserMetadata = List.of();
        when(mongoTemplate.find(query, PublicUserMetadata.class)).thenReturn(expectedUserMetadata);

        final PublicUserMetadataOutput actualOutput = userMetadataDAL.putPublicUserMetadataForAccountId(TEST_ACCOUNT_ID_NO_USER_METADATA, TEST_DEFAULT_NAME);
        assertEquals(actualOutput, TEST_PUBLIC_USER_METADATA_OUTPUT_WITH_DEFAULT_NAME);
    }

    @Test
    public void testPutPublicUserMetadataForAccountId_nullAccountId() throws Exception {
        final Query query = new Query();
        query.addCriteria(where(ACCOUNT_ID_KEY).is(null));
        
        assertThrows(NullPointerException.class, () ->  userMetadataDAL.putPublicUserMetadataForAccountId(null, null));
    }

    @Test
    public void testGetPublicUserMetadataForAccountId_happyPath() throws Exception {
        final Query query = new Query();
        query.addCriteria(where(ACCOUNT_ID_KEY).is(TEST_ACCOUNT_ID));

        when(mongoTemplate.find(query, PublicUserMetadata.class)).thenReturn(TEST_PUBLIC_USER_METADATA_LIST);

        final PublicUserMetadataOutput actualOutput = userMetadataDAL.getPublicUserMetadataForAccountId(TEST_ACCOUNT_ID);
        assertEquals(actualOutput, TEST_USER_METADATA_OUTPUT_1);
    }

    @Test
    public void testGetPublicUserMetadataForAccountId_noUserMetadata() throws Exception {
        final Query query = new Query();
        query.addCriteria(where(ACCOUNT_ID_KEY).is(TEST_ACCOUNT_ID_NO_USER_METADATA));

        final List<PublicUserMetadata> expectedUserMetadata = List.of();
        when(mongoTemplate.find(query, PublicUserMetadata.class)).thenReturn(expectedUserMetadata);

        final PublicUserMetadataOutput actualOutput = userMetadataDAL.getPublicUserMetadataForAccountId(TEST_ACCOUNT_ID_NO_USER_METADATA);
        assertEquals(actualOutput, TEST_PUBLIC_USER_METADATA_OUTPUT_EMPTY);
    }

    @Test
    public void testGetPublicUserMetadataForAccountId_nullAccountId() throws Exception {
        final Query query = new Query();
        query.addCriteria(where(ACCOUNT_ID_KEY).is(null));

        assertThrows(NullPointerException.class, () ->  userMetadataDAL.getPublicUserMetadataForAccountId(null));
    }

    @Test
    public void testUpsertPublicUserMetadata() throws Exception {
        when(mongoTemplate.findAndReplace(any(Query.class), eq(TEST_USER_METADATA_1), any(FindAndReplaceOptions.class))).thenReturn(TEST_USER_METADATA_1);

        final PublicUserMetadataOutput actualUserMetadata = userMetadataDAL.upsertPublicUserMetadata(TEST_USER_METADATA_1);
        assertEquals(TEST_USER_METADATA_OUTPUT_1, actualUserMetadata);
    }
}