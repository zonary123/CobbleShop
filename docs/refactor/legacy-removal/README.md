# Legacy Shop Removal — Índice de Lotes

> **Modo de ejecución elegido**: **B (Lotes)** — agrupa pasos relacionados en
> commits coherentes, balance entre velocidad y bisectabilidad.

## Estado global

| Lote | Pasos | Riesgo | Estado | Commit |
|------|-------|--------|--------|--------|
| [1](./BATCH-1_helpers-y-readonly.md) | 0 + 1 + 2 | Low/Med | 🔴 Pendiente | — |
| [2](./BATCH-2_datashop.md) | 3 | Medium | 🔴 Pendiente | — |
| [3](./BATCH-3_editor.md) | 4 | **High** | 🔴 Pendiente | — |
| [4](./BATCH-4_cleanup.md) | 5 + 6 + 7 | Low/Med | 🔴 Pendiente | — |

## Reglas que aplican a TODOS los lotes

1. **Compila al final del lote** — `gradle compileJava` verde.
2. **Tests verdes** — `gradle test` sin regresiones.
3. **Cero `TODO`/`FIXME` legacy nuevos** — si un caso es muy complejo,
   se documenta acá y se plantea como lote aparte.
4. **Commit conventional**: `refactor(shop): remove legacy Shop — batch N (...)`.
5. **NO se hace build del mod** (regla del usuario).
6. **NO se mezcla cross-server** — Fases 5-9 quedan para después.

## Criterio de éxito global

Al terminar Lote 4:
- `grep -r "domain.model.Shop "` → 0 matches
- `grep -r "RotationSchedule"` → 0 matches
- `grep -r "ShopBridge"` → 0 matches
- Los archivos `domain/model/Shop.java`, `RotationSchedule.java`,
  `ShopBridge.java`, `migrate/Old*.java` no existen.
- README de `domain/model/shop/` con guía "agregar nuevo ShopType en N pasos".

## Rollback

Cada lote es un commit. Rollback = `git revert <sha-del-lote>`. NO hay estados
intermedios committeados — si un lote no termina, queda en WIP local hasta que
compile + tests verdes.

## Próximo paso después del Lote 4

Volver al usuario con las **5 decisiones de cross-server** (rol DEV/MEMBER,
formato configs en Mongo, hot-reload trigger, replica set, identidad del nodo)
documentadas en `SHOP_AND_SCHEDULER_REFACTOR.md` § 10.b.

