# Contrato de citas (S3 — implementación parcial)

## Estado

**Implementación parcial verificada.** Backend Java compila; migración Flyway 4 está aplicada en MySQL 8.4 local. La última ejecución de `verify` pasó 26 pruebas backend y 9 frontend; la prueba del seed se repite en H2. Se cargaron reglas semanales en la base local mediante el modo enfocado, sin tocar usuarios ni citas existentes. La evidencia exacta y sus límites están en [evidencias S3](evidencias-s3.md). No se probó un booking con sesión de paciente en el browser.

Los roles se resuelven en BD por petición; el JWT transporta `sub`, `type`, `iat`, `exp`, `jti`. El registro existente S2 asigna `PATIENT`. Autorización por método usa `@PreAuthorize`; los endpoints no listados bajo `/auth` requieren token.

## Catálogos y profesionales

| Método y ruta | Rol | Body/query | Respuesta prevista |
|---|---|---|---|
| `GET /api/catalog/specialties` | Autenticado | — | Lista `{id,name,durationMinutes,category}` activa. La duración proviene del catálogo, 30/60. |
| `GET /api/catalog/sites` | Autenticado | — | Lista `{id,code,name,address}` de sedes activas. |
| `GET /api/professionals?type=&specialtyId=` | `PATIENT` | Filtros opcionales | Profesionales activos con tipo, especialidades y sedes asignadas. Tipo solo `GENERAL` o `ESPECIALISTA`. |
| `GET /api/admin/professionals?type=&specialtyId=` | `ADMIN` | Filtros opcionales | Mismo listado activo para administración. |
| `POST /api/admin/professionals` | `ADMIN` | `{userId,type,description,licenseCode,specialtyIds,siteIds}` | `201`, profesional creado/asignaciones reemplazadas. Usuario existente pasa a `PROFESSIONAL`. |
| `PUT /api/admin/professionals/{userId}` | `ADMIN` | `{userId,type,description,licenseCode?,specialtyIds,siteIds}`; `userId` no cambia | `200`, profile/asignaciones sustituidos. Omitir código preserva el anterior. |
| `DELETE /api/admin/professionals/{userId}` | `ADMIN` | — | `204`, desactivación lógica. |

## Disponibilidad

| Método y ruta | Rol | Body/query | Respuesta prevista |
|---|---|---|---|
| `GET /api/professional/availability?from={ISO-8601}&to={ISO-8601}` | `PROFESSIONAL` (propio) | Instantes UTC; rango máximo 31 días | Lista de bloques propios. |
| `POST /api/professional/availability` | `PROFESSIONAL` (propio) | `{siteId,startsAt,endsAt}` | `201`, bloque creado. |
| `PUT /api/professional/availability/{id}` | `PROFESSIONAL` (propio) | Mismo body | `200`, bloque actualizado si no tiene citas comprometidas. |
| `DELETE /api/professional/availability/{id}` | `PROFESSIONAL` (propio) | — | `204` si no hay citas comprometidas. |
| `GET /api/availability?professionalId=&specialtyId=&siteId=&from=&to=` | `PATIENT` | Instantes UTC; rango máximo 31 días | Franjas libres que cubren la duración de especialidad. |

Los bloques deben ser futuros y estar alineados a media hora, no solaparse, y usar sede asignada. Duración por especialidad sigue PRD RF-09. El endpoint de disponibilidad revisa todos los slots de 30 min requeridos. Propiedad de bloque ajeno, grilla incorrecta, solapamiento y fecha pasada se probaron en la suite.

La disponibilidad también puede ser semanal recurrente. `GET /api/availability` genera ventanas según su día ISO laboral, horas locales y zona IANA (`America/Bogota` para el horario seeded), convierte cada fecha a instantes y excluye slots reservados. Al reservar, el servicio permite el booking si la duración completa cabe en un bloque fechado o en la regla semanal de esa fecha/sede; no permite fines de semana ni horas fuera del horario. Se conserva el máximo de rango de consulta de 31 días.

## Citas

| Método y ruta | Rol | Body/query | Respuesta prevista |
|---|---|---|---|
| `POST /api/appointments` | `PATIENT` | `{professionalId,specialtyId,siteId,startsAt}` | `201`, duración y tipo se deducen, estado `APPROVED` si GENERAL y `REQUESTED` si ESPECIALISTA. |
| `GET /api/appointments/mine` | `PATIENT` | — | Solo citas del paciente autenticado. |
| `GET /api/admin/appointments?status=` | `ADMIN` | Filtro opcional `APPROVED`, `REQUESTED`, `REJECTED` | Lista general. |
| `POST /api/admin/appointments/{id}/decision` | `ADMIN` | `{decision:"APPROVE"}` o `{decision:"REJECT",reason:"..."}` | `200`, decisión a partir solo de `REQUESTED`. Rechazo exige motivo y libera slots. |

