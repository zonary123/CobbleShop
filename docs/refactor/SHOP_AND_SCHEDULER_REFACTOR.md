# UltraShop — Plan de Refactor: Shop Hierarchy + Scheduler Discriminator

> **Versión**: 1.0  
> **Fecha**: 2026-04-22  
> **Alcance**: Dominio (`com.kingpixel.ultrashop.domain.model`) + Infraestructura de serialización + (parcial) Service
> layer.  
> **NO afecta**: Persistencia (Mongo/SQL), GUI, comandos, web dashboard. Esos consumen el dominio refactorizado a través
> de la fachada existente.

---

## 1. Diagnóstico del estado actual

### 1.1 Shop como god-object

`Shop` (232 líneas) viola múltiples principios SOLID al mismo tiempo:

| Síntoma                                        | Evidencia                                                                                                  | Principio violado                                                   | 
|------------------------------------------------|------------------------------------------------------------------------------------------------------------|---------------------------------------------------------------------|
| Una clase, 3 comportamientos                   | `type ∈ {NORMAL, CATEGORY, ROTATION}` con `if/switch` por todo el código                                   | **SRP** — una sola clase con 3 responsabilidades                    |
| Campos válidos según tipo                      | `subShops` solo para CATEGORY, `rotationSchedule` solo para ROTATION, `products` solo para NORMAL/ROTATION | **LSP / ISP** — los clientes reciben campos que NUNCA deberían usar |
| Auto-promoción frágil                          | `check()` infiere el tipo desde campos legacy poblados                                                     | **OCP** — agregar un tipo nuevo obliga a tocar `check()`            |
| Métodos `isNormal()/isCategory()/isRotation()` | Ya hay 3 helpers + 1 deprecated (`hasCategories()`)                                                        | Code smell: **type-checking en lugar de polimorfismo**              |
| Constructor con flag booleano deprecated       | `Shop(String, boolean hasRotation)`                                                                        | **Anti-pattern**: boolean parameters                                |

### 1.2 RotationSchedule mezclando dos modos

```java
// Estado actual — RotationSchedule.java
@Nullable
private String interval;  // "30m", "4h"
@Nullable
private String cron;      // "0 18 * * 5" — overrides interval
private int amount;
```

Problemas:

1. **Optional fields que se anulan entre sí** — `cron` "pisa" `interval` por convención implícita en el código.
2. **Sin discriminator explícito** — el JSON no declara qué modo usar, hay que inferirlo.
3. **No es extensible** — agregar `RANDOM`, `EVENT_DRIVEN`, etc. requiere más nullable fields.
4. **Default poco claro** — el código no comunica que CRON debería ser preferido.

### 1.3 Serialización monolítica

Gson serializa `Shop` directamente con reflection. Campos no aplicables al tipo se escriben como `null` o vacíos en el
JSON, ensuciando los archivos de config y obligando a `check()` a sanear post-deserialización.

---

## 2. Arquitectura propuesta

### 2.1 Visión general

```
┌─────────────────────────────────────────────────────────────────┐
│                      DOMAIN (sin I/O, sin Gson)                  │
│                                                                   │
│   Shop (sealed interface)                                        │
│      ├── NormalShop      (productos estáticos)                   │
│      ├── CategoryShop    (subShops)                              │
│      └── RotationShop    (productos + Scheduler)                 │
│                                                                   │
│   Scheduler (sealed interface)     ◄── SchedulerType (enum)      │
│      ├── CronScheduler  ──── CRON (default)                      │
│      └── DurationScheduler ── DURATION                           │
└─────────────────────────────────────────────────────────────────┘
                              ▲ 
                              │ usa
┌─────────────────────────────┴───────────────────────────────────┐
│              INFRASTRUCTURE/serialization (adapters)             │
│                                                                   │
│   ShopJsonAdapter          (Gson TypeAdapterFactory)             │
│      ├── NormalShopAdapter      ◄─┐                              │
│      ├── CategoryShopAdapter    ◄─┼─ ShopAdapterRegistry         │
│      └── RotationShopAdapter    ◄─┘    (Map<ShopType, Adapter>)  │
│                                                                   │
│   SchedulerJsonAdapter     (Gson TypeAdapterFactory)             │
│      ├── CronSchedulerAdapter                                    │
│      └── DurationSchedulerAdapter                                │
└─────────────────────────────────────────────────────────────────┘
```

### 2.2 Patrones de diseño aplicados

| Patrón                              | Dónde                                             | Por qué                                                                                                  |
|-------------------------------------|---------------------------------------------------|----------------------------------------------------------------------------------------------------------|
| **Sealed Interface + Polymorphism** | `Shop`, `Scheduler`                               | Reemplaza `if/switch` por dispatch polimórfico (Java 17+). Compilador valida exhaustividad.              |
| **Adapter** (GoF Structural)        | `ShopJsonAdapter`, `SchedulerJsonAdapter`         | Desacopla serialización del dominio. Cada subtipo tiene su propio adapter.                               |
| **Registry**                        | `ShopAdapterRegistry`, `SchedulerAdapterRegistry` | `Map<ShopType, Adapter>` — agregar tipo nuevo = registrar adapter, sin tocar código existente (**OCP**). |
| **Strategy**                        | `Scheduler.nextFireTime()`                        | Cada implementación calcula su próxima ejecución sin que `Shop` sepa cómo.                               |
| **Factory Method**                  | `Shop.of(ShopType, ...)`                          | Construcción centralizada sin exponer constructores específicos.                                         |
| **Discriminator Field**             | `"type": "ROTATION"` en JSON                      | Convención REST/event-sourcing para deserialización polimórfica.                                         |
| **Null Object**                     | `Scheduler.NEVER` (opcional)                      | Evita `Optional<Scheduler>` o `null` checks en `NormalShop`.                                             |

---

## 3. Diseño detallado

