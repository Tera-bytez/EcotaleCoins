package com.ecotalecoins.config;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;

/**
 * Configuration for a single currency tier.
 * Defines which item is used and its value.
 */
public class CurrencyTierConfig {

    public static final BuilderCodec<CurrencyTierConfig> CODEC = BuilderCodec.builder(CurrencyTierConfig.class, CurrencyTierConfig::new)
        .append(new KeyedCodec<>("ItemId", Codec.STRING),
            (c, v, e) -> c.itemId = v, (c, e) -> c.itemId).add()
        .append(new KeyedCodec<>("Value", Codec.LONG),
            (c, v, e) -> c.value = v, (c, e) -> c.value).add()
        .append(new KeyedCodec<>("DisplayName", Codec.STRING),
            (c, v, e) -> c.displayName = v, (c, e) -> c.displayName).add()
        .build();

    private String itemId;
    private long value;
    private String displayName;

    public CurrencyTierConfig() {}

    public CurrencyTierConfig(String itemId, long value, String displayName) {
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

    public void setItemId(String itemId) {
        this.itemId = itemId;
    }

    public void setValue(long value) {
        this.value = value;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }
}
