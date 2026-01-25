package com.ecotalecoins;

import com.ecotale.api.EcotaleAPI;
import com.ecotalecoins.commands.BankCommand;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import org.checkerframework.checker.nullness.compatqual.NonNullDecl;

import java.util.logging.Level;

/**
 * EcotaleCoins - Physical Currency addon for Ecotale economy.
 *
 * Features:
 * - Uses vanilla Hytale metal ingots as physical currency
 * - Bank vault for depositing/withdrawing ingots
 * - Exchange between ingot denominations
 * - Ingot-backed virtual economy integration
 *
 * Supported Ingots (ascending value):
 * - Copper Bar (1) → Iron Bar (10) → Cobalt Bar (100)
 * - Gold Bar (1,000) → Mithril Bar (10,000) → Adamantite Bar (100,000)
 *
 * @author Ecotale
 * @since 1.0.0
 */
public class Main extends JavaPlugin {
    
    private static Main instance;
    // CoinAssetManager removed - using vanilla Hytale ingots instead of custom coins
    private EcotaleCoinsProviderImpl coinsProvider;
    
    public Main(@NonNullDecl JavaPluginInit init) {
        super(init);
    }
    
    @Override
    protected void setup() {
        super.setup();
        instance = this;
        
        // Verify Ecotale Core is available
        if (!EcotaleAPI.isAvailable()) {
            this.getLogger().at(Level.SEVERE).log("[EcotaleCoins] Ecotale Core not loaded! Disabling.");
            return;
        }

        // Register provider with Ecotale Core
        this.coinsProvider = new EcotaleCoinsProviderImpl();
        EcotaleAPI.registerPhysicalCoinsProvider(this.coinsProvider);

        // Register commands
        this.getCommandRegistry().registerCommand(new BankCommand());

        this.getLogger().at(Level.INFO).log("[EcotaleCoins] Physical currency system loaded!");
        this.getLogger().at(Level.INFO).log("[EcotaleCoins] Using vanilla Hytale metal ingots as currency.");
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
