package com.ecotalecoins.transaction;
import com.ecotale.api.EcotaleAPI;
import com.ecotalecoins.currency.CoinManager;
import com.ecotalecoins.currency.CoinType;
import com.ecotalecoins.currency.InventorySpaceCalculator;
import com.hypixel.hytale.server.core.entity.entities.Player;
import javax.annotation.Nonnull;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Logger;
import static com.ecotalecoins.util.TranslationHelper.t;



/**
 * Secure transaction system for coin exchanges, deposits, and withdrawals.
 */
public class SecureTransaction {
    
    // Player locks to ensure isolation
    private static final Map<UUID, ReentrantLock> playerLocks = new ConcurrentHashMap<>();
    
    // Transaction log (in-memory for now, can be persisted)
    private static final Map<String, TransactionRecord> transactionLog = new ConcurrentHashMap<>();
    
    /**
     * Result of a secure transaction.
     */
    public static class TransactionResult {
        private final boolean success;
        private final boolean moneySafe;
        private final String message;
        private final String txHash;
        
        private TransactionResult(boolean success, boolean moneySafe, String message, String txHash) {
            this.success = success;
            this.moneySafe = moneySafe;
            this.message = message;
            this.txHash = txHash;
        }
        
        public static TransactionResult success(String message, String txHash) {
            return new TransactionResult(true, true, message, txHash);
        }
        
        public static TransactionResult failedButSafe(String message, String txHash) {
            return new TransactionResult(false, true, message, txHash);
        }
        
        public static TransactionResult rejected(String message) {
            return new TransactionResult(false, true, message, null);
        }
        
        public boolean isSuccess() { return success; }
        public boolean isMoneySafe() { return moneySafe; }
        public String getMessage() { return message; }
        public String getTxHash() { return txHash; }
    }
    
    /**
     * Transaction record for logging and recovery.
     */
    public static class TransactionRecord {
        public final String txHash;
        public final UUID playerUuid;
        public final String type; // EXCHANGE, DEPOSIT, WITHDRAW
        public final String status; // PENDING, COMMITTED, ROLLED_BACK
        public final CoinType fromCoin;
        public final int fromAmount;
        public final CoinType toCoin;
        public final long toAmount;
        public final long escrowValue;
        public final long timestamp;
        public final String errorMessage;
        
        public TransactionRecord(String txHash, UUID playerUuid, String type, String status,
                                CoinType fromCoin, int fromAmount, CoinType toCoin, long toAmount,
                                long escrowValue, String errorMessage) {
            this.txHash = txHash;
            this.playerUuid = playerUuid;
            this.type = type;
            this.status = status;
            this.fromCoin = fromCoin;
            this.fromAmount = fromAmount;
            this.toCoin = toCoin;
            this.toAmount = toAmount;
            this.escrowValue = escrowValue;
            this.timestamp = System.currentTimeMillis();
            this.errorMessage = errorMessage;
        }
    }
    
