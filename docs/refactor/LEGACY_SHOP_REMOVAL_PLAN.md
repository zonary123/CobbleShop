# Plan: Eliminación del legacy `domain.model.Shop`

> **Versión**: 1.0
> **Fecha**: 2026-04-23
> **Estado**: Aprobado, pendiente de ejecución
> **Decisión arquitectónica**: Sealed hierarchy mutable (opción A). Los typed Shops
> (`NormalShop`, `CategoryShop`, `RotationShop`) ya tienen setters Lombok porque la
> admin GUI muta los shops in-place. NO se reescribe a inmutable.
> **Breaking change**: Aceptado por el usuario — se avisa en `CHANGELOG.md` que los
> admins deben hacer backup de `shop/` antes de actualizar.

## Resumen

Reemplazar el god-class legacy `com.kingpixel.ultrashop.domain.model.Shop` por la
sealed hierarchy `com.kingpixel.ultrashop.domain.model.shop.Shop` ya implementada.
Trabajo en **8 pasos atómicos**, cada uno mergeable independientemente
(compila + tests verdes).

**Estrategia**: migrar consumidores read-only primero (hojas), luego `DataShop`
(lógica de rotación), luego editor mutable y `ConfigLoader`, finalmente borrar legacy.

**Patrón de dispatch**:
- `ShopVisitor` para dispatch cerrado en presentación/índices.
- `instanceof RotationShop r` para operaciones específicas de un subtipo.
- `ShopReference` (ya existente) para utilidades como `Product.check`.

---

## Paso 0 — Helpers de dispatch y registry mutation

**Riesgo**: Low.

- Añadir `ShopContext.replaceShop(modId, id, Shop newShop)`, `addTypedShop`,
  `removeTypedShop` que mutan sólo `typedShops`. Necesarios para que el editor
  pueda cambiar `NORMAL ↔ ROTATION` (cambia la clase, no el contenido).
- Añadir helper `ShopProducts.active(Shop, String modId)` en `domain.model.shop`
  (visitor) que centraliza:
    - `NormalShop` → `getProducts()`
    - `RotationShop` → `DataShop.update(...)`
    - `CategoryShop` → `List.of()`

  Reemplaza `shop.isRotation() ? ... : shop.getProducts()`.

**Archivos**: `ShopContext.java`, nuevo `ShopProducts.java`.

---

## Paso 1 — Migrar read-only GUI/command consumers a typed Shop

**Riesgo**: Medium (muchos archivos, lógica de paginación).

**Cambios de import** `domain.model.Shop` → `domain.model.shop.Shop` en:
- `ShopMenuBuilder`
- `SearchMenuBuilder`
- `ProductRenderer`
- `NavigationContext`
- `BuyAndSellMenuBuilder`
- `MainMenuBuilder` (borrar el `legacyById` fallback)
- `SearchCommand`
- `CommandTree`

**Reemplazos de acceso**:
- `shop.getRows()` → `shop.getDisplayConfig().getRows()`
- `shop.getTitle()` → `shop.getDisplayConfig().getTitle()`
- `shop.getDisplay()` → `shop.getDisplayConfig().getDisplayItem()`
- `shop.getOpenConditions()` → `shop.getConditionsConfig().getOpenConditions()`
- `shop.getEconomies()` → ya está como default en el interface
- `shop.isRotation()` → `shop instanceof RotationShop`
- `shop.isCategory()` → `shop instanceof CategoryShop`
- `getActiveProducts(shop)` → `ShopProducts.active(shop, modId)`
- `ctx.getShops(modId)` → `ctx.getTypedShops(modId)`

---

## Paso 2 — `SellProductIndex` y rebuild calls

**Riesgo**: Low.

- Cambiar firma a `rebuild(Map<String, List<typed.Shop>>)` en `SellProductIndex`.
- Recorrer usando visitor (sólo `Normal`+`Rotation` aportan productos vendibles).
- Ajustar callers: `ConfigLoader.load()` y `DataShop.updateDynamicProducts`
  (pasan `ctx.getShops()` → `ctx.getTypedShops()`).

---

## Paso 3 — Refactor `DataShop` a `RotationShop` directo

**Riesgo**: Medium (lógica de cron/cooldown, tests existentes).

- Cambiar firma a `updateDynamicProducts(RotationShop shop, String modId, boolean force)`
  y `getActualCooldown(RotationShop, modId)` en `DataShop.java`.
- Eliminar `computeNextFireTime` y `isScheduleStale`: delegar a
  `shop.getScheduler().nextFireTime(now)` (`Scheduler` ya encapsula cron+duration).
- Reemplazos:
    - `getRotationSchedule().getAmount()` → `shop.getRotationAmount()`
    - `shop.getProducts()` → `shop.getProductPool()`
    - `shop.getName()` → `shop.getDisplayConfig().getName()`
    - `shop.isAnnounceRotation()` → `shop.getConditionsConfig().isAnnounceRotation()`
- Adaptar los 4 callers (todos ya tienen typed `Shop` tras paso 1) con
  `if (shop instanceof RotationShop r) data.updateDynamicProducts(r, modId, force);`

---

## Paso 4 — Migrar `ShopEditMenuBuilder` a typed Shop

**Riesgo**: High (1684 líneas, mutaciones async vía chat input, cambio de tipo).

