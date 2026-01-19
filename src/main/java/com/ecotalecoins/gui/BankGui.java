package com.ecotalecoins.gui;

import com.ecotale.api.EcotaleAPI;
import com.ecotalecoins.currency.BankManager;
import com.ecotalecoins.currency.CoinManager;
import com.ecotalecoins.currency.CoinType;
import com.ecotalecoins.currency.InventorySpaceCalculator;
import com.ecotalecoins.transaction.SecureTransaction;
import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.Message;
import com.ecotalecoins.util.TranslationHelper;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import org.checkerframework.checker.nullness.compatqual.NonNullDecl;

import java.awt.Color;
import java.util.Map;
import java.util.UUID;

/**
 * Premium Bank GUI with tabbed interface.
 * 
 * Tabs: WALLET, DEPOSIT, WITHDRAW, EXCHANGE
 * 
 * Security measures:
 * - All amounts validated server-side
 * - Balance checks before every operation
 * - Atomic operations for exchange
 * - Input sanitization
 * - Overflow protection
 */
public class BankGui extends InteractiveCustomUIPage<BankGui.BankGuiData> {
    
    private enum Tab { WALLET, DEPOSIT, WITHDRAW, EXCHANGE }
    
    // Store playerRef for per-player translations
    private final PlayerRef playerRef;
    
    private Tab currentTab = Tab.WALLET;
    private String amountInput = "";
    
    // Exchange coin indices (0-5 for CoinType values)
    private int fromCoinIndex = 0;  // Default: Copper
    private int toCoinIndex = 1;    // Default: Iron
    
    // Security: Track last operation time to prevent spam
    private long lastClickTime = 0;
    
    public BankGui(@NonNullDecl PlayerRef playerRef) {
        super(playerRef, CustomPageLifetime.CanDismiss, BankGuiData.CODEC);
        this.playerRef = playerRef;
    }
    
    // Per-player translation helpers (use stored playerRef)
    private String t(String key, String fallback) {
        return TranslationHelper.t(playerRef, key, fallback);
    }
    
    private String t(String key, String fallback, Object... args) {
        return TranslationHelper.t(playerRef, key, fallback, args);
    }
    
