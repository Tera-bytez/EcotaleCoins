package com.ecotalecoins.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.hypixel.hytale.logger.HytaleLogger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Level;

/**
 * Configuration manager for EcotaleCoins.
 * Handles coin values, enabled/disabled states, and other settings.
 * 
 * @author Ecotale
 * @since 1.0.0
 */
public class CoinConfig {
    
    private static final Gson GSON = new GsonBuilder()
        .setPrettyPrinting()
        .disableHtmlEscaping()
        .create();
    
    private final Path configPath;
    private final HytaleLogger logger;
    
    // Config values
    private Map<String, CoinTypeConfig> coinTypes = new LinkedHashMap<>();
    
    public CoinConfig(Path configPath, HytaleLogger logger) {
        this.configPath = configPath;
        this.logger = logger;
    }
    
    /**
     * Load or create default config.
     */
    public boolean load() {
        try {
            if (!Files.exists(configPath)) {
                createDefaultConfig();
                logger.at(Level.INFO).log("[EcotaleCoins] Created default config.json");
                return true;
            }
            
            String json = Files.readString(configPath, StandardCharsets.UTF_8);
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            
            // Load coin types
            if (root.has("coin_types")) {
                JsonObject coinTypesObj = root.getAsJsonObject("coin_types");
                
                for (String coinName : coinTypesObj.keySet()) {
                    JsonObject coinObj = coinTypesObj.getAsJsonObject(coinName);
                    
                    CoinTypeConfig config = new CoinTypeConfig(
                        coinName,
                        coinObj.get("enabled").getAsBoolean(),
                        coinObj.get("value").getAsLong(),
                        coinObj.has("item_id") ? coinObj.get("item_id").getAsString() : "Coin_" + capitalize(coinName),
                        coinObj.has("display_name") ? coinObj.get("display_name").getAsString() : capitalize(coinName)
                    );
                    
                    coinTypes.put(coinName.toUpperCase(), config);
                }
            }
            
            logger.at(Level.INFO).log("[EcotaleCoins] Config loaded successfully");
            return true;
            
        } catch (IOException e) {
            logger.at(Level.SEVERE).withCause(e).log("[EcotaleCoins] Failed to load config");
            return false;
        }
    }
    
    /**
     * Create default configuration file.
     */
    private void createDefaultConfig() throws IOException {
        // Ensure parent directory exists
        if (!Files.exists(configPath.getParent())) {
            Files.createDirectories(configPath.getParent());
        }
        
        // Build default config
        Map<String, Object> config = new LinkedHashMap<>();
        
        // Coin types section
        Map<String, CoinTypeConfig> defaultCoins = new LinkedHashMap<>();
        
        defaultCoins.put("copper", new CoinTypeConfig(
            "copper", true, 1L, "Coin_Copper", "Copper"
        ));
        
        defaultCoins.put("iron", new CoinTypeConfig(
            "iron", true, 10L, "Coin_Iron", "Iron"
        ));
        
        defaultCoins.put("cobalt", new CoinTypeConfig(
            "cobalt", true, 100L, "Coin_Cobalt", "Cobalt"
        ));
        
        defaultCoins.put("gold", new CoinTypeConfig(
            "gold", true, 1000L, "Coin_Gold", "Gold"
        ));
        
        defaultCoins.put("mithril", new CoinTypeConfig(
            "mithril", true, 10000L, "Coin_Mithril", "Mithril"
        ));
        
        defaultCoins.put("adamantite", new CoinTypeConfig(
            "adamantite", true, 100000L, "Coin_Adamantite", "Adamantite"
        ));
        
        this.coinTypes = new LinkedHashMap<>();
        for (Map.Entry<String, CoinTypeConfig> entry : defaultCoins.entrySet()) {
            this.coinTypes.put(entry.getKey().toUpperCase(), entry.getValue());
        }
        
        config.put("coin_types", defaultCoins);
        
        // Write to file
        String json = GSON.toJson(config);
        Files.writeString(configPath, json, StandardCharsets.UTF_8);
    }
    
    /**
     * Get configuration for a specific coin type.
     */
    public CoinTypeConfig getCoinConfig(String coinTypeName) {
        return coinTypes.get(coinTypeName.toUpperCase());
    }
    
    /**
     * Check if a coin type is enabled.
     */
    public boolean isCoinEnabled(String coinTypeName) {
        CoinTypeConfig config = getCoinConfig(coinTypeName);
        return config != null && config.enabled;
    }
    
    /**
     * Get the value of a coin type.
     */
    public long getCoinValue(String coinTypeName) {
        CoinTypeConfig config = getCoinConfig(coinTypeName);
        return config != null ? config.value : 0L;
    }
    
    /**
     * Get all enabled coin types in value order (ascending).
     */
    public Map<String, CoinTypeConfig> getEnabledCoinsInOrder() {
        Map<String, CoinTypeConfig> enabled = new LinkedHashMap<>();
        
        coinTypes.values().stream()
            .filter(c -> c.enabled)
            .sorted((a, b) -> Long.compare(a.value, b.value))
            .forEach(c -> enabled.put(c.name.toUpperCase(), c));
        
        return enabled;
    }
    
    /**
     * Get all coin type configs.
     */
    public Map<String, CoinTypeConfig> getAllCoinConfigs() {
        return new LinkedHashMap<>(coinTypes);
    }
    
    /**
     * Reload configuration from disk.
     */
    public boolean reload() {
        coinTypes.clear();
        return load();
    }
    
    /**
     * Configuration for a single coin type.
     */
    public static class CoinTypeConfig {
        public final String name;
        public final boolean enabled;
        public final long value;
        public final String itemId;
        public final String displayName;
        
        public CoinTypeConfig(String name, boolean enabled, long value, String itemId, String displayName) {
            this.name = name;
            this.enabled = enabled;
            this.value = value;
            this.itemId = itemId;
            this.displayName = displayName;
        }
    }
    
    private static String capitalize(String str) {
        if (str == null || str.isEmpty()) return str;
        return str.substring(0, 1).toUpperCase() + str.substring(1).toLowerCase();
    }
}