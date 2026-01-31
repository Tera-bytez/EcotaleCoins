package com.ecotalecoins.gui;

import com.ecotale.api.EcotaleAPI;
import com.ecotalecoins.Main;
import com.ecotalecoins.currency.BankManager;
import com.ecotalecoins.currency.CoinManager;
import com.ecotalecoins.currency.CoinType;
import com.ecotalecoins.currency.InventorySpaceCalculator;
import com.ecotalecoins.transaction.SecureTransaction;
import com.ecotalecoins.util.TranslationHelper;
import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.awt.Color;
import java.util.Map;
import java.util.UUID;
import org.checkerframework.checker.nullness.compatqual.NonNullDecl;

public class BankGui extends InteractiveCustomUIPage<BankGui.BankGuiData> {
   private final PlayerRef playerRef;
   private BankGui.Tab currentTab = BankGui.Tab.WALLET;
   private String amountInput = "";
   private int fromCoinIndex = 0;
   private int toCoinIndex = 1;
   private final CoinType[] enabledTypes;
   private long lastClickTime = 0L;

   public BankGui(@NonNullDecl PlayerRef playerRef) {
      super(playerRef, CustomPageLifetime.CanDismiss, BankGui.BankGuiData.CODEC);
      this.playerRef = playerRef;
      this.enabledTypes = CoinType.valuesAscending();
   }

   private String t(String key, String fallback) {
      return TranslationHelper.t(this.playerRef, key, fallback);
   }

   private String t(String key, String fallback, Object... args) {
      return TranslationHelper.t(this.playerRef, key, fallback, args);
   }

   public void build(
      @NonNullDecl Ref<EntityStore> ref, @NonNullDecl UICommandBuilder cmd, @NonNullDecl UIEventBuilder events, @NonNullDecl Store<EntityStore> store
   ) {
      cmd.append("Pages/Ecotale_BankPage.ui");
      Player player = (Player)store.getComponent(ref, Player.getComponentType());
      PlayerRef playerRefComp = (PlayerRef)store.getComponent(ref, PlayerRef.getComponentType());
      if (player != null && playerRefComp != null) {
         UUID playerUuid = playerRefComp.getUuid();
         long bankBalance = BankManager.getBankBalance(playerUuid);
         long pocketBalance = CoinManager.countCoins(player);
         long totalWealth = bankBalance + pocketBalance;
         String symbol = EcotaleAPI.getCurrencySymbol();
         events.addEventBinding(CustomUIEventBindingType.Activating, "#CloseButton", EventData.of("Action", "Close"), false);
         cmd.set("#TotalWealth.Text", symbol + this.formatLong(totalWealth));
         cmd.set("#BankBalance.Text", symbol + this.formatLong(bankBalance));
         cmd.set("#PocketBalance.Text", symbol + this.formatLong(pocketBalance));
         events.addEventBinding(CustomUIEventBindingType.Activating, "#Tab4Wallet", EventData.of("Tab", "Wallet"), false);
         events.addEventBinding(CustomUIEventBindingType.Activating, "#Tab4Deposit", EventData.of("Tab", "Deposit"), false);
         events.addEventBinding(CustomUIEventBindingType.Activating, "#Tab4Withdraw", EventData.of("Tab", "Withdraw"), false);
         events.addEventBinding(CustomUIEventBindingType.Activating, "#Tab4Exchange", EventData.of("Tab", "Exchange"), false);
         events.addEventBinding(CustomUIEventBindingType.Activating, "#Tab3Wallet", EventData.of("Tab", "Wallet"), false);
         events.addEventBinding(CustomUIEventBindingType.Activating, "#Tab3Deposit", EventData.of("Tab", "Deposit"), false);
         events.addEventBinding(CustomUIEventBindingType.Activating, "#Tab3Withdraw", EventData.of("Tab", "Withdraw"), false);
         cmd.set("#WalletContent.Visible", this.currentTab == BankGui.Tab.WALLET);
         cmd.set("#DepositContent.Visible", this.currentTab == BankGui.Tab.DEPOSIT);
         cmd.set("#WithdrawContent.Visible", this.currentTab == BankGui.Tab.WITHDRAW);
         cmd.set("#ExchangeContent.Visible", this.currentTab == BankGui.Tab.EXCHANGE);
         boolean showExchange = Main.getInstance().getCoinConfig() == null || Main.getInstance().getCoinConfig().showExchangeTab();
         boolean showConsolidate = Main.getInstance().getCoinConfig() == null || Main.getInstance().getCoinConfig().showConsolidateButton();
         cmd.set("#BtnConsolidate.Visible", showConsolidate);
         cmd.set("#TabButtonsRow4Tabs.Visible", showExchange);
         cmd.set("#TabButtonsRow3Tabs.Visible", !showExchange);
         this.updateTabStyles(cmd);
         switch (this.currentTab) {
            case WALLET:
               this.buildWalletTab(cmd, events, player, bankBalance, pocketBalance, symbol);
               break;
            case DEPOSIT:
               this.buildDepositTab(cmd, events, pocketBalance, bankBalance, symbol);
               break;
            case WITHDRAW:
               this.buildWithdrawTab(cmd, events, pocketBalance, bankBalance, symbol);
               break;
            case EXCHANGE:
               this.buildExchangeTab(cmd, events, player);
         }

         this.translateUI(cmd);
      }
   }

