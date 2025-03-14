package com.kingpixel.cobbleshop.models;

import lombok.Data;

import java.time.LocalDate;

/**
 * @author Carlos Varas Alonso - 07/03/2025 19:06
 */
@Data
public class DateRange {
  private LocalDate startDate;
  private LocalDate endDate;

  public DateRange(LocalDate startDate, LocalDate endDate) {
    this.startDate = startDate;
    this.endDate = endDate;
  }
}
