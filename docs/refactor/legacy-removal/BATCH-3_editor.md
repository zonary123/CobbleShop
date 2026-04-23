# Lote 3 — ShopEditMenuBuilder (Paso 4)

> **Riesgo**: 🔴 **HIGH**.
> **Tamaño**: 1684 líneas, 11 puntos de acceso a `RotationSchedule` legacy.
> **Objetivo**: el editor in-game opera sobre typed Shop, soportando promoción
> NORMAL → ROTATION y degradación inversa via `ShopTypeConverter`.

## Por qué un commit propio

Es el archivo más grande y peligroso del refactor. Si rompe el editor:
- Los admins NO pueden modificar shops en runtime.
- Las mutaciones via chat input async pueden corromper el estado del shop.
- Pierde funcionalidad sin warning visible.

Aislarlo permite testear EXCLUSIVAMENTE el editor antes de seguir.

## Pre-requisito: `ShopTypeConverter`

Crear `domain/model/shop/ShopTypeConverter.java` ANTES de tocar el editor:

```java
package com.kingpixel.ultrashop.domain.model.shop;

/**
 * Promotes / demotes shops between subtypes preserving the four config VOs
 * ({@link DisplayConfig}, {@link EconomyConfig}, {@link ConditionsConfig},
 * {@link SoundConfig}) and the id.
 *
 * <p>Used by the admin editor when the user toggles "rotation enabled" on a
 * shop — the type changes but the rest of the config must survive intact.</p>
 */
public final class ShopTypeConverter {
    private ShopTypeConverter() {}

    public static RotationShop promoteToRotation(NormalShop source,
                                                 Scheduler scheduler,
                                                 int amount) {
        RotationShop r = new RotationShop();
        copyCommon(source, r);
        r.setProductPool(source.getProducts());
        r.setScheduler(scheduler);
        r.setRotationAmount(amount);
        return r;
    }

    public static NormalShop demoteToNormal(RotationShop source) {
        NormalShop n = new NormalShop();
        copyCommon(source, n);
        n.setProducts(source.getProductPool()); // pool becomes static catalog
        return n;
    }

    private static void copyCommon(AbstractShop from, AbstractShop to) {
        to.setId(from.getId());
        to.setDisplayConfig(from.getDisplayConfig());
        to.setEconomyConfig(from.getEconomyConfig());
        to.setConditionsConfig(from.getConditionsConfig());
        to.setSoundConfig(from.getSoundConfig());
    }
}
```

**Tests obligatorios** (red antes de green):
- promote: `NormalShop` con 5 productos + display custom → `RotationShop` con
  los 5 productos en pool + display intacto + scheduler+amount aplicados.
- demote: `RotationShop` con 10 en pool + scheduler cron → `NormalShop` con
  10 productos + sin scheduler.
- Round-trip: `promote(demote(promote(n, sched, 3)))` ≡ shop original.

## Cambios en ShopEditMenuBuilder

### Imports
```java
// REEMPLAZAR
import com.kingpixel.ultrashop.domain.model.Shop;
import com.kingpixel.ultrashop.domain.model.RotationSchedule;
// CON
import com.kingpixel.ultrashop.domain.model.shop.Shop;
import com.kingpixel.ultrashop.domain.model.shop.NormalShop;
import com.kingpixel.ultrashop.domain.model.shop.RotationShop;
import com.kingpixel.ultrashop.domain.model.shop.ShopTypeConverter;
import com.kingpixel.ultrashop.domain.scheduler.Scheduler;
import com.kingpixel.ultrashop.domain.scheduler.CronScheduler;
import com.kingpixel.ultrashop.domain.scheduler.DurationScheduler;
import com.kingpixel.ultrashop.domain.scheduler.SchedulerFactory;
```

### Patrón para mutar Display/Economy/Conditions/Sound

Los VOs son inmutables → reemplazo via builder:

```java
// ANTES
shop.setRows(6);
shop.setTitle("New Title");

// DESPUÉS
shop.setDisplayConfig(
    shop.getDisplayConfig().toBuilder().rows(6).title("New Title").build()
);
```

### Sección rotación (líneas 1018-1087) — el más delicado

