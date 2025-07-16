package com.fryrank.util;

import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPEvent;
import com.fryrank.Constants;
import lombok.extern.log4j.Log4j2;

import java.util.HashMap;
import java.util.Map;

import static com.fryrank.Constants.*;

/**
 * Utility methods for Lambda function handlers.
 */
@Log4j2
public class HeaderUtils {
    /**
     * Checks if the Origin header from the request is in the allowed origins list.
     * 
     * @param event The API Gateway HTTP event
     * @return The Origin header value if it's allowed, otherwise null
     */
    public static String getAllowedOrigin(APIGatewayV2HTTPEvent event) {
        log.info("Extracting origin from incoming HTTP request");
        Map<String, String> headers = toLowerCaseMap(event.getHeaders());
        log.info("Found headers: {}", headers);
        if (headers == null) {
            log.info("No headers found");
            return null;
        }

        // CORS headers are given to us in lowercase from API Gateway
        String origin = headers.get(ORIGIN);
        log.info("Found origin: " + origin);
        
        if (origin != null && Constants.ALLOWED_ORIGINS.contains(origin)) {
            return origin;
        }

        return null;
    }

    /**
     * Creates CORS headers with the provided origin.
     *
     * @param event The API Gateway HTTP event from which we will extract the valid origin value to use in CORS headers, if any
     * @return Map of CORS headers
     */
    public static Map<String, String> createCorsHeaders(APIGatewayV2HTTPEvent event) {
        Map<String, String> corsHeaders = new HashMap<>(Map.of(
                HEADER_ACCESS_CONTROL_ALLOW_METHODS, ALLOWED_METHODS,
                HEADER_ACCESS_CONTROL_ALLOW_HEADERS, CONTENT_TYPE
        ));


        String allowedOrigin = getAllowedOrigin(event);
        if(allowedOrigin != null) {
            corsHeaders.put(HEADER_ACCESS_CONTROL_ALLOW_ORIGIN, getAllowedOrigin(event));
        }

        return corsHeaders;
    }

    /**
     * Converts all keys and values in a Map<String, String> to lowercase.
     *
     * @param originalMap The original map to convert
     * @return A new map with all keys and values converted to lowercase
     */
    public static Map<String, String> toLowerCaseMap(Map<String, String> originalMap) {
        if (originalMap == null) {
            return null;
        }

        Map<String, String> lowerCaseMap = new HashMap<>();

        for (Map.Entry<String, String> entry : originalMap.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();

            // Convert key to lowercase
            String lowerCaseKey = (key != null) ? key.toLowerCase() : null;

            // Convert value to lowercase
            String lowerCaseValue = (value != null) ? value.toLowerCase() : null;

            lowerCaseMap.put(lowerCaseKey, lowerCaseValue);
        }

        return lowerCaseMap;
    }
}