- Cambiar import `domain.model.*` → typed.
- Reemplazar todos los getters al nuevo modelo VO (`getDisplayConfig()`, etc.).
- Mutar VOs inmutables:
  ```java
  shop.setDisplayConfig(shop.getDisplayConfig().toBuilder().rows(N).build());
  ```
- **Sección rotación** (líneas 1018–1087): operar **sólo si
  `shop instanceof RotationShop r`**. Si el usuario hace "Set interval/cron/amount"
  sobre un `NormalShop`, llamar:
  ```java
  ShopContext.replaceShop(modId, id, promoteToRotation(normal, scheduler, amount));
  ```
  Helper local que copia los 4 configs a un nuevo `RotationShop`. Para "Remove
  rotation" → `replaceShop` con un `NormalShop` equivalente.
- Para `setScheduler`/`setRotationAmount`/`setProductPool` usar setters Lombok ya
  existentes; las mutaciones in-place son thread-safe en el sentido del editor
  (siempre vuelven a `ctx.runOnServer` antes de re-abrir).
- Persistir vía `ConfigLoader.saveShop(Shop typed)` (firma migrada en paso 6).

---

## Paso 5 — Reescribir `createDefaultShops` con typed shops

**Riesgo**: Low (sólo afecta primer arranque).

- En `ConfigLoader.java` líneas 218–498: cada `buildXxx()` devuelve el subtipo
  concreto (`NormalShop`, `CategoryShop`, `RotationShop`).
- Construir VOs con `DisplayConfig.builder().name(...).displayItem(...).build()`
  (los builders Lombok ya existen).
- Reemplazar `new RotationSchedule("1h", 4)` por `RotationShop` con
  `setScheduler(SchedulerFactory.fromInterval("1h"))` y `setRotationAmount(4)`.
- Cron: `SchedulerFactory.fromCron("0 18 * * 5")`.
- Reemplazar `setSubShops(...)` directo en `CategoryShop`. Eliminar
  `setProducts(empty)` para shops `CATEGORY`.

> **Sub-paso 5.0**: verificar que existan `SchedulerFactory.fromInterval/fromCron`.
> Si sólo existe `fromLegacy(RotationSchedule)`, añadir las dos factory methods
> nuevas como sub-paso (Low risk, additivo).

---

## Paso 6 — Migrar `loadShops/saveShop/createShop` a typed; eliminar `bridgeAll` y mapa legacy

**Riesgo**: Medium (boot path).

- `UtilsFile.read(file, Shop.class)` ya devuelve typed gracias a
  `ShopTypeAdapterFactory`. Eliminar el doble path:
    - Borrar `bridgeAll`.
    - Borrar `ctx.getShops()` y el campo `shops` en `ShopContext`.
    - Renombrar `typedShops` → `shops` y `getTypedShops` → `getShops` para
      limpiar nomenclatura.
- Compilación quiebra solo donde aún quedaban referencias legacy — verificar
  grep cero.
- `saveShop(Shop)` y `createShop(...)` toman typed.
- `setFilePath` ya no existe en typed → migrar a un `Map<String,Path>` paralelo
  en `ShopContext` (o regenerar de `id` + base dir, más limpio). **Recomendado**:
  segunda opción → `Path shopFile(modId, shopId)` derivado.

---

## Paso 7 — Borrar legacy y tests obsoletos

**Riesgo**: Low (post-migración, sólo archivos sin referencias).

**Borrar**:
- `domain/model/Shop.java`
- `domain/model/RotationSchedule.java`
- `domain/model/shop/ShopBridge.java`
- `migrate/OldShop.java`, `migrate/OldProduct.java`, `migrate/OldLang.java`
- La mitad v0 (`convertOldShop`, `migrateV0IfNeeded`) de `V1ToV2Migrator.java`
  — la v1→v2 in-JSON queda intacta (puro Gson, no toca el modelo). Quitar el
  import legacy del migrator.
- `ShopBridgeTest`
- `SchedulerFactoryTest` (o adaptarlo a `SchedulerFactory.fromCron/fromInterval`
  sin pasar por `RotationSchedule`).

**Documentar** en `CHANGELOG.md` el breaking + recomendación de backup `shop/`
previo al update. ✅ (ya hecho).

---

## Cosas pendientes de decisión menor

1. **Renombrar `typedShops` → `shops` en paso 6 vs. mantener nombres**:
   recomendado **renombrar** porque la única razón del nombre era coexistencia;
   deja la API limpia. Alternativa: dejar `typedShops` para evitar churn en
   imports — pero genera deuda semántica.

2. **`promoteToRotation`/`demoteToNormal` helpers**: ¿en `ShopContext`, en una
   clase aparte `ShopTypeConverter`, o métodos estáticos en `RotationShop`/
   `NormalShop`? **Recomendado**: `ShopTypeConverter` en `domain.model.shop` —
   concentra la lógica de copia de los 4 VOs en un solo lugar testeable.

3. **`SchedulerFactory.fromInterval/fromCron`**: verificar antes de paso 5.

---

## Próximos pasos (post-eliminación legacy)

Una vez completados los 8 pasos, la base queda lista para arrancar las **Fases
5-9 del refactor cross-server** documentadas en
`SHOP_AND_SCHEDULER_REFACTOR.md` § 10.b. Antes de arrancar, hay que cerrar las
5 decisiones pendientes (rol DEV/MEMBER, formato de configs en Mongo, trigger
de hot-reload, replica set, identidad del nodo).

