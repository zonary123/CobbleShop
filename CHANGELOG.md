# Changelog

## [1.6.0] - 2026-07-04

### Added

- **MongoDB Shop Template Persistence**: Added central synchronization for shop configurations, templates, products, and categories across cross-server setups using MongoDB.
- **Admin/Replica Server Mode**: Introduced `adminServer` config setting. A designated admin server loads shops from local JSON files, updates MongoDB, and broadcasts changes, while replica servers read directly from MongoDB.

## [1.5.3] - 2026-07-03

### Added

- **GUILD Rotation Scope**: Added support for a new rotation scope `GUILD`. Shared rotation catalog is generated and persisted per-guild using CobbleUtils `GuildAPI`. If a player is not in a guild, fallback to player-specific catalog rotation.
- **In-Game Shop Settings Editor**: Added a dynamic "Shop Type" button (Compass) to cycle between `NORMAL`, `ROTATION`, and `CATEGORY` shop types dynamically, retaining products catalog across type promotions.
- **Guild Scope Toggle**: Included the `GUILD` scope in the rotation scope toggle cycle.

### Changed

- **Sub-Menu Navigation UX**: Upgraded sub-menus' "Close" buttons to act as "Back" buttons returning to the parent menu instead of closing the entire GUI.

### Fixed

- **Serialization Key Compatibility**: Renamed `productPool` to `products` inside `RotationShop` config files to share the catalog field structure with `NormalShop`, and provided backward compatibility in `RotationShopAdapter` to migrate legacy files automatically.

## [1.5.2] - 2026-07-02

### Added

- **Rotation Scope (`GLOBAL` / `PLAYER`)**: ROTATION shops now support `rotationScope` to choose how the catalog rotates:
  - **GLOBAL** (default) — one shared rotation for the entire server, persisted under `data/rotations/`.
  - **PLAYER** — each player gets their own rotation timer and product selection, persisted in their user data.
- **Rotation Scope Editor**: Added a **⟳ Rotation Scope** toggle in shop settings to switch between `GLOBAL` and `PLAYER` in-game.
- **Rotation Slots for Dynamic Shops**: ROTATION shops now support a `rotationSlots` list (`int[]`) to pin each rotated product to a fixed GUI slot. The 1st picked product uses `rotationSlots[0]`, the 2nd uses `rotationSlots[1]`, and so on. When set, fixed slots take priority over `autoPlace`.
- **Rotation Slots Editor**: Added a **⊞ Rotation Slots** button in the admin shop settings GUI (`/shop edit` → right-click shop → Shop Settings) to configure, add, or clear rotation slots in-game.
- **Config Documentation**: Documented `rotationSlots` and `rotationScope` in the embedded shop README. Default examples: `hourly_rotation` (GLOBAL + fixed slots) and `daily_specials` (`PLAYER`).

### Changed

- **Context-Aware Shop Editor**: Shop settings and product editor menus now only show options relevant to the shop type (e.g. rotation schedule, slots, scope, and announce only for `ROTATION`; rotation chance only in rotation shop pools; rotation schedule hidden for `CATEGORY`).
- **PLAYER Rotation Announcements**: When `rotationScope` is `PLAYER` and announce rotation is enabled, only the affected player is notified instead of broadcasting server-wide.
- **Web Dashboard Frontend**: Refactored the embedded analytics dashboard into modular JavaScript (`api.js`, `state.js`, `ui.js`, `charts.js`, `app.js`) with improved layout, styling, and client-side state management.

### Fixed

- **Dynamic Shop Rotation**: Fixed rotation state persistence by writing rotation files synchronously instead of asynchronously, preventing lost or stale rotation data on shutdown.
- **Dynamic Shop Pool Validation**: Empty `productPool` now returns no products instead of attempting a rotation. If the pool is smaller than `rotationAmount`, the amount is automatically adjusted with a warning.
- **Category Shop Layout**: Fixed category shops incorrectly forcing `autoPlace` off during config validation, which could break sub-shop pagination.

## [1.5.1] - 2026-06-23

### Added

- **Improved Pokémon Selection in Editor**: Added two options when adding a Pokémon product in the admin GUI editor:
  - **Chat Option**: Sends a clickable link that pre-fills a command with tab completions/suggestions from Cobblemon's `/pokegive` system (species, level, shiny status, ability, form, gender, nature, etc.).
  - **Party/PC Option**: Opens a GUI menu of the player's Party or PC (using `PartyPcMenu` from CobbleUtils) to select a Pokémon directly.

## [1.5.0] - 2026-06-17

### ⚠️ IMPORTANT: BACKUP & TEST THE MOD!

> [!WARNING]
> Before updating to version 1.5.0, you **must make a backup of your configuration files** (specifically the entire
> `config.json` and all files inside the `shop/` folder).
> The mod automatically migrates older configurations to the new formats on boot. Keeping a backup ensures you can
> safely restore your files in case of any issues.

> [!IMPORTANT]
> This version is a complete rework of the mod. It is crucial to test it thoroughly before deploying it to production. Please report any issues or bugs you find!

### Added

- **Multi-Level Sell Limits**: Configurable sell limits at three distinct levels with custom cooldowns to prevent market exploits:
  - **Product-Level Limits**: Set a limit on how much of a single product a player can sell using `sellMax` and `sellCooldown`.
  - **Shop-Level Limits**: Set daily sell earnings limits per shop using `dailySellLimits` (currency map) and `dailySellResetCooldown`.
  - **Transaction Scaledown**: Automatically scales down sales to only sell up to the remaining limit instead of completely blocking them.
  - **GUI Editor Integration**: Allows dynamically configuring product sell limits and shop daily sell limits within the `/shop editor` settings menus.
- **Interactive Sell GUI**: Added a chest-based drop-and-sell GUI accessible via `/sell` or `/sell gui` that automatically processes items on close and returns unsold contents safely to the player.
- **Purchase Transaction Feedback**: Added `messageSimpleBuy` to notify players in chat when they buy a product.
- **MongoDB Persistence**: Updated serialization to save and restore player-specific product and shop sell limits in MongoDB.

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
- **Fixed Loggers**: Fixed loggers that were printing the mod id in the logs.
- **Web Dashboard Classpath Resolution**: Fixed an issue where the embedded Jetty server failed to load static web resources inside JAR packaging due to directory resource lookup limitations.
- **Menu Sell Transaction Feedback**: Fixed a bug where selling an item via the GUI confirmation menu would exit silently without sending a notification when the player had no matching items in their inventory.

## [1.0.0] - 2025-12-01

- "CobbleShop" -> "UltraShop" renaming.

### Added

- New format cooldown in dynamic shop. The new format is "1y 1mo 1w 1d 2h 30m 15s".
- Checker that looks for a similar UUID in another product to generate a new UUID and avoid cooldown conflicts.
- Shops now have an option to notify the rotating products when they are about to rotate.

### Fixed

- Fixed minor bugs in the initial release.
- Fixed async menu opening.
