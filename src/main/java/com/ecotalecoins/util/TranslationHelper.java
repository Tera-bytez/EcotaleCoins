package com.ecotalecoins.util;

import com.ecotale.api.EcotaleAPI;
import com.hypixel.hytale.server.core.modules.i18n.I18nModule;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import org.checkerframework.checker.nullness.compatqual.NonNullDecl;
import org.checkerframework.checker.nullness.compatqual.NullableDecl;

/**
 * Optimized translation utility for all Ecotale GUIs.
 * 
 * Supports two modes (configured via UsePlayerLanguage in config):
 * - Server-wide: All players see same language (default)
 * - Per-player: Each player sees their client's language with fallback
 * 
 * Usage:
 *   // Server language (for broadcasts, logs)
 *   String text = TranslationHelper.t("gui.bank.title", "BANK");
 *   
 *   // Per-player language (respects UsePlayerLanguage setting)
 *   String text = TranslationHelper.t(playerRef, "gui.bank.title", "BANK");
 */
public class TranslationHelper {
    
    // Cached server language - invalidated only on config change
    private static volatile String cachedServerLanguage = null;
    
    /**
     * Get the server's configured default language (cached for performance).
     */
    public static String getServerLanguage() {
        String lang = cachedServerLanguage;
        if (lang == null) {
            lang = EcotaleAPI.getLanguage();
            cachedServerLanguage = lang;
        }
        return lang;
    }
    
    /**
     * Get the effective language for a player.
     * - If UsePlayerLanguage is enabled: returns player's client language
     * - Otherwise: returns server's configured language
     * 
     * @param playerRef The player (can be null for server default)
     * @return Language code (e.g., "en-US", "es-ES")
     */
    public static String getLanguageFor(@NullableDecl PlayerRef playerRef) {
        // If no player or per-player disabled, use server language
        if (playerRef == null || !EcotaleAPI.isUsePlayerLanguage()) {
            return getServerLanguage();
        }
        
        // Get player's client language
        String playerLang = playerRef.getLanguage();
        if (playerLang == null || playerLang.isEmpty()) {
            return getServerLanguage();
        }
        
        return playerLang;
    }
    
    /**
     * Invalidate the language cache.
     * Call this when language is changed in EcoAdminGui or config is reloaded.
     */
    public static void invalidateCache() {
        cachedServerLanguage = null;
    }
    
    // ═══════════════════════════════════════════════════════════════════
    // SERVER-WIDE TRANSLATIONS (for broadcasts, logs, etc.)
    // ═══════════════════════════════════════════════════════════════════
    
    /**
     * Get translated text using server's configured language.
     * Use this for broadcasts, logs, or when no player context.
     */
    public static String t(@NonNullDecl String key, @NonNullDecl String fallback) {
        return t(null, key, fallback);
    }
    
    /**
     * Get translated text with parameters using server's language.
     */
    public static String t(@NonNullDecl String key, @NonNullDecl String fallback, Object... args) {
        return t(null, key, fallback, args);
    }
    
    // ═══════════════════════════════════════════════════════════════════
    // PER-PLAYER TRANSLATIONS (respects UsePlayerLanguage setting)
    // ═══════════════════════════════════════════════════════════════════
    
    /**
     * Get translated text for a specific player.
     * If UsePlayerLanguage is enabled, uses player's client language.
     * Falls back to server language if translation not found in player's language.
     * 
     * @param playerRef The target player (null = server language)
     * @param key Translation key without "ecotale." prefix
     * @param fallback Default value if translation not found
     * @return Translated text
     */
    public static String t(@NullableDecl PlayerRef playerRef, @NonNullDecl String key, @NonNullDecl String fallback) {
        String fullKey = "ecotale." + key;
        String language = getLanguageFor(playerRef);
        
        // Try player's language first
        String value = I18nModule.get().getMessage(language, fullKey);
        
        // Fallback to server language if not found and languages differ
        if (value == null && !language.equals(getServerLanguage())) {
            value = I18nModule.get().getMessage(getServerLanguage(), fullKey);
        }
        
        return value != null ? value : fallback;
    }
    
    /**
     * Get translated text with parameters for a specific player.
     */
    public static String t(@NullableDecl PlayerRef playerRef, @NonNullDecl String key, @NonNullDecl String fallback, Object... args) {
        String template = t(playerRef, key, fallback);
        try {
            String result = template;
            for (int i = 0; i < args.length && i < 10; i++) {
                result = result.replace("{" + i + "}", String.valueOf(args[i]));
            }
            return result;
        } catch (Exception e) {
            return template;
        }
    }
    
    // ═══════════════════════════════════════════════════════════════════
    // UI HELPERS
    // ═══════════════════════════════════════════════════════════════════
    
    /**
     * Set translated text directly on UI element (server language).
     */
    public static void set(@NonNullDecl UICommandBuilder cmd, @NonNullDecl String selector,
                           @NonNullDecl String key, @NonNullDecl String fallback) {
        cmd.set(selector, t(key, fallback));
    }
    
    /**
     * Set translated text directly on UI element (player-specific).
     */
    public static void set(@NonNullDecl UICommandBuilder cmd, @NullableDecl PlayerRef playerRef,
                           @NonNullDecl String selector, @NonNullDecl String key, @NonNullDecl String fallback) {
        cmd.set(selector, t(playerRef, key, fallback));
    }
    
    // Legacy alias for backward compatibility
    public static String getLanguage() {
        return getServerLanguage();
    }
}

