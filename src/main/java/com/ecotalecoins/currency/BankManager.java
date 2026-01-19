package com.ecotalecoins.currency;

import com.ecotale.api.EcotaleAPI;
import com.hypixel.hytale.server.core.entity.entities.Player;

import javax.annotation.Nonnull;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Manages bank operations (virtual storage for coins).
 * Bank = safe storage that doesn't drop on death.
 * Uses Ecotale Core's balance system for storage.
 */
public class BankManager {

    private static final ConcurrentHashMap<UUID, ReentrantLock> playerLocks = new ConcurrentHashMap<>();

    private BankManager() {}

    /**
     * Get bank balance for a player (uses Ecotale Core DB).
     */
    public static long getBankBalance(@Nonnull UUID playerId) {
        return (long) EcotaleAPI.getBalance(playerId);
    }

    /**
     * Check if bank has sufficient funds.
     */
    public static boolean canAffordFromBank(@Nonnull UUID playerId, long amount) {
        return getBankBalance(playerId) >= amount;
    }

    /**
     * Get or create lock for player.
     */
    public static ReentrantLock getPlayerLock(UUID playerId) {
        return playerLocks.computeIfAbsent(playerId, k -> new ReentrantLock());
    }

    /**
     * Clean up locks for offline players (call periodically).
     */
    public static void cleanupLocks() {
        playerLocks.entrySet().removeIf(entry -> !entry.getValue().isLocked());
    }

    /**
     * Get total wealth (physical + bank).
     */
    public static long getTotalWealth(@Nonnull Player player, @Nonnull UUID playerUuid) {
        long physical = CoinManager.countCoins(player);
        long bank = getBankBalance(playerUuid);
        return physical + bank;
    }
    
    /**
     * Deposit from physical coins to bank.
     * Returns true if successful.
     */
    public static boolean deposit(@Nonnull Player player, @Nonnull UUID playerUuid, long amount) {
        if (amount <= 0) return false;
        
        ReentrantLock lock = getPlayerLock(playerUuid);
        lock.lock();
        try {
            if (!CoinManager.canAfford(player, amount)) {
                return false;
            }
            
            boolean removed = CoinManager.takeCoins(player, amount);
            if (!removed) {
                return false;
            }
            
            EcotaleAPI.deposit(playerUuid, (double) amount, "Bank deposit");
            return true;
        } finally {
            lock.unlock();
        }
    }
    
    /**
     * Withdraw from bank to physical coins.
     * Returns true if successful.
     */
    public static boolean withdraw(@Nonnull Player player, @Nonnull UUID playerUuid, long amount) {
        if (amount <= 0) return false;
        
        ReentrantLock lock = getPlayerLock(playerUuid);
        lock.lock();
        try {
            long currentBank = getBankBalance(playerUuid);
            if (currentBank < amount) {
                return false;
            }
            
            boolean withdrawn = EcotaleAPI.withdraw(playerUuid, (double) amount, "Bank withdrawal");
            if (!withdrawn) {
                return false;
            }
            
            boolean given = CoinManager.giveCoins(player, amount);
            if (!given) {
                // Rollback
                EcotaleAPI.deposit(playerUuid, (double) amount, "Withdrawal rollback");
                return false;
            }
            
            return true;
        } finally {
            lock.unlock();
        }
    }
}
