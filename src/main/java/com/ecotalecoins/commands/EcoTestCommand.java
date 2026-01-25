package com.ecotalecoins.commands;

import com.ecotale.api.EcotaleAPI;
import com.ecotalecoins.currency.BankManager;
import com.ecotalecoins.currency.CoinManager;
import com.ecotalecoins.currency.CoinType;
import com.ecotalecoins.api.CoinResult;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractAsyncCommand;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import org.checkerframework.checker.nullness.compatqual.NonNullDecl;

import java.awt.Color;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Debug command to test EcotaleCoins functionality.
 * Usage: /cointest
 * 
 * Tests physical coin operations including:
 * - Coin counting
 * - Give/take operations
 * - Bank operations
 * - Coin breakdown
 * 
 * Requires OP permission.
 * Only registered when DebugMode is enabled in config.
 * 
 * @author Ecotale
 * @since 1.0.0
 */
public class EcoTestCommand extends AbstractAsyncCommand {
    
    public EcoTestCommand() {
        super("cointest", "Test EcotaleCoins functionality");
        this.setPermissionGroup(null); // OP only
    }
    
    @NonNullDecl
    @Override
    protected CompletableFuture<Void> executeAsync(CommandContext context) {
        if (context.sender() instanceof Player player) {
            Ref<EntityStore> ref = player.getReference();
            if (ref != null && ref.isValid()) {
                Store<EntityStore> store = ref.getStore();
                
                return CompletableFuture.runAsync(() -> {
                    PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
                    if (playerRef == null) {
                        player.sendMessage(Message.raw("Error: Could not get player data").color(Color.RED));
                        return;
                    }
                    
                    UUID uuid = playerRef.getUuid();
                    int passed = 0;
                    int failed = 0;
                    
                    player.sendMessage(Message.raw("=== EcotaleCoins Test Suite ===").color(Color.YELLOW));
                    player.sendMessage(Message.raw(""));
                    
                    // Test 1: Physical Coins Provider Check
                    if (EcotaleAPI.isPhysicalCoinsAvailable()) {
                        player.sendMessage(Message.raw("[1] Provider Registered: PASS").color(Color.GREEN));
                        passed++;
                    } else {
                        player.sendMessage(Message.raw("[1] Provider Registered: FAIL").color(Color.RED));
                        failed++;
                    }
                    
                    // Test 2: Coin Count
                    try {
                        long coinBalance = CoinManager.countCoins(player);
                        player.sendMessage(Message.raw("[2] Coin Count: PASS - Pocket=" + coinBalance).color(Color.GREEN));
                        passed++;
                    } catch (Exception e) {
                        player.sendMessage(Message.raw("[2] Coin Count: FAIL - " + e.getMessage()).color(Color.RED));
                        failed++;
                    }
                    
                    // Test 3: Coin Breakdown
                    try {
                        Map<CoinType, Integer> breakdown = CoinManager.getBreakdown(player);
                        StringBuilder sb = new StringBuilder();
                        for (CoinType type : CoinType.values()) {
                            int count = breakdown.getOrDefault(type, 0);
                            if (count > 0) {
                                if (sb.length() > 0) sb.append(", ");
                                sb.append(type.name()).append("=").append(count);
                            }
                        }
                        String breakdownStr = sb.length() > 0 ? sb.toString() : "empty";
                        player.sendMessage(Message.raw("[3] Coin Breakdown: PASS - " + breakdownStr).color(Color.GREEN));
                        passed++;
                    } catch (Exception e) {
                        player.sendMessage(Message.raw("[3] Coin Breakdown: FAIL - " + e.getMessage()).color(Color.RED));
                        failed++;
                    }
                    
                    // Test 4: Bank Balance
                    try {
                        long bankBalance = BankManager.getBankBalance(uuid);
                        long totalWealth = BankManager.getTotalWealth(player, uuid);
                        player.sendMessage(Message.raw("[4] Bank Balance: PASS - Bank=" + bankBalance + ", Total=" + totalWealth).color(Color.GREEN));
                        passed++;
                    } catch (Exception e) {
                        player.sendMessage(Message.raw("[4] Bank Balance: FAIL - " + e.getMessage()).color(Color.RED));
                        failed++;
                    }
                    
                    // Test 5: Coin Give/Take Cycle (non-destructive test)
                    try {
                        long before = CoinManager.countCoins(player);
                        boolean giveSuccess = CoinManager.giveCoins(player, 100L);
                        
                        if (giveSuccess) {
                            long afterGive = CoinManager.countCoins(player);
                            boolean takeSuccess = CoinManager.takeCoins(player, 100L);
                            
                            if (takeSuccess) {
                                long afterTake = CoinManager.countCoins(player);
                                if (afterGive == before + 100 && afterTake == before) {
                                    player.sendMessage(Message.raw("[5] Coin Give/Take: PASS - Cycle OK").color(Color.GREEN));
                                    passed++;
                                } else {
                                    player.sendMessage(Message.raw("[5] Coin Give/Take: FAIL - Amounts mismatch").color(Color.RED));
                                    failed++;
                                }
                            } else {
                                player.sendMessage(Message.raw("[5] Coin Give/Take: FAIL - Take failed").color(Color.RED));
                                failed++;
                            }
                        } else {
                            player.sendMessage(Message.raw("[5] Coin Give/Take: FAIL - Give failed (full inventory?)").color(Color.RED));
                            failed++;
                        }
                    } catch (Exception e) {
                        player.sendMessage(Message.raw("[5] Coin Give/Take: FAIL - " + e.getMessage()).color(Color.RED));
                        failed++;
                    }
                    
                    // Test 6: Specific Coin Type Operations
                    try {
                        CoinType testType = CoinType.byName("COPPER");
                        int beforeCount = CoinManager.getBreakdown(player).getOrDefault(testType, 0);
                        
                        boolean giveSpecific = CoinManager.giveSpecificCoins(player, testType, 10);
                        if (giveSpecific) {
                            int afterCount = CoinManager.getBreakdown(player).getOrDefault(testType, 0);
                            CoinManager.takeSpecificCoins(player, testType, 10);
                            
                            if (afterCount == beforeCount + 10) {
                                player.sendMessage(Message.raw("[6] Specific Coin Ops: PASS").color(Color.GREEN));
                                passed++;
                            } else {
                                player.sendMessage(Message.raw("[6] Specific Coin Ops: FAIL - Count mismatch").color(Color.RED));
                                failed++;
                            }
                        } else {
                            player.sendMessage(Message.raw("[6] Specific Coin Ops: FAIL - Give failed").color(Color.RED));
                            failed++;
                        }
                    } catch (Exception e) {
                        player.sendMessage(Message.raw("[6] Specific Coin Ops: FAIL - " + e.getMessage()).color(Color.RED));
                        failed++;
                    }
                    
                    // Test 7: Free Slots Calculation
                    try {
                        int freeSlots = CoinManager.countFreeSlots(player);
                        player.sendMessage(Message.raw("[7] Free Slots: PASS - " + freeSlots + " slots available").color(Color.GREEN));
                        passed++;
                    } catch (Exception e) {
                        player.sendMessage(Message.raw("[7] Free Slots: FAIL - " + e.getMessage()).color(Color.RED));
                        failed++;
                    }
                    
                    // Test 8: Coin Value Calculation
                    try {
                        CoinType copper = CoinType.byName("COPPER");
                        CoinType gold = CoinType.byName("GOLD");
                        CoinType adamantite = CoinType.byName("ADAMANTITE");

                        if (copper != null && gold != null && adamantite != null) {
                            long copperValue = copper.getValue();
                            long goldValue = gold.getValue();
                            long adamantiteValue = adamantite.getValue();

                            player.sendMessage(Message.raw("[8] Coin Values: PASS - Cu=" + copperValue + ", Au=" + goldValue + ", Ad=" + adamantiteValue).color(Color.GREEN));
                            passed++;
                        } else {
                            player.sendMessage(Message.raw("[8] Coin Values: FAIL - Missing coin types").color(Color.RED));
                            failed++;
                        }
                    } catch (Exception e) {
                        player.sendMessage(Message.raw("[8] Coin Values: FAIL - " + e.getMessage()).color(Color.RED));
                        failed++;
                    }
                    
                    // Summary
                    player.sendMessage(Message.raw(""));
                    player.sendMessage(Message.raw("=== Results: Passed " + passed + " | Failed " + failed + " ===").color(Color.YELLOW));
                    if (failed == 0) {
                        player.sendMessage(Message.raw("ALL TESTS PASSED!").color(Color.GREEN));
                    } else {
                        player.sendMessage(Message.raw("Some tests failed").color(Color.RED));
                    }
                    
                }, player.getWorld());
            }
        }
        return CompletableFuture.completedFuture(null);
    }
}
