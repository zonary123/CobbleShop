package com.kingpixel.ultrashop.domain.service;

import com.kingpixel.ultrashop.ShopContext;
import com.kingpixel.ultrashop.domain.model.ActionShop;
import com.kingpixel.ultrashop.domain.model.ProductStats;
import com.kingpixel.ultrashop.domain.model.Transaction;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Aggregates transaction data into statistics.
 * Results are cached for 60 seconds to avoid re-reading files on every request.
 */
public final class StatsService {

  private static final long CACHE_TTL_MS = 60_000L;
  private static final DateTimeFormatter DAY_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    .withZone(ZoneId.systemDefault());

  private static volatile long lastCacheTime;
  private static volatile int lastCacheDays;
  private static volatile List<Transaction> cachedTransactions;

  private StatsService() {
  }

  // ── Core data ──

  private static synchronized List<Transaction> loadTransactions(int maxDays) {
    long now = System.currentTimeMillis();
    if (cachedTransactions != null && lastCacheDays == maxDays && (now - lastCacheTime) < CACHE_TTL_MS) {
      return cachedTransactions;
    }
    var repo = ShopContext.get().getRepositories();
    if (repo == null) return List.of();
    cachedTransactions = repo.getTransactionRepository().findAll(maxDays);
    lastCacheDays = maxDays;
    lastCacheTime = now;
    return cachedTransactions;
  }

  /**
   * Invalidate the cache (e.g. after reload).
   */
  public static synchronized void invalidateCache() {
    cachedTransactions = null;
  }

  // ── Product Stats ──

  /**
   * Returns stats per product, sorted by total revenue descending.
   */
  public static List<ProductStats> getProductStats(int maxDays) {
    List<Transaction> txs = loadTransactions(maxDays);
    Map<String, ProductStats> map = new LinkedHashMap<>();

    for (Transaction tx : txs) {
      String key = tx.getShopId() + "::" + tx.getProductId();
      map.computeIfAbsent(key, k -> new ProductStats(tx.getShopId(), tx.getProductId()))
        .accumulate(tx);
    }

    return map.values().stream()
      .sorted(Comparator.comparing(ProductStats::getTotalRevenue).reversed())
      .collect(Collectors.toList());
  }

  /**
   * Top N products by revenue.
   */
  public static List<ProductStats> getTopProducts(int maxDays, int limit) {
    List<ProductStats> all = getProductStats(maxDays);
    return all.subList(0, Math.min(limit, all.size()));
  }

  // ── Shop-level aggregates ──

  /**
   * Aggregate stats grouped by shopId.
   */
  public static Map<String, ShopAggregate> getShopStats(int maxDays) {
    List<Transaction> txs = loadTransactions(maxDays);
    Map<String, ShopAggregate> map = new LinkedHashMap<>();

    for (Transaction tx : txs) {
      map.computeIfAbsent(tx.getShopId(), ShopAggregate::new).accumulate(tx);
    }
    return map;
  }

  // ── Daily revenue timeseries ──

  /**
   * Returns daily revenue/payout for chart rendering.
   */
  public static List<DailyStats> getDailyStats(int maxDays) {
    List<Transaction> txs = loadTransactions(maxDays);
    Map<String, DailyStats> map = new TreeMap<>();

    for (Transaction tx : txs) {
      String day = DAY_FMT.format(Instant.ofEpochMilli(tx.getTimestamp()));
      map.computeIfAbsent(day, DailyStats::new).accumulate(tx);
    }
    return new ArrayList<>(map.values());
  }

  // ── Player Stats ──

  /**
   * Stats for a specific player.
   */
  public static PlayerAggregate getPlayerStats(UUID playerUuid, int maxDays) {
    List<Transaction> txs = loadTransactions(maxDays);
    PlayerAggregate agg = new PlayerAggregate(playerUuid);
    for (Transaction tx : txs) {
      if (tx.getPlayerUuid().equals(playerUuid)) {
        agg.accumulate(tx);
      }
    }
    return agg;
  }

  /**
   * Find a player by name (case-insensitive) and return their stats.
   */
  public static PlayerAggregate getPlayerStatsByName(String name, int maxDays) {
    List<Transaction> txs = loadTransactions(maxDays);
    UUID found = null;
    for (Transaction tx : txs) {
      if (tx.getPlayerName() != null && tx.getPlayerName().equalsIgnoreCase(name)) {
        found = tx.getPlayerUuid();
        break;
      }
    }
    if (found == null) return null;
    return getPlayerStats(found, maxDays);
  }

  /**
   * Returns all players ranked by spending, with name info.
   */
  public static List<PlayerRanking> getPlayerRanking(int maxDays) {
    List<Transaction> txs = loadTransactions(maxDays);
    Map<UUID, PlayerRanking> map = new LinkedHashMap<>();

    for (Transaction tx : txs) {
      map.computeIfAbsent(tx.getPlayerUuid(), id -> new PlayerRanking(id, tx.getPlayerName()))
        .accumulate(tx);
    }
    return new ArrayList<>(map.values());
  }