   private void updateTabStyles(UICommandBuilder cmd) {
      String walletName = this.getTabName(BankGui.Tab.WALLET);
      String depositName = this.getTabName(BankGui.Tab.DEPOSIT);
      String withdrawName = this.getTabName(BankGui.Tab.WITHDRAW);
      String exchangeName = this.getTabName(BankGui.Tab.EXCHANGE);
      String walletText = this.currentTab == BankGui.Tab.WALLET ? "[ " + walletName + " ]" : walletName;
      String depositText = this.currentTab == BankGui.Tab.DEPOSIT ? "[ " + depositName + " ]" : depositName;
      String withdrawText = this.currentTab == BankGui.Tab.WITHDRAW ? "[ " + withdrawName + " ]" : withdrawName;
      String exchangeText = this.currentTab == BankGui.Tab.EXCHANGE ? "[ " + exchangeName + " ]" : exchangeName;
      cmd.set("#Tab4Wallet.Text", walletText);
      cmd.set("#Tab4Deposit.Text", depositText);
      cmd.set("#Tab4Withdraw.Text", withdrawText);
      cmd.set("#Tab4Exchange.Text", exchangeText);
      cmd.set("#Tab3Wallet.Text", walletText);
      cmd.set("#Tab3Deposit.Text", depositText);
      cmd.set("#Tab3Withdraw.Text", withdrawText);
   }

   private void updatePreviewLabels(UICommandBuilder cmd, Store<EntityStore> store, Ref<EntityStore> ref) {
      Player player = (Player)store.getComponent(ref, Player.getComponentType());
      PlayerRef playerRefComp = (PlayerRef)store.getComponent(ref, PlayerRef.getComponentType());
      if (player != null && playerRefComp != null) {
         UUID playerUuid = playerRefComp.getUuid();
         String symbol = EcotaleAPI.getCurrencySymbol();
         long bankBalance = BankManager.getBankBalance(playerUuid);
         long pocketBalance = CoinManager.countCoins(player);
         switch (this.currentTab) {
            case DEPOSIT:
               long amountx = this.parseAmount(this.amountInput, pocketBalance);
               cmd.set("#DepositPreview.Visible", amountx > 0L);
               cmd.set("#DepositCoinPreview.Visible", amountx > 0L);
               if (amountx > 0L) {
                  long afterBank = bankBalance + amountx;
                  cmd.set(
                     "#DepositPreviewText.Text",
                     this.t("gui.bank.deposit.preview", "After: Bank {0} (+{1})", symbol + this.formatLong(afterBank), symbol + this.formatLong(amountx))
                  );
                  this.renderCoinPreview(cmd, "#DepositCoinRow", amountx, true);
               }
               break;
            case WITHDRAW:
               long amount = this.parseAmount(this.amountInput, bankBalance);
               cmd.set("#WithdrawPreview.Visible", amount > 0L);
               cmd.set("#WithdrawCoinPreview.Visible", amount > 0L);
               if (amount > 0L) {
                  long afterPocket = pocketBalance + amount;
                  cmd.set(
                     "#WithdrawPreviewText.Text",
                     this.t("gui.bank.withdraw.preview", "After: Pocket {0} (+{1})", symbol + this.formatLong(afterPocket), symbol + this.formatLong(amount))
                  );
                  this.renderCoinPreview(cmd, "#WithdrawCoinRow", amount, false);
               }
               break;
            case EXCHANGE:
               this.updateExchangePreview(cmd, player);
         }
      }
   }

