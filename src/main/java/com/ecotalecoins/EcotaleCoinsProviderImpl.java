package com.ecotalecoins;

import com.ecotale.api.CoinOperationResult;
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
    public CoinOperationResult canFitAmount(@Nonnull Player player, long amount) {
        if (player == null) {
            return CoinOperationResult.invalidPlayer();
        }
        if (amount <= 0) {
            return CoinOperationResult.invalidAmount(amount);
        }
        // For now, assume it can fit (EcotaleCoins doesn't have sophisticated space checking yet)
        // TODO: Implement proper space checking
        return CoinOperationResult.success(amount);
    }

    @Override
    public CoinOperationResult giveCoins(@Nonnull Player player, long amount) {
        if (player == null) {
            return CoinOperationResult.invalidPlayer();
        }
        if (amount <= 0) {
            return CoinOperationResult.invalidAmount(amount);
        }

        boolean success = CoinManager.giveCoins(player, amount);
        if (success) {
            return CoinOperationResult.success(amount);
        } else {
            // Assume inventory full if give failed
            return CoinOperationResult.notEnoughSpace(amount, 1, 0);
        }
    }

    @Override
    public CoinOperationResult takeCoins(@Nonnull Player player, long amount) {
        if (player == null) {
            return CoinOperationResult.invalidPlayer();
        }
        if (amount <= 0) {
            return CoinOperationResult.invalidAmount(amount);
        }

        boolean success = CoinManager.takeCoins(player, amount);
        if (success) {
            return CoinOperationResult.success(amount);
        } else {
            // Assume insufficient funds if take failed
            long actualBalance = CoinManager.countCoins(player);
            return CoinOperationResult.insufficientFunds(amount, actualBalance);
        }
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
            return CoinOperationResult.invalidAmount(amount);
        }

        boolean success = BankManager.deposit(player, playerUuid, amount);
        if (success) {
            return CoinOperationResult.success(amount);
        } else {
            // Assume insufficient funds in inventory
            long actualBalance = CoinManager.countCoins(player);
            return CoinOperationResult.insufficientFunds(amount, actualBalance);
        }
    }

    @Override
    public CoinOperationResult bankWithdraw(@Nonnull Player player, @Nonnull UUID playerUuid, long amount) {
        if (player == null) {
            return CoinOperationResult.invalidPlayer();
        }
        if (amount <= 0) {
            return CoinOperationResult.invalidAmount(amount);
        }

        boolean success = BankManager.withdraw(player, playerUuid, amount);
        if (success) {
            return CoinOperationResult.success(amount);
        } else {
            // Check if it's a funds issue or space issue
            long bankBalance = BankManager.getBankBalance(playerUuid);
            if (bankBalance < amount) {
                return CoinOperationResult.insufficientFunds(amount, bankBalance);
            } else {
                // Assume inventory full
                return CoinOperationResult.notEnoughSpace(amount, 1, 0);
            }
        }
    }

    @Override
    public long getTotalWealth(@Nonnull Player player, @Nonnull UUID playerUuid) {
        return BankManager.getTotalWealth(player, playerUuid);
    }
}
