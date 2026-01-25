package com.ecotalecoins.currency;

/**
 * Currency types using vanilla Hytale metal ingots.
 * Values are in base units (1 Copper Bar = 1 unit).
 * Uses vanilla game items instead of custom coins.
 */
public enum CoinType {
    COPPER("Ingredient_Bar_Copper", 1, "Copper Bar"),
    IRON("Ingredient_Bar_Iron", 10, "Iron Bar"),
    COBALT("Ingredient_Bar_Cobalt", 100, "Cobalt Bar"),
    GOLD("Ingredient_Bar_Gold", 1_000, "Gold Bar"),
    MITHRIL("Ingredient_Bar_Mithril", 10_000, "Mithril Bar"),
    ADAMANTITE("Ingredient_Bar_Adamantite", 100_000, "Adamantite Bar");

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