### 3.1 Jerarquía de Shop (sealed)

```java
package com.kingpixel.ultrashop.domain.model.shop;

public sealed interface Shop permits NormalShop, CategoryShop, RotationShop {

  String getId();

  ShopType getType();

  DisplayConfig getDisplay();

  EconomyConfig getEconomy();

  ConditionsConfig getConditions();

  /** Validación + sanitización post-deserialización. */
  void check();

  /** Productos visibles en este momento (resuelto polimórficamente). */
  List<Product> activeProducts();

  /** Visitor entry point — evita instanceof en clientes. */
  <R> R accept(ShopVisitor<R> visitor);
}
```

#### 3.1.1 Implementaciones

```java
// NormalShop — catálogo estático
public final class NormalShop extends AbstractShop {
  private final List<Product> products;

  @Override
  public ShopType getType() {
    return ShopType.NORMAL;
  }

  @Override
  public List<Product> activeProducts() {
    return products;
  }

  @Override
  public <R> R accept(ShopVisitor<R> v) {
    return v.visit(this);
  }
}

// CategoryShop — menú de subshops
public final class CategoryShop extends AbstractShop {
  private final List<SubShop> subShops;

  @Override
  public ShopType getType() {
    return ShopType.CATEGORY;
  }

  @Override
  public List<Product> activeProducts() {
    return List.of();
  } // delega navegación

  @Override
  public <R> R accept(ShopVisitor<R> v) {
    return v.visit(this);
  }
}

// RotationShop — productos dinámicos + Scheduler
public final class RotationShop extends AbstractShop {
  private final List<Product> productPool;
  private final Scheduler scheduler;
  private final int rotationAmount;
  private transient DynamicRotation currentRotation; // estado mutable runtime

  @Override
  public ShopType getType() {
    return ShopType.ROTATION;
  }

  @Override
  public List<Product> activeProducts() {
    return currentRotation.getProducts();
  }

  @Override
  public <R> R accept(ShopVisitor<R> v) {
    return v.visit(this);
  }
}
```

#### 3.1.2 `AbstractShop` — campos comunes

```java
abstract class AbstractShop implements Shop {
  protected String id;
  protected DisplayConfig display;          // name, title, rows, panels, items...
  protected EconomyConfig economy;          // economies, discounts, globalDiscount
  protected ConditionsConfig conditions;    // openConditions, closeCommand
  protected SoundConfig sound;              // soundOpen, soundClose

  @Override
  public String getId() {
    return id;
  }

  @Override
  public DisplayConfig getDisplay() {
    return display;
  }
  // ... etc
}
```

**Beneficio inmediato**: el Shop actual de 232 líneas se descompone en 4 archivos pequeños + 4 value objects de
configuración, cada uno con UNA responsabilidad clara.

#### 3.1.3 ShopVisitor (Behavioral Pattern)

```java
public interface ShopVisitor<R> {
  R visit(NormalShop shop);

  R visit(CategoryShop shop);

  R visit(RotationShop shop);
}

// Uso típico en GUI:
String menuTitle = shop.accept(new ShopVisitor<String>() {
  @Override
  public String visit(NormalShop s) {
    return s.getDisplay().getTitle();
  }

  @Override
  public String visit(CategoryShop s) {
    return s.getDisplay().getTitle() + " (categories)";
  }

  @Override
  public String visit(RotationShop s) {
    return s.getDisplay().getTitle() + " (rotates)";
  }
});
```

Con sealed interface, **el compilador obliga** a manejar todos los casos. Adiós `default` branches sin sentido.

### 3.2 Sistema de Scheduler

#### 3.2.1 Interface y enum

```java
package com.kingpixel.ultrashop.domain.scheduler;

public enum SchedulerType {
  CRON,       // default — máxima flexibilidad
  DURATION    // fallback — intervalo simple
}

public sealed interface Scheduler permits CronScheduler, DurationScheduler {

  SchedulerType getType();

  /** Próxima ejecución estrictamente después de {@code afterEpochMs}. */
  long nextFireTime(long afterEpochMs);

  /** Representación legible para logs / UI. */
  String describe();

  /** Default Factory — aplica la convención del proyecto: CRON es preferido. */
  static Scheduler defaultScheduler() {
    return new CronScheduler("0 * * * *"); // cada hora en punto
  }
}
```

#### 3.2.2 Implementaciones

```java
public final class CronScheduler implements Scheduler {
  private final String expression;
  private final transient CronExpression compiled; // cache parser

  public CronScheduler(String expression) {
    this.expression = expression;
    this.compiled = CronExpression.parse(expression);
  }

  @Override
  public SchedulerType getType() {
    return SchedulerType.CRON;
  }

  @Override
  public long nextFireTime(long after) {
    return compiled.nextFireTime(after);
  }

  @Override
  public String describe() {
    return "cron(" + expression + ")";
  }
}

public final class DurationScheduler implements Scheduler {
  private final String duration; // "30m", "4h", "1d"
  private final transient long durationMs;

  public DurationScheduler(String duration) {
    this.duration = duration;
    this.durationMs = DurationParser.parse(duration);
  }

  @Override
  public SchedulerType getType() {
    return SchedulerType.DURATION;
  }

  @Override
  public long nextFireTime(long after) {
    return after + durationMs;
  }

  @Override
  public String describe() {
    return "every(" + duration + ")";
  }
}
```

#### 3.2.3 Justificación de CRON como default

| Criterio                          | CRON                                  | DURATION                               |
|-----------------------------------|---------------------------------------|----------------------------------------|
| Alineación con calendario humano  | ✅ "viernes 18hs"                      | ❌ deriva con cada restart del server   |
| Determinismo entre reinicios      | ✅ siguiente fire = wallclock          | ❌ depende de `lastRotation` persistido |
| Expresividad                      | ✅ días, semanas, horarios específicos | ❌ solo períodos fijos                  |
| Curva de aprendizaje admin        | ⚠️ requiere conocer cron              | ✅ "30m" es trivial                     |
| Integración con eventos del juego | ✅ "todos los reset diarios"           | ❌ no se alinea                         |

