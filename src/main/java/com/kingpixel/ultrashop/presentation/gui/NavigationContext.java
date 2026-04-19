package com.kingpixel.ultrashop.presentation.gui;

import com.kingpixel.ultrashop.domain.model.Shop;
import lombok.Data;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Manages navigation between shops/categories.
 * Replaces the error-prone Stack<Shop> with a safe breadcrumb.
 */
@Data
public class NavigationContext {
  private final Deque<Shop> breadcrumb = new ArrayDeque<>();

  public void push(Shop shop) {
    breadcrumb.push(shop);
  }

  public Shop current() {
    return breadcrumb.peek();
  }

  public Shop pop() {
    return breadcrumb.isEmpty() ? null : breadcrumb.pop();
  }

  public boolean canGoBack() {
    return breadcrumb.size() > 1;
  }

  public boolean isEmpty() {
    return breadcrumb.isEmpty();
  }

  /**
   * Goes back one level, returning the shop to open.
   * Returns null if we should go back to the main menu.
   */
  public Shop goBack() {
    if (breadcrumb.isEmpty()) return null;
    breadcrumb.pop(); // Remove current
    return breadcrumb.peek(); // Return parent (or null for main menu)
  }

  /**
   * Creates a copy for safe passing between async operations.
   */
  public NavigationContext copy() {
    NavigationContext copy = new NavigationContext();
    copy.breadcrumb.addAll(this.breadcrumb);
    return copy;
  }
}

