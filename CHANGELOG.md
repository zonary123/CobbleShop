# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added

- **Sealed Shop hierarchy**: `Shop` is now a sealed interface with three concrete subtypes
  (`NormalShop`, `CategoryShop`, `RotationShop`) under `domain.model.shop`. Replaces the legacy
  god-class. Polymorphic dispatch via `ShopVisitor` pattern.
- **Sealed Scheduler hierarchy**: `Scheduler` sealed interface with `CronScheduler` and
  `DurationScheduler` implementations. CRON is the new default (deterministic across restarts).
- **Per-shop config Value Objects**: `DisplayConfig`, `EconomyConfig`, `ConditionsConfig`,
  `SoundConfig` — each focused on a single concern, builder-friendly via Lombok.
- **Transparent JSON migration**: `ShopTypeAdapterFactory` auto-detects legacy shop JSON and
  bridges to the new typed hierarchy at load time. No manual migration needed.

### Changed

- **JSON shop file format**: New format uses a `displayConfig` sub-object and an explicit
  `type` discriminator. Old flat format is still readable on first load and gets rewritten
  on save.

### ⚠️ Breaking (planned for next release)

> **Heads-up to server admins**: The legacy `com.kingpixel.ultrashop.domain.model.Shop`
> god-class will be removed in the upcoming release. The new sealed hierarchy under
> `domain.model.shop` is the only supported model from there onward.
>
> **What you must do BEFORE updating**:
> 1. **Backup your `shop/` folder** (the entire `ultrashop/shop/*.json` tree).
> 2. After update, the mod auto-rewrites every shop JSON to the new format on first load.
> If a file fails to convert, the mod logs the offending shop id and skips it — your other
> shops keep working.
> 3. **Recommended sanity check**: open each shop in-game with `/shop edit <id>` and confirm
> that products, rotation schedules, and sub-shop links survived the conversion.
>
> **What stays the same**: in-game GUIs, commands, permissions, economies, transaction
> history, web dashboard. Only the on-disk JSON shape changes — and only on the way out.

### Migration plan

Tracked in `docs/refactor/LEGACY_SHOP_REMOVAL_PLAN.md`. Eight atomic, mergeable steps:
read-only consumers → `DataShop` → editor → `ConfigLoader` → delete legacy.

## [1.5.0] - 2026-04-19

### Added

- **Statistics System**: In-game `/shop stats` command with paginated GUI showing server overview, player stats, shop
  breakdown, and top products.
- **Web Dashboard**: Embedded Jetty web server with real-time analytics dashboard.
    - Overview tab with cards, daily revenue/payouts chart, revenue by shop doughnut chart, and top 10 products.
    - Products tab with search, shop filter, sort options, min transactions filter, and pagination.
    - Players tab with player lookup (by name or UUID), per-player product breakdown, and top players ranking.
    - Global product statistics cards (most bought, most sold, highest revenue, avg revenue).
    - Configurable port (`webDashboardPort`) and optional Basic Auth (`webDashboardPassword`).
    - Auto-refresh every 60 seconds.
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

## [1.0.0] - 2025-12-01

- "CobbleShop" -> "UltraShop" renaming.

### Added

- New format cooldown in dynamic shop. The new format is "1y 1mo 1w 1d 2h 30m 15s".
- Checker that looks for a similar UUID in another product to generate a new UUID and avoid cooldown conflicts.
- Shops now have an option to notify the rotating products when they are about to rotate.

### Fixed

- Fixed minor bugs in the initial release.
- Fixed async menu opening.