   private void updateExchangePreview(UICommandBuilder cmd, Player player) {
      CoinType fromType = this.enabledTypes[this.fromCoinIndex];
      CoinType toType = this.enabledTypes[this.toCoinIndex];
      long inputAmount = this.parseAmountSimple(this.amountInput);
      int haveFrom = CoinManager.getBreakdown(player).getOrDefault(fromType, 0);
      long fromValue = fromType.getValue();
      long toValue = toType.getValue();
      long resultAmount = 0L;
      if (fromValue < toValue) {
         long exchangeRate = toValue / fromValue;
         resultAmount = inputAmount / exchangeRate;
      } else {
         resultAmount = inputAmount * (fromValue / toValue);
      }

      InventorySpaceCalculator.SpaceResult space = InventorySpaceCalculator.canFitSpecific(player, toType, (int)resultAmount);
      String message;
      if (inputAmount <= 0L) {
         message = this.t("gui.bank.exchange.enter_amount", "Enter amount to exchange");
      } else if (inputAmount > haveFrom) {
         message = this.t("gui.bank.exchange.not_enough", "Not enough {0} (have {1})", this.getCoinName(fromType), haveFrom);
      } else if (fromValue < toValue) {
         long exchangeRate = toValue / fromValue;
         if (inputAmount < exchangeRate) {
            message = this.t("gui.bank.exchange.need_at_least", "Need at least {0} {1}", exchangeRate, this.getCoinName(fromType));
         } else if (!space.canFit()) {
            message = this.t("gui.bank.exchange.need_slots", "Need {0} new slots, only {1} free", space.slotsNeeded(), space.slotsAvailable());
         } else {
            message = this.t(
               "gui.bank.exchange.use_get", "USE: {0} {1}  -->  GET: {2} {3}", inputAmount, this.getCoinName(fromType), resultAmount, this.getCoinName(toType)
            );
         }
      } else if (!space.canFit()) {
         message = this.t("gui.bank.exchange.need_slots", "Need {0} new slots, only {1} free", space.slotsNeeded(), space.slotsAvailable());
      } else {
         message = this.t(
            "gui.bank.exchange.use_get", "USE: {0} {1}  -->  GET: {2} {3}", inputAmount, this.getCoinName(fromType), resultAmount, this.getCoinName(toType)
         );
      }

      cmd.set("#ExchangeResultText.Text", message);
   }

   private int calculateSmartMax(Player player) {
      CoinType fromType = this.enabledTypes[this.fromCoinIndex];
      CoinType toType = this.enabledTypes[this.toCoinIndex];
      int haveFrom = CoinManager.getBreakdown(player).getOrDefault(fromType, 0);
      if (haveFrom == 0) {
         return 0;
      } else {
         long fromValue = fromType.getValue();
         long toValue = toType.getValue();
         long totalSpaceForTarget = InventorySpaceCalculator.calculateTotalSpaceFor(player, toType);
         int maxFromCoins;
         if (fromValue < toValue) {
            maxFromCoins = haveFrom;
            long resultIfAll = haveFrom * fromValue / toValue;
            if (resultIfAll > totalSpaceForTarget) {
               maxFromCoins = (int)(totalSpaceForTarget * toValue / fromValue);
            }
         } else {
            long resultPerFrom = fromValue / toValue;
            if (resultPerFrom == 0L) {
               return 0;
            }

            long maxFromBySpace = totalSpaceForTarget / resultPerFrom;
            maxFromCoins = (int)Math.min((long)haveFrom, maxFromBySpace);
         }

         return maxFromCoins;
      }
   }

   private void buildWalletTab(UICommandBuilder cmd, UIEventBuilder events, Player player, long bankBalance, long pocketBalance, String symbol) {
      Map<CoinType, Integer> coinBreakdown = CoinManager.getBreakdown(player);
      CoinType[] types = this.enabledTypes;
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
         cmd.set(targetRow + "[" + rowIndex + "] #CoinName.Text", this.getCoinName(type));
         cmd.set(targetRow + "[" + rowIndex + "] #CoinCount.Text", "x" + count);
         cmd.set(targetRow + "[" + rowIndex + "] #CoinValue.Text", symbol + this.formatLong(value));
      }

