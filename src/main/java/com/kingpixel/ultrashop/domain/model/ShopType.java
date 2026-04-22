package com.kingpixel.ultrashop.domain.model;

/**
 * Defines the operational type of a {@link Shop}.
 *
 * <ul>
 *   <li>{@link #NORMAL} — Static catalog. All declared {@code products} are always
 *       available. {@code rotationSchedule} and {@code subShops} are ignored.</li>
 *   <li>{@link #CATEGORY} — Menu shop that exposes {@code subShops} (other shops as
 *       categories). {@code products} and {@code rotationSchedule} are ignored.</li>
 *   <li>{@link #ROTATION} — Dynamic catalog. Products are picked from the pool every
 *       rotation tick driven by {@link RotationSchedule#getCron() cron} (priority)
 *       or {@link RotationSchedule#getInterval() interval} (fallback).</li>
 * </ul>
 *
 * <p>If {@code type} is missing from the JSON it defaults to {@link #NORMAL}, but
 * {@link Shop#check()} will auto-promote based on the legacy fields:</p>
 * <ul>
 *   <li>{@code subShops} non-empty → {@link #CATEGORY}</li>
 *   <li>{@code rotationSchedule} present → {@link #ROTATION}</li>
 * </ul>
 */
public enum ShopType {
  NORMAL,
  CATEGORY,
  ROTATION
}

