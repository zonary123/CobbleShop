package com.kingpixel.ultrashop.domain.model;

import lombok.Data;

import java.time.LocalDate;

/**
 * A date range used for calendar-based conditions (legacy support).
 */
@Data
public class DateRange {
  private LocalDate startDate;
  private LocalDate endDate;

  public DateRange() {
    this.startDate = LocalDate.now();
    this.endDate = LocalDate.now().plusWeeks(1);
  }

  public DateRange(LocalDate startDate, LocalDate endDate) {
    this.startDate = startDate;
    this.endDate = endDate;
  }

  public boolean isInRange() {
    LocalDate today = LocalDate.now();
    return !today.isBefore(startDate) && !today.isAfter(endDate);
  }
}