    /**
     * Execute a secure coin exchange.
     * Money is NEVER lost - in worst case, value goes to player's bank.
     */
    public static TransactionResult executeSecureExchange(
            @Nonnull Player player,
            @Nonnull UUID playerUuid,
            @Nonnull CoinType fromType,
            int fromAmount,
            @Nonnull CoinType toType) {
        
                String txHash = generateTxHash(playerUuid, fromType, fromAmount, toType);
        
        // Check for replay attack
        if (transactionLog.containsKey(txHash)) {
            return TransactionResult.rejected("Duplicate transaction detected");
        }
        
        // Validate same coin type
        if (fromType == toType) {
            return TransactionResult.rejected("Cannot exchange same coin type");
        }
        
        // Validate amount
        if (fromAmount <= 0) {
            return TransactionResult.rejected("Invalid amount");
        }
        
        // Calculate exchange
        long sourceValue = fromType.getValue() * fromAmount;
        long targetValue = toType.getValue();
        
        if (sourceValue < targetValue) {
            long needed = (long) Math.ceil((double) targetValue / fromType.getValue());
            return TransactionResult.rejected("Need at least " + needed + " " + fromType.getDisplayName());
        }
        
        long resultAmount = sourceValue / targetValue;
        
        // Overflow protection
        if (resultAmount > 10_000_000) {
            return TransactionResult.rejected(t("transaction.error.too_large", "Amount too large"));
        }
        
        // Get player lock for isolation
        ReentrantLock lock = playerLocks.computeIfAbsent(playerUuid, k -> new ReentrantLock());
        lock.lock();
        
        try {
                        Map<CoinType, Integer> breakdown = CoinManager.getBreakdown(player);
            int available = breakdown.getOrDefault(fromType, 0);
            
            if (available < fromAmount) {
                return TransactionResult.rejected(t("transaction.error.insufficient_funds", 
                    "Not enough {0} (have {1}, need {2})", fromType.getDisplayName(), available, fromAmount));
            }
            
            // Calculate actual source used (avoid rounding issues)
            long usedSourceValue = resultAmount * targetValue;
            int actualSourceUsed = (int) (usedSourceValue / fromType.getValue());
            
            // Pre-check space (Intelligent check)
            InventorySpaceCalculator.SpaceResult space = 
                InventorySpaceCalculator.canFitSpecific(player, toType, (int) resultAmount);
            
            if (!space.canFit()) {
                return TransactionResult.rejected("Not enough inventory space (need " + 
                    space.slotsNeeded() + " new slots, have " + space.slotsAvailable() + ")");
            }
            
                        // Log as PENDING before any changes
            TransactionRecord record = new TransactionRecord(
                txHash, playerUuid, "EXCHANGE", "PENDING",
                fromType, actualSourceUsed, toType, resultAmount,
                usedSourceValue, null
            );
            transactionLog.put(txHash, record);
            
            // Take coins from player
            boolean taken = CoinManager.takeSpecificCoins(player, fromType, actualSourceUsed);
            if (!taken) {
                updateTransactionStatus(txHash, "REJECTED", "Failed to take coins");
                return TransactionResult.rejected(t("transaction.error.take_failed", "Failed to take coins from inventory"));
            }
            
            // Deposit to bank as ESCROW (money is now SAFE in bank)
            EcotaleAPI.deposit(playerUuid, (double) usedSourceValue, "TX_ESCROW:" + txHash);
            
            // === PHASE 4: RE-VERIFY (TOCTOU Protection) ===
            space = InventorySpaceCalculator.canFitSpecific(player, toType, (int) resultAmount);
            
            if (!space.canFit()) {
                // Space was taken during transaction!
                // Money is SAFE in bank - inform player
                updateTransactionStatus(txHash, "ROLLED_BACK_TO_BANK", 
                    "Inventory space changed during transaction - value saved to bank");
                
                return TransactionResult.failedButSafe(
                    t("transaction.error.safe_deposit", 
                    "No inventory space - your {0} has been deposited to your bank instead", formatValue(usedSourceValue)),
                    txHash
                );
            }
            
                        // Withdraw escrow from bank
            EcotaleAPI.withdraw(playerUuid, (double) usedSourceValue, "TX_COMPLETE:" + txHash);
            
            // Give new coins
            boolean given = CoinManager.giveSpecificCoins(player, toType, (int) resultAmount);
            
            if (!given) {
                // This shouldn't happen after re-verify, but handle gracefully
                // Put value back in bank
                EcotaleAPI.deposit(playerUuid, (double) usedSourceValue, "TX_FALLBACK:" + txHash);
                updateTransactionStatus(txHash, "ROLLED_BACK_TO_BANK", 
                    "Could not give coins - value returned to bank");
                
                return TransactionResult.failedButSafe(
                    t("transaction.error.delivery_failed", 
                    "Could not deliver coins - your {0} has been deposited to your bank", formatValue(usedSourceValue)),
                    txHash
                );
            }
            
                        updateTransactionStatus(txHash, "COMMITTED", null);
            
            return TransactionResult.success(
                t("transaction.success.exchange", 
                "Exchanged {0} {1} for {2} {3}", 
                actualSourceUsed, fromType.getDisplayName(), resultAmount, toType.getDisplayName()),
                txHash
            );
            
        } finally {
            lock.unlock();
        }
    }
    
