package com.ecotalecoins;

import com.ecotale.api.EcotaleAPI;
import com.ecotalecoins.commands.BankCommand;
import com.ecotalecoins.currency.CoinAssetManager;
import com.hypixel.hytale.server.core.HytaleServer;
import com.hypixel.hytale.server.core.ShutdownReason;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import org.checkerframework.checker.nullness.compatqual.NonNullDecl;

import java.util.logging.Level;

/**
 * EcotaleCoins - Physical Coins addon for Ecotale economy.
 * 
 * Features:
 * - Physical coin items that drop on death
 * - Bank vault for safe storage
 * - Coin drops from mobs and mining
 * - Multiple coin denominations
 * 
 * @author Ecotale
 * @since 1.0.0
 */
public class Main extends JavaPlugin {
    
    private static Main instance;
    private CoinAssetManager coinAssetManager;
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
        
        // Initialize asset pack
        java.nio.file.Path modsFolder = this.getDataDirectory().getParent();
        java.nio.file.Path assetPackPath = modsFolder.resolve("Ecotale_EcotaleCoins");
        
        this.coinAssetManager = new CoinAssetManager(assetPackPath, this.getLogger());
        this.coinAssetManager.initialize();
        
        // Register provider with Ecotale Core
        this.coinsProvider = new EcotaleCoinsProviderImpl();
        EcotaleAPI.registerPhysicalCoinsProvider(this.coinsProvider);
        
        // Register commands
        this.getCommandRegistry().registerCommand(new BankCommand());
        
        // First-time setup check
        if (this.coinAssetManager.isFirstTimeSetup()) {
            scheduleFirstTimeRestart();
            return;
        }
        
        this.getLogger().at(Level.INFO).log("[EcotaleCoins] Physical coins system loaded!");
    }
    
    @Override
    protected void shutdown() {
        EcotaleAPI.unregisterPhysicalCoinsProvider();
        this.getLogger().at(Level.INFO).log("[EcotaleCoins] Shutdown complete.");
    }
    
    private void scheduleFirstTimeRestart() {
        new Thread(() -> {
            try {
                HytaleServer server = HytaleServer.get();
                while (!server.isBooted() && !server.isShuttingDown()) {
                    Thread.sleep(100);
                }
                
                if (server.isShuttingDown()) {
                    return;
                }
                
                Thread.sleep(2000);
                
                this.getLogger().at(Level.INFO).log("");
                this.getLogger().at(Level.INFO).log("[EcotaleCoins] First-time setup complete!");
                this.getLogger().at(Level.INFO).log("[EcotaleCoins] Asset pack: mods/Ecotale_EcotaleCoins/");
                this.getLogger().at(Level.INFO).log("[EcotaleCoins] Restarting in 5 seconds...");
                this.getLogger().at(Level.INFO).log("");
                
                Thread.sleep(5000);
                
                this.getLogger().at(Level.INFO).log("[EcotaleCoins] Restarting now...");
                server.shutdownServer(ShutdownReason.SHUTDOWN.withMessage(
                    "EcotaleCoins first-time setup complete."
                ));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, "EcotaleCoins-FirstTimeSetup").start();
    }
    
    public static Main getInstance() {
        return instance;
    }
    
    public CoinAssetManager getCoinAssetManager() {
        return coinAssetManager;
    }
}
