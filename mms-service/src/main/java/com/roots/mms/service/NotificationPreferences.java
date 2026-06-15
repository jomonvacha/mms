package com.roots.mms.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Canonical notification preference matrix (category x channel) plus merge and
 * sanitize helpers. Replaces the single {@code emailNotifications} boolean with
 * a granular, auditable preference center (MMS-Settings-Enhancements #4).
 *
 * <p>SMS and webhook channels from the TradingView benchmark are intentionally
 * omitted until MMS can actually deliver on them; only {@code email} and
 * {@code push} are exposed today.
 */
public final class NotificationPreferences {

    private NotificationPreferences() {}

    /** Notification categories, in display order. */
    public static final List<String> CATEGORIES = List.of(
            "security", "account", "billing", "product", "marketing");

    /** Delivery channels MMS supports today. */
    public static final List<String> CHANNELS = List.of("email", "push");

    // Sensible, compliance-aware defaults: security/account/billing default ON
    // (transactional), product email-only, marketing OFF (explicit opt-in).
    private static final Map<String, Map<String, Boolean>> DEFAULTS = Map.of(
            "security", Map.of("email", true,  "push", true),
            "account",  Map.of("email", true,  "push", true),
            "billing",  Map.of("email", true,  "push", true),
            "product",  Map.of("email", true,  "push", false),
            "marketing", Map.of("email", false, "push", false));

    /** Defaults overlaid with the user's stored choices, for known keys only. */
    public static Map<String, Map<String, Boolean>> effective(Map<String, Map<String, Boolean>> stored) {
        Map<String, Map<String, Boolean>> result = new LinkedHashMap<>();
        for (String category : CATEGORIES) {
            Map<String, Boolean> channels = new LinkedHashMap<>();
            Map<String, Boolean> defaults = DEFAULTS.get(category);
            Map<String, Boolean> overrides = stored != null ? stored.get(category) : null;
            for (String channel : CHANNELS) {
                boolean value = defaults.getOrDefault(channel, false);
                if (overrides != null && overrides.get(channel) != null) {
                    value = overrides.get(channel);
                }
                channels.put(channel, value);
            }
            result.put(category, channels);
        }
        return result;
    }

    /** Keeps only recognised categories/channels with boolean values, for storage. */
    public static Map<String, Map<String, Boolean>> sanitize(Map<String, Map<String, Boolean>> input) {
        if (input == null) return null;
        Map<String, Map<String, Boolean>> clean = new LinkedHashMap<>();
        for (String category : CATEGORIES) {
            Map<String, Boolean> overrides = input.get(category);
            if (overrides == null) continue;
            Map<String, Boolean> channels = new LinkedHashMap<>();
            for (String channel : CHANNELS) {
                Boolean v = overrides.get(channel);
                if (v != null) channels.put(channel, v);
            }
            if (!channels.isEmpty()) clean.put(category, channels);
        }
        return clean;
    }
}