```java
// Sección actual: opera siempre asumiendo legacy mutable.
// Nuevo patrón:

// 1. LECTURA (lore display)
String summary = shop.accept(new ShopVisitor<String>() {
    @Override public String visit(NormalShop s)   { return "§7Type: §fNORMAL"; }
    @Override public String visit(CategoryShop s) { return "§7Type: §fCATEGORY"; }
    @Override public String visit(RotationShop s) {
        return "§7Scheduler: §f" + s.getScheduler().describe()
             + "\n§7Amount: §f" + s.getRotationAmount();
    }
});

// 2. MUTACIÓN — "Set cron"
chatInput.then((player, input) -> {
    Scheduler newSched = SchedulerFactory.fromCron(input);  // valida cron
    Shop current = ctx.getTypedShops(modId).stream()
        .filter(s -> s.getId().equals(shopId)).findFirst().orElseThrow();

    Shop replacement;
    if (current instanceof RotationShop r) {
        r.setScheduler(newSched);
        replacement = r; // mismo objeto, mutación in-place
    } else if (current instanceof NormalShop n) {
        replacement = ShopTypeConverter.promoteToRotation(n, newSched, 3);
    } else {
        player.sendMessage("Cannot set rotation on CATEGORY shop");
        return;
    }
    ctx.replaceShop(modId, shopId, replacement); // del Lote 1
    ConfigLoader.saveShop(replacement);
});

// 3. MUTACIÓN — "Remove rotation"
Shop current = ...;
if (current instanceof RotationShop r) {
    NormalShop n = ShopTypeConverter.demoteToNormal(r);
    ctx.replaceShop(modId, shopId, n);
    ConfigLoader.saveShop(n);
}
```

### Mapping completo de los 11 puntos

| Línea aprox | Código actual | Código nuevo |
|------|---------------|--------------|
| 80   | `shop.isRotation() && shop.getRotationSchedule() != null` | `shop instanceof RotationShop r` |
| 83-86 | `getCron() / getInterval() / getAmount()` (lore) | `r.getScheduler().describe()` + `r.getRotationAmount()` |
| 1023-1028 | mismo (lore en submenu) | mismo patrón |
| 1048 | `shop.setRotationSchedule(null)` | `replaceShop(modId, id, ShopTypeConverter.demoteToNormal(r))` |
| 1056-1057 | `setRotationSchedule(new RotationSchedule(...))` o `setAmount` | si NORMAL → promote; si ROTATION → `r.setRotationAmount(amt)` |
| 1072-1073 | `setRotationSchedule(new ...).setCron(input)` | `r.setScheduler(SchedulerFactory.fromCron(input))` o promote |
| 1079-1080 | `setRotationSchedule(new ...).setInterval(input)` | `r.setScheduler(SchedulerFactory.fromInterval(input))` o promote |

## Checklist de verificación

- [ ] `ShopTypeConverter.java` creado con 3 tests verdes.
- [ ] Imports legacy ELIMINADOS de `ShopEditMenuBuilder` y `edit/ChatInputManager`.
- [ ] Cero `RotationSchedule` references en `presentation/`.
- [ ] Cero `setRotationSchedule` references.
- [ ] Compila: `gradlew compileJava` verde.
- [ ] Tests: `gradlew test` verde.
- [ ] **Smoke manual EXTENSO** (NO se puede skip):
  - [ ] Abrir editor de shop NORMAL: cambiar título, rows, items.
  - [ ] Convertir NORMAL → ROTATION via "set cron". Verificar que productos
        del NORMAL pasan al `productPool` del ROTATION.
  - [ ] Cambiar cron en un ROTATION existente.
  - [ ] Cambiar interval (DurationScheduler) en un ROTATION existente.
  - [ ] Cambiar `amount` en un ROTATION.
  - [ ] Convertir ROTATION → NORMAL via "remove rotation". Verificar que
        productos del pool quedan en el catálogo estático.
  - [ ] Editar un CATEGORY shop: subShops, displayname.
  - [ ] Reload `/shop reload` y verificar que cambios persisten en disco.

## Riesgos específicos

| Riesgo | Mitigación |
|--------|------------|
| Promoción NORMAL → ROTATION pierde productos | Test unitario de `ShopTypeConverter.promoteToRotation` con productos no triviales (con UUIDs, conditions, etc.) |
| Mutación in-place de RotationShop colisiona con DataShop leyendo `currentRotation` | El editor siempre vuelve al hilo principal antes de mutar (`ctx.runOnServer`) — verificar ANTES de cambiar |
| Chat input async invoca callback con shop ya removido (rotation toggle rapido) | Re-fetch del shop por `id` dentro del callback, no capturar referencia |
| Cron inválido del usuario crashea el editor | `SchedulerFactory.fromCron` debe lanzar excepción tipada → atrapar y mostrar al player |

## Criterios de éxito

- Cero `RotationSchedule`/`setRotationSchedule`/`isRotation()` en presentation.
- Cambiar tipo NORMAL ↔ ROTATION funciona sin pérdida de datos.
- Persistencia post-edit: el JSON resultante está en formato nuevo.

## Rollback

`git revert <sha-batch-3>`. El editor vuelve a operar sobre legacy. NO requiere
restore de configs porque los Lotes 1-2 NO tocan persistencia.

