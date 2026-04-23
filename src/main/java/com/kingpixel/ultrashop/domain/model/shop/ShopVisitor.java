package com.kingpixel.ultrashop.domain.model.shop;

/**
 * Visitor pattern for the sealed {@link Shop} hierarchy.
 *
 * <p>Provides exhaustive, compile-time-checked dispatch over the three concrete
 * shop types — replacing scattered {@code instanceof} / {@code shop.getType()}
 * checks with polymorphic method calls.</p>
 *
 * <p>Adding a new shop type forces every visitor in the codebase to be updated
 * (compilation fails otherwise) — that is the explicit Open/Closed Principle
 * benefit of using sealed types here.</p>
 *
 * <p><b>Typical usage:</b></p>
 * <pre>{@code
 * String title = shop.accept(new ShopVisitor<String>() {
 *     public String visit(NormalShop s)   { return s.getDisplayConfig().getTitle(); }
 *     public String visit(CategoryShop s) { return s.getDisplayConfig().getTitle() + " (categories)"; }
 *     public String visit(RotationShop s) { return s.getDisplayConfig().getTitle() + " (rotates)"; }
 * });
 * }</pre>
 *
 * @param <R> visitor return type ({@code Void} when only side-effects are needed)
 */
public interface ShopVisitor<R> {

  R visit(NormalShop shop);

  R visit(CategoryShop shop);

  R visit(RotationShop shop);
}

