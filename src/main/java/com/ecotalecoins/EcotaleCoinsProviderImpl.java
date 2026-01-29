package com.ecotalecoins;

import com.ecotale.api.CoinOperationResult;
import com.ecotale.api.PhysicalCoinsProvider;
import com.ecotalecoins.currency.BankManager;
import com.ecotalecoins.currency.CoinDropper;
import com.ecotalecoins.currency.CoinManager;
import com.ecotalecoins.currency.InventorySpaceCalculator;
import com.ecotalecoins.currency.InventorySpaceCalculator.SpaceResult;
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
 * Uses secure operations with pre-validation and detailed results.
 * 
 * @author Ecotale
 * @since 1.0.0 (Secure API: 1.2.0)
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
    public CoinOperationResult canFitAmount(@Nonnull Player player, long amount) {
        if (player == null) {
            return CoinOperationResult.invalidPlayer();
        }
        if (amount <= 0) {
            return amount == 0 ? CoinOperationResult.success(0) : CoinOperationResult.invalidAmount(amount);
        }
        
        SpaceResult result = InventorySpaceCalculator.canFitAmount(player, amount);
        if (result.canFit()) {
            return CoinOperationResult.success(amount);
        }
        return CoinOperationResult.notEnoughSpace(amount, result.slotsNeeded(), result.slotsAvailable());
    }
    
    @Override
    public CoinOperationResult giveCoins(@Nonnull Player player, long amount) {
        if (player == null) {
            return CoinOperationResult.invalidPlayer();
        }
        if (amount <= 0) {
            return amount == 0 ? CoinOperationResult.success(0) : CoinOperationResult.invalidAmount(amount);
        }
        
        // Pre-check space before attempting
        CoinOperationResult check = canFitAmount(player, amount);
        if (!check.isSuccess()) {
            return check;
        }
        
        // Actually give coins
        boolean success = CoinManager.giveCoins(player, amount);
        return success ? CoinOperationResult.success(amount) 
                       : CoinOperationResult.notEnoughSpace(amount, 0, 0);
    }
    
    @Override
    public CoinOperationResult takeCoins(@Nonnull Player player, long amount) {
        if (player == null) {
            return CoinOperationResult.invalidPlayer();
        }
        if (amount <= 0) {
            return amount == 0 ? CoinOperationResult.success(0) : CoinOperationResult.invalidAmount(amount);
        }
        
        long balance = CoinManager.countCoins(player);
        if (balance < amount) {
            return CoinOperationResult.insufficientFunds(amount, balance);
        }
        
        boolean success = CoinManager.takeCoins(player, amount);
        return success ? CoinOperationResult.success(amount)
                       : CoinOperationResult.insufficientFunds(amount, balance);
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
    public CoinOperationResult bankDeposit(@Nonnull Player player, @Nonnull UUID playerUuid, long amount) {
        if (player == null) {
            return CoinOperationResult.invalidPlayer();
        }
        if (amount <= 0) {
            return amount == 0 ? CoinOperationResult.success(0) : CoinOperationResult.invalidAmount(amount);
        }
        
        // Check if player has enough coins
        long balance = CoinManager.countCoins(player);
        if (balance < amount) {
            return CoinOperationResult.insufficientFunds(amount, balance);
        }
        
        boolean success = BankManager.deposit(player, playerUuid, amount);
        return success ? CoinOperationResult.success(amount)
                       : CoinOperationResult.insufficientFunds(amount, balance);
    }
    
    @Override
    public CoinOperationResult bankWithdraw(@Nonnull Player player, @Nonnull UUID playerUuid, long amount) {
        if (player == null) {
            return CoinOperationResult.invalidPlayer();
        }
        if (amount <= 0) {
            return amount == 0 ? CoinOperationResult.success(0) : CoinOperationResult.invalidAmount(amount);
        }
        
        // Check if bank has enough
        long bankBalance = BankManager.getBankBalance(playerUuid);
        if (bankBalance < amount) {
            return CoinOperationResult.insufficientFunds(amount, bankBalance);
        }
        
        // Check if coins will fit in inventory
        CoinOperationResult spaceCheck = canFitAmount(player, amount);
        if (!spaceCheck.isSuccess()) {
            return spaceCheck;
        }
        
        boolean success = BankManager.withdraw(player, playerUuid, amount);
        return success ? CoinOperationResult.success(amount)
                       : CoinOperationResult.insufficientFunds(amount, bankBalance);
    }
    
    @Override
    public long getTotalWealth(@Nonnull Player player, @Nonnull UUID playerUuid) {
        return BankManager.getTotalWealth(player, playerUuid);
    }
}
