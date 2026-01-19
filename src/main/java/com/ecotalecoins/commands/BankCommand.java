package com.ecotalecoins.commands;

import com.ecotalecoins.currency.BankManager;
import com.ecotalecoins.currency.CoinManager;
import com.ecotalecoins.transaction.SecureTransaction;
import com.ecotale.api.EcotaleAPI;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.GameMode;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.CommandSender;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractAsyncCommand;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import org.checkerframework.checker.nullness.compatqual.NonNullDecl;

import java.awt.Color;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Bank command - Manage your bank account (physical coins).
 * 
 * Usage:
 * - /bank                      → Shows balance info
 * - /bank deposit <amount|all> → Deposit coins from inventory to bank
 * - /bank withdraw <amount|all> → Withdraw coins from bank to inventory
 * 
 * @author Ecotale
 * @since 1.0.0
 */
public class BankCommand extends AbstractAsyncCommand {
    
    public BankCommand() {
        super("bank", "Manage your bank account");
        this.setPermissionGroup(GameMode.Adventure);
        
        this.addSubCommand(new BankDepositCommand());
        this.addSubCommand(new BankWithdrawCommand());
    }
    
    @NonNullDecl
    @Override
    protected CompletableFuture<Void> executeAsync(CommandContext ctx) {
        CommandSender sender = ctx.sender();
        
        if (!(sender instanceof Player player)) {
            ctx.sendMessage(Message.raw("This command can only be used by players").color(Color.RED));
            return CompletableFuture.completedFuture(null);
        }
        
        Ref<EntityStore> ref = player.getReference();
        if (ref == null || !ref.isValid()) {
            ctx.sendMessage(Message.raw("Error: Could not get your player data").color(Color.RED));
            return CompletableFuture.completedFuture(null);
        }

        Store<EntityStore> store = ref.getStore();
        World world = store.getExternalData().getWorld();
        if (world == null) {
            return CompletableFuture.completedFuture(null);
        }

        // No subcommand = open Bank GUI
        return CompletableFuture.runAsync(() -> {
            PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
            if (playerRef != null) {
                player.getPageManager().openCustomPage(ref, store, new com.ecotalecoins.gui.BankGui(playerRef));
            }
        }, world);
    }

    // ========== Deposit Subcommand ==========
    private static class BankDepositCommand extends AbstractAsyncCommand {
        private final RequiredArg<String> amountArg;
        
        public BankDepositCommand() {
            super("deposit", "Deposit coins to your bank");
            this.addAliases("d");
            this.amountArg = this.withRequiredArg("amount", "Amount or 'all'", ArgTypes.STRING);
        }
        
        @NonNullDecl
        @Override
        protected CompletableFuture<Void> executeAsync(CommandContext ctx) {
            CommandSender sender = ctx.sender();
            
            if (!(sender instanceof Player player)) {
                ctx.sendMessage(Message.raw("This command can only be used by players").color(Color.RED));
                return CompletableFuture.completedFuture(null);
            }
            
            Ref<EntityStore> ref = player.getReference();
            if (ref == null || !ref.isValid()) {
                return CompletableFuture.completedFuture(null);
            }

            Store<EntityStore> store = ref.getStore();
            World world = store.getExternalData().getWorld();
            if (world == null) {
                return CompletableFuture.completedFuture(null);
            }

            String amountStr = ctx.get(amountArg);
            
            return CompletableFuture.runAsync(() -> {
                PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
                if (playerRef == null) return;
                
                UUID playerUuid = playerRef.getUuid();
                
                long amount;
                if (amountStr.equalsIgnoreCase("all")) {
                    amount = CoinManager.countCoins(player);
                } else {
                    try {
                        amount = Long.parseLong(amountStr);
                    } catch (NumberFormatException e) {
                        ctx.sendMessage(Message.raw("Invalid amount. Use a number or 'all'").color(Color.RED));
                        return;
                    }
                }

                if (amount <= 0) {
                    ctx.sendMessage(Message.raw("Amount must be positive").color(Color.RED));
                    return;
                }

                long currentPhysical = CoinManager.countCoins(player);
                if (currentPhysical < amount) {
                    ctx.sendMessage(Message.join(
                        Message.raw("Not enough coins. You have: ").color(Color.RED),
                        Message.raw(formatLong(currentPhysical)).color(Color.WHITE)
                    ));
                    return;
                }

                SecureTransaction.TransactionResult result = SecureTransaction.executeSecureDeposit(player, playerUuid, amount);
                
                if (result.isSuccess()) {
                    long newBankBalance = BankManager.getBankBalance(playerUuid);
                    ctx.sendMessage(Message.join(
                        Message.raw("Deposited ").color(Color.GREEN),
                        Message.raw(formatLong(amount)).color(new Color(50, 205, 50)).bold(true),
                        Message.raw(" coins. Bank: ").color(Color.GREEN),
                        Message.raw(formatLong(newBankBalance)).color(Color.WHITE)
                    ));
                } else {
                    ctx.sendMessage(Message.raw(result.getMessage()).color(Color.RED));
                }
            }, world);
        }
    }

