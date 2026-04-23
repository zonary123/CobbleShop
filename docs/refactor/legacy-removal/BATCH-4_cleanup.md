# Lote 4 — Cleanup: ConfigLoader + ShopContext + Borrado legacy (Pasos 5, 6, 7)

> **Riesgo combinado**: Medium (boot path + borrado masivo).
> **Objetivo**: completar la migración. Después de este lote, el legacy `Shop`
> god-class y todos sus satélites NO existen en el código.

## Por qué juntos

Los 3 pasos forman un solo "limpiar la casa":
1. Reescribir defaults con typed builders.
2. Eliminar el doble path `shops` + `typedShops`, renombrar a `shops` solamente.
3. Borrar archivos legacy huérfanos.

Hacerlos en commits separados crearía estados intermedios raros (e.g. defaults
typed pero `bridgeAll` aún existe) que no aportan bisectabilidad real.

## Pre-requisito: `SchedulerFactory.fromInterval/fromCron`

Verificar que existen ANTES de tocar `createDefaultShops`. Si no:

```java
// domain/scheduler/SchedulerFactory.java — añadir si faltan
public static Scheduler fromCron(String expression) {
    return new CronScheduler(expression);
}
public static Scheduler fromInterval(String duration) {
    return new DurationScheduler(duration);
}
```

(Ya existe `fromLegacy(RotationSchedule)` — lo borramos en este mismo lote.)

## Archivos a modificar

### Paso 5 — Defaults
- `infrastructure/config/ConfigLoader.java` *(líneas 218-498 — los 9 builders)*

### Paso 6 — Boot path
- `infrastructure/config/ConfigLoader.java` *(loadShops, saveShop, createShop, bridgeAll)*
- `ShopContext.java` *(eliminar campo `shops`, renombrar `typedShops` → `shops`)*

## Archivos a borrar

### Paso 7 — Borrado masivo
- `domain/model/Shop.java` *(god-class legacy — 232 líneas)*
- `domain/model/RotationSchedule.java`
- `domain/model/shop/ShopBridge.java`
- `migrate/OldShop.java`
- `migrate/OldProduct.java`
- `migrate/OldLang.java`
- `src/test/java/.../ShopBridgeTest.java` *(si existe)*

### Modificar (no borrar)
- `migrate/V1ToV2Migrator.java` — quitar import legacy, quitar v0 path
  (`convertOldShop`, `migrateV0IfNeeded`). La v1→v2 in-JSON queda intacta.
- `infrastructure/serialization/scheduler/SchedulerJsonAdapter.java` — quitar
  el path legacy `RotationSchedule` (después de borrar la clase).
- `infrastructure/serialization/shop/ShopTypeAdapterFactory.java` — el
  `readLegacyFormat` ya NO compila tras borrar legacy; **NO lo borramos** —
  lo refactorizamos para detectar el formato viejo a nivel JSON
  (sin instanciar la clase legacy) y emitir un error claro al usuario:

  ```java
  private Shop readLegacyFormat(JsonObject obj, JsonDeserializationContext ctx) {
      throw new JsonParseException(
          "Shop file is in the legacy format. Please run /shop reload — "
        + "the file will be auto-migrated, OR restore from backup if conversion fails. "
        + "See CHANGELOG.md → '⚠️ Breaking' section."
      );
  }
  ```

  > **Nota**: la auto-migración transparente DEJA de funcionar tras borrar
  > legacy. El JSON viejo en disco se regrabó al formato nuevo en la primera
  > carga post-Lote 1 (cuando aún existía el bridge), así que en producción
  > esto no debería dispararse — pero es la red de seguridad por si alguien
  > restauró un backup viejo.

## Reescritura de `createDefaultShops` (Paso 5)

Patrón por shop:

```java
// ANTES
private static Shop buildHourlyRotation() {
    Shop shop = new Shop("hourly_rotation", ShopType.ROTATION);
    shop.setName("Hourly Rotation");
    shop.getDisplay().setDisplayname("§b§lHourly Rotation");
    shop.setRotationSchedule(new RotationSchedule("1h", 4));
    shop.setAnnounceRotation(true);
    shop.setProducts(new ArrayList<>(List.of(...)));
    return shop;
}

// DESPUÉS
private static RotationShop buildHourlyRotation() {
    RotationShop shop = new RotationShop();
    shop.setId("hourly_rotation");

    shop.setDisplayConfig(DisplayConfig.builder()
        .name("Hourly Rotation")
        .displayItem(ItemModel.builder()
            .displayname("§b§lHourly Rotation")
            .lore(List.of("§7Refreshes every hour.", "..."))
            .build())
        .build());

    shop.setConditionsConfig(ConditionsConfig.builder()
        .announceRotation(true)
        .build());

    shop.setScheduler(SchedulerFactory.fromInterval("1h"));
    shop.setRotationAmount(4);
    shop.setProductPool(new ArrayList<>(List.of(...)));
    return shop;
}
```