    /**
     * Execute a secure bank withdrawal.
     * Money stays in bank until coins are successfully delivered to inventory.
     * If delivery fails, money remains safely in bank.
     */
    public static TransactionResult executeSecureWithdraw(
            @Nonnull Player player,
            @Nonnull UUID playerUuid,
            long amount) {
        
                String txHash = generateWithdrawTxHash(playerUuid, amount);
        
        // Check for replay attack
        if (transactionLog.containsKey(txHash)) {
            return TransactionResult.rejected("Duplicate transaction detected");
        }
        
        // Validate amount
        if (amount <= 0) {
            return TransactionResult.rejected(t("transaction.error.invalid_amount", "Invalid amount"));
        }
        
        // Get player lock for isolation
        ReentrantLock lock = playerLocks.computeIfAbsent(playerUuid, k -> new ReentrantLock());
        lock.lock();
        
        try {
                        long bankBalance = com.ecotalecoins.currency.BankManager.getBankBalance(playerUuid);
            
            if (bankBalance < amount) {
                return TransactionResult.rejected(t("transaction.error.bank_insufficient", 
                    "Insufficient bank balance (have {0}, need {1})", formatValue(bankBalance), formatValue(amount)));
            }
            
            // Pre-check space (Intelligent check)
            InventorySpaceCalculator.SpaceResult space = 
                InventorySpaceCalculator.canFitAmount(player, amount);
            
            if (!space.canFit()) {
                return TransactionResult.rejected("Not enough inventory space (need " + 
                    space.slotsNeeded() + " new slots, have " + space.slotsAvailable() + ")");
            }
            
            // === PHASE 3: LOG PENDING ===
            TransactionRecord record = new TransactionRecord(
                txHash, playerUuid, "WITHDRAW", "PENDING",
                null, 0, null, amount,
                amount, null
            );
            transactionLog.put(txHash, record);
            
            // === PHASE 4: RE-VERIFY (TOCTOU Protection) ===
            // Check space again right before attempting delivery
            space = InventorySpaceCalculator.canFitAmount(player, amount);
            
            if (!space.canFit()) {
                updateTransactionStatus(txHash, "REJECTED", "Inventory space changed");
                return TransactionResult.rejected(t("transaction.error.space_changed", "Inventory space changed - please try again"));
            }
            
                        // Withdraw from bank (this is atomic in EcotaleAPI)
            boolean withdrawn = EcotaleAPI.withdraw(playerUuid, (double) amount, "TX_WITHDRAW:" + txHash);
            if (!withdrawn) {
                updateTransactionStatus(txHash, "REJECTED", "Bank withdrawal failed");
                return TransactionResult.rejected(t("transaction.error.bank_withdraw_failed", "Bank withdrawal failed"));
            }
            
            // Give coins to player
            boolean given = CoinManager.giveCoins(player, amount);
            
            if (!given) {
                // CRITICAL: Put money back in bank immediately
                EcotaleAPI.deposit(playerUuid, (double) amount, "TX_ROLLBACK:" + txHash);
                updateTransactionStatus(txHash, "ROLLED_BACK_TO_BANK", 
                    "Could not deliver coins - value returned to bank");
                
                return TransactionResult.failedButSafe(
                    t("transaction.error.delivery_failed", 
                    "Could not deliver coins - your {0} remains safely in your bank", formatValue(amount)),
                    txHash
                );
            }
            
                        updateTransactionStatus(txHash, "COMMITTED", null);
            
            return TransactionResult.success(
                t("transaction.success.withdraw", "Withdrew {0} from bank", formatValue(amount)),
                txHash
            );
            
        } finally {
            lock.unlock();
        }
    }
    
    /**
     * Generate unique transaction hash for withdraw operations.
     */
    private static String generateWithdrawTxHash(UUID playerUuid, long amount) {
        String data = playerUuid.toString() + "|WITHDRAW|" + 
                     System.nanoTime() + "|" +
                     amount + "|" +
                     UUID.randomUUID().toString(); // Nonce
        
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(data.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return "W" + hexString.toString().substring(0, 15); // W prefix for withdraw
        } catch (NoSuchAlgorithmException e) {
            return "W" + UUID.randomUUID().toString().substring(0, 15);
        }
    }
    
    /**
     * Execute a secure bank deposit.
     * Tracks balance before/after to deposit exactly what was taken.
     * If coins are removed mid-transaction, only the actual taken amount is deposited.
     */
    public static TransactionResult executeSecureDeposit(
            @Nonnull Player player,
            @Nonnull UUID playerUuid,
            long requestedAmount) {
        
                String txHash = generateDepositTxHash(playerUuid, requestedAmount);
        
        // Check for replay attack
        if (transactionLog.containsKey(txHash)) {
            return TransactionResult.rejected(t("transaction.error.duplicate", "Duplicate transaction detected"));
        }
        
        // Validate amount
        if (requestedAmount <= 0) {
            return TransactionResult.rejected(t("transaction.error.invalid_amount", "Invalid amount"));
        }
        
        // Get player lock for isolation
        ReentrantLock lock = playerLocks.computeIfAbsent(playerUuid, k -> new ReentrantLock());
        lock.lock();
        
        try {
                        long balanceBefore = CoinManager.countCoins(player);
            
            if (balanceBefore < requestedAmount) {
                return TransactionResult.rejected(t("transaction.error.insufficient_funds", 
                    "Insufficient pocket balance (have {0}, need {1})", formatValue(balanceBefore), formatValue(requestedAmount)));
            }
            
            // Note: Bank limit validation should be done before calling this method
            
            // === PHASE 3: LOG PENDING ===
            TransactionRecord record = new TransactionRecord(
                txHash, playerUuid, "DEPOSIT", "PENDING",
                null, 0, null, requestedAmount,
                requestedAmount, null
            );
            transactionLog.put(txHash, record);
            
                        // Take coins for deposit - rounds up if exact change can't be made
            // This returns the ACTUAL amount taken (may be more than requested)
            long actuallyTaken = CoinManager.takeCoinsForDeposit(player, requestedAmount);
            
            if (actuallyTaken <= 0) {
                updateTransactionStatus(txHash, "REJECTED", "Could not take any coins");
                return TransactionResult.rejected(t("transaction.error.take_failed", "Could not take coins from inventory"));
            }
            
            // === PHASE 5: DEPOSIT EXACTLY WHAT WAS TAKEN ===
            EcotaleAPI.deposit(playerUuid, (double) actuallyTaken, "TX_DEPOSIT:" + txHash);
            
            // Update transaction status
            updateTransactionStatus(txHash, "COMMITTED", null);

            if (actuallyTaken == requestedAmount) {
                // Exact amount deposited
                return TransactionResult.success(
                    t("transaction.success.deposit", "Deposited {0} to bank", formatValue(actuallyTaken)),
                    txHash
                );
            } else if (actuallyTaken > requestedAmount) {
                // Rounded up (couldn't make exact change)
                return TransactionResult.success(
                    t("transaction.success.deposit.rounded", "Deposited {0} to bank (rounded up from {1})",
                        formatValue(actuallyTaken), formatValue(requestedAmount)),
                    txHash
                );
            } else {
                // Less than requested (shouldn't happen with new logic, but handle gracefully)
                return TransactionResult.success(
                    t("transaction.success.deposit.partial", "Deposited {0} to bank", formatValue(actuallyTaken)),
                    txHash
                );
            }
            
        } finally {
            lock.unlock();
        }
    }
    
