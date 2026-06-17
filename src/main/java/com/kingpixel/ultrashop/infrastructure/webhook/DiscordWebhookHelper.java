package com.kingpixel.ultrashop.infrastructure.webhook;

import com.kingpixel.ultrashop.UltraShop;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;

/**
 * Utility helper to send webhooks to Discord asynchronously.
 */
public final class DiscordWebhookHelper {

  private static final HttpClient CLIENT = HttpClient.newHttpClient();

  private DiscordWebhookHelper() {
  }

  /**
   * Sends a JSON payload to the specified webhook URL.
   */
  public static CompletableFuture<Void> sendWebhook(String url, String jsonPayload) {
    if (url == null || url.isBlank() || jsonPayload == null || jsonPayload.isBlank()) {
      return CompletableFuture.completedFuture(null);
    }
    try {
      HttpRequest request = HttpRequest.newBuilder()
        .uri(URI.create(url.trim()))
        .header("Content-Type", "application/json")
        .POST(HttpRequest.BodyPublishers.ofString(jsonPayload, StandardCharsets.UTF_8))
        .build();

      return CLIENT.sendAsync(request, HttpResponse.BodyHandlers.discarding())
        .thenAccept(response -> {
          int status = response.statusCode();
          if (status < 200 || status >= 300) {
            UltraShop.LOGGER.warn("Discord Webhook returned non-success code: " + status);
          }
        })
        .exceptionally(ex -> {
          UltraShop.LOGGER.error("Failed to send Discord Webhook to " + url, ex);
          return null;
        });
    } catch (Exception e) {
      UltraShop.LOGGER.error("Error creating Discord Webhook request", e);
      return CompletableFuture.failedFuture(e);
    }
  }

  /**
   * Helper to build a clean JSON embed.
   */
  public static String buildEmbedJson(String title, String description, int color) {
    return "{"
      + "\"embeds\": [{"
      + "\"title\": \"" + escapeJson(title) + "\","
      + "\"description\": \"" + escapeJson(description) + "\","
      + "\"color\": " + color
      + "}]"
      + "}";
  }

  private static String escapeJson(String raw) {
    if (raw == null) return "";
    return raw.replace("\\", "\\\\")
      .replace("\"", "\\\"")
      .replace("\b", "\\b")
      .replace("\f", "\\f")
      .replace("\n", "\\n")
      .replace("\r", "\\r")
      .replace("\t", "\\t");
  }
}
