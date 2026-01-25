# EcotaleCoins - Vanilla Ingot Currency for Hytale

**Modified version using vanilla Hytale metal ingots as physical currency.** Banking system for the Ecotale economy using the game's existing metal bars.

![Version](https://img.shields.io/badge/version-1.0.0--vanilla--ingots-blue)
![Modified By](https://img.shields.io/badge/modified--by-mad--001-purple)
![Requires](https://img.shields.io/badge/requires-Ecotale-green)

## What's Different?

This is a **modified fork** that uses vanilla Hytale metal ingots instead of custom coin items:
- ✅ No custom textures or models needed
- ✅ Works with existing game items
- ✅ No first-time server restart required
- ✅ Players can mine ingots and deposit them directly

## Features

### Vanilla Ingot Currency
- **6 metal tiers** - COPPER, IRON, COBALT, GOLD, MITHRIL, ADAMANTITE bars
- **1:10 conversion rates** - Each tier worth 10x the previous (same as original)
- **Uses vanilla items** - `Ingredient_Bar_*` items already in the game
- **Optimal breakdown** - Large values auto-convert to highest denominations

### Bank System
- **Secure storage** - Bank balance backed by physical ingots
- **Deposit/Withdraw** - Convert between virtual balance and physical ingots
- **Exchange** - Convert between ingot denominations (e.g., 10 Iron → 1 Gold)
- **Consolidate** - Combine lower-tier ingots into higher ones

### API
- Full provider API for other plugins
- Compatible with Ecotale Core economy
- Secure transaction handling

## Installation

1. Install [Ecotale](https://github.com/Tera-bytez/Ecotale) first
2. Download `EcotaleCoins-1.0.0-vanilla-ingots.jar`
3. Place in your Hytale `mods/` folder
4. Start the server

**No restart required!** Since this uses vanilla items, there are no custom assets to load.

```
[EcotaleCoins] First-time setup detected. Restarting server to load assets...
```

After the restart, coins will work normally. This only happens once.

## Commands

| Command | Description | Permission |
|---------|-------------|------------|
| `/bank` | Open bank GUI | All players (Adventure mode) |
| `/bank deposit <amount\|all>` | Deposit coins to bank | All players (Adventure mode) |
| `/bank withdraw <amount\|all>` | Withdraw coins from bank | All players (Adventure mode) |

## Permissions

| Permission | Description | Default |
|------------|-------------|---------|
| `ecotale.ecotalecoins.command.bank` | Access to /bank commands | All players (Adventure mode) |

### Important: LuckPerms Override Behavior

**If you install LuckPerms**, commands will be blocked for all players until you grant permissions:

```bash
# Grant bank access to all players
/lp group default permission set ecotale.ecotalecoins.command.bank true

# Verify permission
/lp user <username> permission check ecotale.ecotalecoins.command.bank
```

---

## Customization

All coin assets are extracted to `mods/Ecotale_EcotaleCoins/` and can be modified.

### Texture Customization

Located in `Common/Items/Currency/Coins/`:

| File | Description |
|------|-------------|
| `Coin_Copper.png` | Copper coin texture (16x16) |
| `Coin_Iron.png` | Iron coin texture |
| `Coin_Cobalt.png` | Cobalt coin texture |
| `Coin_Gold.png` | Gold coin texture |
| `Coin_Mithril.png` | Mithril coin texture |
| `Coin_Adamantite.png` | Adamantite coin texture |

**To customize:** Replace any PNG with your own 64x64 texture and restart the server.

### Model Customization

| File | Description |
|------|-------------|
| `Coin.blockymodel` | Dropped coin 3D model |
| `Coin_Held.blockymodel` | Held/inventory coin model |

### Icon Customization

Located in `Common/Icons/Items/Coins/` - these appear in the inventory UI.

---

## Commands

| Command | Description |
|---------|-------------|
| `/bank` | Open the bank GUI |
| `/bank deposit <amount>` | Deposit coins to bank |
| `/bank withdraw <amount>` | Withdraw coins from bank |

## Permissions

| Permission | Description |
|------------|-------------|
| `ecotale.ecotalecoins.command.bank` | Access to /bank command |

> **Note:** This permission is **auto-generated** by Hytale's command system. You must grant it via LuckPerms for players to use the bank.

### LuckPerms Setup
```bash
# Grant bank access to all players
lp group default permission set ecotale.ecotalecoins.command.bank true
```

## Coin Values

| Coin | Base Value | Relative Value |
|------|------------|----------------|
| COPPER | 1 | 1 coin |
| IRON | 10 | 10 copper |
| COBALT | 100 | 10 iron |
| GOLD | 1,000 | 10 cobalt |
| MITHRIL | 10,000 | 10 gold |
| ADAMANTITE | 100,000 | 10 mithril |

## Configuration

> **Note:** EcotaleCoins does not have a config file. Coin values are defined in `CoinType.java` enum and cannot be changed without recompiling.

If you need configurable coin values, submit a feature request or modify `CoinType.java`:

```java
public enum CoinType {
    COPPER("Coin_Copper", 1, "Copper"),
    IRON("Coin_Iron", 10, "Iron"),
    COBALT("Coin_Cobalt", 100, "Cobalt"),
    GOLD("Coin_Gold", 1_000, "Gold"),
    MITHRIL("Coin_Mithril", 10_000, "Mithril"),
    ADAMANTITE("Coin_Adamantite", 100_000, "Adamantite");
    // ...
}
```


## API Usage

```java
import com.ecotale.api.EcotaleAPI;
import com.ecotale.api.PhysicalCoinsProvider;

// Check if coins addon is available
if (EcotaleAPI.isPhysicalCoinsAvailable()) {
    PhysicalCoinsProvider coins = EcotaleAPI.getPhysicalCoins();
    
    // Drop coins at entity position
    coins.dropCoinsAtEntity(entityRef, store, commandBuffer, 500L);
}
```

## Building from Source

**Requirements:** Place JARs in `libs/` folder:
- `hytale-server.jar` (Hytale dedicated server)
- `Ecotale-1.0.0.jar` (from Ecotale project)

```bash
./gradlew jar
```

Output: `build/libs/EcotaleCoins-1.0.0.jar`

## License

MIT License - 2026 Tera-bytez
