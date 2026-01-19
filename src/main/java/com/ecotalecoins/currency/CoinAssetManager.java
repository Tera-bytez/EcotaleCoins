package com.ecotalecoins.currency;

import com.hypixel.hytale.logger.HytaleLogger;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.logging.Level;

/**
 * Manages coin textures and models as a standalone asset pack.
 * 
 * Creates a complete Hytale asset pack in the mods folder:
 * mods/Ecotale_EcotaleCoins/
 * 
 * @author Ecotale
 * @since 1.0.0
 */
public class CoinAssetManager {
    
    private static final String COMMON_COINS_PATH = "Common/Items/Currency/Coins";
    private static final String ICONS_PATH = "Common/Icons/Items/Coins";
    private static final String UI_PATH = "Common/UI/Custom/Pages";
    private static final String SERVER_ITEMS_PATH = "Server/Item/Items";
    private static final String MODEL_DROPPED = "Coin.blockymodel";
    private static final String MODEL_HELD = "Coin_Held.blockymodel";
    // Assets stored in /templates/ so Hytale won't auto-load them
    private static final String JAR_RESOURCE_BASE = "/templates/Common/Items/Currency/Coins/";
    private static final String JAR_ICONS_BASE = "/templates/Common/Icons/Items/Coins/";
    private static final String JAR_UI_BASE = "/templates/Common/UI/Custom/Pages/";
    private static final String JAR_ITEMS_BASE = "/templates/Server/Item/Items/";
    
    private final Path assetPackRoot;
    private final Path coinsFolder;
    private final Path iconsFolder;
    private final Path uiFolder;
    private final Path itemsFolder;
    private final HytaleLogger logger;
    
    private boolean initialized = false;
    private boolean firstTimeSetup = false;
    
    public CoinAssetManager(Path assetPackRoot, HytaleLogger logger) {
        this.assetPackRoot = assetPackRoot;
        this.coinsFolder = assetPackRoot.resolve(COMMON_COINS_PATH);
        this.iconsFolder = assetPackRoot.resolve(ICONS_PATH);
        this.uiFolder = assetPackRoot.resolve(UI_PATH);
        this.itemsFolder = assetPackRoot.resolve(SERVER_ITEMS_PATH);
        this.logger = logger;
    }
    
    public boolean initialize() {
        if (initialized) {
            return true;
        }
        
        try {
            Path manifestPath = assetPackRoot.resolve("manifest.json");
            boolean manifestExisted = Files.exists(manifestPath);
            
            if (!Files.exists(coinsFolder)) {
                Files.createDirectories(coinsFolder);
                logger.at(Level.INFO).log("[EcotaleCoins] Created asset pack structure: %s", coinsFolder);
            }
            
            if (!Files.exists(itemsFolder)) {
                Files.createDirectories(itemsFolder);
                logger.at(Level.INFO).log("[EcotaleCoins] Created items folder: %s", itemsFolder);
            }
            
            if (!Files.exists(iconsFolder)) {
                Files.createDirectories(iconsFolder);
                logger.at(Level.INFO).log("[EcotaleCoins] Created icons folder: %s", iconsFolder);
            }
            
            createManifestIfMissing();
            createReadmeIfMissing();
            
            // Extract textures and models
            for (CoinType type : CoinType.values()) {
                String textureName = "Coin_" + capitalizeFirst(type.name().toLowerCase()) + ".png";
                extractResourceIfMissing(textureName);
            }
            
            extractResourceIfMissing(MODEL_DROPPED);
            extractResourceIfMissing(MODEL_HELD);
            
            // Extract icons for inventory display
            for (CoinType type : CoinType.values()) {
                String iconName = "Coin_" + capitalizeFirst(type.name().toLowerCase()) + ".png";
                extractIconIfMissing(iconName);
            }
            
            // Extract item definitions (critical for items to work!)
            for (CoinType type : CoinType.values()) {
                String itemName = "Coin_" + capitalizeFirst(type.name().toLowerCase()) + ".json";
                extractItemDefinitionIfMissing(itemName);
            }
            
            this.firstTimeSetup = !manifestExisted;
            
            initialized = true;
            logger.at(Level.INFO).log("[EcotaleCoins] Asset pack initialized at: %s", assetPackRoot);
            
            return true;
            
        } catch (IOException e) {
            logger.at(Level.SEVERE).withCause(e).log("[EcotaleCoins] Failed to initialize asset pack");
            return false;
        }
    }
    
    private void createManifestIfMissing() throws IOException {
        Path manifestPath = assetPackRoot.resolve("manifest.json");
        
        if (Files.exists(manifestPath)) {
            return;
        }
        
        String manifest = """
            {
              "Group": "Ecotale",
              "Name": "EcotaleCoins_Assets",
              "Version": "1.0.0",
              "Description": "Customizable coin textures - edit these to change coin appearance",
              "Authors": [
                {
                  "Name": "Ecotale"
                }
              ],
              "Website": "",
              "Dependencies": {
                "Ecotale:EcotaleCoins": "*"
              },
              "OptionalDependencies": {},
              "DisabledByDefault": false,
              "IncludesAssetPack": true,
              "SubPlugins": []
            }
            """;
        
        Files.writeString(manifestPath, manifest, StandardCharsets.UTF_8);
        logger.at(Level.INFO).log("[EcotaleCoins] Created manifest.json for asset pack");
    }
    
