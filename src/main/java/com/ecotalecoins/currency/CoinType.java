package com.ecotalecoins.currency;

import com.ecotalecoins.config.CurrencyTierConfig;
import com.ecotalecoins.config.EcotaleCoinsConfig;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Currency type loaded from configuration.
 * Replaces hard-coded enum with config-driven currency system.
 *
 * Currency types are loaded from EcotaleCoins.json on plugin startup.
 * This allows server owners to customize which items are used as currency
 * and their values without recompiling.
 */
public class CoinType {

    private static final Map<String, CoinType> BY_NAME = new LinkedHashMap<>();
    private static final Map<String, CoinType> BY_ITEM_ID = new HashMap<>();
    private static boolean initialized = false;

    private final String name;
    private final String itemId;
    private final long value;
    private final String displayName;

    private CoinType(String name, String itemId, long value, String displayName) {
        this.name = name;
        this.itemId = itemId;
        this.value = value;
        this.displayName = displayName;
    }

    /**
     * Initialize currency types from configuration.
     * Must be called before using any CoinType methods.
     *
     * @param config The EcotaleCoins configuration
     */
    public static void initialize(EcotaleCoinsConfig config) {
        if (initialized) {
            BY_NAME.clear();
            BY_ITEM_ID.clear();
        }

        for (Map.Entry<String, CurrencyTierConfig> entry : config.getCurrencyTiers().entrySet()) {
            String tierName = entry.getKey();
            CurrencyTierConfig tierConfig = entry.getValue();

            CoinType coinType = new CoinType(
                tierName,
                tierConfig.getItemId(),
                tierConfig.getValue(),
                tierConfig.getDisplayName()
            );

            BY_NAME.put(tierName, coinType);
            BY_ITEM_ID.put(tierConfig.getItemId(), coinType);
        }

        initialized = true;
    }

    /**
     * Get coin type by tier name (e.g., "COPPER", "GOLD").
     * @return CoinType or null if not found
     */
    public static CoinType byName(String name) {
        return BY_NAME.get(name);
    }

    /**
     * Find coin type by item ID.
     * @return CoinType or null if not a valid currency item
     */
    public static CoinType fromItemId(String itemId) {
        return BY_ITEM_ID.get(itemId);
    }

    /**
     * Check if an item ID is a valid currency item.
     */
    public static boolean isCoin(String itemId) {
        return BY_ITEM_ID.containsKey(itemId);
    }

    /**
     * Get all coin types in order (lowest to highest value).
     */
    public static CoinType[] values() {
        return BY_NAME.values().toArray(new CoinType[0]);
    }

    /**
     * Get all coin types sorted by value descending (for consolidation).
     */
    public static CoinType[] valuesDescending() {
        List<CoinType> sorted = new ArrayList<>(BY_NAME.values());
        sorted.sort(Comparator.comparingLong(CoinType::getValue).reversed());
        return sorted.toArray(new CoinType[0]);
    }

    // Getters

    public String name() {
        return name;
    }

    public String getItemId() {
        return itemId;
    }

    public long getValue() {
        return value;
    }

    public String getDisplayName() {
        return displayName;
    }

    @Override
    public String toString() {
        return name;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof CoinType)) return false;
        CoinType other = (CoinType) obj;
        return name.equals(other.name);
    }

    @Override
    public int hashCode() {
        return name.hashCode();
    }
}

