# Índice de la Wiki de `citas-api`

Wiki propia del backend, mantenida por su agente Codex y enlazada con la [Wiki global del workspace](../../../wiki/wiki/index.md) y la [Wiki de `citas-web`](../../../citas-web/wiki/wiki/index.md).

## Backend

- [Estado del repositorio](estado-del-repositorio.md) — artefactos presentes, verificaciones y bloqueadores.
- [Auditoría inicial S3/S4](auditoria-inicial.md) — línea base backend ejecutada y faltantes comprobados.
- [Responsabilidades y requisitos backend](responsabilidades.md) — reglas asignadas al backend según PRD/prompts.
- [Persistencia de referencia](persistencia.md) — resumen del modelo de datos que debe validarse al inicializar implementación.
- [Contrato de autenticación](contrato-auth.md) — endpoints, cuerpos, respuestas y reglas implementados en código para sesión 2.
- [Contrato de citas](contrato-citas.md) — endpoints, roles, reglas de S3 en implementación y gaps de verificación.
- [Evidencias S3](evidencias-s3.md) — salidas ejecutadas de Maven, Flyway, HTTP, frontend y limitaciones.
- [Historias Scrum S3](../../docs/wiki/scrum/README.md) — HU/DoD derivadas de PRD con autorización explícita; evidencia por CA y estados.
- [Log local](log.md) — registro append-only de cambios documentales/backend.

## Gobierno

- [Operación](../schema/operacion.md) — ingest, query, learn y lint.
- [Instrucciones Codex](../AGENTS.md) — alcance y evidencia local.
- [Fuentes curadas](../raw/README.md) — estado de las fuentes locales e inmutabilidad.
- [Wiki global](../../../wiki/wiki/index.md) — contexto compartido, estado del workspace y requisitos.
- [Wiki frontend](../../../citas-web/wiki/wiki/index.md) — integración y estado de cliente.
- [Stories S3](../../docs/wiki/scrum/README.md) — historias derivadas del PRD RF-07—RF-12; todavía no cerradas.

## Fuentes

- [README backend](../../README.md)
- [PRD global](../../../PRD.md)
- [Restricciones técnicas](../../../RESTRICCIONES_TECNICAS.md)
- [SQL de referencia](../../../database/reference/db.sql)
- [Prompt del agente backend](../../../prompts/agents/PROMPT_AGENT_CITAS_API.md)

## Plan S3 y decisiones

- Las historias/CA derivadas desde PRD están en [docs/wiki/scrum](../../docs/wiki/scrum/README.md).
- Backend S3 parcial: migraciones hasta V4 aplicadas, API y seed implementados; última suite `verify` 26/26 pasa. El horario semanal seeded para los ocho médicos es L–V 07:00–16:00 `America/Bogota`, en una sede de agenda por médico. `scripts/verify.ps1` sin Git tiene evidencia PASS/FAIL, scanner de secreto ficticio y Red→Green de rango de slots.
- El usuario decidió priorizar PRD RF-09: duración única de 30 o 60 min por especialidad.
- Usuario autorizó migrar actores de registro a rol `PATIENT`; PRD los nombra `USER`.
- Git ausente y el usuario prohíbe inicializarlo: no hay commits ni hook; para este GOAL el hook queda sustituido por runner local. S4 aún no se ha implementado, y el resto de gaps S3 sigue abierto.
- Seed probado dos veces en test aislado; horario recurrente aplicado una vez a MySQL local mediante `SEED_WEEKLY_SCHEDULES_ON_START` sin cambiar cuentas/citas. Ambos flags de carga automática quedaron en `false`; el flujo browser autenticado de booking sigue pendiente.
- Red→Green de límite de slots de 60 min pasó; cobertura completa S3, e2e y cierre siguen pendientes.
- No existe `.git` ni hook; el script verify desacoplado de Git tiene escenarios observados PASS/FAIL. No se realizaron commits.
