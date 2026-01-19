package com.ecotalecoins.currency;

import com.ecotalecoins.Main;
import com.ecotalecoins.config.CoinConfig;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Coin types based on in-game ores.
 * Values are in base units.
 */
public enum CoinType {
    COPPER("copper"),
    IRON("iron"),
    COBALT("cobalt"),
    GOLD("gold"),
    MITHRIL("mithril"),
    ADAMANTITE("adamantite");

    private final String configKey;

    CoinType(String configKey) {
        this.configKey = configKey;
    }

    /**
     * Get the item ID for this coin type.
     */
    public String getItemId() {
        CoinConfig.CoinTypeConfig config = getConfig();
        return config != null ? config.itemId : "Coin_" + capitalize(configKey);
    }

    /**
     * Get the value of this coin type.
     */
    public long getValue() {
        CoinConfig.CoinTypeConfig config = getConfig();
        return config != null ? config.value : 1L;
    }

    /**
     * Get the display name for this coin type.
     */
    public String getDisplayName() {
        CoinConfig.CoinTypeConfig config = getConfig();
        return config != null ? config.displayName : capitalize(configKey);
    }
    
    /**
     * Check if this coin type is enabled in config.
     */
    public boolean isEnabled() {
        CoinConfig.CoinTypeConfig config = getConfig();
        return config != null && config.enabled;
    }

    /**
     * Find coin type by item ID.
     * Only returns enabled coins.
     * @return CoinType or null if not a valid/enabled coin
     */
    public static CoinType fromItemId(String itemId) {
        for (CoinType type : values()) {
            if (type.isEnabled() && type.getItemId().equals(itemId)) {
                return type;
            }
        }
        return null;
    }

    /**
     * Check if an item ID is a valid enabled coin.
     */
    public static boolean isCoin(String itemId) {
        return fromItemId(itemId) != null;
    }

    /**
     * Get all enabled coin types sorted by value descending.
     * This is used for consolidation and giving change.
     */
    public static CoinType[] valuesDescending() {
        List<CoinType> enabled = new ArrayList<>();
        
        for (CoinType type : values()) {
            if (type.isEnabled()) {
                enabled.add(type);
            }
        }
        
        // Sort by value descending
        enabled.sort((a, b) -> Long.compare(b.getValue(), a.getValue()));
        
        return enabled.toArray(new CoinType[0]);
    }
    
    /**
     * Get all enabled coin types sorted by value ascending.
     */
    public static CoinType[] valuesAscending() {
        List<CoinType> enabled = new ArrayList<>();
        
        for (CoinType type : values()) {
            if (type.isEnabled()) {
                enabled.add(type);
            }
        }
        
        // Sort by value ascending
        enabled.sort((a, b) -> Long.compare(a.getValue(), b.getValue()));
        
        return enabled.toArray(new CoinType[0]);
    }
    
    /**
     * Get all enabled coin types (no sorting).
     */
    public static CoinType[] enabledValues() {
        List<CoinType> enabled = new ArrayList<>();
        
        for (CoinType type : values()) {
            if (type.isEnabled()) {
                enabled.add(type);
            }
        }
        
        return enabled.toArray(new CoinType[0]);
    }
    
    /**
     * Get configuration for this coin type from the config manager.
     */
    private CoinConfig.CoinTypeConfig getConfig() {
        Main plugin = Main.getInstance();
        if (plugin == null) {
            // Fallback during initialization
            return null;
        }
        
        CoinConfig config = plugin.getCoinConfig();
        if (config == null) {
            return null;
        }
        
        return config.getCoinConfig(configKey);
    }
    
    private static String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return s.substring(0, 1).toUpperCase() + s.substring(1);
    }
}