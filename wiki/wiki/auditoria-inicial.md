# Auditoría inicial backend S3/S4

**Fecha:** 2026-10-04 · **Mantenida por:** agente de Codex.

## Evidencia de línea base

Comando real:

```powershell
docker compose --profile dev stop citas-api-dev
docker compose --profile dev run --rm --no-deps citas-api-dev mvn -B -ntp test
docker compose --profile dev up -d citas-api-dev
```

Resultado:

```text
AppointmentApiIntegrationTest: 9 tests, 0 failures, 0 errors
AuthApiIntegrationTest: 11 tests, 0 failures, 0 errors
DevelopmentSeedIntegrationTest: 1 test, 0 failures, 0 errors
Tests run: 21, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

Después del reinicio, `GET /auth/me` sin token devolvió HTTP 401. El contenedor se mostró `Up`; MySQL quedó `healthy`. Las pruebas corrieron contra H2, no contra la DB persistente.

## Inventario comprobado

- Hay controladores `AuthController` y `AppointmentController`; este último expone catálogo de lectura, profesionales, disponibilidad, reserva, mis citas y decisiones administrativas.
- El código de citas y migraciones actuales llega a V3. Las pruebas existentes cubren auth, reglas de reserva/disponibilidad, permisos/decisiones y la idempotencia del seed.
- No se encontraron endpoints/código para recuperación de contraseña, cancelación, reprogramación, historial, agenda/cierre de atención, ni CRUD de EPS/planes/especialidades, búsqueda por esos recursos o afiliación paciente.
- El test de seed ejecuta dos veces contra H2 y comprueba cantidades; no se corrió seed en MySQL. No se usó `.env` ni se copiaron valores de configuración privados.
- No hay `verify` ni escáner de secretos en el backend al inicio. No hay `.git`; por instrucción de este GOAL no se inicializa ni se crea hook.

## Requisitos de persistencia pendientes

El PRD exige auditoría append-only y relación de EPS/plan al paciente; profesionales asociados al catálogo de especialidades; solicitudes de cancelación/reprogramación con reserva e historial; registro de cambios de estado; y recuperación mediante token temporal de un uso guardado hasheado. No se confunde el SQL de referencia con esas implementaciones.

## Siguiente referencia

La síntesis de reconciliación y estados de las tres capas está en la [auditoría global](../../../wiki/wiki/auditoria-inicial-mvp.md). Contrato actual: [contrato de citas](contrato-citas.md). Ejecución completa de la línea base: [evidencias S3](evidencias-s3.md).