    private void createReadmeIfMissing() throws IOException {
        Path readmePath = assetPackRoot.resolve("README.txt");
        
        if (Files.exists(readmePath)) {
            return;
        }
        
        String readme = """
            ===============================================
            ECOTALE COINS - CUSTOMIZABLE ASSETS
            ===============================================
            
            This folder contains textures and models for physical coins.
            You can customize them by replacing the files!
            
            TEXTURES (Common/Items/Currency/Coins/):
            - Coin_Copper.png    - Copper coin (value: 1)
            - Coin_Iron.png      - Iron coin (value: 10)
            - Coin_Cobalt.png    - Cobalt coin (value: 100)
            - Coin_Gold.png      - Gold coin (value: 1,000)
            - Coin_Mithril.png   - Mithril coin (value: 10,000)
            - Coin_Adamantite.png - Adamantite coin (value: 100,000)
            
            HOW TO CUSTOMIZE:
            1. Replace the PNG textures (keep 64x64 dimensions)
            2. Restart the server to apply changes
            
            ===============================================
            """;
        
        Files.writeString(readmePath, readme, StandardCharsets.UTF_8);
    }
    
    private static String capitalizeFirst(String s) {
        if (s == null || s.isEmpty()) return s;
        return s.substring(0, 1).toUpperCase() + s.substring(1);
    }
    
    private void extractResourceIfMissing(String resourceName) throws IOException {
        Path targetPath = coinsFolder.resolve(resourceName);
        
        if (Files.exists(targetPath)) {
            return;
        }
        
        String resourcePath = JAR_RESOURCE_BASE + resourceName;
        try (InputStream is = getClass().getResourceAsStream(resourcePath)) {
            if (is == null) {
                logger.at(Level.WARNING).log("[EcotaleCoins] Resource not found in JAR: %s", resourcePath);
                return;
            }
            
            Files.copy(is, targetPath, StandardCopyOption.REPLACE_EXISTING);
            logger.at(Level.INFO).log("[EcotaleCoins] Extracted: %s", resourceName);
        }
    }
    
    private void extractItemDefinitionIfMissing(String itemFileName) throws IOException {
        Path targetPath = itemsFolder.resolve(itemFileName);
        
        if (Files.exists(targetPath)) {
            return;
        }
        
        String resourcePath = JAR_ITEMS_BASE + itemFileName;
        try (InputStream is = getClass().getResourceAsStream(resourcePath)) {
            if (is == null) {
                logger.at(Level.WARNING).log("[EcotaleCoins] Item definition not found in JAR: %s", resourcePath);
                return;
            }
            
            Files.copy(is, targetPath, StandardCopyOption.REPLACE_EXISTING);
            logger.at(Level.INFO).log("[EcotaleCoins] Extracted item definition: %s", itemFileName);
        }
    }
    
    private void extractIconIfMissing(String iconFileName) throws IOException {
        Path targetPath = iconsFolder.resolve(iconFileName);
        
        if (Files.exists(targetPath)) {
            return;
        }
        
        String resourcePath = JAR_ICONS_BASE + iconFileName;
        try (InputStream is = getClass().getResourceAsStream(resourcePath)) {
            if (is == null) {
                logger.at(Level.WARNING).log("[EcotaleCoins] Icon not found in JAR: %s", resourcePath);
                return;
            }
            
            Files.copy(is, targetPath, StandardCopyOption.REPLACE_EXISTING);
            logger.at(Level.INFO).log("[EcotaleCoins] Extracted icon: %s", iconFileName);
        }
    }
    
    private void extractUiIfMissing(String uiFileName) throws IOException {
        Path targetPath = uiFolder.resolve(uiFileName);
        
        if (Files.exists(targetPath)) {
            return;
        }
        
        String resourcePath = JAR_UI_BASE + uiFileName;
        try (InputStream is = getClass().getResourceAsStream(resourcePath)) {
            if (is == null) {
                logger.at(Level.WARNING).log("[EcotaleCoins] UI file not found in JAR: %s", resourcePath);
                return;
            }
            
            Files.copy(is, targetPath, StandardCopyOption.REPLACE_EXISTING);
            logger.at(Level.INFO).log("[EcotaleCoins] Extracted UI: %s", uiFileName);
        }
    }
    
    
    public Path getAssetPackRoot() {
        return assetPackRoot;
    }
    
    public boolean isInitialized() {
        return initialized;
    }
    
    public boolean isFirstTimeSetup() {
        return firstTimeSetup;
    }
}