**Decisión**: CRON por default. DURATION queda disponible para casos triviales ("rotación cada hora sin importar
wallclock").

### 3.3 Serialización con Adapter Pattern

#### 3.3.1 Formato JSON resultante

**Antes (todos los campos siempre presentes):**

```json
{
  "type": "NORMAL",
  "products": [
    ...
  ],
  "subShops": [],
  // basura — no aplica a NORMAL
  "rotationSchedule": null
  // basura — no aplica a NORMAL
}
```

**Después (solo campos del tipo):**

```json
{
  "type": "NORMAL",
  "id": "weapons",
  "display": {
    ...
  },
  "economy": {
    ...
  },
  "products": [
    ...
  ]
}
```

```json
{
  "type": "ROTATION",
  "id": "daily_deals",
  "display": {
    ...
  },
  "economy": {
    ...
  },
  "rotationAmount": 3,
  "scheduler": {
    "type": "CRON",
    "expression": "0 0 * * *"
  },
  "productPool": [
    ...
  ]
}
```

```json
{
  "type": "ROTATION",
  "scheduler": {
    "type": "DURATION",
    "duration": "30m"
  }
}
```

#### 3.3.2 Adapter Registry

```java
package com.kingpixel.ultrashop.infrastructure.serialization.shop;

public final class ShopAdapterRegistry {
  private final Map<ShopType, ShopJsonAdapter<? extends Shop>> adapters = new EnumMap<>(ShopType.class);

  public ShopAdapterRegistry() {
    register(ShopType.NORMAL, new NormalShopAdapter());
    register(ShopType.CATEGORY, new CategoryShopAdapter());
    register(ShopType.ROTATION, new RotationShopAdapter());
  }

  public <S extends Shop> void register(ShopType type, ShopJsonAdapter<S> adapter) {
    adapters.put(type, adapter);
  }

  @SuppressWarnings("unchecked")
  public ShopJsonAdapter<Shop> resolve(ShopType type) {
    ShopJsonAdapter<?> adapter = adapters.get(type);
    if (adapter == null) {
      throw new IllegalStateException("No adapter registered for ShopType: " + type);
    }
    return (ShopJsonAdapter<Shop>) adapter;
  }
}
```

#### 3.3.3 Adapter base

```java
public interface ShopJsonAdapter<S extends Shop> {
  JsonObject serialize(S shop, JsonSerializationContext ctx);

  S deserialize(JsonObject json, JsonDeserializationContext ctx);
}

public final class RotationShopAdapter implements ShopJsonAdapter<RotationShop> {

  @Override
  public JsonObject serialize(RotationShop shop, JsonSerializationContext ctx) {
    JsonObject obj = new JsonObject();
    obj.addProperty("type", ShopType.ROTATION.name());
    obj.addProperty("id", shop.getId());
    obj.add("display", ctx.serialize(shop.getDisplay()));
    obj.add("economy", ctx.serialize(shop.getEconomy()));
    obj.add("scheduler", ctx.serialize(shop.getScheduler()));      // delega al SchedulerAdapter
    obj.addProperty("rotationAmount", shop.getRotationAmount());
    obj.add("productPool", ctx.serialize(shop.getProductPool()));
    return obj;
  }

  @Override
  public RotationShop deserialize(JsonObject json, JsonDeserializationContext ctx) {
    return RotationShop.builder()
      .id(json.get("id").getAsString())
      .display(ctx.deserialize(json.get("display"), DisplayConfig.class))
      .economy(ctx.deserialize(json.get("economy"), EconomyConfig.class))
      .scheduler(ctx.deserialize(json.get("scheduler"), Scheduler.class))
      .rotationAmount(json.get("rotationAmount").getAsInt())
      .productPool(ctx.deserialize(json.get("productPool"), Product.LIST_TYPE))
      .build();
  }
}
```

#### 3.3.4 Dispatcher Gson — TypeAdapterFactory

```java
public final class ShopTypeAdapterFactory implements TypeAdapterFactory {
  private final ShopAdapterRegistry registry;

  public ShopTypeAdapterFactory(ShopAdapterRegistry registry) {
    this.registry = registry;
  }

  @Override
  public <T> TypeAdapter<T> create(Gson gson, TypeToken<T> typeToken) {
    if (!Shop.class.isAssignableFrom(typeToken.getRawType())) return null;

    return (TypeAdapter<T>) new TypeAdapter<Shop>() {
      @Override
      public void write(JsonWriter out, Shop shop) throws IOException {
        JsonObject json = registry.resolve(shop.getType())
          .serialize(shop, new GsonContextAdapter(gson));
        gson.toJson(json, out);
      }

      @Override
      public Shop read(JsonReader in) throws IOException {
        JsonObject json = JsonParser.parseReader(in).getAsJsonObject();
        ShopType type = ShopType.valueOf(json.get("type").getAsString());
        return registry.resolve(type).deserialize(json, new GsonContextAdapter(gson));
      }
    }.nullSafe();
  }
}
```

#### 3.3.5 Wiring en CobbleUtils Gson

```java
// ConfigLoader o donde se inicializa el Gson de UltraShop
public static Gson buildGson() {
  return new GsonBuilder()
    .registerTypeAdapterFactory(new ShopTypeAdapterFactory(new ShopAdapterRegistry()))
    .registerTypeAdapterFactory(new SchedulerTypeAdapterFactory(new SchedulerAdapterRegistry()))
    .setPrettyPrinting()
    .create();
}
```

El `SchedulerTypeAdapterFactory` sigue el MISMO patrón — discriminator `"type": "CRON"|"DURATION"` + registry.

---

## 4. Estructura de carpetas propuesta

```
src/main/java/com/kingpixel/ultrashop/
├── domain/
│   ├── model/
│   │   ├── shop/
│   │   │   ├── Shop.java                  ← sealed interface
│   │   │   ├── ShopType.java              ← enum (existente, refactor menor)
│   │   │   ├── AbstractShop.java          ← campos comunes
│   │   │   ├── NormalShop.java
│   │   │   ├── CategoryShop.java
│   │   │   ├── RotationShop.java
│   │   │   ├── ShopVisitor.java           ← Visitor pattern
│   │   │   └── config/                    ← Value Objects de Shop
│   │   │       ├── DisplayConfig.java     ← name, title, rows, panels, items
│   │   │       ├── EconomyConfig.java     ← economies, discounts, globalDiscount
│   │   │       ├── ConditionsConfig.java  ← openConditions, closeCommand
│   │   │       └── SoundConfig.java       ← soundOpen, soundClose
│   │   ├── product/
│   │   │   ├── Product.java               (existente)
│   │   │   ├── ProductLimit.java          (existente)
│   │   │   ├── ProductStats.java          (existente)
│   │   │   └── PriceEntry.java            (existente)
│   │   ├── transaction/
│   │   │   ├── Transaction.java           (existente)
│   │   │   └── ActionShop.java            (existente)
│   │   ├── user/
│   │   │   └── UserInfo.java              (existente)
│   │   └── DataShop.java                  (existente)
│   ├── scheduler/
│   │   ├── Scheduler.java                 ← sealed interface
│   │   ├── SchedulerType.java             ← enum CRON, DURATION
│   │   ├── CronScheduler.java
│   │   ├── DurationScheduler.java
│   │   ├── DynamicRotation.java           ← state runtime (movido)
│   │   └── parser/
│   │       ├── CronExpression.java        (existente, movido)
│   │       └── DurationParser.java        ← extraído de hardcoded "30m"/"4h"
│   └── service/                           (existente — sin cambios estructurales)
│       ├── PriceCalculator.java
│       ├── ProductMatcher.java
│       ├── StatsService.java
│       └── TransactionService.java
│
├── infrastructure/
│   ├── serialization/
│   │   ├── shop/
│   │   │   ├── ShopJsonAdapter.java       ← interface
│   │   │   ├── ShopAdapterRegistry.java   ← Map<ShopType, Adapter>
│   │   │   ├── ShopTypeAdapterFactory.java← Gson dispatcher
│   │   │   ├── NormalShopAdapter.java
│   │   │   ├── CategoryShopAdapter.java
│   │   │   └── RotationShopAdapter.java
│   │   └── scheduler/
│   │       ├── SchedulerJsonAdapter.java
│   │       ├── SchedulerAdapterRegistry.java
│   │       ├── SchedulerTypeAdapterFactory.java
│   │       ├── CronSchedulerAdapter.java
│   │       └── DurationSchedulerAdapter.java
│   ├── config/                            (existente)
│   ├── index/                             (existente)
│   ├── persistence/                       (existente — refactor reciente)
│   └── web/                               (existente)
│
├── presentation/                          (existente — actualiza imports)
└── migrate/
    └── V2ToV3Migrator.java                ← NUEVO: migra JSON viejo (Shop monolítico) al nuevo formato
```

---

## 5. Plan de ejecución por fases

> Cada fase es **mergeable en aislamiento**. No se rompe nada en el medio.

### Fase 1 — Foundation: Value Objects de Shop *(bajo riesgo)*

1. Crear `DisplayConfig`, `EconomyConfig`, `ConditionsConfig`, `SoundConfig` extrayendo los campos del Shop actual.
2. Refactor de `Shop.java` actual para que internamente use los nuevos VOs (sin romper API pública).
3. Tests unitarios por VO (Strict TDD: red → green → refactor).

**Salida**: Shop sigue siendo un god-object pero internamente compone VOs limpios. Cero cambios en JSON.

### Fase 2 — Scheduler discriminator *(riesgo medio — toca persistencia de RotationSchedule)*

1. Crear `domain/scheduler/` con `Scheduler` sealed interface, `SchedulerType` enum, `CronScheduler`,
   `DurationScheduler`, `DurationParser`.
2. Crear `SchedulerJsonAdapter` + `SchedulerAdapterRegistry` + `SchedulerTypeAdapterFactory`.
3. Adapter de compatibilidad: leer JSON viejo (`{cron: ..., interval: ...}`) y mapear a `Scheduler` apropiado:
    - Si `cron` no es null/blank → `CronScheduler`
    - Si `interval` no es null/blank → `DurationScheduler`
    - Default → `Scheduler.defaultScheduler()` (CRON cada hora)
4. Reemplazar uso de `RotationSchedule` por `Scheduler` en `RotationShop` (cuando exista — Fase 3).
5. **Mantener `RotationSchedule` deprecated** durante 1 versión para que configs viejas sigan cargando.

**Salida**: nuevo formato JSON para scheduler funciona, viejo sigue siendo aceptado.

### Fase 3 — Shop hierarchy *(riesgo alto — toca dominio entero)*

1. Crear `Shop` sealed interface + `AbstractShop` + `NormalShop` / `CategoryShop` / `RotationShop`.
2. Crear `ShopVisitor` con métodos `visit(NormalShop)`, `visit(CategoryShop)`, `visit(RotationShop)`.
3. Adapters de serialización por tipo + `ShopAdapterRegistry` + `ShopTypeAdapterFactory`.
4. Migrar GUI builders (`ShopMenuBuilder`, `SearchMenuBuilder`, `StatsMenuBuilder`, `ShopEditMenuBuilder`) para usar
   `ShopVisitor` en lugar de `if (shop.isCategory()) ...`.
5. `V2ToV3Migrator`: lee JSON viejo (Shop monolítico), detecta tipo, escribe nuevo formato.
6. Borrar `Shop.java` viejo.

**Salida**: dominio limpio, JSON nuevo, GUIs migradas, configs viejas auto-migran al primer arranque.

### Fase 4 — Cleanup *(bajo riesgo)*

1. Borrar `RotationSchedule` deprecated.
2. Borrar `Shop(String, boolean)` constructor deprecated.
3. Borrar `Shop.hasCategories()` deprecated.
4. Borrar `Shop.isNormal/isCategory/isRotation()` (reemplazados por `accept(visitor)`).
5. Update CHANGELOG.md siguiendo Keep a Changelog.

---

## 6. Estrategia de testing (Strict TDD)

| Capa                               | Tipo de test     | Ejemplos                                                                  |
|------------------------------------|------------------|---------------------------------------------------------------------------|
| Domain VOs (`DisplayConfig`, etc.) | Unit puros       | igualdad, defaults, builder                                               |
| `CronScheduler.nextFireTime()`     | Unit             | cron conocido + timestamp fijo → resultado determinístico                 |
| `DurationScheduler.nextFireTime()` | Unit             | "30m" + t0 → t0 + 1_800_000                                               |
| `Shop` polymorphism                | Unit con visitor | Visitor que cuenta visitas por tipo                                       |
| Serialización round-trip           | Unit             | `gson.toJson(shop) → fromJson` ≡ shop original (todos los tipos)          |
| Migración v2→v3                    | Integration      | JSON viejo del fixture → carga sin errores → produce JSON nuevo válido    |
| Backwards compat scheduler         | Integration      | JSON con `{cron: "0 * * * *", interval: null}` carga como `CronScheduler` |

**Patrón de test obligatorio**: cada Adapter NUEVO arranca con un test rojo de round-trip antes de implementarse.

---

## 7. Ejemplo de uso post-refactor

### 7.1 Antes (cliente con type-checking)

```java
// ShopMenuBuilder.java actual
if(shop.isCategory()){

renderSubShops(shop.getSubShops());
  }else if(shop.

isRotation()){
long next = shop.getRotationSchedule().getCron() != null
  ? CronExpression.parse(shop.getRotationSchedule().getCron()).nextFireTime(now)
  : now + parseInterval(shop.getRotationSchedule().getInterval());

renderRotation(shop.getProducts(),next);
  }else{

renderProducts(shop.getProducts());
  }
```

### 7.2 Después (Visitor + polimorfismo)

```java
shop.accept(new ShopVisitor<Void>() {
  @Override public Void visit (NormalShop s){
    renderProducts(s.activeProducts());
    return null;
  }
  @Override public Void visit (CategoryShop s){
    renderSubShops(s.getSubShops());
    return null;
  }
  @Override public Void visit (RotationShop s){
    long next = s.getScheduler().nextFireTime(System.currentTimeMillis());
    renderRotation(s.activeProducts(), next);
    return null;
  }
});
```

**Compilador garantiza** que si mañana agregamos `EventDrivenShop`, NINGÚN visitor compila sin manejarlo. Adiós bugs por
olvidos.

### 7.3 Agregar un nuevo ShopType (extensión OCP)

```java
// 1. Nueva implementación
public final class AuctionShop extends AbstractShop { /* ... */
}

// 2. Nuevo adapter
public final class AuctionShopAdapter implements ShopJsonAdapter<AuctionShop> { /* ... */
}

// 3. Registrar
registry.

register(ShopType.AUCTION, new AuctionShopAdapter());

// 4. Compilador exige actualizar ShopVisitor — SOLO los visitors necesitan cambiar.
//    Ningún `if/switch` disperso por el código se rompe silenciosamente.
```

---

## 8. Decisiones técnicas — justificación condensada

| Decisión                                                 | Razón                                                                                       |
|----------------------------------------------------------|---------------------------------------------------------------------------------------------|
| **Sealed interface en vez de abstract class**            | Permite registry exhaustivo en compile time + composición de VOs sin herencia profunda      |
| **Visitor en vez de `instanceof`**                       | Compile-time safety + open/closed: el visitor cambia, el dominio no                         |
| **Adapter Registry vs `instanceof` en TypeAdapter**      | Permite plugins externos registrar tipos sin tocar UltraShop core                           |
| **Discriminator field `"type"`**                         | Convención industrial (Jackson, Avro, Protobuf) — interoperable con cualquier consumer JSON |
| **CRON como Scheduler default**                          | Determinístico entre reinicios, alineado con calendario, expresivo                          |
| **`Scheduler` como sealed interface y NO enum-strategy** | Cada implementación tiene su propio estado/parser cacheado — un enum no puede               |
| **VOs separados (Display, Economy, etc.)**               | Permite reusar configs entre tipos de shop + tests granulares + Single Responsibility       |
| **No usar Optional para Scheduler**                      | `NormalShop` ni `CategoryShop` lo tienen — el tipo lo deja claro, sin Optional ruidoso      |
| **Migrator dedicado (V2→V3)**                            | Carga vieja sigue funcionando sin código de compatibilidad permanente en el dominio         |

---

## 9. Mejoras futuras / Extensiones

### 9.1 Schedulers adicionales

- **`RandomScheduler`**: rotación a intervalo aleatorio entre min/max → "loot box" feel.
- **`EventDrivenScheduler`**: fire en eventos del juego (server start, player join milestone, etc.).
- **`CompositeScheduler`**: dispara cuando CUALQUIERA de N schedulers internos lo haga (`OR`) o solo cuando TODOS
  coincidan (`AND`).

Cada uno se agrega sin tocar `Scheduler` ni código existente — solo registry.

### 9.2 Shop types adicionales

- **`AuctionShop`**: bidding, expiración por scheduler.
- **`StockShop`**: inventario limitado por producto, repone con scheduler.
- **`PlayerShop`**: jugador como dueño, comisión configurable.

### 9.3 Performance

- **Cache de `compiled` en CronScheduler**: ya está en el diseño (`transient CronExpression compiled`).
- **Pool de visitors reutilizables** (stateless): evita allocations en hot paths de la GUI.
- **JSON Streaming en RotationShop**: si el `productPool` crece >1000 items, usar `JsonReader` directo en lugar de
  cargar todo el JsonObject.

### 9.4 Integración con eventos cross-mod

- `ShopRotatedEvent` (Architectury Events) disparado por `RotationShop` cuando el scheduler fire — UltraEconomy,
  UltraQuests, etc. pueden suscribirse.

### 9.5 Testing avanzado

- **Property-based testing** (jqwik) sobre `CronExpression`: cualquier cron válido, `nextFireTime(now)` siempre > now.
- **Mutation testing** (PIT) sobre `Scheduler` impls — garantiza que los tests realmente validan la lógica.

### 9.6 Observabilidad

- Métricas: `scheduler.fire.count{type=CRON|DURATION}`, `shop.activeProducts.size{type=...}`.
- Exponer en el dashboard web actual.

---

## 10. Riesgos y mitigaciones

| Riesgo                                              | Mitigación                                                                                    |
|-----------------------------------------------------|-----------------------------------------------------------------------------------------------|
| Configs en producción rompen al cargar              | Migrator V2→V3 + tests de fixtures con configs reales del repo                                |
| Plugins externos dependen de `Shop` clase concreta  | Mantener `Shop` deprecated 1 versión + comunicar en CHANGELOG                                 |
| Aumento de archivos asusta a contributors           | README de la carpeta `domain/model/shop/` con diagrama y guía "agregar nuevo tipo en 4 pasos" |
| Performance de Visitor allocation                   | Visitors stateless como `static final` constantes; medir antes de optimizar                   |
| Cron parser actual puede tener bugs no descubiertos | Property-based tests en Fase 2 antes de promover CRON a default                               |

---

## 10.b. Cross-server support (multi-instancia)

> El plan original asume **una sola JVM**. Si UltraShop corre en una red BungeeCord/Velocity con N servers fabric apuntando al mismo Mongo, hay 7 problemas que el diseño actual NO resuelve. Esta sección los enumera y propone solución.

### 10.b.1 Diagnóstico de problemas cross-server

| # | Problema | Por qué falla hoy |
|---|---|---|
| 1 | **Cache stale de UserInfo** | `MongoUserRepository` cachea en `ConcurrentMap<UUID, UserInfo>` y solo recarga en `PLAYER_JOIN`. Si el player compra en Server A y salta a Server B, B ve cooldowns viejos. |
| 2 | **Rotación N veces** | Cada server ejecuta `scheduler.nextFireTime()` independientemente → N servers rotan N veces el mismo `RotationShop` con productos potencialmente distintos. |
| 3 | **Productos rotados divergentes** | `RotationShop.currentRotation` es `transient` → cada server tiene un set diferente de productos visibles. Players ven catálogos distintos según en qué server estén. |
| 4 | **Race conditions en stock** | `transactionLocks` es `ConcurrentHashMap` en memoria → solo protege dentro de UN JVM. Dos compras simultáneas en servers distintos del mismo item con stock=1 → se vende 2 veces. |
| 5 | **Configs de shop locales** | Los `.json` viven en disco de cada server. Si admin edita en Server A, B no se entera hasta `/shop reload` manual. |
| 6 | **Clock skew entre servers** | `System.currentTimeMillis()` puede diferir entre nodos → scheduler dispara desfasado. Mongo `serverStatus().localTime` debería ser la fuente de verdad. |
| 7 | **Web dashboard duplicado** | Cada server arranca su propio Jetty en puerto X → conflicto si están en la misma máquina, o N dashboards inconsistentes si están en máquinas distintas. |

### 10.b.2 Solución propuesta — extensión del refactor
 
#### A) Cache invalidation con pub/sub Mongo Change Streams

```java
// domain/sync/CacheInvalidator.java (nuevo)
public interface CacheInvalidator {
    void invalidateUser(UUID uuid);
    void invalidateShop(String shopId);
    void onRemoteInvalidation(Consumer<InvalidationEvent> handler);
}

// infrastructure/sync/MongoChangeStreamInvalidator.java
public final class MongoChangeStreamInvalidator implements CacheInvalidator {
    // Suscribe a Change Stream de la colección "users" + "shop_state".
    // Cuando otro server modifica un doc, dispara el handler local.
}
```

**Implementación**: Mongo 4.0+ soporta Change Streams sobre replica sets. Ya estamos usando Mongo → costo cero de infra extra.

#### B) Rotación coordinada — Leader Election + Distributed Lock

```java
// domain/scheduler/CoordinatedScheduler.java (decorator)
public final class CoordinatedScheduler implements Scheduler {
    private final Scheduler delegate;
    private final DistributedLock lock;
    private final RotationStateRepository stateRepo;

    @Override
    public long nextFireTime(long after) {
        return delegate.nextFireTime(after);
    }

    /** Intenta ejecutar la rotación. Solo el server que adquiere el lock la ejecuta;
     *  los demás leen el resultado persistido. */
    public RotationResult tryRotate(String shopId, RotationStrategy strategy) {
        String lockKey = "ultrashop:rotation:" + shopId;
        return lock.tryWithLock(lockKey, Duration.ofSeconds(10), () -> {
            // Doble-check: ¿alguien ya rotó mientras esperaba el lock?
            RotationResult existing = stateRepo.findActive(shopId);
            if (existing != null && existing.isStillValid()) return existing;

            RotationResult fresh = strategy.pick();
            stateRepo.save(shopId, fresh);
            return fresh;
        });
    }
}
```

**Implementación del lock**: Mongo `findOneAndUpdate` con TTL index sobre colección `distributed_locks` — patrón estándar, sin necesidad de Redis ni Zookeeper.

```java
// infrastructure/sync/MongoDistributedLock.java
public final class MongoDistributedLock implements DistributedLock {
    // Crea índice TTL sobre 'expiresAt' al init.
    // Acquire = upsert con _id=lockKey + expiresAt=now+ttl, falla si existe vivo.
    // Release = deleteOne(_id=lockKey, owner=this.serverId).
}
```

#### C) Estado de rotación en Mongo, no en memoria

Mover `DynamicRotation` de `transient` en `RotationShop` a una colección `shop_rotations` consultada bajo demanda + cacheada localmente con TTL corto (30s) e invalidación vía Change Stream.

```java
// domain/model/shop/RotationShop.java — refactor
public final class RotationShop extends AbstractShop {
    private final List<Product> productPool;
    private final Scheduler scheduler;
    private final int rotationAmount;
    // YA NO transient — se obtiene del repo cross-server:
    // private transient DynamicRotation currentRotation;

    @Override
    public List<Product> activeProducts() {
        // Delegado al RotationStateService (con cache local de 30s).
        return rotationStateService.getCurrent(getId(), this).getProducts();
    }
}
```

#### D) Stock cross-server con Optimistic Locking

```java
// infrastructure/persistence/mongodb/MongoProductStockRepository.java
public boolean tryDecrement(UUID productUuid, int amount) {
    // Mongo update condicional: solo decrementa si stock >= amount.
    UpdateResult r = collection.updateOne(
        Filters.and(
            Filters.eq("_id", productUuid.toString()),
            Filters.gte("stock", amount)
        ),
        Updates.inc("stock", -amount)
    );
    return r.getModifiedCount() == 1;
}
```

**Patrón**: nunca leer-modificar-escribir. El check del stock va dentro del update atómico de Mongo. Los `transactionLocks` actuales se borran (eran un parche para single-JVM).

#### E) Reload de configs vía pub/sub

Dos opciones:

| Opción | Pros | Contras |
|---|---|---|
| **Mongo Change Stream sobre `shop_configs`** | Misma infra, sin deps extras | Configs deberían vivir en Mongo, no en `.json` (cambio grande) |
| **Comando broadcast `/shop reload --all`** | Mantiene `.json` locales | Requiere infra de mensajería entre servers (BungeePluginMessage o similar) |

**Recomendación**: opción A — migrar configs de shop a colección `shop_configs` en Mongo. Es coherente con tener Mongo como source-of-truth y hace trivial el sync.

#### F) Tiempo coordinado

```java
// domain/scheduler/ServerClock.java
public interface ServerClock {
    long currentTimeMillis();
}

// infrastructure/sync/MongoServerClock.java
public final class MongoServerClock implements ServerClock {
    // Cachea offset = (mongo.serverStatus().localTime - System.currentTimeMillis())
    // refresca cada 30s. Devuelve System.currentTimeMillis() + offset.
}
```

Todos los `Scheduler.nextFireTime()` reciben `serverClock.currentTimeMillis()` en lugar de `System.currentTimeMillis()` directo. Adiós divergencia de wallclock entre nodos.

#### G) Dashboard único

Tres opciones:
1. **Solo el "leader" arranca Jetty** — leader election vía mismo lock distribuido. Otros servers exponen redirect HTTP al leader.
2. **Dashboard standalone** — extraer el dashboard a un proceso aparte que solo lee Mongo, sin depender de ningún server fabric.
3. **Reverse proxy externo** (nginx/traefik) hace LB sobre los dashboards de los N servers (asumiendo que son read-only e idempotentes).

**Recomendación**: opción 2 a medio plazo. Opción 1 como solución inmediata.

### 10.b.3 Estructura de carpetas — adiciones

```
domain/
├── sync/                            ← NUEVO
│   ├── CacheInvalidator.java
│   ├── DistributedLock.java
│   ├── ServerClock.java
│   └── RotationStateRepository.java
└── scheduler/
    └── CoordinatedScheduler.java    ← decorator cross-server

infrastructure/
└── sync/                            ← NUEVO
    ├── MongoChangeStreamInvalidator.java
    ├── MongoDistributedLock.java
    ├── MongoServerClock.java
    └── MongoRotationStateRepository.java
```

### 10.b.4 Plan de fases — adiciones

> Estas fases son **independientes del refactor principal** pero deben ir DESPUÉS de Fase 3 (Shop hierarchy ya en su lugar).

| Fase | Trabajo | Riesgo |
|---|---|---|
| **5 — Cross-server foundation** | `ServerClock`, `DistributedLock` (Mongo), `CacheInvalidator` (Change Streams). Tests con Testcontainers Mongo replica set. | Medio — requiere Mongo en replica set (no standalone) |
| **6 — Stock atómico** | Refactor de stock a `findOneAndUpdate` condicional. Borrado de `transactionLocks` in-memory. | Alto — toca camino crítico de compras |
| **7 — Rotación coordinada** | `CoordinatedScheduler` decorator + `RotationStateRepository`. `RotationShop` deja de cachear `currentRotation` local. | Alto — cambia semántica de rotación |
| **8 — Configs en Mongo** | Migrar shop configs de `.json` a `shop_configs` collection. JSON queda solo como import/export. | Alto — toca admin GUI y flujo de carga inicial |
| **9 — Dashboard centralizado** | Leader-election para Jetty O dashboard standalone. | Bajo — feature aislado |

### 10.b.5 Configuración nueva

```yaml
# config.yml (ultrashop)
crossServer:
  enabled: false                     # default OFF — single-server compat
  serverId: "${HOSTNAME:-survival-1}" # identifica el nodo en logs/locks
  rotationLeader: true               # si false, este nodo NUNCA intenta rotar
  cacheInvalidation:
    enabled: true
    backend: MONGO_CHANGE_STREAM     # futuro: REDIS_PUBSUB
  distributedLock:
    backend: MONGO                   # futuro: REDIS, ZOOKEEPER
    defaultTtlSeconds: 10
  clock:
    backend: MONGO_SERVER_TIME       # futuro: NTP, SYSTEM
    syncIntervalSeconds: 30
```

### 10.b.6 Requisitos de infraestructura

| Requisito | Razón | Costo |
|---|---|---|
| **Mongo replica set** (mínimo 1 primary + 1 arbiter) | Change Streams requieren oplog | $0 si ya hay Mongo, ~5min de setup |
| **NTP sincronizado entre nodos** | Reduce drift incluso con `MongoServerClock` | $0 — viene en cualquier OS |
| **Network entre servers** ↔ Mongo con latencia <50ms | Lock acquisition en hot path de compra | Asumido en cualquier deploy serio |

**NO requiere**: Redis, Kafka, RabbitMQ, Zookeeper. Todo se hace con la Mongo que ya tenés (gracias al pool compartido de CobbleUtils).

### 10.b.7 Lo que sigue siendo single-server (intencional)

- **GUI**: cada player abre la GUI en SU server actual. No hay "sesión cross-server" — las invalidaciones son del estado, no de la UI.
- **Async tasks** (`UtilsAsync`): cada server procesa sus propios async; los workers NO se distribuyen.
- **Logs**: locales por server. Si querés agregación → ELK / Loki, fuera del scope de UltraShop.

### 10.b.8 Riesgos específicos cross-server

| Riesgo | Mitigación |
|---|---|
| Lock holder crashea sin liberar | TTL en el lock (ya en el diseño) — auto-libera tras N segundos |
| Change Stream se desconecta | Reconnect con resume token + full reload de cache al recuperar |
| Network partition (split brain) | Mongo replica set con majority write concern → solo el lado mayoritario puede escribir |
| Migración de single → cross sin downtime | Fase 5 NO requiere downtime; Fases 6-8 sí pueden requerir restart coordinado |
| Stale rotation visible 30s post-cambio | TTL del cache local — aceptable para UX. Si no, bajar a 5s con costo de más reads. |

### 10.b.9 Resumen

> El refactor de Shop + Scheduler es **necesario pero NO suficiente** para cross-server. El diseño actual ya facilita la transición (sealed types + adapters + repos por backend), pero faltan **5 componentes nuevos**: `DistributedLock`, `ServerClock`, `CacheInvalidator`, `RotationStateRepository`, `CoordinatedScheduler`. Todo se implementa sobre Mongo (ya disponible) — sin Redis ni infra adicional. Las Fases 5-9 son **opt-in vía config flag** (`crossServer.enabled: true`), preservando compat con deploys single-server.

---

## 11. Checklist de aceptación

- [ ] `Shop.java` god-object eliminado, reemplazado por sealed hierarchy
- [ ] `ShopVisitor` cubre todos los subtipos (compilador valida)
- [ ] Cero `instanceof Shop` o `shop.getType() == X` en `presentation/`
- [ ] `Scheduler` con `SchedulerType` enum, default `CRON`
- [ ] JSON nuevo SOLO contiene campos del tipo declarado
- [ ] Migrator V2→V3 carga el 100% de los shops fixture del repo sin warnings
- [ ] Cobertura de tests >80% en `domain/scheduler/` y `infrastructure/serialization/`
- [ ] CHANGELOG.md actualizado siguiendo Keep a Changelog
- [ ] README de `domain/model/shop/` con guía de extensión
- [ ] Cero warnings de `@Deprecated` en el código propio (los deprecated viejos eliminados en Fase 4)

### 11.b Cross-server (opt-in, Fases 5-9)

- [ ] `crossServer.enabled` flag de config con default `false` (compat single-server)
- [ ] `DistributedLock` con TTL implementado sobre Mongo + tests con replica set
- [ ] `ServerClock` cachea offset de Mongo serverTime; refresh cada 30s
- [ ] Stock se decrementa con `findOneAndUpdate` condicional, NUNCA leer-modificar-escribir
- [ ] `CoordinatedScheduler` garantiza UNA sola rotación por shop por ciclo en N servers
- [ ] `RotationShop.activeProducts()` consistente entre servers (delta máximo: TTL de cache local)
- [ ] Change Stream sobre `users` invalida cache local al detectar cambio remoto
- [ ] Test: 3 instancias UltraShop concurrentes vendiendo el mismo item con stock=10 → exactamente 10 ventas exitosas
- [ ] Test: 3 instancias rotando el mismo `RotationShop` cada minuto → 1 sola rotación por minuto, todos ven los mismos productos
- [ ] Documentación de setup Mongo replica set en `docs/cross-server-setup.md`

---

## 12. Resumen ejecutivo

> Pasamos de un **god-object** (`Shop` 232 líneas con 3 modos coexistiendo) a una **jerarquía sealed** con dispatch
> polimórfico, donde cada tipo de Shop tiene su propio adapter de serialización registrado en un Registry. El Scheduler
> queda discriminado por `SchedulerType` enum (CRON default, DURATION secundario), reemplazando el patrón actual donde
`cron` "pisa" `interval` por convención implícita. Todo el refactor es **incremental en 4 fases mergeables** y
> backwards-compatible vía Migrator V2→V3.

**Beneficio medible**: agregar un `ShopType.AUCTION` post-refactor → 3 archivos nuevos, 0 modificaciones a código
existente. Hoy → 1 archivo modificado por cada `if (shop.isX())` regado por la codebase.

**Cross-server (Fases 5-9)**: opt-in vía `crossServer.enabled=true`. Sin infra extra (todo sobre la Mongo que ya tenés). Garantiza coherencia de stock, rotaciones únicas y cache invalidation entre N nodos fabric apuntando al mismo cluster.

