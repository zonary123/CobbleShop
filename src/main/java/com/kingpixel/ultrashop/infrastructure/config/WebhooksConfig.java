package com.kingpixel.ultrashop.infrastructure.config;

import lombok.Data;

/**
 * Webhooks configuration POJO.
 */
@Data
public class WebhooksConfig {
  private String rotationWebhookUrl = "";
  private String maintenanceWebhookUrl = "";
}
