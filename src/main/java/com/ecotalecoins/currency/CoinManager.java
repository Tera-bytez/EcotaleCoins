package com.ecotalecoins.currency;

import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.Inventory;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.inventory.transaction.ItemStackTransaction;

import javax.annotation.Nonnull;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Manages physical coin operations in player inventory.
 * Thread-safe operations with validation.
 */
public class CoinManager {

    private static final Logger LOGGER = Logger.getLogger("EcotaleCoins");
    
    private CoinManager() {}

    /**
     * Count total coin value in player inventory.
     * @return Total value in base units (copper)
     */
    public static long countCoins(@Nonnull Player player) {
        Inventory inventory = player.getInventory();
        long total = 0;

        total += countInContainer(inventory.getStorage());
        total += countInContainer(inventory.getHotbar());
        total += countInContainer(inventory.getBackpack());

        return total;
    }

    public static long countInContainer(@Nonnull ItemContainer container) {
        long total = 0;
        for (short i = 0; i < container.getCapacity(); i++) {
            ItemStack stack = container.getItemStack(i);
            if (stack != null && !stack.isEmpty()) {
                CoinType type = CoinType.fromItemId(stack.getItemId());
                if (type != null) {
                    total += type.getValue() * stack.getQuantity();
                }
            }
        }
        return total;
    }
    
    public static int countFreeSlots(@Nonnull Player player) {
        Inventory inventory = player.getInventory();
        // Only count storage slots - giveSpecificCoins only uses storage
        return countFreeSlotsInContainer(inventory.getStorage());
    }
    
    private static int countFreeSlotsInContainer(@Nonnull ItemContainer container) {
        int free = 0;
        for (short i = 0; i < container.getCapacity(); i++) {
            ItemStack stack = container.getItemStack(i);
            if (stack == null || stack.isEmpty()) {
                free++;
            }
        }
        return free;
    }

    /**
     * Give coins to player, using optimal denominations.
     */
    public static boolean giveCoins(@Nonnull Player player, long amount) {
        if (amount <= 0) return false;

        Inventory inventory = player.getInventory();
        Map<CoinType, Integer> breakdown = calculateOptimalBreakdown(amount);
        final int MAX_STACK_SIZE = 999;

        for (Map.Entry<CoinType, Integer> entry : breakdown.entrySet()) {
            CoinType type = entry.getKey();
            int remaining = entry.getValue();

            while (remaining > 0) {
                int stackSize = Math.min(remaining, MAX_STACK_SIZE);
                ItemStack coinStack = new ItemStack(type.getItemId(), stackSize);
                ItemStackTransaction transaction = inventory.getStorage().addItemStack(coinStack);

                if (!transaction.succeeded()) {
                    transaction = inventory.getHotbar().addItemStack(coinStack);
                    if (!transaction.succeeded()) {
                        LOGGER.fine("Could not give all coins (inventory full). Type: " + type + " Remaining: " + remaining);
                        return false;
                    }
                }
                remaining -= stackSize;
            }
        }

        return true;
    }

    /**
     * Take coins from player inventory.
     */
    public static boolean takeCoins(@Nonnull Player player, long amount) {
        if (amount <= 0) return true;

        long currentBalance = countCoins(player);
        if (currentBalance < amount) {
            return false;
        }

        Inventory inventory = player.getInventory();
        long remaining = amount;

        for (CoinType type : CoinType.valuesDescending()) {
            if (remaining <= 0) break;

            remaining = removeCoinsOfType(inventory.getStorage(), type, remaining);
            remaining = removeCoinsOfType(inventory.getHotbar(), type, remaining);
            remaining = removeCoinsOfType(inventory.getBackpack(), type, remaining);
        }

        if (remaining < 0) {
            giveCoins(player, -remaining);
        }

        return remaining <= 0;
    }

    private static long removeCoinsOfType(ItemContainer container, CoinType type, long remaining) {
        if (remaining <= 0) return remaining;

        for (short i = 0; i < container.getCapacity(); i++) {
            ItemStack stack = container.getItemStack(i);
            if (stack != null && type.getItemId().equals(stack.getItemId())) {
                int quantity = stack.getQuantity();
                long stackValue = type.getValue() * quantity;

                if (stackValue <= remaining) {
                    container.removeItemStack(stack);
                    remaining -= stackValue;
                } else {
                    int coinsToRemove = (int) Math.ceil((double) remaining / type.getValue());
                    int newQuantity = quantity - coinsToRemove;

                    if (newQuantity > 0) {
                        container.setItemStackForSlot(i, stack.withQuantity(newQuantity));
                    } else {
                        container.removeItemStack(stack);
                    }

                    long actualValueRemoved = coinsToRemove * type.getValue();
                    remaining -= actualValueRemoved;
                }

                if (remaining <= 0) break;
            }
        }

        return remaining;
    }

