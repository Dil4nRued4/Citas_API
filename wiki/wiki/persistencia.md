# Persistencia de referencia

La solución de referencia vive en [`database/reference/db.sql`](../../../database/reference/db.sql); requisitos de normalización en [`database/REQUISITOS_NORMALIZACION_3FN.md`](../../../database/REQUISITOS_NORMALIZACION_3FN.md). El SQL no es una migración del backend.

El modelo de referencia contiene entidades para usuarios, roles, tokens de refresh/reset, afiliación EPS/plan/régimen, sedes/especialidades/profesionales, agenda y slots, citas e historial, y solicitudes de reprogramación. La Wiki global mantiene [síntesis de modelo](../../../wiki/wiki/modelo-de-datos.md).

Para autenticación, el código añade `users` y `refresh_tokens` con migración [`V1__create_auth_tables.sql`](../../src/main/resources/db/migration/V1__create_auth_tables.sql); refresh tokens se guardan como hash SHA-256 con expiración/revocación. Es un esquema propio del primer incremento, no una adopción completa del SQL de referencia.

## Ejecución verificada (2026-10-04)

Las migraciones Flyway V1–V3 se aplicaron contra MySQL del perfil Compose; el log del 2026-10-04 reportó versión de schema 3. Hibernate inicialmente rechazó el esquema porque validaba `token_hash` como `VARCHAR(64)` mientras V1 lo define `CHAR(64)`. Se corrigió únicamente la entidad `RefreshTokenEntity` (`columnDefinition = "CHAR(64)"`); no se modificó V1, pues ya estaba aplicada al volumen persistente. La API inició y el test dirigido pasó 21 pruebas en total (9 citas, 11 auth, 1 seed); detalle en [evidencias](evidencias-s3.md).

V2 añade `users.role` con default `PATIENT`, `sites`, `specialties`, `professional_profiles`, asignaciones `professional_specialties`/`professional_sites`, `availability_blocks`, `appointments` y `appointment_slots`. V3 añade `specialties.category`, claves idempotentes opcionales `seed_key` para disponibilidad y citas y restricciones únicas. La reserva ocupa una fila de slot por cada 30 minutos; `uq_appointment_reserved_slot` garantiza que un profesional no tenga dos reservas que empiecen en la misma media hora. Citas `REQUESTED` retienen slots; rechazo elimina las filas. La aplicación usa transacción y bloqueo del profesional además de la unicidad de BD.

El test de seed H2 se ejecuta dos veces y confirma cantidades, pero **no** se ejecutó contra el esquema persistente MySQL de dev. La migración desplegada localmente y los tests no certifican instalación independiente ni producción. V2 ya está aplicada al volumen; no editar migraciones aplicadas.
