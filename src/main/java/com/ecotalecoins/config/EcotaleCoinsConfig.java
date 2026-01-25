package com.ecotalecoins.config;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.codec.codecs.map.MapCodec;

import java.util.HashMap;
import java.util.Map;
import java.util.LinkedHashMap;

/**
 * Configuration for EcotaleCoins.
 *
 * Allows customization of which items are used as currency and their values.
 * All settings are saved to EcotaleCoins.json in the universe folder.
 */
public class EcotaleCoinsConfig {

    public static final BuilderCodec<EcotaleCoinsConfig> CODEC = BuilderCodec.builder(EcotaleCoinsConfig.class, EcotaleCoinsConfig::new)
        .append(new KeyedCodec<>("CurrencyTiers", new MapCodec<>(CurrencyTierConfig.CODEC, LinkedHashMap::new)),
            (c, v, e) -> c.currencyTiers = v, (c, e) -> c.currencyTiers).add()
        .append(new KeyedCodec<>("EnableBankCommand", Codec.BOOLEAN),
            (c, v, e) -> c.enableBankCommand = v, (c, e) -> c.enableBankCommand).add()
        .build();

    private Map<String, CurrencyTierConfig> currencyTiers = createDefaultTiers();
    private boolean enableBankCommand = true;

    public EcotaleCoinsConfig() {}

    /**
     * Create default currency tiers using vanilla Hytale metal ingots.
     */
    private static Map<String, CurrencyTierConfig> createDefaultTiers() {
        Map<String, CurrencyTierConfig> tiers = new LinkedHashMap<>();

        // Vanilla Hytale metal ingots in ascending value order
        // Each tier is worth 10x the previous tier
        tiers.put("COPPER", new CurrencyTierConfig("Ingredient_Bar_Copper", 1L, "Copper Bar"));
        tiers.put("IRON", new CurrencyTierConfig("Ingredient_Bar_Iron", 10L, "Iron Bar"));
        tiers.put("COBALT", new CurrencyTierConfig("Ingredient_Bar_Cobalt", 100L, "Cobalt Bar"));
        tiers.put("GOLD", new CurrencyTierConfig("Ingredient_Bar_Gold", 1_000L, "Gold Bar"));
        tiers.put("MITHRIL", new CurrencyTierConfig("Ingredient_Bar_Mithril", 10_000L, "Mithril Bar"));
        tiers.put("ADAMANTITE", new CurrencyTierConfig("Ingredient_Bar_Adamantite", 100_000L, "Adamantite Bar"));

        return tiers;
    }

    /**
     * Get all currency tiers.
     * The map keys are tier names (e.g., "COPPER", "IRON").
     * The map is ordered from lowest to highest value.
     */
    public Map<String, CurrencyTierConfig> getCurrencyTiers() {
        return currencyTiers;
    }

    /**
     * Get a specific currency tier by name.
     */
    public CurrencyTierConfig getCurrencyTier(String tierName) {
        return currencyTiers.get(tierName);
    }

    /**
     * Check if the /bank command is enabled.
     */
    public boolean isEnableBankCommand() {
        return enableBankCommand;
    }

    /**
     * Enable or disable the /bank command.
     */
    public void setEnableBankCommand(boolean enabled) {
        this.enableBankCommand = enabled;
    }
}
