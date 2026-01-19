package com.ecotalecoins.currency;

/**
 * Coin types based on in-game ores.
 * Values are in base units (1 Copper = 1 unit).
 */
public enum CoinType {
    COPPER("Coin_Copper", 1, "Copper"),
    IRON("Coin_Iron", 10, "Iron"),
    COBALT("Coin_Cobalt", 100, "Cobalt"),
    GOLD("Coin_Gold", 1_000, "Gold"),
    MITHRIL("Coin_Mithril", 10_000, "Mithril"),
    ADAMANTITE("Coin_Adamantite", 100_000, "Adamantite");

    private final String itemId;
    private final long value;
    private final String displayName;

    CoinType(String itemId, long value, String displayName) {
        this.itemId = itemId;
        this.value = value;
        this.displayName = displayName;
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

    /**
     * Find coin type by item ID.
     * @return CoinType or null if not a valid coin
     */
    public static CoinType fromItemId(String itemId) {
        for (CoinType type : values()) {
            if (type.itemId.equals(itemId)) {
                return type;
            }
        }
        return null;
    }

    /**
     * Check if an item ID is a valid coin.
     */
    public static boolean isCoin(String itemId) {
        return fromItemId(itemId) != null;
    }

    /**
     * Get all coin types sorted by value descending (for consolidation).
     */
    public static CoinType[] valuesDescending() {
        CoinType[] types = values();
        CoinType[] result = new CoinType[types.length];
        for (int i = 0; i < types.length; i++) {
            result[i] = types[types.length - 1 - i];
        }
        return result;
    }
}
