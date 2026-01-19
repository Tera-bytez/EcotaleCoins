# EcotaleCoins API Documentation

## Overview

EcotaleCoins provides a public API for managing physical coins and bank operations.

## Core Classes

### CoinManager

Static utility class for managing physical coins in player inventories.

```java
import com.ecotalecoins.currency.CoinManager;

// Count total coin value in inventory
long totalValue = CoinManager.countCoins(player);

// Give coins (optimal breakdown)
boolean success = CoinManager.giveCoins(player, 1500L);

// Take coins from inventory
boolean success = CoinManager.takeCoins(player, 500L);

// Check if player can afford
boolean canAfford = CoinManager.canAfford(player, 100L);

// Get breakdown by coin type
Map<CoinType, Integer> breakdown = CoinManager.getBreakdown(player);

// Consolidate to highest denominations
CoinManager.consolidate(player);
```

### BankManager

Static utility class for bank balance operations.

```java
import com.ecotalecoins.bank.BankManager;

// Get bank balance
long bankBalance = BankManager.getBankBalance(playerUuid);

// Deposit coins to bank
boolean success = BankManager.deposit(player, playerUuid, 1000L);

// Withdraw from bank to coins
boolean success = BankManager.withdraw(player, playerUuid, 500L);
```

### SecureTransaction

Bank-grade secure transactions with escrow pattern.

```java
import com.ecotalecoins.transaction.SecureTransaction;

// Secure exchange between coin types
TransactionResult result = SecureTransaction.executeSecureExchange(
    player, playerUuid, CoinType.GOLD, 10, CoinType.IRON
);

if (result.isSuccess()) {
    // Transaction completed
} else if (result.isMoneySafe()) {
    // Failed but money is in bank
    String txHash = result.getTxHash();
}
```

## CoinType Enum

```java
public enum CoinType {
    IRON(1),
    COPPER(10),
    BRONZE(50),
    SILVER(100),
    GOLD(1000),
    COBALT(10000),
    MITHRIL(100000);
    
    public long getValue();
    public String getDisplayName();
    public String getItemId();
}
```

## TransactionResult

```java
public record TransactionResult(
    boolean success,
    boolean moneySafe,
    String message,
    String txHash
) {
    public boolean isSuccess();
    public boolean isMoneySafe();
    public String getMessage();
    public String getTxHash();
}
```
