package com.fryrank.model;

import lombok.Data;
import org.springframework.data.mongodb.core.mapping.Document;

@Document("user-metadata")
@Data
public class PublicUserMetadataOutput {
    private final String username;
}