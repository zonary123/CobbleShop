# Lote 2 — DataShop + Scheduler directo (Paso 3)

> **Riesgo**: Medium.
> **Objetivo**: `DataShop` deja de hablar el lenguaje legacy
> (`getRotationSchedule().getCron()`, `getProducts()` ambiguo) y opera
> directamente sobre `RotationShop` + `Scheduler`.

## Por qué un commit propio

Toca lógica de rotación: cron, intervalos, cooldowns persistidos en disco.
Si rompe, se nota inmediatamente (tiendas rotativas dejan de rotar). Aislarlo
en su commit hace trivial el `git bisect`.

Además, este lote desbloquea el `ShopProducts.active()` del Lote 1 para que
NO necesite el shim transitorio con `ShopBridge.toLegacy()`.

## Archivos a modificar

- `domain/model/DataShop.java` *(núcleo del cambio)*
- `domain/model/shop/ShopProducts.java` *(remover el shim si lo tuvo)*
- Cualquier caller de `data.updateDynamicProducts(...)`:
  - `ConfigLoader.loadShops` *(parcial — solo este call)*
  - GUI builders que ya migraron en Lote 1

## Cambios de firma en DataShop

```java
// ANTES
public List<Product> updateDynamicProducts(Shop legacyShop, String modId, boolean force);
public long getActualCooldown(Shop legacyShop, String modId);
private long computeNextFireTime(Shop shop, long lastRotation);
private boolean isScheduleStale(Shop shop, long lastRotation);

// DESPUÉS
public List<Product> updateDynamicProducts(RotationShop shop, String modId, boolean force);
public long getActualCooldown(RotationShop shop, String modId);
// computeNextFireTime y isScheduleStale ELIMINADOS — los reemplaza:
//     shop.getScheduler().nextFireTime(now)
//     shop.getScheduler().nextFireTime(lastRotation) <= now
```

## Reemplazos dentro de DataShop

```java
// ANTES                                          // DESPUÉS
shop.getRotationSchedule().getAmount()          → shop.getRotationAmount()
shop.getRotationSchedule().getCron()            → // gone — usar shop.getScheduler()
shop.getRotationSchedule().getInterval()        → // gone — usar shop.getScheduler()
shop.getProducts()                              → shop.getProductPool()
shop.getName()                                  → shop.getDisplayConfig().getName()
shop.isAnnounceRotation()                       → shop.getConditionsConfig().isAnnounceRotation()

// Cálculo de próxima rotación:
long next = computeNextFireTime(shop, last);    → long next = shop.getScheduler().nextFireTime(last);
boolean stale = isScheduleStale(shop, last);    → boolean stale = shop.getScheduler().nextFireTime(last) <= now;
```

## Adaptación de callers

```java
// Patrón en sitios que iteran shops genéricos:
for (Shop shop : ctx.getTypedShops(modId)) {
    if (shop instanceof RotationShop r) {
        data.updateDynamicProducts(r, modId, false);
    }
}
```

## Checklist de verificación

- [ ] Firmas de `DataShop` migradas (compila sin warnings deprecated).
- [ ] `computeNextFireTime` y `isScheduleStale` ELIMINADOS (cero llamadas).
- [ ] Tests existentes de cron/duration siguen verdes (probablemente hay que
      ajustar fixtures que pasan `Shop` legacy → ahora `RotationShop`).
- [ ] `ShopProducts.active()` ya no usa `ShopBridge.toLegacy`.
- [ ] Compila: `gradlew compileJava` verde.
- [ ] Tests: `gradlew test` verde.
- [ ] Smoke manual: shop con `cron: "*/2 * * * *"` rota cada 2 min;
      shop con `interval: "30s"` rota cada 30s; ambos persisten lastRotation.

## Riesgos específicos

| Riesgo | Mitigación |
|--------|------------|
| `Scheduler.nextFireTime()` semántica diferente a `computeNextFireTime` legacy | Tests de equivalencia ANTES de migrar — comparar resultado para 5 cron + 5 intervals conocidos |
| `lastRotation` persistido en disco con formato viejo | El persistido es `long epochMillis`, no toca |
| Caller olvidado que pase legacy `Shop` | Compilador lo atrapa (cambio de firma) — esto es lo bueno del strong typing |

## Criterios de éxito

- `DataShop` no importa nada de `domain.model.Shop` legacy.
- `RotationShop.getScheduler()` es la única vía a información de schedule.
- Tests de rotación con CRON y DURATION pasan sin tocar fixtures (idealmente).

## Rollback

`git revert <sha-batch-2>`. Como el Lote 1 dejó el shim transitorio en
`ShopProducts`, post-revert sigue funcionando con la API legacy.

