package com.ecotalecoins.api;

import com.ecotalecoins.currency.CoinType;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Result object for coin operations.
 * 
 * Provides detailed information about success or failure,
 * allowing modders to give proper feedback to players.
 */
public final class CoinResult {
    
    /**
     * Status codes for coin operations.
     */
    public enum Status {
        /** Operation completed successfully. */
        SUCCESS,
        /** Player's inventory doesn't have enough space. */
        NOT_ENOUGH_SPACE,
        /** Player doesn't have enough coins to take. */
        INSUFFICIENT_FUNDS,
        /** Amount was zero or negative. */
        INVALID_AMOUNT,
        /** Specific coin type not found in inventory (count = 0). */
        COIN_TYPE_NOT_FOUND,
        /** Player entity was null or invalid. */
        INVALID_PLAYER,
        /** Internal error during operation. */
        INTERNAL_ERROR
    }
    
    private final Status status;
    private final long requestedAmount;
    private final long actualAmount;
    private final String message;
    private final CoinType coinType;
    private final int slotsNeeded;
    private final int slotsAvailable;
    
    private CoinResult(Status status, long requestedAmount, long actualAmount, 
                       String message, CoinType coinType, int slotsNeeded, int slotsAvailable) {
        this.status = status;
        this.requestedAmount = requestedAmount;
        this.actualAmount = actualAmount;
        this.message = message;
        this.coinType = coinType;
        this.slotsNeeded = slotsNeeded;
        this.slotsAvailable = slotsAvailable;
    }
    
    // ========== Factory Methods ==========
    
    public static CoinResult success(long amount) {
        return new CoinResult(Status.SUCCESS, amount, amount, "Operation successful", null, 0, 0);
    }
    
    public static CoinResult success(long amount, @Nonnull CoinType type) {
        return new CoinResult(Status.SUCCESS, amount, amount, "Operation successful", type, 0, 0);
    }
    
    public static CoinResult notEnoughSpace(long requestedAmount, int slotsNeeded, int slotsAvailable) {
        String msg = String.format("Need %d slots, only %d available", slotsNeeded, slotsAvailable);
        return new CoinResult(Status.NOT_ENOUGH_SPACE, requestedAmount, 0, msg, null, slotsNeeded, slotsAvailable);
    }
    
    public static CoinResult insufficientFunds(long requestedAmount, long actualBalance) {
        String msg = String.format("Requested %d but only have %d", requestedAmount, actualBalance);
        return new CoinResult(Status.INSUFFICIENT_FUNDS, requestedAmount, actualBalance, msg, null, 0, 0);
    }
    
    public static CoinResult insufficientFunds(long requestedAmount, long actualBalance, @Nonnull CoinType type) {
        String msg = String.format("Requested %d %s but only have %d", requestedAmount, type.name(), actualBalance);
        return new CoinResult(Status.INSUFFICIENT_FUNDS, requestedAmount, actualBalance, msg, type, 0, 0);
    }
    
    public static CoinResult invalidAmount(long amount) {
        return new CoinResult(Status.INVALID_AMOUNT, amount, 0, "Amount must be positive", null, 0, 0);
    }
    
    public static CoinResult invalidPlayer() {
        return new CoinResult(Status.INVALID_PLAYER, 0, 0, "Player is null or invalid", null, 0, 0);
    }
    
    public static CoinResult coinTypeNotFound(@Nonnull CoinType type) {
        String msg = String.format("No %s coins in inventory", type.name());
        return new CoinResult(Status.COIN_TYPE_NOT_FOUND, 0, 0, msg, type, 0, 0);
    }
    
    public static CoinResult error(String message) {
        return new CoinResult(Status.INTERNAL_ERROR, 0, 0, message, null, 0, 0);
    }
    
    // ========== Getters ==========
    
    /** @return true if operation was successful */
    public boolean isSuccess() {
        return status == Status.SUCCESS;
    }
    
    /** @return The status code */
    @Nonnull
    public Status getStatus() {
        return status;
    }
    
    /** @return The amount that was requested */
    public long getRequestedAmount() {
        return requestedAmount;
    }
    
    /** @return The actual amount processed (may differ on partial success or checks) */
    public long getActualAmount() {
        return actualAmount;
    }
    
    /** @return Human-readable message describing the result */
    @Nonnull
    public String getMessage() {
        return message;
    }
    
    /** @return The specific coin type involved (null for generic operations) */
    @Nullable
    public CoinType getCoinType() {
        return coinType;
    }
    
    /** @return Number of inventory slots needed (only set for NOT_ENOUGH_SPACE) */
    public int getSlotsNeeded() {
        return slotsNeeded;
    }
    
    /** @return Number of slots available (only set for NOT_ENOUGH_SPACE) */
    public int getSlotsAvailable() {
        return slotsAvailable;
    }
    
    @Override
    public String toString() {
        return "CoinResult{" +
               "status=" + status +
               ", requested=" + requestedAmount +
               ", actual=" + actualAmount +
               ", message='" + message + '\'' +
               '}';
    }
}
