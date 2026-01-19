package com.ecotalecoins;

import com.ecotale.api.PhysicalCoinsProvider;
import com.ecotalecoins.currency.BankManager;
import com.ecotalecoins.currency.CoinDropper;
import com.ecotalecoins.currency.CoinManager;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentAccessor;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import java.util.UUID;

/**
 * Implementation of PhysicalCoinsProvider for EcotaleCoins addon.
 * This bridges the Core's interface with our actual coin implementations.
 * 
 * @author Ecotale
 * @since 1.0.0
 */
public class EcotaleCoinsProviderImpl implements PhysicalCoinsProvider {
    
    // ========== Inventory Operations ==========
    
    @Override
    public long countInInventory(@Nonnull Player player) {
        return CoinManager.countCoins(player);
    }
    
    @Override
    public boolean canAfford(@Nonnull Player player, long amount) {
        return CoinManager.canAfford(player, amount);
    }
    
    @Override
    public boolean giveCoins(@Nonnull Player player, long amount) {
        if (player == null || amount <= 0) {
            return false;
        }
        return CoinManager.giveCoins(player, amount);
    }
    
    @Override
    public boolean takeCoins(@Nonnull Player player, long amount) {
        if (player == null || amount <= 0) {
            return false;
        }
        return CoinManager.takeCoins(player, amount);
    }
    
    // ========== World Drop Operations ==========
    
    @Override
    public void dropCoins(
        @Nonnull ComponentAccessor<EntityStore> store,
        @Nonnull CommandBuffer<EntityStore> commandBuffer,
        @Nonnull Vector3d position,
        long amount
    ) {
        CoinDropper.dropCoins(store, commandBuffer, position, amount);
    }
    
    @Override
    public void dropCoinsAtEntity(
        @Nonnull Ref<EntityStore> entityRef,
        @Nonnull ComponentAccessor<EntityStore> store,
        @Nonnull CommandBuffer<EntityStore> commandBuffer,
        long amount
    ) {
        CoinDropper.dropCoinsAtEntity(entityRef, store, commandBuffer, amount);
    }
    
    // ========== Bank Operations ==========
    
    @Override
    public long getBankBalance(@Nonnull UUID playerUuid) {
        return BankManager.getBankBalance(playerUuid);
    }
    
    @Override
    public boolean canAffordFromBank(@Nonnull UUID playerUuid, long amount) {
        return BankManager.canAffordFromBank(playerUuid, amount);
    }
    
    @Override
    public boolean bankDeposit(@Nonnull Player player, @Nonnull UUID playerUuid, long amount) {
        return BankManager.deposit(player, playerUuid, amount);
    }
    
    @Override
    public boolean bankWithdraw(@Nonnull Player player, @Nonnull UUID playerUuid, long amount) {
        return BankManager.withdraw(player, playerUuid, amount);
    }
    
    @Override
    public long getTotalWealth(@Nonnull Player player, @Nonnull UUID playerUuid) {
        return BankManager.getTotalWealth(player, playerUuid);
    }
}
