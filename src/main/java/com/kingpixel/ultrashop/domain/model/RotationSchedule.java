package com.kingpixel.ultrashop.domain.model;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Handles the schedule for when a shop's dynamic products should rotate.
 * Decoupled from `Condition` which only determines if the shop is accessible.
 */
@Data
@NoArgsConstructor
public class RotationSchedule {

  /**
   * Evaluated if schedule is based on a relative cooldown (e.g. "4h", "30m").
   */
  private String interval;

  /**
   * Cron expression (e.g. "0 18 * * 5" for every Friday at 18:00).
   * Overrides `interval` if present.
   */
  private String cron;

  /**
   * The number of products to pick from the pool during a rotation.
   */
  private int amount;

  public RotationSchedule(String interval, int amount) {
    this.interval = interval;
    this.amount = amount;
  }
}
