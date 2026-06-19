# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

## [1.5.0] - 2026-06-17

### ⚠️ IMPORTANT: BACKUP & TEST THE MOD!

> [!WARNING]
> Before updating to version 1.5.0, you **must make a backup of your configuration files** (specifically the entire
`config.json` and all files inside the `shop/` folder).
> The mod automatically migrates older configurations to the new formats on boot. Keeping a backup ensures you can
> safely restore your files in case of any issues.

> [!IMPORTANT]
> This version is a complete rework of the mod. It is crucial to test it thoroughly before deploying it to production. Please report any issues or bugs you find!

### Added

- **Discord Webhooks (Integration)**: Link Discord channels to your shops using Webhooks! The mod now automatically
  posts rich embeds in Discord when a shop rotates its stock or when its maintenance status changes.
- **Shop Maintenance Mode**: Temporarily close or open shops for players using the new command
  `/shop maintenance <shopId> <true/false>`. Players who try to access a shop under maintenance will see a friendly
  translation-supported message.
- **Flexible Product Cooldowns**: Migrate numerical cooldown limits to `ScheduleValue`, allowing server administrators
  to use time duration strings (e.g. `"30m"`, `"12h"`, `"2d"`) or cron expressions (e.g. `"0 0 * * *"`) for highly
  customizable shop reset intervals.
- **Inventory Stock System**: Added support for player-specific and server-wide product stock limits, compatible with
  both JSON files and MongoDB databases.
- **Web Analytics Dashboard**: Real-time web dashboard to visualize server overview charts, revenue summaries, top
  selling products, and detailed player transaction statistics, protected with credentials and security headers.
    - Overview tab with cards, daily revenue/payouts chart, revenue by shop doughnut chart, and top 10 products.
    - Products tab with search, shop filter, sort options, min transactions filter, and pagination.
    - Players tab with player lookup (by name or UUID), per-player product breakdown, and top players ranking.
    - Global product statistics cards (most bought, most sold, highest revenue, avg revenue).
    - Configurable port (`webDashboardPort`) and optional Basic Auth (`webDashboardPassword`).
    - Auto-refresh every 60 seconds.
- **Statistics System**: In-game `/shop stats` command with paginated GUI showing server overview, player stats, shop
  breakdown, and top products.
- **MongoDB Support**: Added `MongoUserRepository` and `MongoTransactionRepository` with automatic index creation and
  fallback to JSON on connection failure.
- **Product Conditions Editor**: Buy Conditions and Visibility Conditions buttons (slots 41/42) in the product editor
  with paginated submenus and add/delete functionality.
- **Shop Info Button**: Shows rotation countdown, products shown vs total, using dynamic/permanent templates.
- **Anti-Exploit Price Protection**: 3-layer protection (GUI block, `TransactionService.sell()` check, `sellAll()` skip)
  preventing sell > buy price exploits.
- **Enhanced Placeholders**: Added `%limit%`, `%bought%`, `%remaining%`, `%cooldown_time%` placeholders and
  `%removelimit%` filter to auto-remove lines when product has no limit. Works across all GUIs.
- **Product Context in GUIs**: Stock remaining (per player and global) now displayed in `ProductRenderer`,
  `ShopMenuBuilder`, and `BuyAndSellMenuBuilder`.
- **Automatic Configuration Repair**: The mod now automatically repairs missing, old, or corrupted shop config fields (
  such as missing close, back, and balance buttons or invalid menu sizes) when loading, rewriting them with safe default
  layouts.
- **Fully Translatable GUIs**: All remaining text strings, menu titles, button labels, and prompts in the shop editor
  menus can now be fully translated inside the language configuration file.

### Changed

- **Automatic Cooldown Conversion**: Older numerical cooldown configurations (representing minutes) are automatically
  converted to text representation (e.g., `60` -> `"60m"`), meaning no manual adjustments are required.
- **Default Shop Thematic Icons**: Replaced the generic poke ball display item fallback in default template shops with
  custom thematic items (grass block, wheat, compass, clock, trident, elytra, etc.) that represent each department.
- **Modernized Hex Color Styling**: Updated default configurations and messages to use modern, premium hex-based color
  formatting (e.g., gold accents, gray subtitles) instead of legacy Minecraft color codes.

### Fixed

- **Datapack Load Server Crash**: Fixed a critical server crash during boot when loading custom or incomplete shop
  configurations with missing economy definitions.
- **Category Menu Layout Crash**: Fixed a template pagination crash when opening category shops or the main menu by
  disabling auto-placement and correcting invalid layout coordinates.
- **Blank Cooldown Tooltips**: Fixed a bug where the cooldown tooltip was displayed blank instead of showing "Ready"
  when a product was not on active cooldown.

## [1.0.0] - 2025-12-01

- "CobbleShop" -> "UltraShop" renaming.

### Added

- New format cooldown in dynamic shop. The new format is "1y 1mo 1w 1d 2h 30m 15s".
- Checker that looks for a similar UUID in another product to generate a new UUID and avoid cooldown conflicts.
- Shops now have an option to notify the rotating products when they are about to rotate.

### Fixed

- Fixed minor bugs in the initial release.
- Fixed async menu opening.
