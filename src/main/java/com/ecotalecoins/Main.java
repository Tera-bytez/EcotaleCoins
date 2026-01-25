package com.ecotalecoins;

import com.ecotale.api.EcotaleAPI;
import com.ecotalecoins.commands.BankCommand;
import com.ecotalecoins.config.EcotaleCoinsConfig;
import com.ecotalecoins.currency.CoinType;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.hypixel.hytale.server.core.util.Config;
import org.checkerframework.checker.nullness.compatqual.NonNullDecl;

import java.util.logging.Level;

/**
 * EcotaleCoins - Physical Currency addon for Ecotale economy.
 *
 * Features:
 * - Configurable physical currency items (defaults to vanilla Hytale metal ingots)
 * - Bank vault for depositing/withdrawing currency items
 * - Exchange between currency denominations
 * - Item-backed virtual economy integration
 *
 * Configuration:
 * - Currency items and values defined in EcotaleCoins.json
 * - No code changes needed to customize currency
 * - Default: Copper/Iron/Cobalt/Gold/Mithril/Adamantite Bars (1:10 ratio)
 *
 * @author Ecotale
 * @since 1.0.0
 */
public class Main extends JavaPlugin {

    private static Main instance;
    public static Config<EcotaleCoinsConfig> CONFIG;

    // CoinAssetManager removed - using vanilla Hytale ingots instead of custom coins
    private EcotaleCoinsProviderImpl coinsProvider;

    public Main(@NonNullDecl JavaPluginInit init) {
        super(init);
        CONFIG = this.withConfig("EcotaleCoins", EcotaleCoinsConfig.CODEC);
    }
    
    @Override
    protected void setup() {
        super.setup();
        instance = this;

        // Save default config
        CONFIG.save();

        // Verify Ecotale Core is available
        if (!EcotaleAPI.isAvailable()) {
            this.getLogger().at(Level.SEVERE).log("[EcotaleCoins] Ecotale Core not loaded! Disabling.");
            return;
        }

        // Initialize currency types from configuration
        CoinType.initialize(CONFIG.get());
        this.getLogger().at(Level.INFO).log("[EcotaleCoins] Loaded " + CoinType.values().length + " currency tiers from config.");

        // Register provider with Ecotale Core
        this.coinsProvider = new EcotaleCoinsProviderImpl();
        EcotaleAPI.registerPhysicalCoinsProvider(this.coinsProvider);

        // Register commands
        if (CONFIG.get().isEnableBankCommand()) {
            this.getCommandRegistry().registerCommand(new BankCommand());
        }

        this.getLogger().at(Level.INFO).log("[EcotaleCoins] Physical currency system loaded!");
        this.getLogger().at(Level.INFO).log("[EcotaleCoins] Using configurable currency items (default: vanilla ingots).");
    }
    
    @Override
    protected void shutdown() {
        EcotaleAPI.unregisterPhysicalCoinsProvider();
        this.getLogger().at(Level.INFO).log("[EcotaleCoins] Shutdown complete.");
    }
    
    public static Main getInstance() {
        return instance;
    }
}
