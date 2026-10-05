# Historias de usuario derivadas para S3

Estas historias se derivan del PRD v1.0 (RF-07 a RF-12, RN-01 a RN-08, RF-20) y del alcance explícito de S3 autorizado por el usuario el 2026-10-04. No son HU preexistentes ni sustituyen al PRD. HU-001–004 siguen Parciales; HU-005 se completó para el runner local sin Git solicitado en el GOAL vigente.

## Stack observado

Backend Java 21, Spring Boot 3.5.6, Maven, JPA, Flyway, MySQL 8.4; frontend React 19 + TypeScript 6 + Vite 8. El contrato es REST directo, sin BFF.

## Épicas

- [EP-001 — Agendamiento de citas](epicas/EP-001-agendamiento-de-citas.md)
- [EP-002 — Red automatizada de verificación](epicas/EP-002-red-de-verificacion.md)

## Historias

- [HU-001 — Administrar profesionales y especialidades](historias-de-usuario/HU-001-administrar-profesionales-y-especialidades.md)
- [HU-002 — Gestionar disponibilidad y reservar consultas](historias-de-usuario/HU-002-gestionar-disponibilidad-y-reservar-consultas.md)
- [HU-003 — Resolver solicitudes especializadas](historias-de-usuario/HU-003-resolver-solicitudes-especializadas.md)
- [HU-004 — Cargar datos ficticios de desarrollo](historias-de-usuario/HU-004-cargar-datos-ficticios-de-desarrollo.md)
- [HU-005 — Bloquear cambios que incumplen verificaciones](historias-de-usuario/HU-005-bloquear-cambios-invalidos.md)

## Decisiones e incertidumbres

- El PRD conserva `USER` como actor de paciente. A solicitud del usuario, la implementación S3 incorporará el rol `PATIENT`; las cuentas S2 existentes sin rol se mapearán a `PATIENT` en migración.
- La duración se mantiene conforme a PRD RF-09: es dato único de la especialidad (30 o 60 minutos); los profesionales no la sobrescriben. La solicitud del usuario de variantes de duración por doctor general queda subordinada a esta decisión PRD-first.
- La categoría GENERAL/ESPECIALISTA y su aprobación sí se derivan del prompt S3; se deducirá del profesional y no se solicitará al paciente.
- No existe repositorio Git y el GOAL vigente prohíbe iniciarlo; no se realizarán commits ni hooks. HU-005 usa `scripts/verify.ps1`, ya probado en PASS/FAIL.
- S4 queda bloqueada hasta cumplir S3 A8, incluido el hook y su evidencia, tal como exige el orden de trabajo.
- El portal y la API de citas especializadas están conectados en código, pero el GOAL avanzado cross-repo no se ha ejecutado ni demostrado en browser con datos del seed.

## Trazabilidad y estado observado

| HU | Criterios de aceptación | Pruebas que la cubren | Evidencia (log/loop) | Estado |
|---|---|---|---|---|
| HU-001 | CA-01–03: CRUD profesional/asignaciones, catálogo válido | `AppointmentApiIntegrationTest.adminCanCreateAndUpdateProfessionalAssignments` (incluye 403 al intentar crear como PATIENT); `AuthApiIntegrationTest.patientCannotAccessAdminAppointmentQueue`; frontend test de guarda | [Evidencias S3](../../../wiki/wiki/evidencias-s3.md) | Parcial; no hay CRUD del catálogo y la creación requiere usuario existente |
| HU-002 | CA-01–06: propiedad/disponibilidad, reserva y slots | `AppointmentApiIntegrationTest` (10 casos, incluye concurrencia, rango 60 min, fecha pasada, grilla, propiedad y duración derivada); test UI `SlotGrid` | [Evidencias S3](../../../wiki/wiki/evidencias-s3.md) | Parcial; faltan filtros/rutas completos y flujo browser autenticado |
| HU-003 | CA-01–03: aprobación/rechazo, roles y transición | `specializedBookingIsRequestedAndAdminDecisionIsOneWay`, `adminRejectionReleasesReservedSlots` | [Evidencias S3](../../../wiki/wiki/evidencias-s3.md) | Parcial; API probada, interacción UI aún sin e2e |
| HU-004 | CA-01–03: guard de entorno, cantidades, datos sintéticos | `DevelopmentSeedIntegrationTest.seedCanRunTwiceWithoutAddingDuplicates` | [Evidencias S3](../../../wiki/wiki/evidencias-s3.md) | Parcial; se probó idempotencia en H2, no guard fuera de dev ni carga MySQL live |
| HU-005 | CA-01–03: runner local, salida FAIL/PASS y escaneo de secretos (sin Git) | `scripts/verify.ps1`; Maven (22), frontend (6), scanner; escenario de aserción rota y fixture falso | [Evidencias S3](../../../wiki/wiki/evidencias-s3.md) | Done para HU-005; Red→Green funcional global y demás HU siguen pendientes |
