package com.mihir.jobworker.model;

public enum NewsCategory {

    INDIA("India latest news"),
    GOLD("Gold price updates"),
    STOCKS("Stock updates");

    private final String displayName;

    NewsCategory(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }

    public static NewsCategory from(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("news category is required");
        }
        try {
            return NewsCategory.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Unsupported news category: " + value);
        }
    }
}