      events.addEventBinding(CustomUIEventBindingType.Activating, "#BtnDepositAll", EventData.of("Action", "DepositAll"), false);
      events.addEventBinding(CustomUIEventBindingType.Activating, "#BtnWithdrawAll", EventData.of("Action", "WithdrawAll"), false);
      events.addEventBinding(CustomUIEventBindingType.Activating, "#BtnConsolidate", EventData.of("Action", "Consolidate"), false);
   }

   private void buildDepositTab(UICommandBuilder cmd, UIEventBuilder events, long pocketBalance, long bankBalance, String symbol) {
      cmd.set("#DepositFromValue.Text", symbol + this.formatLong(pocketBalance));
      cmd.set("#DepositToValue.Text", symbol + this.formatLong(bankBalance));
      cmd.set("#DepositAmountInput.Value", this.amountInput);
      events.addEventBinding(CustomUIEventBindingType.ValueChanged, "#DepositAmountInput", EventData.of("@AmountInput", "#DepositAmountInput.Value"), false);
      events.addEventBinding(CustomUIEventBindingType.Activating, "#DepositQuick25", EventData.of("Action", "Quick25"), false);
      events.addEventBinding(CustomUIEventBindingType.Activating, "#DepositQuick50", EventData.of("Action", "Quick50"), false);
      events.addEventBinding(CustomUIEventBindingType.Activating, "#DepositQuick75", EventData.of("Action", "Quick75"), false);
      events.addEventBinding(CustomUIEventBindingType.Activating, "#DepositQuickMax", EventData.of("Action", "QuickMax"), false);
      events.addEventBinding(CustomUIEventBindingType.Activating, "#ConfirmDeposit", EventData.of("Action", "ConfirmDeposit"), false);
      long amount = this.parseAmount(this.amountInput, pocketBalance);
      if (amount > 0L && amount <= pocketBalance) {
         cmd.set("#DepositPreview.Visible", true);
         cmd.set(
            "#DepositPreviewText.Text",
            this.t("gui.bank.deposit.preview", "After: Bank {0} (+{1})", symbol + this.formatLong(bankBalance + amount), symbol + this.formatLong(amount))
         );
         cmd.set("#DepositCoinPreview.Visible", true);
         this.renderCoinPreview(cmd, "#DepositCoinRow", amount, true);
      } else {
         cmd.set("#DepositPreview.Visible", false);
         cmd.set("#DepositCoinPreview.Visible", false);
      }
   }

   private void buildWithdrawTab(UICommandBuilder cmd, UIEventBuilder events, long pocketBalance, long bankBalance, String symbol) {
      cmd.set("#WithdrawFromValue.Text", symbol + this.formatLong(bankBalance));
      cmd.set("#WithdrawToValue.Text", symbol + this.formatLong(pocketBalance));
      cmd.set("#WithdrawAmountInput.Value", this.amountInput);
      events.addEventBinding(CustomUIEventBindingType.ValueChanged, "#WithdrawAmountInput", EventData.of("@AmountInput", "#WithdrawAmountInput.Value"), false);
      events.addEventBinding(CustomUIEventBindingType.Activating, "#WithdrawQuick25", EventData.of("Action", "Quick25"), false);
      events.addEventBinding(CustomUIEventBindingType.Activating, "#WithdrawQuick50", EventData.of("Action", "Quick50"), false);
      events.addEventBinding(CustomUIEventBindingType.Activating, "#WithdrawQuick75", EventData.of("Action", "Quick75"), false);
      events.addEventBinding(CustomUIEventBindingType.Activating, "#WithdrawQuickMax", EventData.of("Action", "QuickMax"), false);
      events.addEventBinding(CustomUIEventBindingType.Activating, "#ConfirmWithdraw", EventData.of("Action", "ConfirmWithdraw"), false);
      long amount = this.parseAmount(this.amountInput, bankBalance);
      if (amount > 0L && amount <= bankBalance) {
         cmd.set("#WithdrawPreview.Visible", true);
         cmd.set(
            "#WithdrawPreviewText.Text",
            this.t("gui.bank.withdraw.preview", "After: Pocket {0} (+{1})", symbol + this.formatLong(pocketBalance + amount), symbol + this.formatLong(amount))
         );
         cmd.set("#WithdrawCoinPreview.Visible", true);
         this.renderCoinPreview(cmd, "#WithdrawCoinRow", amount, false);
      } else {
         cmd.set("#WithdrawPreview.Visible", false);
         cmd.set("#WithdrawCoinPreview.Visible", false);
      }
   }

   private void buildExchangeTab(UICommandBuilder cmd, UIEventBuilder events, Player player) {
      this.fromCoinIndex = Math.max(0, Math.min(this.fromCoinIndex, this.enabledTypes.length - 1));
      this.toCoinIndex = Math.max(0, Math.min(this.toCoinIndex, this.enabledTypes.length - 1));
      CoinType fromType = this.enabledTypes[this.fromCoinIndex];
      CoinType toType = this.enabledTypes[this.toCoinIndex];
      Map<CoinType, Integer> coinBreakdown = CoinManager.getBreakdown(player);
      cmd.set("#FromCoinIcon.ItemId", fromType.getItemId());
      cmd.set("#FromCoinName.Text", this.getCoinName(fromType));
      cmd.set("#FromCoinHave.Text", this.t("gui.bank.exchange.you_have", "You have: {0}", coinBreakdown.getOrDefault(fromType, 0)));
      cmd.set("#ToCoinIcon.ItemId", toType.getItemId());
      cmd.set("#ToCoinName.Text", this.getCoinName(toType));
      cmd.set("#ToCoinHave.Text", this.t("gui.bank.exchange.you_have", "You have: {0}", coinBreakdown.getOrDefault(toType, 0)));
      events.addEventBinding(CustomUIEventBindingType.Activating, "#FromPrev", EventData.of("Action", "FromPrev"), false);
      events.addEventBinding(CustomUIEventBindingType.Activating, "#FromNext", EventData.of("Action", "FromNext"), false);
      events.addEventBinding(CustomUIEventBindingType.Activating, "#ToPrev", EventData.of("Action", "ToPrev"), false);
      events.addEventBinding(CustomUIEventBindingType.Activating, "#ToNext", EventData.of("Action", "ToNext"), false);
      long rate = toType.getValue() / fromType.getValue();
      if (rate >= 1L) {
         cmd.set(
            "#ExchangeRate.Text", this.t("gui.bank.exchange.rate", "RATE: {0} {1} = {2} {3}", rate, this.getCoinName(fromType), "1", this.getCoinName(toType))
         );
      } else {
         rate = fromType.getValue() / toType.getValue();
         cmd.set(
            "#ExchangeRate.Text", this.t("gui.bank.exchange.rate", "RATE: {0} {1} = {2} {3}", "1", this.getCoinName(fromType), rate, this.getCoinName(toType))
         );
      }

      cmd.set("#ExchangeAmountInput.Value", this.amountInput);
      events.addEventBinding(CustomUIEventBindingType.ValueChanged, "#ExchangeAmountInput", EventData.of("@AmountInput", "#ExchangeAmountInput.Value"), false);
      events.addEventBinding(CustomUIEventBindingType.Activating, "#ExchangeQuickMax", EventData.of("Action", "ExchangeMax"), false);
      events.addEventBinding(CustomUIEventBindingType.Activating, "#ConfirmExchange", EventData.of("Action", "ConfirmExchange"), false);
      long amount = this.parseAmountSimple(this.amountInput);
      int available = coinBreakdown.getOrDefault(fromType, 0);
      if (amount > 0L && fromType != toType) {
         long sourceValue = fromType.getValue() * amount;
         long targetValue = toType.getValue();
         if (sourceValue >= targetValue) {
            long resultAmount = sourceValue / targetValue;
            cmd.set(
               "#ExchangeResultText.Text", "USE: " + amount + " " + this.getCoinName(fromType) + "  -->  GET: " + resultAmount + " " + this.getCoinName(toType)
            );
         } else {
            long needed = (long)Math.ceil((double)targetValue / fromType.getValue());
            cmd.set("#ExchangeResultText.Text", "Need at least " + needed + " " + this.getCoinName(fromType));
         }
      } else if (fromType == toType) {
         cmd.set("#ExchangeResultText.Text", "Select different coin types");
      } else {
         cmd.set("#ExchangeResultText.Text", this.t("gui.bank.exchange.enter_amount", "Enter amount to exchange"));
      }

      cmd.set("#ExchangeQuickMax.Text", this.t("gui.bank.exchange.max_possible", "MAX POSSIBLE"));
   }

   public void handleDataEvent(@NonNullDecl Ref<EntityStore> ref, @NonNullDecl Store<EntityStore> store, @NonNullDecl BankGui.BankGuiData data) {
      super.handleDataEvent(ref, store, data);
      Player player = (Player)store.getComponent(ref, Player.getComponentType());
      PlayerRef playerRefComp = (PlayerRef)store.getComponent(ref, PlayerRef.getComponentType());
      if (player != null && playerRefComp != null) {
         UUID playerUuid = playerRefComp.getUuid();
         if (data.tab != null) {
            String var10 = data.tab;
            switch (var10) {
               case "Wallet":
                  this.currentTab = BankGui.Tab.WALLET;
                  break;
               case "Deposit":
                  this.currentTab = BankGui.Tab.DEPOSIT;
                  break;
               case "Withdraw":
                  this.currentTab = BankGui.Tab.WITHDRAW;
                  break;
               case "Exchange":
                  this.currentTab = BankGui.Tab.EXCHANGE;
            }

            this.amountInput = "";
            this.refreshUI(ref, store);
         } else if (data.amountInput != null) {
            this.amountInput = this.sanitizeInput(data.amountInput);
            UICommandBuilder cmd = new UICommandBuilder();
            this.updatePreviewLabels(cmd, store, ref);
            this.sendUpdate(cmd, new UIEventBuilder(), false);
         } else {
            if (data.action != null) {
               if (System.currentTimeMillis() - this.lastClickTime < 250L) {
                  this.playerRef.sendMessage(Message.raw(this.t("gui.bank.wait", "Please wait before performing another action")).color(Color.YELLOW));
                  return;
               }

               String cmd = data.action;
               switch (cmd) {
                  case "Close":
                     this.close();
                     return;
                  case "Quick25":
                     this.handleQuickAmount(player, playerUuid, 0.25);
                     break;
                  case "Quick50":
                     this.handleQuickAmount(player, playerUuid, 0.5);
                     break;
                  case "Quick75":
                     this.handleQuickAmount(player, playerUuid, 0.75);
                     break;
                  case "QuickMax":
                     this.handleQuickAmount(player, playerUuid, 1.0);
                     break;
                  case "DepositAll":
                     this.executeDepositAll(player, playerUuid);
                     break;
                  case "WithdrawAll":
                     this.executeWithdrawAll(player, playerUuid);
                     break;
                  case "Consolidate":
                     this.executeConsolidate(player);
                     break;
                  case "ConfirmDeposit":
                     this.executeDeposit(player, playerUuid);
                     break;
                  case "ConfirmWithdraw":
                     this.executeWithdraw(player, playerUuid);
                     break;
                  case "ConfirmExchange":
                     this.executeExchange(player);
                     break;
                  case "FromPrev":
                     this.fromCoinIndex = this.cycleIndex(this.fromCoinIndex, -1);
                     this.ensureDifferentCoins();
                     break;
                  case "FromNext":
                     this.fromCoinIndex = this.cycleIndex(this.fromCoinIndex, 1);
                     this.ensureDifferentCoins();
                     break;
                  case "ToPrev":
                     this.toCoinIndex = this.cycleToIndex(this.toCoinIndex, -1);
                     break;
                  case "ToNext":
                     this.toCoinIndex = this.cycleToIndex(this.toCoinIndex, 1);
                     break;
                  case "ExchangeMax":
                     this.handleExchangeMax(player);
               }

               this.lastClickTime = System.currentTimeMillis();
               this.refreshUI(ref, store);
            }
         }
      }
   }

   private void executeDeposit(Player player, UUID playerUuid) {
      long pocketBalance = CoinManager.countCoins(player);
      long amount = this.parseAmount(this.amountInput, pocketBalance);
      if (amount <= 0L) {
         this.playerRef.sendMessage(Message.raw(this.t("gui.bank.error.invalid_amount", "Enter a valid amount")).color(Color.RED));
      } else {
         SecureTransaction.TransactionResult result = SecureTransaction.executeSecureDeposit(player, playerUuid, amount);
         if (result.isSuccess()) {
            this.amountInput = "";
            this.playerRef.sendMessage(Message.raw(result.getMessage()).color(Color.GREEN));
         } else if (result.isMoneySafe() && result.getTxHash() != null) {
            this.playerRef.sendMessage(Message.raw(result.getMessage()).color(Color.YELLOW));
         } else {
            this.playerRef.sendMessage(Message.raw(result.getMessage()).color(Color.RED));
         }
      }
   }

   private void executeWithdraw(Player player, UUID playerUuid) {
      long bankBalance = BankManager.getBankBalance(playerUuid);
      long amount = this.parseAmount(this.amountInput, bankBalance);
      if (amount <= 0L) {
         this.playerRef.sendMessage(Message.raw(this.t("gui.bank.error.invalid_amount", "Enter a valid amount")).color(Color.RED));
      } else {
         SecureTransaction.TransactionResult result = SecureTransaction.executeSecureWithdraw(player, playerUuid, amount);
         if (result.isSuccess()) {
            this.amountInput = "";
            this.playerRef.sendMessage(Message.raw(result.getMessage()).color(Color.GREEN));
         } else if (result.isMoneySafe() && result.getTxHash() != null) {
            this.playerRef.sendMessage(Message.raw(result.getMessage()).color(Color.YELLOW));
         } else {
            this.playerRef.sendMessage(Message.raw(result.getMessage()).color(Color.RED));
         }
      }
   }

   private void executeDepositAll(Player player, UUID playerUuid) {
      long pocketBalance = CoinManager.countCoins(player);
      if (pocketBalance <= 0L) {
         this.playerRef.sendMessage(Message.raw(this.t("gui.bank.error.no_pocket_coins", "No coins in pocket to deposit")).color(Color.YELLOW));
      } else {
         SecureTransaction.TransactionResult result = SecureTransaction.executeSecureDeposit(player, playerUuid, pocketBalance);
         if (result.isSuccess()) {
            this.playerRef.sendMessage(Message.raw(result.getMessage()).color(Color.GREEN));
         } else {
            this.playerRef.sendMessage(Message.raw(result.getMessage()).color(Color.RED));
         }
      }
   }

   private void executeWithdrawAll(Player player, UUID playerUuid) {
      long bankBalance = BankManager.getBankBalance(playerUuid);
      if (bankBalance <= 0L) {
         this.playerRef.sendMessage(Message.raw(this.t("gui.bank.error.no_bank_coins", "No coins in bank to withdraw")).color(Color.YELLOW));
      } else {
         SecureTransaction.TransactionResult result = SecureTransaction.executeSecureWithdraw(player, playerUuid, bankBalance);
         if (result.isSuccess()) {
            this.playerRef.sendMessage(Message.raw(result.getMessage()).color(Color.GREEN));
         } else if (result.isMoneySafe() && result.getTxHash() != null) {
            this.playerRef.sendMessage(Message.raw(result.getMessage()).color(Color.YELLOW));
         } else {
            this.playerRef.sendMessage(Message.raw(result.getMessage()).color(Color.RED));
         }
      }
   }

   private void executeConsolidate(Player player) {
      long pocketBalance = CoinManager.countCoins(player);
      if (pocketBalance <= 0L) {
         this.playerRef.sendMessage(Message.raw(this.t("gui.bank.error.no_consolidate", "No coins to consolidate")).color(Color.YELLOW));
      } else {
         CoinManager.consolidate(player);
         this.playerRef.sendMessage(Message.raw(this.t("gui.bank.consolidate_success", "Coins consolidated to highest denominations")).color(Color.GREEN));
      }
   }

   private void executeExchange(Player player) {
      CoinType[] types = this.enabledTypes;
      CoinType fromType = types[this.fromCoinIndex];
      CoinType toType = types[this.toCoinIndex];
      long amount = this.parseAmountSimple(this.amountInput);
      if (amount > 0L && amount <= 2147483647L) {
         SecureTransaction.TransactionResult result = SecureTransaction.executeSecureExchange(player, this.playerRef.getUuid(), fromType, (int)amount, toType);
         if (result.isSuccess()) {
            this.amountInput = "";
            this.playerRef.sendMessage(Message.raw(result.getMessage()).color(Color.GREEN));
         } else if (result.isMoneySafe() && result.getTxHash() != null) {
            this.playerRef.sendMessage(Message.raw(result.getMessage()).color(Color.YELLOW));
         } else {
            this.playerRef.sendMessage(Message.raw(result.getMessage()).color(Color.RED));
         }
      } else {
         this.playerRef.sendMessage(Message.raw(this.t("gui.bank.error.invalid_amount", "Enter a valid amount")).color(Color.RED));
      }
   }

   private void handleQuickAmount(Player player, UUID playerUuid, double percentage) {
      long max = switch (this.currentTab) {
         case DEPOSIT -> CoinManager.countCoins(player);
         case WITHDRAW -> BankManager.getBankBalance(playerUuid);
         default -> 0L;
      };
      this.amountInput = String.valueOf(Math.max(1L, (long)(max * percentage)));
   }

   private void handleExchangeMax(Player player) {
      int smartMax = this.calculateSmartMax(player);
      this.amountInput = String.valueOf(smartMax);
   }

   private int cycleIndex(int current, int delta) {
      int length = this.enabledTypes.length;
      return (current + delta + length) % length;
   }

   private boolean isExchangePossible(int fromIndex, int toIndex) {
      if (fromIndex == toIndex) {
         return false;
      } else {
         CoinType fromType = this.enabledTypes[fromIndex];
         CoinType toType = this.enabledTypes[toIndex];
         if (fromType.getValue() <= toType.getValue()) {
            return true;
         } else {
            long resultCoins = fromType.getValue() / toType.getValue();
            int MAX_INVENTORY_SLOTS = 45;
            int MAX_STACK_SIZE = 999;
            long MAX_COINS = 44955L;
            return resultCoins <= 44955L;
         }
      }
   }

   private int cycleToIndex(int current, int delta) {
      int length = this.enabledTypes.length;
      int attempts = 0;
      int newIndex = current;

      do {
         newIndex = (newIndex + delta + length) % length;
      } while (++attempts < length && !this.isExchangePossible(this.fromCoinIndex, newIndex));

      return newIndex;
   }

   private void ensureDifferentCoins() {
      if (this.fromCoinIndex == this.toCoinIndex) {
         this.toCoinIndex = this.cycleIndex(this.toCoinIndex, 1);
      }

      if (!this.isExchangePossible(this.fromCoinIndex, this.toCoinIndex)) {
         this.toCoinIndex = this.cycleToIndex(this.toCoinIndex, 1);
      }
   }

   private String sanitizeInput(String input) {
      if (input == null) {
         return "";
      } else {
         String cleaned = input.trim().toLowerCase();
         if (cleaned.equals("all")) {
            return cleaned;
         } else {
            String digitsOnly = input.replaceAll("[^0-9]", "");
            if (digitsOnly.length() > 15) {
               digitsOnly = digitsOnly.substring(0, 15);
            }

            return digitsOnly;
         }
      }
   }

   private long parseAmount(String input, long maxForAll) {
      if (input == null || input.isEmpty()) {
         return 0L;
      } else if (input.equalsIgnoreCase("all")) {
         return maxForAll;
      } else {
         String sanitized = this.sanitizeInput(input);
         if (sanitized.isEmpty()) {
            return 0L;
         } else {
            try {
               long val = Long.parseLong(sanitized);
               if (val <= 0L) {
                  return 0L;
               } else {
                  long maxBalance = (long)EcotaleAPI.getMaxBalance();
                  return Math.min(val, maxBalance);
               }
            } catch (NumberFormatException var9) {
               return 0L;
            }
         }
      }
   }

   private long parseAmountSimple(String input) {
      if (input != null && !input.isEmpty()) {
         String sanitized = this.sanitizeInput(input);
         if (!sanitized.isEmpty() && !sanitized.equals("all")) {
            try {
               long val = Long.parseLong(sanitized);
               if (val <= 0L) {
                  return 0L;
               } else {
                  long maxBalance = (long)EcotaleAPI.getMaxBalance();
                  return Math.min(val, maxBalance);
               }
            } catch (NumberFormatException var7) {
               return 0L;
            }
         } else {
            return 0L;
         }
      } else {
         return 0L;
      }
   }

   private void refreshUI(Ref<EntityStore> ref, Store<EntityStore> store) {
      UICommandBuilder cmd = new UICommandBuilder();
      UIEventBuilder events = new UIEventBuilder();
      this.build(ref, cmd, events, store);
      this.sendUpdate(cmd, events, true);
   }

   private void translateUI(UICommandBuilder cmd) {
      cmd.set("#Title.Text", this.t("gui.bank.title", "BANK"));
      cmd.set("#WalletLabel.Text", this.t("gui.bank.wallet", "WALLET"));
      cmd.set("#BankLabel.Text", this.t("gui.bank.bank", "BANK"));
      cmd.set("#TotalLabel.Text", this.t("gui.bank.total", "TOTAL"));
      cmd.set("#CoinCollectionLabel.Text", this.t("gui.bank.wallet.title", "YOUR COIN COLLECTION"));
      cmd.set("#QuickActionsLabel.Text", this.t("gui.bank.wallet.quick_actions", "QUICK ACTIONS"));
      cmd.set("#BtnDepositAll.Text", this.t("gui.bank.wallet.deposit_all", "DEPOSIT ALL"));
      cmd.set("#BtnWithdrawAll.Text", this.t("gui.bank.wallet.withdraw_all", "WITHDRAW ALL"));
      cmd.set("#BtnConsolidate.Text", this.t("gui.bank.wallet.consolidate", "CONSOLIDATE"));
      cmd.set("#DepositFromLabel.Text", this.t("gui.bank.deposit.from", "FROM POCKET"));
      cmd.set("#DepositToLabel.Text", this.t("gui.bank.deposit.to", "TO BANK"));
      cmd.set("#DepositAmountLabel.Text", this.t("gui.bank.deposit.amount", "AMOUNT TO DEPOSIT"));
      cmd.set("#ConfirmDeposit.Text", this.t("gui.bank.deposit.confirm", "CONFIRM DEPOSIT"));
      cmd.set("#DepositCoinsLabel.Text", this.t("gui.bank.deposit.coins_preview", "COINS TO DEPOSIT"));
      cmd.set("#WithdrawFromLabel.Text", this.t("gui.bank.withdraw.from", "FROM BANK"));
      cmd.set("#WithdrawToLabel.Text", this.t("gui.bank.withdraw.to", "TO POCKET"));
      cmd.set("#WithdrawAmountLabel.Text", this.t("gui.bank.withdraw.amount", "AMOUNT TO WITHDRAW"));
      cmd.set("#ConfirmWithdraw.Text", this.t("gui.bank.withdraw.confirm", "CONFIRM WITHDRAW"));
      cmd.set("#WithdrawCoinsLabel.Text", this.t("gui.bank.withdraw.coins_preview", "COINS TO RECEIVE"));
      cmd.set("#ExchangeFromLabel.Text", this.t("gui.bank.exchange.from", "EXCHANGE FROM"));
      cmd.set("#ExchangeToLabel.Text", this.t("gui.bank.exchange.to", "EXCHANGE TO"));
      cmd.set("#ExchangeAmountLabel.Text", this.t("gui.bank.exchange.amount", "AMOUNT:"));
      cmd.set("#ConfirmExchange.Text", this.t("gui.bank.exchange.confirm", "CONFIRM EXCHANGE"));
   }

   private String getTabName(BankGui.Tab tab) {
      return switch (tab) {
         case WALLET -> this.t("gui.bank.tab.wallet", "WALLET");
         case DEPOSIT -> this.t("gui.bank.tab.deposit", "DEPOSIT");
         case WITHDRAW -> this.t("gui.bank.tab.withdraw", "WITHDRAW");
         case EXCHANGE -> this.t("gui.bank.tab.exchange", "EXCHANGE");
      };
   }

   private String getCoinName(CoinType type) {
      if (Main.getInstance().getCoinConfig() != null && !Main.getInstance().getCoinConfig().useTranslationKeys()) {
         return type.getDisplayName();
      } else {
         return switch (type) {
            case COPPER -> this.t("coins.copper", "Copper");
            case IRON -> this.t("coins.iron", "Iron");
            case COBALT -> this.t("coins.cobalt", "Cobalt");
            case GOLD -> this.t("coins.gold", "Gold");
            case MITHRIL -> this.t("coins.mithril", "Mithril");
            case ADAMANTITE -> this.t("coins.adamantite", "Adamantite");
         };
      }
   }

   private String formatLong(long value) {
      if (value >= 1000000000L) {
         return String.format("%.2fB", value / 1.0E9);
      } else if (value >= 1000000L) {
         return String.format("%.2fM", value / 1000000.0);
      } else {
         return value >= 1000L ? String.format("%.1fK", value / 1000.0) : String.valueOf(value);
      }
   }

   private void renderCoinPreview(UICommandBuilder cmd, String targetSelector, long amount, boolean isDeposit) {
      cmd.clear(targetSelector);
      if (amount > 0L) {
         Map<CoinType, Integer> breakdown = CoinManager.calculateOptimalBreakdown(amount);
         int idx = 0;

         for (CoinType type : CoinType.valuesDescending()) {
            int count = breakdown.getOrDefault(type, 0);
            if (count > 0) {
               cmd.append(targetSelector, "Pages/Ecotale_CoinPreviewItem.ui");
               String itemSelector = targetSelector + "[" + idx + "]";
               cmd.set(itemSelector + " #CoinIcon.ItemId", type.getItemId());
               cmd.set(itemSelector + " #CoinQty.Text", "x" + count);
               idx++;
            }
         }
      }
   }

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

   private static enum Tab {
      WALLET,
      DEPOSIT,
      WITHDRAW,
      EXCHANGE;
   }
}
