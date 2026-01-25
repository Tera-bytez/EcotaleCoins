package com.ecotalecoins.currency;

import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.Inventory;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import java.util.EnumMap;
import java.util.Map;
import java.util.Map.Entry;
import javax.annotation.Nonnull;

/**
 * Utility for calculating available inventory space for coins.
 * Supports intelligent stacking and slot availability checks.
 * 
 * @author Ecotale
 * @since 1.0.0
 */
public final class InventorySpaceCalculator {
    private static final int MAX_STACK_SIZE = 999;

    private InventorySpaceCalculator() {
    }

    /**
     * Analyze the player's inventory for coin stack information.
     * Returns detailed breakdown of each coin type's presence.
     * NOTE: Only analyzes storage, not hotbar, to match giveSpecificCoins behavior.
     */
    public static Map<CoinType, CoinStackInfo> analyzeInventory(@Nonnull Player player) {
        Map<CoinType, CoinStackInfo> result = new java.util.LinkedHashMap<>();

        for (CoinType type : CoinType.values()) {
            result.put(type, CoinStackInfo.empty(type));
        }

        Inventory inventory = player.getInventory();
        analyzeContainer(inventory.getStorage(), result);
        // NOTE: Removed hotbar analysis - giveSpecificCoins only uses storage
        return result;
    }

    private static void analyzeContainer(ItemContainer container, Map<CoinType, CoinStackInfo> result) {
        for (short i = 0; i < container.getCapacity(); i++) {
            ItemStack stack = container.getItemStack(i);
            if (stack != null && !stack.isEmpty()) {
                CoinType type = CoinType.fromItemId(stack.getItemId());
                if (type != null) {
                    CoinStackInfo current = result.get(type);
                    int quantity = stack.getQuantity();
                    int spaceLeft = MAX_STACK_SIZE - quantity;
                    result.put(
                        type,
                        new CoinStackInfo(
                            type, 
                            current.totalQuantity() + quantity, 
                            current.stackCount() + 1, 
                            current.spaceInExistingStacks() + spaceLeft
                        )
                    );
                }
            }
        }
    }

    /**
     * Check if a specific amount (auto-denominated) can fit in inventory.
     */
    public static SpaceResult canFitAmount(@Nonnull Player player, long amount) {
        if (amount <= 0L) {
            return SpaceResult.noCoins();
        }
        
        Map<CoinType, Integer> breakdown = CoinManager.calculateOptimalBreakdown(amount);
        Map<CoinType, CoinStackInfo> inventoryState = analyzeInventory(player);
        int totalFitsInExisting = 0;
        int totalNewSlotsNeeded = 0;

        for (Entry<CoinType, Integer> entry : breakdown.entrySet()) {
            CoinType type = entry.getKey();
            int coinsToAdd = entry.getValue();
            CoinStackInfo info = inventoryState.get(type);
            int spaceInExisting = info != null ? info.spaceInExistingStacks() : 0;
            int fitsInExisting = Math.min(coinsToAdd, spaceInExisting);
            totalFitsInExisting += fitsInExisting;
            int needsNewStacks = coinsToAdd - fitsInExisting;
            if (needsNewStacks > 0) {
                int stacksNeeded = (needsNewStacks + MAX_STACK_SIZE - 1) / MAX_STACK_SIZE;
                totalNewSlotsNeeded += stacksNeeded;
            }
        }

        int freeSlots = CoinManager.countFreeSlots(player);
        return totalNewSlotsNeeded <= freeSlots
            ? SpaceResult.success(totalNewSlotsNeeded, freeSlots, totalFitsInExisting, (int)amount - totalFitsInExisting)
            : SpaceResult.notEnoughSpace(totalNewSlotsNeeded, freeSlots);
    }

    /**
     * Check if a specific coin type and count can fit.
     */
    public static SpaceResult canFitSpecific(@Nonnull Player player, @Nonnull CoinType type, int count) {
        if (count <= 0) {
            return SpaceResult.noCoins();
        }
        
        Map<CoinType, CoinStackInfo> inventoryState = analyzeInventory(player);
        CoinStackInfo info = inventoryState.get(type);
        int spaceInExisting = info != null ? info.spaceInExistingStacks() : 0;
        int fitsInExisting = Math.min(count, spaceInExisting);
        int needsNewStacks = count - fitsInExisting;
        int newSlotsNeeded = 0;
        if (needsNewStacks > 0) {
            newSlotsNeeded = (needsNewStacks + MAX_STACK_SIZE - 1) / MAX_STACK_SIZE;
        }

        int freeSlots = CoinManager.countFreeSlots(player);
        
        return newSlotsNeeded <= freeSlots
            ? SpaceResult.success(newSlotsNeeded, freeSlots, fitsInExisting, needsNewStacks)
            : SpaceResult.notEnoughSpace(newSlotsNeeded, freeSlots);
    }

    /**
     * Calculate total space available for a specific coin type.
     */
    public static long calculateTotalSpaceFor(@Nonnull Player player, @Nonnull CoinType type) {
        CoinStackInfo info = analyzeInventory(player).get(type);
        int freeSlots = CoinManager.countFreeSlots(player);
        int spaceInExisting = info != null ? info.spaceInExistingStacks() : 0;
        return freeSlots * (long)MAX_STACK_SIZE + spaceInExisting;
    }

    /**
     * Get a debug summary of the player's coin inventory state.
     */
    public static String getInventorySummary(@Nonnull Player player) {
        Map<CoinType, CoinStackInfo> state = analyzeInventory(player);
        StringBuilder sb = new StringBuilder();
        sb.append("Inventory Coin State:\n");

        for (CoinStackInfo info : state.values()) {
            if (info.totalQuantity() > 0 || info.spaceInExistingStacks() > 0) {
                sb.append(
                    String.format(
                        "  %s: %d coins in %d stacks, %d space available\n",
                        info.type().name(),
                        info.totalQuantity(),
                        info.stackCount(),
                        info.spaceInExistingStacks()
                    )
                );
            }
        }

        int slots = CoinManager.countFreeSlots(player);
        sb.append("  Free slots: ").append(slots);
        return sb.toString();
    }

    /**
     * Information about coin stacks of a specific type in inventory.
     */
    public record CoinStackInfo(CoinType type, int totalQuantity, int stackCount, int spaceInExistingStacks) {
        public static CoinStackInfo empty(CoinType type) {
            return new CoinStackInfo(type, 0, 0, 0);
        }
    }

    /**
     * Result of a space calculation.
     */
    public record SpaceResult(boolean canFit, int slotsNeeded, int slotsAvailable, int coinsInExisting, int coinsNeedingNewSlots, String reason) {
        public static SpaceResult success(int inExisting, int inNew) {
            return new SpaceResult(true, 0, 0, inExisting, inNew, null);
        }

        public static SpaceResult success(int slotsNeeded, int slotsAvailable, int inExisting, int inNew) {
            return new SpaceResult(true, slotsNeeded, slotsAvailable, inExisting, inNew, null);
        }

        public static SpaceResult notEnoughSpace(int needed, int available) {
            return new SpaceResult(false, needed, available, 0, 0, "Need " + needed + " slots, only " + available + " available");
        }

        public static SpaceResult noCoins() {
            return new SpaceResult(true, 0, 0, 0, 0, null);
        }
    }
}