Aplicar a los 9 builders. La firma de `createDefaultShops` cambia de
`List<legacy.Shop>` a `List<typed.Shop>`.

## Cleanup de `ShopContext` (Paso 6)

```java
// ANTES
public final class ShopContext {
    private final Map<String, List<legacy.Shop>> shops = new ConcurrentHashMap<>();
    private final Map<String, List<typed.Shop>> typedShops = new ConcurrentHashMap<>();

    public Map<String, List<legacy.Shop>> getShops() { ... }
    public List<legacy.Shop> getShops(String modId) { ... }
    public List<typed.Shop> getTypedShops(String modId) { ... }
    // ...
}

// DESPUÉS
public final class ShopContext {
    private final Map<String, List<Shop>> shops = new ConcurrentHashMap<>();

    public Map<String, List<Shop>> getShops() { ... }
    public List<Shop> getShops(String modId) { ... }
    // getTypedShops ELIMINADO — todos los call-sites ya migraron en Lotes 1-3.
}
```

**Sed mecánico** post-rename: `ctx.getTypedShops` → `ctx.getShops` en TODO el
codebase (`grep -r "getTypedShops"` → 0 matches al final).

## `setFilePath` ya no existe en typed Shop

Hoy el legacy guarda `shop.setFilePath(file.toString())`. Como typed no lo
tiene, derivar el path:

```java
// ConfigLoader
private static Path shopFile(String modId, String shopId) {
    return CobbleUtils.getPath()
        .resolve(modIdToPath(modId))  // o similar — verificar API existente
        .resolve("shop")
        .resolve(shopId + ".json");
}

public static void saveShop(String modId, Shop shop) {
    Path path = shopFile(modId, shop.getId());
    UtilsFile.writeAsync(path, shop).exceptionally(...);
}
```

`createShop` y el editor llaman `saveShop(modId, shop)` con modId del contexto.

## Checklist de verificación

- [ ] `SchedulerFactory.fromInterval/fromCron` existen.
- [ ] Los 9 `buildXxx()` devuelven typed subtype concreto.
- [ ] `bridgeAll` ELIMINADO de `ConfigLoader`.
- [ ] Campo `shops` legacy y `getShops()` legacy ELIMINADOS de `ShopContext`.
- [ ] `typedShops` renombrado → `shops`. Cero `getTypedShops` en codebase.
- [ ] Archivos del Paso 7 (`Shop.java`, `RotationSchedule.java`, etc.)
      ELIMINADOS del filesystem (`git rm`).
- [ ] `V1ToV2Migrator` sin imports legacy, sin v0 path.
- [ ] `ShopTypeAdapterFactory.readLegacyFormat` lanza `JsonParseException`
      con mensaje útil.
- [ ] Cero matches de:
  - `grep -r "domain.model.Shop "` (con espacio para excluir `.shop.`)
  - `grep -r "RotationSchedule"`
  - `grep -r "ShopBridge"`
- [ ] Compila: `gradlew compileJava` verde.
- [ ] Tests: `gradlew test` verde.
- [ ] Smoke manual:
  - [ ] Borrar carpeta `shop/` y arrancar mod → genera 9 shops default,
        todos cargan correctamente.
  - [ ] Reload con shops nuevos.
  - [ ] Crear shop nuevo via `/shop create`.
  - [ ] Editar y guardar.

## Riesgos específicos

| Riesgo | Mitigación |
|--------|------------|
| Olvidamos un call-site `ctx.getShops()` legacy → NPE en runtime | Compilador lo atrapa porque la firma cambió de tipo de retorno |
| `setFilePath` removido rompe persistencia | Tests de `saveShop` con shop nuevo + verificar que escribe en path correcto |
| `V1ToV2Migrator` v0 era usado en deploys reales | Verificar git log: ¿quedan deploys con shops v0? Si sí, dejar v0 marcado @Deprecated 1 versión más en lugar de borrar |
| `readLegacyFormat` se dispara en producción tras update | El error message pide al usuario hacer `/shop reload` con backup — UX aceptable |
| Generación de defaults rompe ItemModel (legacy usaba setters fluidos en `getDisplay()`) | Tests del primer arranque: comparar JSON generado pre/post — `ItemModel.builder()` debe producir output equivalente |

## Criterios de éxito FINAL

- ✅ `Shop` solo significa una cosa: el sealed interface en `domain.model.shop`.
- ✅ Cero archivo en `domain/model/Shop.java`.
- ✅ Test suite verde.
- ✅ Mod arranca de cero, genera defaults, permite editar, persiste.
- ✅ CHANGELOG.md actualizado movilizando el `[Unreleased]` a versión real.

## Rollback

`git revert <sha-batch-4>`. Restaura legacy y sus satélites. Como Lote 1-3
ya no usaban legacy directamente, la app sigue funcionando con typed Shop
y bridge restaurado.

## Próximo paso post-Lote 4

Volver al usuario con las **5 decisiones de cross-server** documentadas en
`SHOP_AND_SCHEDULER_REFACTOR.md` § 10.b para arrancar Fases 5-9.

