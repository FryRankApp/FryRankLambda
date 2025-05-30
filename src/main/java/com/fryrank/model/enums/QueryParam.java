package com.fryrank.model.enums;

public enum QueryParam {
    RESTAURANT_ID("restaurantId"),
    ACCOUNT_ID("accountId"),
    COUNT("count"),
    IDS("ids"),
    INCLUDE_RATING("rating"),
    USERNAME("defaultUsername");

    private final String value;

    QueryParam(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }
}