    // ========== Withdraw Subcommand ==========
    private static class BankWithdrawCommand extends AbstractAsyncCommand {
        private final RequiredArg<String> amountArg;
        
        public BankWithdrawCommand() {
            super("withdraw", "Withdraw coins from your bank");
            this.addAliases("w");
            this.amountArg = this.withRequiredArg("amount", "Amount or 'all'", ArgTypes.STRING);
        }
        
        @NonNullDecl
        @Override
        protected CompletableFuture<Void> executeAsync(CommandContext ctx) {
            CommandSender sender = ctx.sender();
            
            if (!(sender instanceof Player player)) {
                ctx.sendMessage(Message.raw("This command can only be used by players").color(Color.RED));
                return CompletableFuture.completedFuture(null);
            }
            
            Ref<EntityStore> ref = player.getReference();
            if (ref == null || !ref.isValid()) {
                return CompletableFuture.completedFuture(null);
            }

            Store<EntityStore> store = ref.getStore();
            World world = store.getExternalData().getWorld();
            if (world == null) {
                return CompletableFuture.completedFuture(null);
            }

            String amountStr = ctx.get(amountArg);
            
            return CompletableFuture.runAsync(() -> {
                PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
                if (playerRef == null) return;
                
                UUID playerUuid = playerRef.getUuid();
                
                long amount;
                if (amountStr.equalsIgnoreCase("all")) {
                    amount = BankManager.getBankBalance(playerUuid);
                } else {
                    try {
                        amount = Long.parseLong(amountStr);
                    } catch (NumberFormatException e) {
                        ctx.sendMessage(Message.raw("Invalid amount. Use a number or 'all'").color(Color.RED));
                        return;
                    }
                }

                if (amount <= 0) {
                    ctx.sendMessage(Message.raw("Amount must be positive").color(Color.RED));
                    return;
                }

                long currentBank = BankManager.getBankBalance(playerUuid);
                if (currentBank < amount) {
                    ctx.sendMessage(Message.join(
                        Message.raw("Not enough in bank. You have: ").color(Color.RED),
                        Message.raw(formatLong(currentBank)).color(Color.WHITE)
                    ));
                    return;
                }

                SecureTransaction.TransactionResult result = SecureTransaction.executeSecureWithdraw(player, playerUuid, amount);
                
                if (result.isSuccess()) {
                    long newBankBalance = BankManager.getBankBalance(playerUuid);
                    ctx.sendMessage(Message.join(
                        Message.raw("Withdrew ").color(Color.GREEN),
                        Message.raw(formatLong(amount)).color(new Color(50, 205, 50)).bold(true),
                        Message.raw(" coins. Bank: ").color(Color.GREEN),
                        Message.raw(formatLong(newBankBalance)).color(Color.WHITE)
                    ));
                } else if (result.isMoneySafe() && result.getTxHash() != null) {
                    ctx.sendMessage(Message.raw(result.getMessage()).color(Color.YELLOW));
                } else {
                    ctx.sendMessage(Message.raw(result.getMessage()).color(Color.RED));
                }
            }, world);
        }
    }

    // ========== Utility ==========
    private static String formatLong(long value) {
        if (value >= 1_000_000_000) {
            return String.format("%.2fB", value / 1_000_000_000.0);
        } else if (value >= 1_000_000) {
            return String.format("%.2fM", value / 1_000_000.0);
        } else if (value >= 1_000) {
            return String.format("%.1fK", value / 1_000.0);
        }
        return String.valueOf(value);
    }
}
