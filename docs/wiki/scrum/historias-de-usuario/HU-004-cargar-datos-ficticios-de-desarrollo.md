---
id: HU-004
tipo: historia-de-usuario
titulo: "Cargar datos ficticios de desarrollo"
estado: "En desarrollo"
epica: "[[EP-001-agendamiento-de-citas]]"
esfuerzo: "Alto"
dependencias:
  - "[[HU-001-administrar-profesionales-y-especialidades]]"
  - "[[HU-002-gestionar-disponibilidad-y-reservar-consultas]]"
  - "[[HU-003-resolver-solicitudes-especializadas]]"
relacionadas: []
---

# HU-004 — Cargar datos ficticios de desarrollo

## Historia de usuario

**COMO** desarrollador del laboratorio  
**QUIERO** cargar datos sintéticos de manera repetible y segura  
**PARA** explorar las vistas por rol con escenarios previsibles.

## Reglas de negocio

- Solo ejecución en entorno de desarrollo; nunca producción.
- Idempotente: dos ejecuciones no duplican usuarios, profesionales, especialidades, bloques ni citas.
- Correos de prueba bajo `example.com`; credencial configurable fuera del código mediante `SEED_DEFAULT_PASSWORD`.
- Disponibilidad se calcula con fechas relativas a la ejecución, no fechas pasadas fijas.
- Las pruebas usan datos propios y no dependen del seed.

## Criterios de aceptación

### CA-01 — Entorno
**Dado** entorno distinto a development **cuando** se invoca seed **entonces** aborta explícitamente sin modificar DB.

### CA-02 — Idempotencia y cantidades
**Dado** entorno dev **cuando** se ejecuta seed dos veces **entonces** hay exactamente 1 ADMIN, 5 PATIENT, 8 PROFESSIONAL, 3 profesionales GENERAL, 5 ESPECIALISTA, 3 citas generales APPROVED, 3 REQUESTED y 1 REJECTED, además de escenarios de disponibilidad documentados.

### CA-03 — Datos seguros
**Dado** ejecución de seed **cuando** se crean cuentas **entonces** solo se persisten hashes de contraseña y los nombres son ficticios.

## Definition of Done

- [ ] Prueba de seed corre dos veces y compara cantidades esperadas.
- [ ] Variable de contraseña seed no tiene un secreto real predeterminado ni se registra.
- [ ] Seed guardado en un comando simple y configurado para dev si `SEED_ON_START=true`.
- [ ] No se ejecuta en producción y las pruebas no dependen de él.

## Evidencia

| Criterio | Resultado | Evidencia | Observación |
|---|---|---|---|
| CA-01 | Parcial | `DevelopmentSeed.populate()` guarda entorno `development` como precondición | No se ha ejecutado un test que invoque seed con un entorno de producción para probar que aborta sin mutar datos. |
| CA-02 | Verificado en H2 | `DevelopmentSeedIntegrationTest.seedCanRunTwiceWithoutAddingDuplicates` | Dos ejecuciones confirman cantidades de usuarios/perfiles/citas, 98 bloques y un doctor sin disponibilidad. |
| CA-03 | Parcial | Código `DevelopmentSeed` y datos `@example.com` | Nombres/sedes son ficticios y contraseña viene de `SEED_DEFAULT_PASSWORD`; se inspecciona en código, no se validó carga live. |

Estado global: **Parcial; no Done**. Seed idempotente está comprobado en fixture aislado. No se ejecutó en MySQL persistente ni se verificó variable configurada durante arranque real de Compose.