La tabla `appointment_slots` tiene restricción única `(professional_id, slot_starts_at)`; una cita de 60 min escribe dos filas. La reserva ocurre en transacción bajo lock del profesional; conflicto de índice se traduce a 409. Citas `REQUESTED` ocupan los slots hasta que son rechazadas.

## Errores

- 400: payload inválido, rango inválido, duración/grilla inválida.
- 401: no hay token o JWT inválido/expirado (filtro Spring).
- 403: rol incorrecto, sede fuera de asignaciones o agenda ajena.
- 404: profesional/especialidad/sede/bloque/cita no existe.
- 409: solapamiento, fuera de agenda, slot ya reservado, citas comprometidas en bloque o transición desde estado diferente de `REQUESTED`.

La capa API devuelve `{status,message}` mediante `ApiExceptionHandler`. La suite comprueba el 401 sin credenciales, 403 para rol PATIENT frente a decisión administrativa y 403 al editar disponibilidad ajena, y 409 en doble reserva, fuera de bloque y transición repetida. No todos los endpoints y códigos enumerados se han verificado mediante HTTP.

## Persistencia

Flyway V2 agrega `users.role` con valor default `PATIENT` (preserva cuentas V1), sedes, especialidades, perfiles profesionales, asignaciones, bloques, citas y slots. V3 agrega la categoría del catálogo y las claves idempotentes del seed. V4 agrega `professional_weekly_schedules`, con día ISO laboral, horas locales, zona horaria, sede y clave de seed. Las migraciones aplicadas no se modifican. Definiciones: [`V2__create_appointment_domain.sql`](../../src/main/resources/db/migration/V2__create_appointment_domain.sql), [`V3__classify_specialties_and_seed_keys.sql`](../../src/main/resources/db/migration/V3__classify_specialties_and_seed_keys.sql) y [`V4__add_weekly_availability.sql`](../../src/main/resources/db/migration/V4__add_weekly_availability.sql). El tipo/duración de cita y sus slots se derivan del profesional/especialidad.

### Seed de profesionales de desarrollo

El código del seed conserva ocho profesionales de demostración: tres generales y cinco especialistas. Los tres generales tienen los nombres Mateo Silvestre Fernández, Valeria Sofía Montenegro y Damián Alejandro Cruz; los especialistas Lucía Beatriz Navarro, Esteban Gabriel Pineda y Camila Victoria Sotomayor quedan asignados respectivamente a Cardiología, Dermatología y Pediatría. Los otros dos especialistas se conservan. Los ocho tienen cinco reglas recurrentes (lunes–viernes), 07:00–16:00 hora `America/Bogota`, en una sede de agenda cada uno. Se preserva la sede elegida previamente por el seed: doctor3 usa HIC, doctor6 ICV, y doctor8 su única sede HIC. `DevelopmentSeedIntegrationTest` verifica 40 reglas, rango horario, zona, sedes e idempotencia.

El seed completo no se aplica automáticamente en Compose por defecto. Para actualizar solo las reglas semanales en una DB de desarrollo ya cargada, Compose ofrece `SEED_WEEKLY_SCHEDULES_ON_START=true`; el modo enfocado elimina bloques con claves seed anteriores y reemplaza únicamente reglas `s3-weekly-*`, sin resetear credenciales ni citas. Debe dejarse en `false` después de la carga y no usarse en producción. La carga local del 2026-10-04 confirmó 40 reglas para 8 médicos/5 días y que los conteos de usuarios/citas no cambiaron. `SEED_ON_START` permaneció en `false`.

## Incertidumbres / faltantes de S3

- No hay CRUD administrativo para crear/editar el catálogo de especialidades ni sedes.
- Crear profesional requiere que ya exista un usuario; no hay endpoint de administración de cuentas en este incremento.
- La lista de staff/appointments no tiene paginación todavía.
- El horario recurrente seeded no tiene UI para edición semanal; la UI profesional actual sigue administrando bloques fechados.
- El test del seed verifica reglas recurrentes e idempotencia en H2; la carga enfocada live verificó las filas y la preservación de conteos, pero no se ejecutó consulta de slots con token PATIENT en el browser.
- No hay historial append-only S3; PRD requiere auditoría y el prompt extiende máquina/historial a S4.
- S3 aún carece de una batería exhaustiva de escenarios 30/60 y validación del flujo UI con un dataset operativo. Una regresión de rango para citas de 60 minutos se corrigió con prueba Red→Green; ver [evidencias](evidencias-s3.md).
- No se configuró hook ni se ejecutaron ciclos Builder/Verifier. No iniciar S4 hasta cerrar A8.
