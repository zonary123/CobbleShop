package com.kingpixel.ultrashop.infrastructure.web;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.kingpixel.ultrashop.UltraShop;
import com.kingpixel.ultrashop.domain.model.ProductStats;
import com.kingpixel.ultrashop.domain.service.StatsService;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.eclipse.jetty.server.HttpConfiguration;
import org.eclipse.jetty.server.HttpConnectionFactory;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.servlet.DefaultServlet;
import org.eclipse.jetty.servlet.FilterHolder;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.util.thread.QueuedThreadPool;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Embedded Jetty HTTP server for the UltraShop statistics dashboard.
 * Serves static files from classpath {@code /web/} and exposes JSON API endpoints.
 *
 * <p>Security features:
 * <ul>
 *   <li>Bearer-token authentication for all {@code /api/*} endpoints</li>
 *   <li>Per-IP rate limiting (configurable, default 30 req/min)</li>
 *   <li>Strict security headers (CSP, X-Frame-Options, etc.)</li>
 *   <li>Bounded thread pool &amp; connection limits</li>
 *   <li>Input validation on all query parameters</li>
 * </ul>
 */
public final class DashboardHttpServer {

  private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
  private static final int DEFAULT_DAYS = 30;

  // Rate limiting: max requests per IP within the time window
  private static final int RATE_LIMIT_MAX_REQUESTS = 30;
  private static final long RATE_LIMIT_WINDOW_MS = 60_000L;

  // Max parameter lengths
  private static final int MAX_QUERY_LENGTH = 36; // UUID max length
  private static final int MAX_THREAD_POOL = 10;
  private static final int MIN_THREAD_POOL = 2;
  private static final int IDLE_TIMEOUT_MS = 30_000;

  private final Server server;
  private final int port;
  private final String password;

  /** Per-IP rate-limit tracking: IP → list of request timestamps. */
  private final ConcurrentHashMap<String, List<Long>> rateLimitMap = new ConcurrentHashMap<>();

  public DashboardHttpServer(int port, String password) {
    this.port = port;
    this.password = requirePassword(password);
    // Bounded thread pool — prevents thread exhaustion attacks
    QueuedThreadPool threadPool = new QueuedThreadPool(MAX_THREAD_POOL, MIN_THREAD_POOL);
    threadPool.setName("ultrashop-web");
    this.server = new Server(threadPool);
  }

  private static String requirePassword(String password) {
    if (password == null || password.isBlank()) {
      throw new IllegalArgumentException("Dashboard API password must be configured and non-blank");
    }
    return password.trim();
  }

  /**
   * Starts the Jetty server with security filters, API servlets, static file serving,
   * and SPA fallback.
   */
  public void start() {
    try {
      configureConnector();

      ServletContextHandler context = new ServletContextHandler();
      context.setContextPath("/");

      // ── Security filters (order matters — outermost first) ──
      registerSecurityHeadersFilter(context);
      registerAuthenticationFilter(context);
      registerRateLimitFilter(context);

      // API servlets
      context.addServlet(new ServletHolder(new StatsServlet()), "/api/stats");
      context.addServlet(new ServletHolder(new PlayersServlet()), "/api/players");
      context.addServlet(new ServletHolder(new PlayerServlet()), "/api/player");

      // Static files from classpath /web/
      registerStaticFiles(context);

      // SPA fallback
      registerSpaFallback(context);

      server.setHandler(context);
      server.start();
      UltraShop.LOGGER.info("[Web] UltraShop Dashboard started on port {}", port);
    } catch (Exception e) {
      UltraShop.LOGGER.error("[Web] Failed to start dashboard on port {}", port, e);
    }
  }

  /**
   * Stops the Jetty server.
   */
  public void stop() {
    try {
      server.stop();
      UltraShop.LOGGER.info("[Web] UltraShop Dashboard stopped");
    } catch (Exception e) {
      UltraShop.LOGGER.error("[Web] Error stopping dashboard", e);
    }
  }

  // ── Connector ──

  private void configureConnector() {
    HttpConfiguration httpConfig = new HttpConfiguration();
    httpConfig.setRequestHeaderSize(8192);
    httpConfig.setResponseHeaderSize(8192);
    httpConfig.setSendServerVersion(false);
    httpConfig.setSendDateHeader(true);

    ServerConnector connector = new ServerConnector(server, new HttpConnectionFactory(httpConfig));
    connector.setPort(port);
    connector.setIdleTimeout(IDLE_TIMEOUT_MS);
    // Limit max concurrent connections to prevent resource exhaustion
    connector.setAcceptQueueSize(20);
    server.addConnector(connector);
  }

  // ── Security Filters ──

  /**
   * Adds security headers to ALL responses:
   * CSP, X-Frame-Options, X-Content-Type-Options, Referrer-Policy.
   */
  private void registerSecurityHeadersFilter(ServletContextHandler context) {
    FilterHolder securityHeaders = new FilterHolder((request, response, chain) -> {
      HttpServletResponse resp = (HttpServletResponse) response;
      resp.setHeader("X-Content-Type-Options", "nosniff");
      resp.setHeader("X-Frame-Options", "DENY");
      resp.setHeader("Referrer-Policy", "strict-origin-when-cross-origin");
      resp.setHeader("Permissions-Policy", "camera=(), microphone=(), geolocation=()");
      resp.setHeader("Content-Security-Policy",
        "default-src 'self'; " +
          "script-src 'self' https://cdn.jsdelivr.net https://cdn.tailwindcss.com 'unsafe-inline'; " +
          "style-src 'self' https://fonts.googleapis.com 'unsafe-inline'; " +
          "font-src 'self' https://fonts.gstatic.com; " +
          "img-src 'self' https://mc-heads.net data:; " +
          "connect-src 'self'"
      );
      chain.doFilter(request, response);
    });
    context.addFilter(securityHeaders, "/*", EnumSet.of(DispatcherType.REQUEST));
  }

  /**
   * Authenticates all {@code /api/*} requests using Bearer token.
   * Returns {@code 401 Unauthorized} if the token does not match.
   * If no password is configured (empty), authentication is skipped.
   */
  private void registerAuthenticationFilter(ServletContextHandler context) {
    FilterHolder authFilter = new FilterHolder((request, response, chain) -> {
      HttpServletRequest req = (HttpServletRequest) request;
      HttpServletResponse resp = (HttpServletResponse) response;
      String path = req.getRequestURI();

      // Only protect API endpoints
      if (!path.startsWith("/api/")) {
        chain.doFilter(request, response);
        return;
      }

      // Check Authorization: Bearer <token>
      String authHeader = req.getHeader("Authorization");
      if (authHeader != null && authHeader.startsWith("Bearer ")) {
        String token = authHeader.substring(7).trim();
        if (password.equals(token)) {
          chain.doFilter(request, response);
          return;
        }
      }

      // Authentication failed
      resp.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
      resp.setHeader("WWW-Authenticate", "Bearer");
      sendJson(resp, Map.of("error", "Unauthorized — provide a valid token"));
    });
    context.addFilter(authFilter, "/*", EnumSet.of(DispatcherType.REQUEST));
  }

  /**
   * Rate-limits {@code /api/*} requests per client IP.
   * Uses a sliding window: MAX_REQUESTS within WINDOW_MS.
   * Returns {@code 429 Too Many Requests} when exceeded.
   */
  private void registerRateLimitFilter(ServletContextHandler context) {
    FilterHolder rateLimitFilter = new FilterHolder((request, response, chain) -> {
      HttpServletRequest req = (HttpServletRequest) request;
      HttpServletResponse resp = (HttpServletResponse) response;
      String path = req.getRequestURI();

      // Only rate-limit API endpoints
      if (!path.startsWith("/api/")) {
        chain.doFilter(request, response);
        return;
      }

      String clientIp = getClientIp(req);
      long now = System.currentTimeMillis();

      List<Long> timestamps = rateLimitMap.computeIfAbsent(clientIp, k -> Collections.synchronizedList(new ArrayList<>()));

      // Remove expired timestamps outside the window
      synchronized (timestamps) {
        timestamps.removeIf(t -> (now - t) > RATE_LIMIT_WINDOW_MS);

        if (timestamps.size() >= RATE_LIMIT_MAX_REQUESTS) {
          resp.setStatus(429); // Too Many Requests
          resp.setHeader("Retry-After", String.valueOf(RATE_LIMIT_WINDOW_MS / 1000));
          sendJson(resp, Map.of("error", "Rate limit exceeded — try again later"));
          return;
        }
        timestamps.add(now);
      }

      chain.doFilter(request, response);
    });
    context.addFilter(rateLimitFilter, "/*", EnumSet.of(DispatcherType.REQUEST));

    // Periodically clean up stale IPs to prevent memory leak
    Thread cleanupThread = new Thread(() -> {
      while (server.isRunning()) {
        try {
          Thread.sleep(RATE_LIMIT_WINDOW_MS * 2);
          long now = System.currentTimeMillis();
          rateLimitMap.entrySet().removeIf(entry -> {
            List<Long> ts = entry.getValue();
            synchronized (ts) {
              ts.removeIf(t -> (now - t) > RATE_LIMIT_WINDOW_MS);
              return ts.isEmpty();
            }
          });
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
          break;
        }
      }
    }, "ultrashop-ratelimit-cleanup");
    cleanupThread.setDaemon(true);
    cleanupThread.start();
  }

  // ── Static Files & SPA ──

  private void registerStaticFiles(ServletContextHandler context) {
    String webRoot = Objects.requireNonNull(
      getClass().getClassLoader().getResource("ultrashop-web"),
      "ultrashop-web resources not found on classpath"
    ).toExternalForm();
    context.setResourceBase(webRoot);
    context.addServlet(DefaultServlet.class, "/");
  }

  private void registerSpaFallback(ServletContextHandler context) {
    FilterHolder spaFallback = new FilterHolder(new Filter() {
      @Override
      public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
        throws IOException, ServletException {
        HttpServletRequest req = (HttpServletRequest) request;
        HttpServletResponse resp = (HttpServletResponse) response;
        String path = req.getRequestURI();

        if (path.startsWith("/api/") || resourceExists(path)) {
          chain.doFilter(request, response);
          return;
        }

        resp.setContentType("text/html");
        try (InputStream input = getClass().getClassLoader().getResourceAsStream("ultrashop-web/index.html")) {
          if (input == null) {
            throw new IOException("ultrashop-web/index.html not found");
          }
          resp.getWriter().write(new String(input.readAllBytes(), StandardCharsets.UTF_8));
        }
      }

      private boolean resourceExists(String path) {
        return getClass().getClassLoader().getResource("ultrashop-web" + path) != null;
      }
    });
    context.addFilter(spaFallback, "/*", EnumSet.of(DispatcherType.REQUEST));
  }

  // ── Helpers ──

  /**
   * Extracts client IP from the socket address only.
   * Do not trust proxy headers unless a trusted reverse-proxy mode is implemented.
   */
  private static String getClientIp(HttpServletRequest req) {
    return req.getRemoteAddr();
  }

  private static int parseDays(HttpServletRequest req) {
    String daysStr = req.getParameter("days");
    if (daysStr != null) {
      try {
        return Math.clamp(Integer.parseInt(daysStr), 1, 365);
      } catch (NumberFormatException ignored) {
      }
    }
    return DEFAULT_DAYS;
  }

  /**
   * Validates and sanitizes the player query parameter.
   *
   * @return the sanitized query string, or {@code null} if invalid.
   */
  private static String validatePlayerQuery(String q) {
    if (q == null || q.isBlank()) return null;
    q = q.trim();
    if (q.length() > MAX_QUERY_LENGTH) return null;
    // Only allow alphanumeric, underscores, and hyphens (valid for UUIDs and MC usernames)
    if (!q.matches("^[a-zA-Z0-9_-]+$")) return null;
    return q;
  }

  private static void sendJson(HttpServletResponse resp, Object data) throws IOException {
    resp.setContentType("application/json");
    resp.setCharacterEncoding("UTF-8");
    resp.getWriter().write(GSON.toJson(data));
  }

  // ── API Servlets ──

  /**
   * {@code GET /api/stats?days=N} — server totals, all products, shops, daily timeseries.
   */
  private static class StatsServlet extends HttpServlet {
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
      try {
        int days = parseDays(req);
        Map<String, Object> response = new LinkedHashMap<>();

        StatsService.ServerTotals totals = StatsService.getServerTotals(days);
        Map<String, Object> serverMap = new LinkedHashMap<>();
        serverMap.put("totalTransactions", totals.totalTransactions);
        serverMap.put("uniquePlayers", totals.uniquePlayers.size());
        serverMap.put("totalRevenue", totals.totalRevenue);
        serverMap.put("totalPayout", totals.totalPayout);
        serverMap.put("netProfit", totals.getNetProfit());
        response.put("server", serverMap);

        List<ProductStats> products = StatsService.getProductStats(days);
        List<Map<String, Object>> productList = new ArrayList<>();
        for (ProductStats ps : products) {
          Map<String, Object> pm = new LinkedHashMap<>();
          pm.put("shopId", ps.getShopId());
          pm.put("productId", ps.getProductId());
          pm.put("totalBought", ps.getTotalBought());
          pm.put("totalSold", ps.getTotalSold());
          pm.put("revenue", ps.getTotalRevenue());
          pm.put("payout", ps.getTotalPayout());
          pm.put("netProfit", ps.getNetProfit());
          pm.put("uniqueBuyers", ps.getUniqueBuyers().size());
          pm.put("uniqueSellers", ps.getUniqueSellers().size());
          pm.put("uniquePlayers", ps.getUniquePlayers());
          productList.add(pm);
        }
        response.put("products", productList);

        Map<String, StatsService.ShopAggregate> shopStats = StatsService.getShopStats(days);
        List<Map<String, Object>> shopList = new ArrayList<>();
        for (var entry : shopStats.entrySet()) {
          var s = entry.getValue();
          Map<String, Object> sm = new LinkedHashMap<>();
          sm.put("shopId", entry.getKey());
          sm.put("totalTransactions", s.totalTransactions);
          sm.put("revenue", s.revenue);
          sm.put("payout", s.payout);
          sm.put("netProfit", s.getNetProfit());
          sm.put("uniquePlayers", s.uniquePlayers.size());
          shopList.add(sm);
        }
        response.put("shops", shopList);

        List<StatsService.DailyStats> daily = StatsService.getDailyStats(days);
        List<Map<String, Object>> dailyList = new ArrayList<>();
        for (var d : daily) {
          Map<String, Object> dm = new LinkedHashMap<>();
          dm.put("date", d.date);
          dm.put("revenue", d.revenue);
          dm.put("payout", d.payout);
          dm.put("transactions", d.transactions);
          dailyList.add(dm);
        }
        response.put("daily", dailyList);

        sendJson(resp, response);
      } catch (Exception e) {
        UltraShop.LOGGER.error("[Web] Error processing /api/stats", e);
        resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        sendJson(resp, Map.of("error", "Internal server error"));
      }
    }
  }

  /**
   * {@code GET /api/players?days=N} — all player rankings.
   */
  private static class PlayersServlet extends HttpServlet {
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
      try {
        int days = parseDays(req);
        List<StatsService.PlayerRanking> rankings = StatsService.getPlayerRanking(days);
        List<Map<String, Object>> result = new ArrayList<>();
        for (var r : rankings) {
          Map<String, Object> pm = new LinkedHashMap<>();
          pm.put("playerUuid", r.playerUuid.toString());
          pm.put("playerName", r.playerName != null ? r.playerName : r.playerUuid.toString());
          pm.put("totalBought", r.totalBought);
          pm.put("totalSold", r.totalSold);
          pm.put("totalSpent", r.totalSpent);
          pm.put("totalEarned", r.totalEarned);
          pm.put("transactions", r.totalBought + r.totalSold);
          result.add(pm);
        }
        sendJson(resp, result);
      } catch (Exception e) {
        UltraShop.LOGGER.error("[Web] Error processing /api/players", e);
        resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        sendJson(resp, Map.of("error", "Internal server error"));
      }
    }
  }

  /**
   * {@code GET /api/player?q=name_or_uuid&days=N} — single player detail.
   */
  private static class PlayerServlet extends HttpServlet {
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
      try {
        int days = parseDays(req);
        String q = validatePlayerQuery(req.getParameter("q"));

        if (q == null) {
          resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
          sendJson(resp, Map.of("error", "Missing or invalid 'q' parameter (UUID or player name, max 36 chars, alphanumeric only)"));
          return;
        }

        UUID uuid = null;
        try {
          uuid = UUID.fromString(q);
        } catch (IllegalArgumentException ignored) {
        }

        StatsService.PlayerDetail detail;
        if (uuid != null) {
          detail = StatsService.getPlayerDetail(uuid, days);
        } else {
          List<StatsService.PlayerRanking> rankings = StatsService.getPlayerRanking(days);
          UUID found = rankings.stream()
            .filter(r -> r.playerName != null && r.playerName.equalsIgnoreCase(q))
            .map(r -> r.playerUuid)
            .findFirst().orElse(null);
          if (found == null) {
            resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
            sendJson(resp, Map.of("error", "Player not found"));
            return;
          }
          detail = StatsService.getPlayerDetail(found, days);
        }

        Map<String, Object> response = new LinkedHashMap<>();

        Map<String, Object> agg = new LinkedHashMap<>();
        agg.put("playerUuid", detail.aggregate.playerUuid.toString());
        agg.put("playerName", detail.playerName != null ? detail.playerName : detail.aggregate.playerUuid.toString());
        agg.put("totalBought", detail.aggregate.totalBought);
        agg.put("totalSold", detail.aggregate.totalSold);
        agg.put("totalSpent", detail.aggregate.totalSpent);
        agg.put("totalEarned", detail.aggregate.totalEarned);
        response.put("aggregate", agg);

        List<Map<String, Object>> products = new ArrayList<>();
        for (var ps : detail.productStats) {
          Map<String, Object> pm = new LinkedHashMap<>();
          pm.put("shopId", ps.shopId);
          pm.put("productId", ps.productId);
          pm.put("bought", ps.totalBought);
          pm.put("sold", ps.totalSold);
          pm.put("spent", ps.totalSpent);
          pm.put("earned", ps.totalEarned);
          products.add(pm);
        }
        response.put("products", products);

        sendJson(resp, response);
      } catch (Exception e) {
        UltraShop.LOGGER.error("[Web] Error processing /api/player", e);
        resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        sendJson(resp, Map.of("error", "Internal server error"));
      }
    }
  }
}
