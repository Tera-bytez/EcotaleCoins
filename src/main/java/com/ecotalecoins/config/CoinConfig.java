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
 * Handles coin values, enabled/disabled states, UI settings and other configuration.
 * 
 * @author Ecotale
 * @since 1.1.0
 */
public class CoinConfig {
    
    private static final Gson GSON = new GsonBuilder()
        .setPrettyPrinting()
        .disableHtmlEscaping()
        .create();
    
    public static final String CURRENT_VERSION = "1.1";
    
    private final Path configPath;
    private final HytaleLogger logger;
    
    // Config values
    private String configVersion = null;
    private boolean useTranslationKeys = true;
    private boolean showExchangeTab = true;
    private boolean showConsolidateButton = true;
    private Map<String, CoinTypeConfig> coinTypes = new LinkedHashMap<>();
    private boolean isLegacyUpgrade = false;
    
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
                createDefaultConfig(false);
                logger.at(Level.INFO).log("[EcotaleCoins] Created config.json with v2.0 defaults");
                logger.at(Level.INFO).log("[EcotaleCoins] Coin hierarchy: Copper → Iron → Cobalt → Gold → Adamantite → Mithril");
                return true;
            }
            
            String json = Files.readString(configPath, StandardCharsets.UTF_8);
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            
            // Check config version
            if (root.has("config_version")) {
                this.configVersion = root.get("config_version").getAsString();
            } else {
                this.isLegacyUpgrade = true;
                this.configVersion = "1.0-legacy";
                showLegacyMigrationWarning();
            }
            
            // Load UI settings
            if (root.has("use_translation_keys")) {
                this.useTranslationKeys = root.get("use_translation_keys").getAsBoolean();
            }
            
            if (root.has("show_exchange_tab")) {
                this.showExchangeTab = root.get("show_exchange_tab").getAsBoolean();
            }
            
            if (root.has("show_consolidate_button")) {
                this.showConsolidateButton = root.get("show_consolidate_button").getAsBoolean();
            }
            
            // Load coin types
            if (root.has("coin_types")) {
                JsonObject coinTypesObj = root.getAsJsonObject("coin_types");
                
                for (String coinName : coinTypesObj.keySet()) {
                    JsonObject coinObj = coinTypesObj.getAsJsonObject(coinName);
                    
                    // Support both snake_case and camelCase field names
                    String itemId = "Coin_" + capitalize(coinName);
                    if (coinObj.has("item_id")) {
                        itemId = coinObj.get("item_id").getAsString();
                    } else if (coinObj.has("itemId")) {
                        itemId = coinObj.get("itemId").getAsString();
                    }
                    
                    String displayName = capitalize(coinName);
                    if (coinObj.has("display_name")) {
                        displayName = coinObj.get("display_name").getAsString();
                    } else if (coinObj.has("displayName")) {
                        displayName = coinObj.get("displayName").getAsString();
                    }
                    
                    CoinTypeConfig config = new CoinTypeConfig(
                        coinName,
                        coinObj.get("enabled").getAsBoolean(),
                        coinObj.get("value").getAsLong(),
                        itemId,
                        displayName
                    );
                    
                    coinTypes.put(coinName.toUpperCase(), config);
                }
            }
            
            logger.at(Level.INFO).log("[EcotaleCoins] Config loaded successfully (version: " + configVersion + ")");
            return true;
            
        } catch (IOException e) {
            logger.at(Level.SEVERE).withCause(e).log("[EcotaleCoins] Failed to load config");
            return false;
        }
    }
    
    private void showLegacyMigrationWarning() {
        logger.at(Level.WARNING).log("");
        logger.at(Level.WARNING).log("╔══════════════════════════════════════════════════════════════════╗");
        logger.at(Level.WARNING).log("║                    ⚠️ MIGRATION NOTICE ⚠️                         ║");
        logger.at(Level.WARNING).log("╠══════════════════════════════════════════════════════════════════╣");
        logger.at(Level.WARNING).log("║ EcotaleCoins v2.0 has CORRECTED coin values:                     ║");
        logger.at(Level.WARNING).log("║                                                                  ║");
        logger.at(Level.WARNING).log("║   OLD (v1.0):                    NEW (v2.0):                     ║");
        logger.at(Level.WARNING).log("║   • Mithril = 10,000             • Adamantite = 10,000           ║");
        logger.at(Level.WARNING).log("║   • Adamantite = 100,000         • Mithril = 100,000             ║");
        logger.at(Level.WARNING).log("║                                                                  ║");
        logger.at(Level.WARNING).log("║ Your existing config is being preserved to avoid disrupting      ║");
        logger.at(Level.WARNING).log("║ your economy. To adopt correct values, edit config.json          ║");
        logger.at(Level.WARNING).log("║                                                                  ║");
        logger.at(Level.WARNING).log("║ Use: /bank admin values  to see current configuration            ║");
        logger.at(Level.WARNING).log("╚══════════════════════════════════════════════════════════════════╝");
        logger.at(Level.WARNING).log("");
    }
    
    /**
     * Create default configuration file.
     */
    private void createDefaultConfig(boolean useLegacyValues) throws IOException {
        // Ensure parent directory exists
        if (!Files.exists(configPath.getParent())) {
            Files.createDirectories(configPath.getParent());
        }
        
        // Build default config
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("config_version", CURRENT_VERSION);
        config.put("use_translation_keys", true);
        config.put("show_exchange_tab", true);
        config.put("show_consolidate_button", true);
        
        // Coin types section - v2.0 corrected order
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
        
        if (useLegacyValues) {
            // Legacy order (v1.0 - incorrect)
            defaultCoins.put("mithril", new CoinTypeConfig(
                "mithril", true, 10000L, "Coin_Mithril", "Mithril"
            ));
            defaultCoins.put("adamantite", new CoinTypeConfig(
                "adamantite", true, 100000L, "Coin_Adamantite", "Adamantite"
            ));
        } else {
            // Correct order (v2.0) - Adamantite before Mithril
            defaultCoins.put("adamantite", new CoinTypeConfig(
                "adamantite", true, 10000L, "Coin_Adamantite", "Adamantite"
            ));
            defaultCoins.put("mithril", new CoinTypeConfig(
                "mithril", true, 100000L, "Coin_Mithril", "Mithril"
            ));
        }
        
        this.coinTypes = new LinkedHashMap<>();
        for (Map.Entry<String, CoinTypeConfig> entry : defaultCoins.entrySet()) {
            this.coinTypes.put(entry.getKey().toUpperCase(), entry.getValue());
        }
        
        config.put("coin_types", defaultCoins);
        
        // Write to file
        String json = GSON.toJson(config);
        Files.writeString(configPath, json, StandardCharsets.UTF_8);
        this.configVersion = CURRENT_VERSION;
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
     * Check if this is a legacy config upgrade.
     */
    public boolean isLegacyUpgrade() {
        return isLegacyUpgrade;
    }
    
    /**
     * Get the config version.
     */
    public String getConfigVersion() {
        return configVersion;
    }
    
    /**
     * Whether to use translation keys for coin names.
     */
    public boolean useTranslationKeys() {
        return useTranslationKeys;
    }
    
    /**
     * Whether to show the Exchange tab in Bank GUI.
     */
    public boolean showExchangeTab() {
        return showExchangeTab;
    }
    
    /**
     * Whether to show the Consolidate button in Bank GUI.
     */
    public boolean showConsolidateButton() {
        return showConsolidateButton;
    }
    
    /**
     * Reload configuration from disk.
     */
    public boolean reload() {
        coinTypes.clear();
        isLegacyUpgrade = false;
        configVersion = null;
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