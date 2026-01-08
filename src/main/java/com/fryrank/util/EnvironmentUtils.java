package com.fryrank.util;

public final class EnvironmentUtils {

    private EnvironmentUtils() {}

    public static String getRequiredEnv(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Required environment variable '" + name + "' is not set");
        }
        return value;
    }

    public static String getEnvOrDefault(String name, String defaultValue) {
        String value = System.getenv(name);
        return (value == null || value.isBlank()) ? defaultValue : value;
    }
}