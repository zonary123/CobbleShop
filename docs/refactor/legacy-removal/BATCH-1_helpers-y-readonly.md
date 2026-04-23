# Lote 1 — Helpers + Read-only consumers (Pasos 0, 1, 2)

> **Riesgo combinado**: Medium.
> **Objetivo**: dejar el dominio listo para mutación typed + migrar todos los
> consumidores READ-ONLY (GUI de display, comandos, índice de venta) al typed
> Shop. NO se toca `DataShop` ni el editor mutable.

## Por qué juntos

Los 3 pasos comparten el patrón "leer typed Shop sin mutar". Migrarlos en el
mismo commit permite:
- Eliminar el `legacyById` fallback de `MainMenuBuilder` de una sola vez.
- Cambiar `ctx.getShops()` → `ctx.getTypedShops()` consistentemente.
- Tener UNA sola compilación verde validando los 3 pasos.

Si rompemos algo, el blast radius está acotado a "lectura de display/products
en GUI", no toca lógica de rotación ni edición.

## Archivos a crear

| Archivo | Propósito |
|---------|-----------|
| `domain/model/shop/ShopProducts.java` | Visitor helper: `active(Shop, modId) → List<Product>` |

## Archivos a modificar

### Paso 0 — Helpers
- `ShopContext.java`
  - Add `replaceShop(modId, id, Shop newShop)` — atómico, reemplaza por id.
  - Add `addTypedShop(modId, Shop)` y `removeTypedShop(modId, id)`.
  - **NO borrar** `getShops()` legacy todavía (lo hace Lote 4).

### Paso 1 — Consumers GUI/command
- `presentation/gui/ShopMenuBuilder.java`
- `presentation/gui/SearchMenuBuilder.java`
- `presentation/gui/ProductRenderer.java`
- `presentation/gui/NavigationContext.java`
- `presentation/gui/BuyAndSellMenuBuilder.java`
- `presentation/gui/MainMenuBuilder.java` *(borra el `legacyById` fallback completo)*
- `presentation/gui/TransactionMenuBuilder.java` *(verificar si usa Shop)*
- `presentation/command/SearchCommand.java`
- `presentation/command/CommandTree.java`

### Paso 2 — SellProductIndex
- `infrastructure/index/SellProductIndex.java`
- `infrastructure/config/ConfigLoader.java` *(solo el `rebuild()` call, no el resto)*
- `domain/model/DataShop.java` *(solo el `rebuild` call)*

## Patrones de migración (cheat sheet)

```java
// ANTES                                          // DESPUÉS
shop.getRows()                                  → shop.getDisplayConfig().getRows()
shop.getTitle()                                 → shop.getDisplayConfig().getTitle()
shop.getDisplay()                               → shop.getDisplayConfig().getDisplayItem()
shop.getName()                                  → shop.getDisplayConfig().getName()
shop.getPanels()                                → shop.getDisplayConfig().getPanels()
shop.getItemClose()                             → shop.getDisplayConfig().getItemClose()
shop.getOpenConditions()                        → shop.getConditionsConfig().getOpenConditions()
shop.isAnnounceRotation()                       → shop.getConditionsConfig().isAnnounceRotation()
shop.getEconomies()                             → shop.getEconomies()  // default ya existe
shop.getGlobalDiscount()                        → shop.getGlobalDiscount()  // default ya existe
shop.getDiscounts()                             → shop.getDiscounts()  // default ya existe
shop.getSoundOpen()                             → shop.getSoundConfig().getSoundOpen()

shop.isCategory()                               → shop instanceof CategoryShop
shop.isRotation()                               → shop instanceof RotationShop
shop.isNormal()                                 → shop instanceof NormalShop

shop.getProducts()                              → ShopProducts.active(shop, modId)  // si es lectura
                                                    + shop instanceof NormalShop n ? n.getProducts() : ...
shop.getSubShops()                              → ((CategoryShop) shop).getSubShops()
shop.getRotationSchedule().getAmount()          → ((RotationShop) shop).getRotationAmount()

ctx.getShops(modId)                             → ctx.getTypedShops(modId)
```

## Diseño de `ShopProducts.active()`

```java
package com.kingpixel.ultrashop.domain.model.shop;

public final class ShopProducts {
    private ShopProducts() {}

    /**
     * Returns the products visible in this shop right now.
     * - NormalShop  → static catalog
     * - CategoryShop → empty (navigate sub-shops instead)
     * - RotationShop → current rotation snapshot (delegates to DataShop)
     */
    public static List<Product> active(Shop shop, String modId) {
        return shop.accept(new ShopVisitor<List<Product>>() {
            @Override public List<Product> visit(NormalShop s)   { return s.getProducts(); }
            @Override public List<Product> visit(CategoryShop s) { return List.of(); }
            @Override public List<Product> visit(RotationShop s) {
                return ShopContext.get().getDataShop()
                    .updateDynamicProducts(s, modId, false);
                // Nota: la firma cambia en Lote 2 — por ahora pasa via instanceof
                // o helper transitorio si rompe la build.
            }
        });
    }
}
```

> ⚠️ **Atención**: `DataShop.updateDynamicProducts(...)` hoy recibe el legacy
> `Shop`. Si el cambio de firma no se puede hacer en el Lote 2, el helper
> usa `ShopBridge.toLegacy(s)` provisionalmente — y se remueve en Lote 2.

## Checklist de verificación

- [ ] `ShopProducts.java` creado con tests unitarios (3 tipos × resultado esperado).
- [ ] `ShopContext.replaceShop/add/remove` con tests (concurrencia básica con
      `ConcurrentHashMap.compute`).
- [ ] Cero imports `com.kingpixel.ultrashop.domain.model.Shop` en `presentation/`
      (excepto `ShopEditMenuBuilder` que migra en Lote 3).
- [ ] Cero imports legacy en `infrastructure/index/`.
- [ ] `MainMenuBuilder.legacyById` ELIMINADO.
- [ ] Compila: `gradlew compileJava` verde.
- [ ] Tests: `gradlew test` verde.
- [ ] Manual smoke: cargar mod en dev, abrir `/shop`, navegar entre categorías,
      ver shop NORMAL y ROTATION renderizan.

## Criterios de éxito

- `MainMenuBuilder.open()` no usa `ShopBridge` ni `legacyById`.
- `SearchMenuBuilder` itera typed shops y filtra por `instanceof`.
- `SellProductIndex.rebuild()` recibe `Map<String, List<typed.Shop>>`.
- `ShopContext` expone los nuevos métodos y los usa el editor (Lote 3).

## Rollback

Si algo falla post-merge: `git revert <sha-batch-1>`. Restaura el legacy
fallback en MainMenuBuilder y deja todo como estaba.

## Notas de implementación

- `ShopProducts.active()` es la PRIMERA pieza que tendrá un import circular
  potencial (`shop` → `ShopContext` → `getDataShop`). Si Java se queja,
  pasar `DataShop` como parámetro o usar `Supplier<DataShop>`. **No usar
  Service Locator pattern** — es un anti-pattern para el dominio.
- El `getEconomies()`/`getGlobalDiscount()` ya están como `default` en
  `Shop.java` interface — verificar que devuelvan los mismos valores que el
  legacy antes de declarar el lote terminado.

