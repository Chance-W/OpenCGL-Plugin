package com.opencgl.base.utils.history;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.function.Function;

/**
 * Configuration class for customizing history cell display.
 * Provides fluent API to configure how history items are rendered in ListView cells.
 *
 * @param <T> The type of history item
 */
public class CellDisplayConfig<T> {
    
    private Function<T, String> primaryTextExtractor;
    private Function<T, String> secondaryTextExtractor;
    private Function<T, String> badgeTextExtractor;
    private Function<T, Date> timestampExtractor;
    
    /**
     * Set the primary text extractor (displayed as bold blue text).
     * Typically used for main title like method name, action name, etc.
     */
    public CellDisplayConfig<T> primaryText(Function<T, String> extractor) {
        this.primaryTextExtractor = extractor;
        return this;
    }
    
    /**
     * Set the secondary text extractor (displayed as normal text).
     * Typically used for subtitle like interface name, description, etc.
     */
    public CellDisplayConfig<T> secondaryText(Function<T, String> extractor) {
        this.secondaryTextExtractor = extractor;
        return this;
    }
    
    /**
     * Set the badge text extractor (displayed as green bold text).
     * Typically used for status, environment name, category, etc.
     */
    public CellDisplayConfig<T> badgeText(Function<T, String> extractor) {
        this.badgeTextExtractor = extractor;
        return this;
    }
    
    /**
     * Set the timestamp field extractor.
     * Will be formatted as "yyyy-MM-dd HH:mm:ss".
     */
    public CellDisplayConfig<T> timestampField(Function<T, Date> extractor) {
        this.timestampExtractor = extractor;
        return this;
    }
    
    // Getters
    
    public String getPrimaryText(T item) {
        return primaryTextExtractor != null ? primaryTextExtractor.apply(item) : "";
    }
    
    public String getSecondaryText(T item) {
        return secondaryTextExtractor != null ? secondaryTextExtractor.apply(item) : "";
    }
    
    public String getBadgeText(T item) {
        return badgeTextExtractor != null ? badgeTextExtractor.apply(item) : "";
    }
    
    public Date getTimestamp(T item) {
        return timestampExtractor != null ? timestampExtractor.apply(item) : null;
    }
    
    public String formatTimestamp(T item) {
        Date timestamp = getTimestamp(item);
        if (timestamp == null) return "";
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(timestamp);
    }
}
