package com.ecotalecoins.currency;

import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentAccessor;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.item.ItemComponent;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Utility to spawn physical coin drops in the world.
 * Uses CommandBuffer for deferred spawning (required by Hytale ECS).
 * 
 * @author Ecotale
 * @since 1.0.0
 */
public class CoinDropper {

    private static final int MAX_STACK_SIZE = 999;

    private CoinDropper() {}

    /**
     * Drop coins at an entity's position using CommandBuffer.
     */
    public static void dropCoinsAtEntity(
        @Nonnull Ref<EntityStore> entityRef,
        @Nonnull ComponentAccessor<EntityStore> store,
        @Nonnull CommandBuffer<EntityStore> commandBuffer,
        long amount
    ) {
        if (amount <= 0) return;

        TransformComponent transformComponent = store.getComponent(entityRef, TransformComponent.getComponentType());
        if (transformComponent == null) return;

        Vector3d position = transformComponent.getPosition().clone();
        position.add(0, 0.5, 0);

        dropCoins(store, commandBuffer, position, amount);
    }

    /**
     * Drop coins at a specific world position.
     */
    public static void dropCoins(
        @Nonnull ComponentAccessor<EntityStore> store,
        @Nonnull CommandBuffer<EntityStore> commandBuffer,
        @Nonnull Vector3d position,
        long amount
    ) {
        if (amount <= 0) return;

        Map<CoinType, Integer> breakdown = CoinManager.calculateOptimalBreakdown(amount);

        for (Map.Entry<CoinType, Integer> entry : breakdown.entrySet()) {
            CoinType type = entry.getKey();
            int quantity = entry.getValue();

            if (quantity > 0) {
                dropCoinStacks(store, commandBuffer, position, type, quantity);
            }
        }
    }

    private static void dropCoinStacks(
        @Nonnull ComponentAccessor<EntityStore> store,
        @Nonnull CommandBuffer<EntityStore> commandBuffer,
        @Nonnull Vector3d position,
        @Nonnull CoinType coinType,
        int totalQuantity
    ) {
        int remaining = totalQuantity;
        
        while (remaining > 0) {
            int stackSize = Math.min(remaining, MAX_STACK_SIZE);
            remaining -= stackSize;
            
            ItemStack coinStack = new ItemStack(coinType.getItemId(), stackSize);
            spawnDroppedItem(store, commandBuffer, coinStack, position);
        }
    }

    private static void spawnDroppedItem(
        @Nonnull ComponentAccessor<EntityStore> store,
        @Nonnull CommandBuffer<EntityStore> commandBuffer,
        @Nonnull ItemStack itemStack,
        @Nonnull Vector3d position
    ) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        
        float velocityX = (random.nextFloat() - 0.5f) * 0.3f;
        float velocityY = 0.2f + random.nextFloat() * 0.1f;
        float velocityZ = (random.nextFloat() - 0.5f) * 0.3f;

        Holder<EntityStore> itemEntityHolder = ItemComponent.generateItemDrop(
            store,
            itemStack,
            position,
            Vector3f.ZERO,
            velocityX,
            velocityY,
            velocityZ
        );

        if (itemEntityHolder != null) {
            ItemComponent itemComponent = itemEntityHolder.getComponent(ItemComponent.getComponentType());
            if (itemComponent != null) {
                itemComponent.setPickupDelay(0.5f);
            }

            commandBuffer.addEntity(itemEntityHolder, AddReason.SPAWN);
        }
    }
}