    /**
     * Generate unique transaction hash for deposit operations.
     */
    private static String generateDepositTxHash(UUID playerUuid, long amount) {
        String data = playerUuid.toString() + "|DEPOSIT|" + 
                     System.nanoTime() + "|" +
                     amount + "|" +
                     UUID.randomUUID().toString(); // Nonce
        
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(data.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return "D" + hexString.toString().substring(0, 15); // D prefix for deposit
        } catch (NoSuchAlgorithmException e) {
            return "D" + UUID.randomUUID().toString().substring(0, 15);
        }
    }
    
    /**
     * Generate unique transaction hash for replay protection.
     */
    private static String generateTxHash(UUID playerUuid, CoinType fromType, int amount, CoinType toType) {
        String data = playerUuid.toString() + "|" + 
                     System.nanoTime() + "|" +
                     fromType.name() + "|" +
                     amount + "|" +
                     toType.name() + "|" +
                     UUID.randomUUID().toString(); // Nonce
        
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(data.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString().substring(0, 16); // Short hash for readability
        } catch (NoSuchAlgorithmException e) {
            // Fallback to simple unique ID
            return UUID.randomUUID().toString().substring(0, 16);
        }
    }
    
    /**
     * Update transaction status in log.
     */
    private static void updateTransactionStatus(String txHash, String status, String errorMessage) {
        TransactionRecord old = transactionLog.get(txHash);
        if (old != null) {
            TransactionRecord updated = new TransactionRecord(
                old.txHash, old.playerUuid, old.type, status,
                old.fromCoin, old.fromAmount, old.toCoin, old.toAmount,
                old.escrowValue, errorMessage
            );
            transactionLog.put(txHash, updated);
            
            // Debug log for transactions
            com.ecotale.util.EcoLogger.debug("TX " + txHash + " -> " + status + 
                (errorMessage != null ? " (" + errorMessage + ")" : ""));
        }
    }
    
    /**
     * Format value for display.
     */
    private static String formatValue(long value) {
        if (value >= 1_000_000) {
            return String.format("%.2fM", value / 1_000_000.0);
        } else if (value >= 1_000) {
            return String.format("%.1fK", value / 1_000.0);
        }
        return String.valueOf(value);
    }
    
    /**
     * Get transaction log for admin inspection.
     */
    public static Map<String, TransactionRecord> getTransactionLog() {
        return new ConcurrentHashMap<>(transactionLog);
    }
    
    /**
     * Recovery on startup - find pending transactions.
     * Called from plugin onEnable.
     */
    public static void recoverPendingTransactions() {
        for (TransactionRecord record : transactionLog.values()) {
            if ("PENDING".equals(record.status)) {
                // This is a warning - always log
                com.ecotale.util.EcoLogger.warn("Found pending transaction " + record.txHash);
                com.ecotale.util.EcoLogger.warn("  Player: " + record.playerUuid);
                com.ecotale.util.EcoLogger.warn("  Escrow value: " + record.escrowValue);
                com.ecotale.util.EcoLogger.warn("  Transaction should have been rolled back to bank.");
                
                // Mark as recovered - the money is in the bank escrow
                updateTransactionStatus(record.txHash, "RECOVERED_TO_BANK", 
                    "Server crashed during transaction - funds safe in bank");
            }
        }
    }
}