  /**
   * Detailed player stats including per-product breakdown.
   */
  public static PlayerDetail getPlayerDetail(UUID playerUuid, int maxDays) {
    List<Transaction> txs = loadTransactions(maxDays);
    PlayerAggregate agg = new PlayerAggregate(playerUuid);
    String playerName = null;
    Map<String, PlayerProductStats> products = new LinkedHashMap<>();

    for (Transaction tx : txs) {
      if (!tx.getPlayerUuid().equals(playerUuid)) continue;
      agg.accumulate(tx);
      if (playerName == null && tx.getPlayerName() != null) playerName = tx.getPlayerName();
      String key = tx.getShopId() + "::" + tx.getProductId();
      products.computeIfAbsent(key, k -> new PlayerProductStats(tx.getShopId(), tx.getProductId()))
        .accumulate(tx);
    }
    return new PlayerDetail(agg, playerName, new ArrayList<>(products.values()));
  }

  // ── Server totals ──

  /**
   * Quick server-wide totals.
   */
  public static ServerTotals getServerTotals(int maxDays) {
    List<Transaction> txs = loadTransactions(maxDays);
    ServerTotals totals = new ServerTotals();
    for (Transaction tx : txs) {
      totals.accumulate(tx);
    }
    return totals;
  }

  // ── Inner aggregate classes ──

  public static class ShopAggregate {
    public final String shopId;
    public int totalTransactions;
    public BigDecimal revenue = BigDecimal.ZERO;
    public BigDecimal payout = BigDecimal.ZERO;
    public final Set<UUID> uniquePlayers = new HashSet<>();

    public ShopAggregate(String shopId) { this.shopId = shopId; }

    public void accumulate(Transaction tx) {
      totalTransactions++;
      uniquePlayers.add(tx.getPlayerUuid());
      if (tx.getAction() == ActionShop.BUY) {
        revenue = revenue.add(tx.getValue());
      } else {
        payout = payout.add(tx.getValue());
      }
    }

    public BigDecimal getNetProfit() { return revenue.subtract(payout); }
  }

  public static class DailyStats {
    public final String date;
    public BigDecimal revenue = BigDecimal.ZERO;
    public BigDecimal payout = BigDecimal.ZERO;
    public int transactions;

    public DailyStats(String date) { this.date = date; }

    public void accumulate(Transaction tx) {
      transactions++;
      if (tx.getAction() == ActionShop.BUY) {
        revenue = revenue.add(tx.getValue());
      } else {
        payout = payout.add(tx.getValue());
      }
    }
  }

  public static class PlayerAggregate {
    public final UUID playerUuid;
    public int totalBought;
    public int totalSold;
    public BigDecimal totalSpent = BigDecimal.ZERO;
    public BigDecimal totalEarned = BigDecimal.ZERO;

    public PlayerAggregate(UUID playerUuid) { this.playerUuid = playerUuid; }

    public void accumulate(Transaction tx) {
      if (tx.getAction() == ActionShop.BUY) {
        totalBought += tx.getAmount();
        totalSpent = totalSpent.add(tx.getValue());
      } else {
        totalSold += tx.getAmount();
        totalEarned = totalEarned.add(tx.getValue());
      }
    }
  }

  public static class PlayerRanking {
    public final UUID playerUuid;
    public final String playerName;
    public int totalBought;
    public int totalSold;
    public BigDecimal totalSpent = BigDecimal.ZERO;
    public BigDecimal totalEarned = BigDecimal.ZERO;

    public PlayerRanking(UUID playerUuid, String playerName) {
      this.playerUuid = playerUuid;
      this.playerName = playerName;
    }

    public void accumulate(Transaction tx) {
      if (tx.getAction() == ActionShop.BUY) {
        totalBought += tx.getAmount();
        totalSpent = totalSpent.add(tx.getValue());
      } else {
        totalSold += tx.getAmount();
        totalEarned = totalEarned.add(tx.getValue());
      }
    }
  }

  public static class PlayerProductStats {
    public final String shopId;
    public final String productId;
    public int totalBought;
    public int totalSold;
    public BigDecimal totalSpent = BigDecimal.ZERO;
    public BigDecimal totalEarned = BigDecimal.ZERO;

    public PlayerProductStats(String shopId, String productId) {
      this.shopId = shopId;
      this.productId = productId;
    }

    public void accumulate(Transaction tx) {
      if (tx.getAction() == ActionShop.BUY) {
        totalBought += tx.getAmount();
        totalSpent = totalSpent.add(tx.getValue());
      } else {
        totalSold += tx.getAmount();
        totalEarned = totalEarned.add(tx.getValue());
      }
    }
  }

  public static class ServerTotals {
    public int totalTransactions;
    public BigDecimal totalRevenue = BigDecimal.ZERO;
    public BigDecimal totalPayout = BigDecimal.ZERO;
    public final Set<UUID> uniquePlayers = new HashSet<>();

    public void accumulate(Transaction tx) {
      totalTransactions++;
      uniquePlayers.add(tx.getPlayerUuid());
      if (tx.getAction() == ActionShop.BUY) {
        totalRevenue = totalRevenue.add(tx.getValue());
      } else {
        totalPayout = totalPayout.add(tx.getValue());
      }
    }

    public BigDecimal getNetProfit() { return totalRevenue.subtract(totalPayout); }
  }

  public static class PlayerDetail {
    public final PlayerAggregate aggregate;
    public final String playerName;
    public final List<PlayerProductStats> productStats;

    public PlayerDetail(PlayerAggregate aggregate, String playerName, List<PlayerProductStats> productStats) {
      this.aggregate = aggregate;
      this.playerName = playerName;
      this.productStats = productStats;
    }
  }
}