    public static boolean canAfford(@Nonnull Player player, long amount) {
        return countCoins(player) >= amount;
    }

    public static Map<CoinType, Integer> calculateOptimalBreakdown(long amount) {
        Map<CoinType, Integer> breakdown = new HashMap<>();
        long remaining = amount;

        for (CoinType type : CoinType.valuesDescending()) {
            if (remaining >= type.getValue()) {
                int count = (int) (remaining / type.getValue());
                breakdown.put(type, count);
                remaining %= type.getValue();
            }
        }

        return breakdown;
    }

    public static void consolidate(@Nonnull Player player) {
        long totalValue = countCoins(player);
        removeAllCoins(player);
        if (totalValue > 0) {
            giveCoins(player, totalValue);
        }
    }

    public static void removeAllCoins(@Nonnull Player player) {
        Inventory inventory = player.getInventory();
        removeAllCoinsFromContainer(inventory.getStorage());
        removeAllCoinsFromContainer(inventory.getHotbar());
        removeAllCoinsFromContainer(inventory.getBackpack());
    }

    private static void removeAllCoinsFromContainer(ItemContainer container) {
        for (short i = 0; i < container.getCapacity(); i++) {
            ItemStack stack = container.getItemStack(i);
            if (stack != null && CoinType.isCoin(stack.getItemId())) {
                container.removeItemStack(stack);
            }
        }
    }

    public static Map<CoinType, Integer> getBreakdown(@Nonnull Player player) {
        Map<CoinType, Integer> breakdown = new HashMap<>();
        Inventory inventory = player.getInventory();

        countBreakdownInContainer(inventory.getStorage(), breakdown);
        countBreakdownInContainer(inventory.getHotbar(), breakdown);
        countBreakdownInContainer(inventory.getBackpack(), breakdown);

        return breakdown;
    }

    private static void countBreakdownInContainer(ItemContainer container, Map<CoinType, Integer> breakdown) {
        for (short i = 0; i < container.getCapacity(); i++) {
            ItemStack stack = container.getItemStack(i);
            if (stack != null && !stack.isEmpty()) {
                CoinType type = CoinType.fromItemId(stack.getItemId());
                if (type != null) {
                    breakdown.merge(type, stack.getQuantity(), Integer::sum);
                }
            }
        }
    }

    public static boolean takeSpecificCoins(@Nonnull Player player, @Nonnull CoinType type, int count) {
        if (count <= 0) return true;
        
        Inventory inventory = player.getInventory();
        int remaining = count;
        
        remaining = removeSpecificFromContainer(inventory.getStorage(), type, remaining);
        remaining = removeSpecificFromContainer(inventory.getHotbar(), type, remaining);
        remaining = removeSpecificFromContainer(inventory.getBackpack(), type, remaining);
        
        return remaining <= 0;
    }
    
    private static int removeSpecificFromContainer(ItemContainer container, CoinType type, int remaining) {
        if (remaining <= 0) return remaining;
        
        for (short i = 0; i < container.getCapacity(); i++) {
            ItemStack stack = container.getItemStack(i);
            if (stack != null && type.getItemId().equals(stack.getItemId())) {
                int quantity = stack.getQuantity();
                
                if (quantity <= remaining) {
                    container.removeItemStack(stack);
                    remaining -= quantity;
                } else {
                    container.setItemStackForSlot(i, stack.withQuantity(quantity - remaining));
                    remaining = 0;
                }
                
                if (remaining <= 0) break;
            }
        }
        
        return remaining;
    }

    public static boolean giveSpecificCoins(@Nonnull Player player, @Nonnull CoinType type, int count) {
        if (count <= 0) return true;
        
        // PRE-VALIDATION: Check if ALL coins can fit BEFORE giving any
        InventorySpaceCalculator.SpaceResult spaceCheck = 
            InventorySpaceCalculator.canFitSpecific(player, type, count);
        if (!spaceCheck.canFit()) {
            return false; // Reject early - don't give partial coins
        }
        
        Inventory inventory = player.getInventory();
        final int MAX_STACK_SIZE = 999;
        
        int remaining = count;
        ItemContainer storage = inventory.getStorage();
        // NOTE: Only using storage (not hotbar) to match countFreeSlots validation
        
        while (remaining > 0) {
            int stackSize = Math.min(remaining, MAX_STACK_SIZE);
            ItemStack coinStack = new ItemStack(type.getItemId(), stackSize);
            
            ItemStackTransaction transaction = storage.addItemStack(coinStack);
            if (transaction.succeeded()) {
                remaining -= stackSize;
            } else {
                // This should NEVER happen after pre-validation
                return false;
            }
        }
        
        return true;
    }
}