    @Override
    public void build(@NonNullDecl Ref<EntityStore> ref, @NonNullDecl UICommandBuilder cmd,
                      @NonNullDecl UIEventBuilder events, @NonNullDecl Store<EntityStore> store) {
        cmd.append("Pages/Ecotale_BankPage.ui");
        
        Player player = store.getComponent(ref, Player.getComponentType());
        PlayerRef playerRefComp = store.getComponent(ref, PlayerRef.getComponentType());
        if (player == null || playerRefComp == null) return;
        
        UUID playerUuid = playerRefComp.getUuid();
        
        // Get current balances
        long bankBalance = BankManager.getBankBalance(playerUuid);
        long pocketBalance = CoinManager.countCoins(player);
        long totalWealth = bankBalance + pocketBalance;
        String symbol = EcotaleAPI.getCurrencySymbol();
        
        // ═══════════════ HEADER BINDINGS ═══════════════
        events.addEventBinding(CustomUIEventBindingType.Activating, "#CloseButton",
            EventData.of(BankGuiData.KEY_ACTION, "Close"), false);
        
        // ═══════════════ WEALTH BAR ═══════════════
        cmd.set("#TotalWealth.Text", symbol + formatLong(totalWealth));
        cmd.set("#BankBalance.Text", symbol + formatLong(bankBalance));
        cmd.set("#PocketBalance.Text", symbol + formatLong(pocketBalance));
        
        // ═══════════════ TAB BAR BINDINGS ═══════════════
        events.addEventBinding(CustomUIEventBindingType.Activating, "#TabWallet",
            EventData.of(BankGuiData.KEY_TAB, "Wallet"), false);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#TabDeposit",
            EventData.of(BankGuiData.KEY_TAB, "Deposit"), false);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#TabWithdraw",
            EventData.of(BankGuiData.KEY_TAB, "Withdraw"), false);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#TabExchange",
            EventData.of(BankGuiData.KEY_TAB, "Exchange"), false);
        
        // Tab visibility
        cmd.set("#WalletContent.Visible", currentTab == Tab.WALLET);
        cmd.set("#DepositContent.Visible", currentTab == Tab.DEPOSIT);
        cmd.set("#WithdrawContent.Visible", currentTab == Tab.WITHDRAW);
        cmd.set("#ExchangeContent.Visible", currentTab == Tab.EXCHANGE);
        
        // Tab active state styling (gold for active, gray for inactive)
        updateTabStyles(cmd);
        
        // Build current tab content
        switch (currentTab) {
            case WALLET -> buildWalletTab(cmd, events, player, bankBalance, pocketBalance, symbol);
            case DEPOSIT -> buildDepositTab(cmd, events, pocketBalance, bankBalance, symbol);
            case WITHDRAW -> buildWithdrawTab(cmd, events, pocketBalance, bankBalance, symbol);
            case EXCHANGE -> buildExchangeTab(cmd, events, player);
        }
        
        // Apply translations to all UI text
        translateUI(cmd);
    }
    
    private void updateTabStyles(UICommandBuilder cmd) {
        // Active tab gets brackets, inactive are plain text (translated)
        String walletName = getTabName(Tab.WALLET);
        String depositName = getTabName(Tab.DEPOSIT);
        String withdrawName = getTabName(Tab.WITHDRAW);
        String exchangeName = getTabName(Tab.EXCHANGE);
        
        cmd.set("#TabWallet.Text", currentTab == Tab.WALLET ? "[ " + walletName + " ]" : walletName);
        cmd.set("#TabDeposit.Text", currentTab == Tab.DEPOSIT ? "[ " + depositName + " ]" : depositName);
        cmd.set("#TabWithdraw.Text", currentTab == Tab.WITHDRAW ? "[ " + withdrawName + " ]" : withdrawName);
        cmd.set("#TabExchange.Text", currentTab == Tab.EXCHANGE ? "[ " + exchangeName + " ]" : exchangeName);
    }
    
    // Update only preview labels for partial updates (preserves TextField focus)
    private void updatePreviewLabels(UICommandBuilder cmd, Store<EntityStore> store, Ref<EntityStore> ref) {
        Player player = store.getComponent(ref, Player.getComponentType());
        PlayerRef playerRefComp = store.getComponent(ref, PlayerRef.getComponentType());
        if (player == null || playerRefComp == null) return;
        
        UUID playerUuid = playerRefComp.getUuid();
        String symbol = EcotaleAPI.getCurrencySymbol();
        long bankBalance = BankManager.getBankBalance(playerUuid);
        long pocketBalance = CoinManager.countCoins(player);
        
        switch (currentTab) {
            case DEPOSIT -> {
                long amount = parseAmount(amountInput, pocketBalance);
                cmd.set("#DepositPreview.Visible", amount > 0);
                cmd.set("#DepositCoinPreview.Visible", amount > 0);
                if (amount > 0) {
                    long afterBank = bankBalance + amount;
                    cmd.set("#DepositPreviewText.Text", t("gui.bank.deposit.preview", "After: Bank {0} (+{1})", 
                        symbol + formatLong(afterBank), symbol + formatLong(amount)));
                    
                    // Render coin breakdown preview
                    renderCoinPreview(cmd, "#DepositCoinRow", amount, true);
                }
            }
            case WITHDRAW -> {
                long amount = parseAmount(amountInput, bankBalance);
                cmd.set("#WithdrawPreview.Visible", amount > 0);
                cmd.set("#WithdrawCoinPreview.Visible", amount > 0);
                if (amount > 0) {
                    long afterPocket = pocketBalance + amount;
                    cmd.set("#WithdrawPreviewText.Text", t("gui.bank.withdraw.preview", "After: Pocket {0} (+{1})", 
                        symbol + formatLong(afterPocket), symbol + formatLong(amount)));
                    
                    // Render coin breakdown preview
                    renderCoinPreview(cmd, "#WithdrawCoinRow", amount, false);
                }
            }
            case EXCHANGE -> {
                updateExchangePreview(cmd, player);
            }
            default -> {}
        }
    }
    
    private void updateExchangePreview(UICommandBuilder cmd, Player player) {
        CoinType fromType = CoinType.values()[fromCoinIndex];
        CoinType toType = CoinType.values()[toCoinIndex];
        
        long inputAmount = parseAmountSimple(amountInput);
        int haveFrom = CoinManager.getBreakdown(player).getOrDefault(fromType, 0);

        
        long fromValue = fromType.getValue();
        long toValue = toType.getValue();
        
        // Calculate result amount
        long resultAmount = 0;
        if (fromValue < toValue) {
            long exchangeRate = toValue / fromValue;
            resultAmount = inputAmount / exchangeRate;
        } else {
            resultAmount = inputAmount * (fromValue / toValue);
        }
        
        // Check space intelligently
        InventorySpaceCalculator.SpaceResult space = 
            InventorySpaceCalculator.canFitSpecific(player, toType, (int) resultAmount);
        
        // Determine message based on validation (using translations)
        String message;
        
        if (inputAmount <= 0) {
            message = t("gui.bank.exchange.enter_amount", "Enter amount to exchange");
        } else if (inputAmount > haveFrom) {
            message = t("gui.bank.exchange.not_enough", "Not enough {0} (have {1})", getCoinName(fromType), haveFrom);
        } else if (fromValue < toValue) {
            // Converting UP - check minimum amount
            long exchangeRate = toValue / fromValue;
            if (inputAmount < exchangeRate) {
                message = t("gui.bank.exchange.need_at_least", "Need at least {0} {1}", exchangeRate, getCoinName(fromType));
            } else if (!space.canFit()) {
                message = t("gui.bank.exchange.need_slots", "Need {0} new slots, only {1} free", 
                    space.slotsNeeded(), space.slotsAvailable());
            } else {
                message = t("gui.bank.exchange.use_get", "USE: {0} {1}  -->  GET: {2} {3}", 
                    inputAmount, getCoinName(fromType), resultAmount, getCoinName(toType));
            }
        } else {
            // Converting DOWN - check inventory space
            if (!space.canFit()) {
                message = t("gui.bank.exchange.need_slots", "Need {0} new slots, only {1} free", 
                    space.slotsNeeded(), space.slotsAvailable());
            } else {
                message = t("gui.bank.exchange.use_get", "USE: {0} {1}  -->  GET: {2} {3}", 
                    inputAmount, getCoinName(fromType), resultAmount, getCoinName(toType));
            }
        }
        
        // Update preview text
        cmd.set("#ExchangeResultText.Text", message);
    }
    
    /**
     * Calculate the maximum amount that can be exchanged considering:
     * 1. Available coins of source type
     * 2. Available inventory slots for result
     */
    private int calculateSmartMax(Player player) {
        CoinType fromType = CoinType.values()[fromCoinIndex];
        CoinType toType = CoinType.values()[toCoinIndex];
        
        int haveFrom = CoinManager.getBreakdown(player).getOrDefault(fromType, 0);
        if (haveFrom == 0) return 0;
        
        long fromValue = fromType.getValue();
        long toValue = toType.getValue();
        
        // INTELLIGENT MAX CALCULATION
        long totalSpaceForTarget = InventorySpaceCalculator.calculateTotalSpaceFor(player, toType);
        
        int maxFromCoins;
        
        if (fromValue < toValue) {
            // Converting UP (e.g. Copper -> Gold)
            // Result is smaller amount, so space is rarely an issue unless converting billions
            // Max is simply what we have
            maxFromCoins = haveFrom;
            
            // Double check just in case (e.g. 1 Copper -> 1000 Gold? Impossible but math safety)
            long resultIfAll = (long) haveFrom * fromValue / toValue;
            if (resultIfAll > totalSpaceForTarget) {
                 // Very rare case: converting up but result still doesn't fit
                 long maxResult = totalSpaceForTarget;
                 maxFromCoins = (int) (maxResult * toValue / fromValue);
            }
        } else {
            // Converting DOWN (e.g. Gold -> Copper)
            // Result is larger amount, space is the limiting factor
            long resultPerFrom = fromValue / toValue;
            
            if (resultPerFrom == 0) return 0; // Should not happen given values
            
            long maxFromBySpace = totalSpaceForTarget / resultPerFrom;
            maxFromCoins = (int) Math.min(haveFrom, maxFromBySpace);
        }
        
        return maxFromCoins;
    }
    
    // ═══════════════════════════════════════════════════════════════════
    // WALLET TAB
    // ═══════════════════════════════════════════════════════════════════
    private void buildWalletTab(UICommandBuilder cmd, UIEventBuilder events, Player player,
                                 long bankBalance, long pocketBalance, String symbol) {
        Map<CoinType, Integer> coinBreakdown = CoinManager.getBreakdown(player);
        CoinType[] types = CoinType.values();
        
        // Build coin grid (3x2)
        cmd.clear("#CoinRow1");
        cmd.clear("#CoinRow2");
        
        for (int i = 0; i < types.length; i++) {
            CoinType type = types[i];
            int count = coinBreakdown.getOrDefault(type, 0);
            long value = count * type.getValue();
            
            String targetRow = i < 3 ? "#CoinRow1" : "#CoinRow2";
            
            cmd.append(targetRow, "Pages/Ecotale_BankCoinCard.ui");
            int rowIndex = i < 3 ? i : i - 3;
            
            cmd.set(targetRow + "[" + rowIndex + "] #CoinIcon.ItemId", type.getItemId());
            cmd.set(targetRow + "[" + rowIndex + "] #CoinName.Text", getCoinName(type));
            cmd.set(targetRow + "[" + rowIndex + "] #CoinCount.Text", "x" + count);
            cmd.set(targetRow + "[" + rowIndex + "] #CoinValue.Text", symbol + formatLong(value));
        }
        
        // Quick action bindings
        events.addEventBinding(CustomUIEventBindingType.Activating, "#BtnDepositAll",
            EventData.of(BankGuiData.KEY_ACTION, "DepositAll"), false);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#BtnWithdrawAll",
            EventData.of(BankGuiData.KEY_ACTION, "WithdrawAll"), false);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#BtnConsolidate",
            EventData.of(BankGuiData.KEY_ACTION, "Consolidate"), false);
    }
    
    // ═══════════════════════════════════════════════════════════════════
    // DEPOSIT TAB
    // ═══════════════════════════════════════════════════════════════════
    private void buildDepositTab(UICommandBuilder cmd, UIEventBuilder events,
                                  long pocketBalance, long bankBalance, String symbol) {
        // Flow values
        cmd.set("#DepositFromValue.Text", symbol + formatLong(pocketBalance));
        cmd.set("#DepositToValue.Text", symbol + formatLong(bankBalance));
        
        // Amount input
        cmd.set("#DepositAmountInput.Value", amountInput);
        events.addEventBinding(CustomUIEventBindingType.ValueChanged, "#DepositAmountInput",
            EventData.of(BankGuiData.KEY_AMOUNT, "#DepositAmountInput.Value"), false);
        
        // Quick buttons
        events.addEventBinding(CustomUIEventBindingType.Activating, "#DepositQuick25",
            EventData.of(BankGuiData.KEY_ACTION, "Quick25"), false);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#DepositQuick50",
            EventData.of(BankGuiData.KEY_ACTION, "Quick50"), false);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#DepositQuick75",
            EventData.of(BankGuiData.KEY_ACTION, "Quick75"), false);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#DepositQuickMax",
            EventData.of(BankGuiData.KEY_ACTION, "QuickMax"), false);
        
        // Confirm
        events.addEventBinding(CustomUIEventBindingType.Activating, "#ConfirmDeposit",
            EventData.of(BankGuiData.KEY_ACTION, "ConfirmDeposit"), false);
        
        // Preview
        long amount = parseAmount(amountInput, pocketBalance);
        if (amount > 0 && amount <= pocketBalance) {
            cmd.set("#DepositPreview.Visible", true);
            cmd.set("#DepositPreviewText.Text", t("gui.bank.deposit.preview", "After: Bank {0} (+{1})",
                symbol + formatLong(bankBalance + amount), symbol + formatLong(amount)));
            
            // Visual coin preview
            cmd.set("#DepositCoinPreview.Visible", true);
            renderCoinPreview(cmd, "#DepositCoinRow", amount, true);
        } else {
            cmd.set("#DepositPreview.Visible", false);
            cmd.set("#DepositCoinPreview.Visible", false);
        }
    }
    
    // ═══════════════════════════════════════════════════════════════════
    // WITHDRAW TAB
    // ═══════════════════════════════════════════════════════════════════
    private void buildWithdrawTab(UICommandBuilder cmd, UIEventBuilder events,
                                   long pocketBalance, long bankBalance, String symbol) {
        // Flow values
        cmd.set("#WithdrawFromValue.Text", symbol + formatLong(bankBalance));
        cmd.set("#WithdrawToValue.Text", symbol + formatLong(pocketBalance));
        
        // Amount input
        cmd.set("#WithdrawAmountInput.Value", amountInput);
        events.addEventBinding(CustomUIEventBindingType.ValueChanged, "#WithdrawAmountInput",
            EventData.of(BankGuiData.KEY_AMOUNT, "#WithdrawAmountInput.Value"), false);
        
        // Quick buttons
        events.addEventBinding(CustomUIEventBindingType.Activating, "#WithdrawQuick25",
            EventData.of(BankGuiData.KEY_ACTION, "Quick25"), false);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#WithdrawQuick50",
            EventData.of(BankGuiData.KEY_ACTION, "Quick50"), false);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#WithdrawQuick75",
            EventData.of(BankGuiData.KEY_ACTION, "Quick75"), false);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#WithdrawQuickMax",
            EventData.of(BankGuiData.KEY_ACTION, "QuickMax"), false);
        
        // Confirm
        events.addEventBinding(CustomUIEventBindingType.Activating, "#ConfirmWithdraw",
            EventData.of(BankGuiData.KEY_ACTION, "ConfirmWithdraw"), false);
        
        // Preview
        long amount = parseAmount(amountInput, bankBalance);
        if (amount > 0 && amount <= bankBalance) {
            cmd.set("#WithdrawPreview.Visible", true);
            cmd.set("#WithdrawPreviewText.Text", t("gui.bank.withdraw.preview", "After: Pocket {0} (+{1})",
                symbol + formatLong(pocketBalance + amount), symbol + formatLong(amount)));
            
            // Visual coin preview
            cmd.set("#WithdrawCoinPreview.Visible", true);
            renderCoinPreview(cmd, "#WithdrawCoinRow", amount, false);
        } else {
            cmd.set("#WithdrawPreview.Visible", false);
            cmd.set("#WithdrawCoinPreview.Visible", false);
        }
    }
    
    // ═══════════════════════════════════════════════════════════════════
    // EXCHANGE TAB
    // ═══════════════════════════════════════════════════════════════════
    private void buildExchangeTab(UICommandBuilder cmd, UIEventBuilder events, Player player) {
        CoinType[] types = CoinType.values();
        
        // Security: Validate indices
        fromCoinIndex = Math.max(0, Math.min(fromCoinIndex, types.length - 1));
        toCoinIndex = Math.max(0, Math.min(toCoinIndex, types.length - 1));
        
        CoinType fromType = types[fromCoinIndex];
        CoinType toType = types[toCoinIndex];
        
        Map<CoinType, Integer> coinBreakdown = CoinManager.getBreakdown(player);
        
        // FROM coin display
        cmd.set("#FromCoinIcon.ItemId", fromType.getItemId());
        cmd.set("#FromCoinName.Text", getCoinName(fromType));
        cmd.set("#FromCoinHave.Text", t("gui.bank.exchange.you_have", "You have: {0}", coinBreakdown.getOrDefault(fromType, 0)));
        
        // TO coin display
        cmd.set("#ToCoinIcon.ItemId", toType.getItemId());
        cmd.set("#ToCoinName.Text", getCoinName(toType));
        cmd.set("#ToCoinHave.Text", t("gui.bank.exchange.you_have", "You have: {0}", coinBreakdown.getOrDefault(toType, 0)));
        
        // Navigation buttons
        events.addEventBinding(CustomUIEventBindingType.Activating, "#FromPrev",
            EventData.of(BankGuiData.KEY_ACTION, "FromPrev"), false);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#FromNext",
            EventData.of(BankGuiData.KEY_ACTION, "FromNext"), false);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#ToPrev",
            EventData.of(BankGuiData.KEY_ACTION, "ToPrev"), false);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#ToNext",
            EventData.of(BankGuiData.KEY_ACTION, "ToNext"), false);
        
        // Exchange rate
        long rate = toType.getValue() / fromType.getValue();
        if (rate >= 1) {
            cmd.set("#ExchangeRate.Text", t("gui.bank.exchange.rate", "RATE: {0} {1} = {2} {3}", 
                rate, getCoinName(fromType), "1", getCoinName(toType)));
        } else {
            rate = fromType.getValue() / toType.getValue();
            cmd.set("#ExchangeRate.Text", t("gui.bank.exchange.rate", "RATE: {0} {1} = {2} {3}", 
                "1", getCoinName(fromType), rate, getCoinName(toType)));
        }
        
        // Amount input
        cmd.set("#ExchangeAmountInput.Value", amountInput);
        events.addEventBinding(CustomUIEventBindingType.ValueChanged, "#ExchangeAmountInput",
            EventData.of(BankGuiData.KEY_AMOUNT, "#ExchangeAmountInput.Value"), false);
        
        events.addEventBinding(CustomUIEventBindingType.Activating, "#ExchangeQuickMax",
            EventData.of(BankGuiData.KEY_ACTION, "ExchangeMax"), false);
        
        // Confirm
        events.addEventBinding(CustomUIEventBindingType.Activating, "#ConfirmExchange",
            EventData.of(BankGuiData.KEY_ACTION, "ConfirmExchange"), false);
        
        // Result preview
        long amount = parseAmountSimple(amountInput);
        int available = coinBreakdown.getOrDefault(fromType, 0);
        
        if (amount > 0 && fromType != toType) {
            long sourceValue = fromType.getValue() * amount;
            long targetValue = toType.getValue();
            
            if (sourceValue >= targetValue) {
                long resultAmount = sourceValue / targetValue;
                cmd.set("#ExchangeResultText.Text", "USE: " + amount + " " + getCoinName(fromType) + 
                    "  -->  GET: " + resultAmount + " " + getCoinName(toType));
            } else {
                long needed = (long) Math.ceil((double) targetValue / fromType.getValue());
                cmd.set("#ExchangeResultText.Text", "Need at least " + needed + " " + getCoinName(fromType));
            }
        } else if (fromType == toType) {
            cmd.set("#ExchangeResultText.Text", "Select different coin types");
        } else {
            cmd.set("#ExchangeResultText.Text", t("gui.bank.exchange.enter_amount", "Enter amount to exchange"));
        }
        
        // Translate MAX POSSIBLE button for Exchange tab
        cmd.set("#ExchangeQuickMax.Text", t("gui.bank.exchange.max_possible", "MAX POSSIBLE"));
    }
    
    // ═══════════════════════════════════════════════════════════════════
    // EVENT HANDLING
    // ═══════════════════════════════════════════════════════════════════
    @Override
    public void handleDataEvent(@NonNullDecl Ref<EntityStore> ref, @NonNullDecl Store<EntityStore> store,
                                @NonNullDecl BankGuiData data) {
        super.handleDataEvent(ref, store, data);
        
        Player player = store.getComponent(ref, Player.getComponentType());
        PlayerRef playerRefComp = store.getComponent(ref, PlayerRef.getComponentType());
        if (player == null || playerRefComp == null) return;
        
        UUID playerUuid = playerRefComp.getUuid();
        
        // Handle tab change
        if (data.tab != null) {
            switch (data.tab) {
                case "Wallet" -> currentTab = Tab.WALLET;
                case "Deposit" -> currentTab = Tab.DEPOSIT;
                case "Withdraw" -> currentTab = Tab.WITHDRAW;
                case "Exchange" -> currentTab = Tab.EXCHANGE;
            }
            amountInput = ""; // Reset input on tab change
            refreshUI(ref, store);
            return;
        }
        
        // Handle amount input - partial update to preserve TextField focus
        if (data.amountInput != null) {
            this.amountInput = sanitizeInput(data.amountInput);
            // Only update preview elements, not the entire UI
            UICommandBuilder cmd = new UICommandBuilder();
            updatePreviewLabels(cmd, store, ref);
            this.sendUpdate(cmd, new UIEventBuilder(), false);
            return;
        }
        
        // Handle actions
        if (data.action != null) {
            // Security: Anti-spam cooldown
            if (System.currentTimeMillis() - lastClickTime < 250) {
                playerRef.sendMessage(Message.raw(t("gui.bank.wait", "Please wait before performing another action")).color(Color.YELLOW));
                return;
            }
            
            switch (data.action) {
                case "Close" -> { this.close(); return; }
                
                // Quick amount buttons
                case "Quick25" -> handleQuickAmount(player, playerUuid, 0.25);
                case "Quick50" -> handleQuickAmount(player, playerUuid, 0.50);
                case "Quick75" -> handleQuickAmount(player, playerUuid, 0.75);
                case "QuickMax" -> handleQuickAmount(player, playerUuid, 1.0);
                
                // Wallet quick actions
                case "DepositAll" -> executeDepositAll(player, playerUuid);
                case "WithdrawAll" -> executeWithdrawAll(player, playerUuid);
                case "Consolidate" -> executeConsolidate(player);
                
                // Confirm actions
                case "ConfirmDeposit" -> executeDeposit(player, playerUuid);
                case "ConfirmWithdraw" -> executeWithdraw(player, playerUuid);
                case "ConfirmExchange" -> executeExchange(player);
                
                // Exchange navigation
                case "FromPrev" -> { fromCoinIndex = cycleIndex(fromCoinIndex, -1); ensureDifferentCoins(); }
                case "FromNext" -> { fromCoinIndex = cycleIndex(fromCoinIndex, 1); ensureDifferentCoins(); }
                case "ToPrev" -> { toCoinIndex = cycleToIndex(toCoinIndex, -1); }
                case "ToNext" -> { toCoinIndex = cycleToIndex(toCoinIndex, 1); }
                case "ExchangeMax" -> handleExchangeMax(player);
            }
            
            lastClickTime = System.currentTimeMillis();
            
            refreshUI(ref, store);
        }
    }
    
    // ═══════════════════════════════════════════════════════════════════
    // EXECUTION METHODS (with full security validation)
    // ═══════════════════════════════════════════════════════════════════
    
    private void executeDeposit(Player player, UUID playerUuid) {
        // Security: Re-fetch current balance for max calculation
        long pocketBalance = CoinManager.countCoins(player);
        long amount = parseAmount(amountInput, pocketBalance);
        
        if (amount <= 0) {
            playerRef.sendMessage(Message.raw(t("gui.bank.error.invalid_amount", "Enter a valid amount")).color(Color.RED));
            return;
        }
        
        
        SecureTransaction.TransactionResult result = SecureTransaction.executeSecureDeposit(
            player,
            playerUuid,
            amount
        );
        
        if (result.isSuccess()) {
            amountInput = "";
            playerRef.sendMessage(Message.raw(result.getMessage()).color(Color.GREEN));
        } else {
            // Check if money was saved to bank
            if (result.isMoneySafe() && result.getTxHash() != null) {
                playerRef.sendMessage(Message.raw(result.getMessage()).color(Color.YELLOW));
            } else {
                playerRef.sendMessage(Message.raw(result.getMessage()).color(Color.RED));
            }
        }
    }
    
    private void executeWithdraw(Player player, UUID playerUuid) {
        // Security: Re-fetch current balance for max calculation
        long bankBalance = BankManager.getBankBalance(playerUuid);
        long amount = parseAmount(amountInput, bankBalance);
        
        if (amount <= 0) {
            playerRef.sendMessage(Message.raw(t("gui.bank.error.invalid_amount", "Enter a valid amount")).color(Color.RED));
            return;
        }
        
        
        SecureTransaction.TransactionResult result = SecureTransaction.executeSecureWithdraw(
            player,
            playerUuid,
            amount
        );
        
        if (result.isSuccess()) {
            amountInput = "";
            playerRef.sendMessage(Message.raw(result.getMessage()).color(Color.GREEN));
        } else {
            // Check if money was saved to bank
            if (result.isMoneySafe() && result.getTxHash() != null) {
                playerRef.sendMessage(Message.raw(result.getMessage()).color(Color.YELLOW));
            } else {
                playerRef.sendMessage(Message.raw(result.getMessage()).color(Color.RED));
            }
        }
    }
    
    private void executeDepositAll(Player player, UUID playerUuid) {
        long pocketBalance = CoinManager.countCoins(player);
        if (pocketBalance <= 0) {
            playerRef.sendMessage(Message.raw(t("gui.bank.error.no_pocket_coins", "No coins in pocket to deposit")).color(Color.YELLOW));
            return;
        }
        
        
        SecureTransaction.TransactionResult result = SecureTransaction.executeSecureDeposit(
            player,
            playerUuid,
            pocketBalance
        );
        
        if (result.isSuccess()) {
            playerRef.sendMessage(Message.raw(result.getMessage()).color(Color.GREEN));
        } else {
            playerRef.sendMessage(Message.raw(result.getMessage()).color(Color.RED));
        }
    }
    
    private void executeWithdrawAll(Player player, UUID playerUuid) {
        long bankBalance = BankManager.getBankBalance(playerUuid);
        if (bankBalance <= 0) {
            playerRef.sendMessage(Message.raw(t("gui.bank.error.no_bank_coins", "No coins in bank to withdraw")).color(Color.YELLOW));
            return;
        }
        
        
        SecureTransaction.TransactionResult result = SecureTransaction.executeSecureWithdraw(
            player,
            playerUuid,
            bankBalance
        );
        
        if (result.isSuccess()) {
            playerRef.sendMessage(Message.raw(result.getMessage()).color(Color.GREEN));
        } else {
            if (result.isMoneySafe() && result.getTxHash() != null) {
                playerRef.sendMessage(Message.raw(result.getMessage()).color(Color.YELLOW));
            } else {
                playerRef.sendMessage(Message.raw(result.getMessage()).color(Color.RED));
            }
        }
    }
    
    private void executeConsolidate(Player player) {
        long pocketBalance = CoinManager.countCoins(player);
        if (pocketBalance <= 0) {
            playerRef.sendMessage(Message.raw(t("gui.bank.error.no_consolidate", "No coins to consolidate")).color(Color.YELLOW));
            return;
        }
        
        CoinManager.consolidate(player);
        playerRef.sendMessage(Message.raw(t("gui.bank.consolidate_success", "Coins consolidated to highest denominations")).color(Color.GREEN));
    }
    
    private void executeExchange(Player player) {
        CoinType[] types = CoinType.values();
        CoinType fromType = types[fromCoinIndex];
        CoinType toType = types[toCoinIndex];
        
        long amount = parseAmountSimple(amountInput);
        if (amount <= 0 || amount > Integer.MAX_VALUE) {
            playerRef.sendMessage(Message.raw(t("gui.bank.error.invalid_amount", "Enter a valid amount")).color(Color.RED));
            return;
        }
        

        SecureTransaction.TransactionResult result = SecureTransaction.executeSecureExchange(
            player,
            playerRef.getUuid(),
            fromType,
            (int) amount,
            toType
        );
        
        if (result.isSuccess()) {
            amountInput = "";
            playerRef.sendMessage(Message.raw(result.getMessage()).color(Color.GREEN));
        } else {
            // Check if money was saved to bank
            if (result.isMoneySafe() && result.getTxHash() != null) {
                // Money went to bank - show special message
                playerRef.sendMessage(Message.raw(result.getMessage()).color(Color.YELLOW));
            } else {
                playerRef.sendMessage(Message.raw(result.getMessage()).color(Color.RED));
            }
        }
    }
    
    // ═══════════════════════════════════════════════════════════════════
    // HELPER METHODS
    // ═══════════════════════════════════════════════════════════════════
    
    private void handleQuickAmount(Player player, UUID playerUuid, double percentage) {
        long max = switch (currentTab) {
            case DEPOSIT -> CoinManager.countCoins(player);
            case WITHDRAW -> BankManager.getBankBalance(playerUuid);
            default -> 0;
        };
        amountInput = String.valueOf(Math.max(1, (long) (max * percentage)));
    }
    
    private void handleExchangeMax(Player player) {
        // Use smart max that considers inventory space
        int smartMax = calculateSmartMax(player);
        amountInput = String.valueOf(smartMax);
    }
    
    private int cycleIndex(int current, int delta) {
        int length = CoinType.values().length;
        return (current + delta + length) % length;
    }
    
    /**
     * Check if converting 1 unit of fromType to toType is physically possible.
     * Based on max inventory: 45 slots × 999 per stack = 44,955 max coins.
     */
    private boolean isExchangePossible(int fromIndex, int toIndex) {
        if (fromIndex == toIndex) return false;
        
        CoinType fromType = CoinType.values()[fromIndex];
        CoinType toType = CoinType.values()[toIndex];
        
        // Only check when converting DOWN (to lower value coins)
        if (fromType.getValue() <= toType.getValue()) return true;
        
        // Calculate max coins needed for 1 source coin
        long resultCoins = fromType.getValue() / toType.getValue();
        
        // Max capacity: 45 slots × 999 per stack
        final int MAX_INVENTORY_SLOTS = 45;
        final int MAX_STACK_SIZE = 999;
        final long MAX_COINS = (long) MAX_INVENTORY_SLOTS * MAX_STACK_SIZE; // 44,955
        
        return resultCoins <= MAX_COINS;
    }
    
    /**
     * Cycle to next valid TO coin index, skipping impossible exchanges.
     */
    private int cycleToIndex(int current, int delta) {
        int length = CoinType.values().length;
        int attempts = 0;
        int newIndex = current;
        
        do {
            newIndex = (newIndex + delta + length) % length;
            attempts++;
            // Prevent infinite loop
            if (attempts >= length) break;
        } while (!isExchangePossible(fromCoinIndex, newIndex));
        
        return newIndex;
    }
    
    private void ensureDifferentCoins() {
        // First, ensure different coins
        if (fromCoinIndex == toCoinIndex) {
            toCoinIndex = cycleIndex(toCoinIndex, 1);
        }
        
        // Then, ensure the exchange is possible
        if (!isExchangePossible(fromCoinIndex, toCoinIndex)) {
            toCoinIndex = cycleToIndex(toCoinIndex, 1);
        }
    }
    
    private String sanitizeInput(String input) {
        if (input == null) return "";
        // Only allow digits and "all"
        String cleaned = input.trim().toLowerCase();
        if (cleaned.equals("all")) return cleaned;
        
        // Remove ALL non-digit characters (no dots, dashes, letters, etc.)
        String digitsOnly = input.replaceAll("[^0-9]", "");
        
        // Limit to 15 characters max (covers values up to 999 trillion)
        if (digitsOnly.length() > 15) {
            digitsOnly = digitsOnly.substring(0, 15);
        }
        
        return digitsOnly;
    }
    
    private long parseAmount(String input, long maxForAll) {
        if (input == null || input.isEmpty()) return 0;
        if (input.equalsIgnoreCase("all")) return maxForAll;
        
        // First sanitize to digits only
        String sanitized = sanitizeInput(input);
        if (sanitized.isEmpty()) return 0;
        
        try {
            long val = Long.parseLong(sanitized);
            if (val <= 0) return 0;
            
            // Cap at max balance from config (prevents overflow and excessive values)
            long maxBalance = (long) EcotaleAPI.getMaxBalance();
            return Math.min(val, maxBalance);
        } catch (NumberFormatException e) {
            return 0;
        }
    }
    
    private long parseAmountSimple(String input) {
        if (input == null || input.isEmpty()) return 0;
        
        // First sanitize to digits only
        String sanitized = sanitizeInput(input);
        if (sanitized.isEmpty() || sanitized.equals("all")) return 0;
        
        try {
            long val = Long.parseLong(sanitized);
            if (val <= 0) return 0;
            
            // Cap at max balance from config
            long maxBalance = (long) EcotaleAPI.getMaxBalance();
            return Math.min(val, maxBalance);
        } catch (NumberFormatException e) {
            return 0;
        }
    }
    
    private void refreshUI(Ref<EntityStore> ref, Store<EntityStore> store) {
        UICommandBuilder cmd = new UICommandBuilder();
        UIEventBuilder events = new UIEventBuilder();
        this.build(ref, cmd, events, store);
        this.sendUpdate(cmd, events, true);
    }
    
    // ═══════════════════════════════════════════════════════════════════
    /** Translate all static UI elements */
    private void translateUI(UICommandBuilder cmd) {
        // Header
        cmd.set("#Title.Text", t("gui.bank.title", "BANK"));
        
        // Wealth bar labels (using direct IDs, not child selectors)
        cmd.set("#WalletLabel.Text", t("gui.bank.wallet", "WALLET"));
        cmd.set("#BankLabel.Text", t("gui.bank.bank", "BANK"));
        cmd.set("#TotalLabel.Text", t("gui.bank.total", "TOTAL"));
        
        // Wallet tab - labels with IDs
        cmd.set("#CoinCollectionLabel.Text", t("gui.bank.wallet.title", "YOUR COIN COLLECTION"));
        cmd.set("#QuickActionsLabel.Text", t("gui.bank.wallet.quick_actions", "QUICK ACTIONS"));
        cmd.set("#BtnDepositAll.Text", t("gui.bank.wallet.deposit_all", "DEPOSIT ALL"));
        cmd.set("#BtnWithdrawAll.Text", t("gui.bank.wallet.withdraw_all", "WITHDRAW ALL"));
        cmd.set("#BtnConsolidate.Text", t("gui.bank.wallet.consolidate", "CONSOLIDATE"));
        
        // Deposit tab
        cmd.set("#DepositFromLabel.Text", t("gui.bank.deposit.from", "FROM POCKET"));
        cmd.set("#DepositToLabel.Text", t("gui.bank.deposit.to", "TO BANK"));
        cmd.set("#DepositAmountLabel.Text", t("gui.bank.deposit.amount", "AMOUNT TO DEPOSIT"));
        cmd.set("#ConfirmDeposit.Text", t("gui.bank.deposit.confirm", "CONFIRM DEPOSIT"));
        cmd.set("#DepositCoinsLabel.Text", t("gui.bank.deposit.coins_preview", "COINS TO DEPOSIT"));
        
        // Withdraw tab
        cmd.set("#WithdrawFromLabel.Text", t("gui.bank.withdraw.from", "FROM BANK"));
        cmd.set("#WithdrawToLabel.Text", t("gui.bank.withdraw.to", "TO POCKET"));
        cmd.set("#WithdrawAmountLabel.Text", t("gui.bank.withdraw.amount", "AMOUNT TO WITHDRAW"));
        cmd.set("#ConfirmWithdraw.Text", t("gui.bank.withdraw.confirm", "CONFIRM WITHDRAW"));
        cmd.set("#WithdrawCoinsLabel.Text", t("gui.bank.withdraw.coins_preview", "COINS TO RECEIVE"));
        
        // Exchange tab
        cmd.set("#ExchangeFromLabel.Text", t("gui.bank.exchange.from", "EXCHANGE FROM"));
        cmd.set("#ExchangeToLabel.Text", t("gui.bank.exchange.to", "EXCHANGE TO"));
        cmd.set("#ExchangeAmountLabel.Text", t("gui.bank.exchange.amount", "AMOUNT:"));
        cmd.set("#ConfirmExchange.Text", t("gui.bank.exchange.confirm", "CONFIRM EXCHANGE"));
    }
    
    /** Get translated tab names */
    private String getTabName(Tab tab) {
        return switch (tab) {
            case WALLET -> t("gui.bank.tab.wallet", "WALLET");
            case DEPOSIT -> t("gui.bank.tab.deposit", "DEPOSIT");
            case WITHDRAW -> t("gui.bank.tab.withdraw", "WITHDRAW");
            case EXCHANGE -> t("gui.bank.tab.exchange", "EXCHANGE");
        };
    }
    
    /** Get translated coin names */
    private String getCoinName(CoinType type) {
        return switch (type) {
            case COPPER -> t("coins.copper", "Copper");
            case IRON -> t("coins.iron", "Iron");
            case COBALT -> t("coins.cobalt", "Cobalt");
            case GOLD -> t("coins.gold", "Gold");
            case MITHRIL -> t("coins.mithril", "Mithril");
            case ADAMANTITE -> t("coins.adamantite", "Adamantite");
        };
    }
    
    private String formatLong(long value) {
        if (value >= 1_000_000_000) {
            return String.format("%.2fB", value / 1_000_000_000.0);
        } else if (value >= 1_000_000) {
            return String.format("%.2fM", value / 1_000_000.0);
        } else if (value >= 1_000) {
            return String.format("%.1fK", value / 1_000.0);
        }
        return String.valueOf(value);
    }
    
    
    // ═══════════════════════════════════════════════════════════════════
    // COIN PREVIEW RENDERING
    // ═══════════════════════════════════════════════════════════════════
    
    /**
     * Render a visual preview of coin breakdown.
     * Shows compact coin icons with quantities for the given amount.
     * 
     * @param cmd UICommandBuilder
     * @param targetSelector The selector for the coin row container (e.g., "#DepositCoinRow")
     * @param amount The amount in base units to display
     * @param isDeposit True if deposit (green theme), false if withdraw (gold theme)
     */
    private void renderCoinPreview(UICommandBuilder cmd, String targetSelector, long amount, boolean isDeposit) {
        cmd.clear(targetSelector);
        
        if (amount <= 0) return;
        
        // Calculate optimal coin breakdown
        Map<CoinType, Integer> breakdown = CoinManager.calculateOptimalBreakdown(amount);
        
        // Track index for selector
        int idx = 0;
        
        // Render each coin type that has a count > 0
        for (CoinType type : CoinType.valuesDescending()) {
            int count = breakdown.getOrDefault(type, 0);
            if (count <= 0) continue;
            
            // Append the coin preview item template
            cmd.append(targetSelector, "Pages/Ecotale_CoinPreviewItem.ui");
            
            // Configure this item via set() - only Text and ItemId are settable
            String itemSelector = targetSelector + "[" + idx + "]";
            cmd.set(itemSelector + " #CoinIcon.ItemId", type.getItemId());
            cmd.set(itemSelector + " #CoinQty.Text", "x" + count);
            
            idx++;
        }
    }
    
    // ═══════════════════════════════════════════════════════════════════
    // DATA CODEC
    // ═══════════════════════════════════════════════════════════════════
    public static class BankGuiData {
        static final String KEY_ACTION = "Action";
        static final String KEY_TAB = "Tab";
        static final String KEY_AMOUNT = "@AmountInput";
        
        public static final BuilderCodec<BankGuiData> CODEC = BuilderCodec.<BankGuiData>builder(BankGuiData.class, BankGuiData::new)
            .append(new KeyedCodec<>(KEY_ACTION, Codec.STRING), (d, v, e) -> d.action = v, (d, e) -> d.action).add()
            .append(new KeyedCodec<>(KEY_TAB, Codec.STRING), (d, v, e) -> d.tab = v, (d, e) -> d.tab).add()
            .append(new KeyedCodec<>(KEY_AMOUNT, Codec.STRING), (d, v, e) -> d.amountInput = v, (d, e) -> d.amountInput).add()
            .build();
        
        private String action;
        private String tab;
        private String amountInput;
    }
}
