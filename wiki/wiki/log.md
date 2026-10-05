# Log de la Wiki backend

Registro append-only. No es evidencia de una implementación ni de tests ejecutados.

## [2026-10-04] implement | Horario semanal recurrente y reservas

- V4 agrega reglas semanales; el servicio genera disponibilidad L–V 07:00–16:00 `America/Bogota` y valida las reservas antes de persistirlas.
- El seed enfocado carga reglas sin refrescar usuarios ni citas. La DB local quedó con 40 reglas para los ocho médicos y el flag de carga desactivado.
- Suite backend completa: 26/26; incluye disponibilidad por zona horaria, rechazo fuera de horario y lectura de la cita reservada en `/mine`.
- Sin reserva live en browser. Evidencia y límites en [S3](evidencias-s3.md).

## [2026-10-04] diagnose | Conectividad reportada en `POST /auth/login`

- GET anónimo `/auth/me` respondió 401 JSON; preflight desde `http://127.0.0.1:5173` respondió 200 y el POST vacío desde browser alcanzó la API con 400 por validación.
- No se reprodujo `net::ERR_FAILED` ni se probó un login válido; no se atribuye causa al error original.
- Evidencia: [contrato de autenticación](contrato-auth.md).

## [2026-10-04] verify | Seed de médicos aplicado a MySQL local

- Tras autorización expresa, se configuró un secreto aleatorio privado de desarrollo en `.env` raíz, se ejecutó el seed una sola vez y se restauró `SEED_ON_START=false`; el valor no se mostró ni se documentó.
- Con sesión PATIENT, `GET /api/professionals` devolvió 8 perfiles y `GET /api/availability` devolvió slots libres para los seis médicos solicitados, consultando sedes y especialidades asignadas.
- Se recargó `/patient` y el catálogo/tarjetas aparecieron. No se creó una cita como parte de esta verificación.
- `verify` pasó con backend 22/22, frontend 8/8, build/typecheck y escáner.

## [2026-10-04] implement | Nombres de médicos del seed de desarrollo

- Los usuarios seed doctor1–doctor6 ahora se llaman Mateo Silvestre Fernández, Valeria Sofía Montenegro, Damián Alejandro Cruz, Lucía Beatriz Navarro, Esteban Gabriel Pineda y Camila Victoria Sotomayor, preservando 3 generales, 3 especialistas nombrados y 2 especialistas previos.
- El test de idempotencia verifica nombres, tipos, asignaciones de Cardiología/Dermatología/Pediatría y 14 bloques por cada uno de los primeros seis médicos.
- `.\scripts\verify.ps1`: backend 22/22, frontend build/typecheck y 8/8 tests, scanner PASS.
- En ese instante, antes de autorizar y aplicar la semilla live, el endpoint autenticado de profesionales devolvió una lista vacía; el estado posterior está documentado en la entrada de verificación que antecede a ésta.

## [2026-10-04] audit | Línea base S3/S4

- Ejecutado `mvn -B -ntp test` en contenedor de una sola ejecución con API detenida: 21 pruebas, cero fallos/errores. API reiniciada después; `/auth/me` anónimo devolvió 401.
- Confirmadas migraciones V1–V3 y endpoints S3 existentes; búsquedas de código no encontraron recuperación, ciclo cancelación/reprogramación/historial, agenda/cierre ni catálogo/afiliación EPS.
- Seed solo verificado por fixture H2; no se cargó en DB persistente. No existe scanner/runner verify ni Git.
- Añadida [auditoría inicial backend](auditoria-inicial.md), enlazada desde el índice y la Wiki global.

## [2026-10-04] test | verify local y escáner de secretos

- `scripts/verify.ps1` pasó: Maven 21/21, frontend build/typecheck/tests 4/4 y secret scan.
- Una expectativa alterada en la prueba concurrente hizo fallar verify con 1 test failed/exit 1; restaurada, verify pasó.
- Fixture con credencial demostrativa hizo fallar el escáner dentro de verify (exit 1). Se eliminó; ejecución completa posterior PASS y búsqueda `rg` sin coincidencias del fixture/patrón.
- No hubo `git init`, commits ni hook. HU-005 se actualizó para el runner sin Git y su alcance local está cumplido; Red→Green de regla funcional nueva permanece pendiente.

## [2026-10-04] implement | Límite de rango de disponibilidad

- Se añadió primero `availabilityRangeNeverReturnsIncompleteSixtyMinuteSlots`; la prueba falló porque el endpoint devolvía un slot de 60 min iniciado dentro del rango pero terminado fuera (`expected: 0, was: 1`).
- Se limitó la enumeración a candidatos cuyo final no excede `to`; prueba enfocada pasó y el `verify` completo pasó 22/22.
- Se actualizaron el contrato, HU-002, las evidencias y el índice. S3 queda parcial por restantes escenarios y E2E.

## [2026-10-04] learn | Historias derivadas para S3

- Se buscó en `citas-api/docs/wiki/scrum/` según instrucciones y no había HU/épicas, solo `.gitkeep`; el usuario autorizó derivarlas del PRD sin cambiar su alcance.
- Creadas EP-001/EP-002 y HU-001—HU-005 bajo `citas-api/docs/wiki/scrum/`, como borradores/en desarrollo o bloqueada, con CA y DoD aún pendientes de prueba.
- Decisión aprobada: conservar RF-09 del PRD, duración única por especialidad de 30 o 60 min (no override por profesional).
- Se alineará modelo de rol a `PATIENT` solicitado; PRD usa actor `USER` y S2 no tenía rol persistido, así que cuentas antiguas se asignarán a PATIENT.
- Git no está inicializado. El usuario rechazó inicializarlo; commit/hook Git permanece impedido. No se inicia S4 antes de satisfacer A8.

## [2026-10-04] implement | Primer bloque backend de citas

- Agregada migración Flyway V2 con rol `PATIENT` por defecto (conserva cuentas S2), catálogos, perfiles/asignaciones profesionales, bloques, citas y slots con restricción única profesional/inicio de slot.
- JWT resuelve autoridad `ROLE_*` desde la BD en cada request; auth registration mantiene su endpoint y asigna `PATIENT`.
- Añadidos endpoints de consulta de catálogo/staff, admin CRUD lógico de profesionales/asignaciones, disponibilidad propia de profesional, consulta y reserva por paciente, mis citas y aprobación/rechazo admin.
- Semántica documentada: tipo/duración derivan del perfil/especialidad; 30/60 min conforme a PRD RF-09; `REQUESTED` retiene slots; rechazo libera; general queda `APPROVED`.
- Primera compilación falló por import faltante en `ApiExceptionHandler`; tras corregir el import, `mvn -B -ntp -DskipTests compile` terminó `BUILD SUCCESS` dentro de contenedor de una sola ejecución.
- Aún pendientes: arranque/migración V2, test backend de reglas/roles/concurrencia, seed, frontend y prueba end-to-end. No considerar el bloque funcional terminado.

## [2026-10-04] ingest | Estado inicial de backend

- Revisados README y prompt backend; inspeccionada la estructura del repositorio y el SQL de referencia.
- Confirmado estado de plantilla sin proyecto Maven/código de aplicación observado.
- Registrados requisitos y estado por separado; no se encontraron endpoints, migraciones ni pruebas backend.
- Enlazada esta Wiki con la global y frontend; fuentes originales se mantienen fuera de `raw/`.

## [2026-10-04] learn | Alcance solicitado para sesión 2

- El usuario solicita implementar registro/login JWT y `/auth/me`, con BCrypt/Argon2, validación, errores, CORS y pruebas.
- Se mantiene el stack objetivo verificado en esta Wiki: Java 21, Spring Boot 3.5.x y Maven; las restricciones compartidas requieren access y refresh separados.
- Antes de crear código se comprobó que `java`, `mvn` y `docker` no están disponibles por PATH. No se ha comprobado si hay una distribución de Java/Maven instalada fuera de PATH.
- Los detalles técnicos por definir se registrarán después de contrastarlos con la implementación.

## [2026-10-04] ingest | Implementación de autenticación en código

- Añadido proyecto Java 21/Spring Boot 3.5.6 con capas de aplicación, puertos, adaptadores web, persistencia JPA y seguridad.
- Añadidos `POST /auth/register`, `POST /auth/login`, `GET /auth/me`, `POST /auth/refresh` (rotación de refresh token) y `POST /auth/logout`.
- Contraseñas usan BCrypt; refresh tokens se almacenan como SHA-256 hash; access/refresh llevan claim `type` y `sub`.
- Añadida migración Flyway, variables documentadas en `.env.example` y pruebas de integración sobre H2.
- Actualizada esta Wiki y README antes de pasar al bloque frontend.
- No ejecutados compile/tests: Java, Maven y Docker no están disponibles por PATH en el entorno; código no verificado.

## [2026-10-04] lint | Validación de límite BCrypt

- Añadida comprobación en bytes UTF-8 para evitar superar el límite de 72 bytes de BCrypt aunque la cadena tenga menos caracteres.
- Error de registro de ese caso responde 400; login lo trata como credenciales inválidas.
- Añadido caso de prueba de contraseña multibyte; suite sigue pendiente de ejecución por ausencia de Java/Maven en PATH.
- La búsqueda de la sesión en carpetas Program Files estándar tampoco encontró JDK; no se hizo búsqueda exhaustiva en todo el perfil/equipo.

## [2026-10-04] lint | Alineación de variables en Compose

- Alineado el entorno del contenedor `citas-api-dev` con `application.properties`: DB_URL interno `mysql:3306`, `JWT_SECRET`, TTLs y CORS.
- Actualizado `.env.example` de raíz y eliminado el puerto Angular, ya que React es el frontend elegido.
- Compose no se pudo validar/levantar porque Docker no está disponible por PATH.

## [2026-10-04] lint | Arranque de API en perfil dev

- Compose ahora arranca `mvn spring-boot:run` en `citas-api-dev`; documentado diagnóstico de API vacía y uso de POST para `/auth/register`.
- No ejecutado por falta de Docker CLI y toolchain Maven/Java local.

## [2026-10-04] lint | Alineación de variables de entorno

- Añadida plantilla `.env.example` para ejecución Spring desde host; documentado que Spring no carga `.env` automáticamente.
- Comparados nombres y relaciones de configuración sin imprimir valores: DB_URL local usa localhost:3307, credenciales DB quedaron alineadas con MYSQL y JWT/TTL/CORS con Compose.
- JWT local de marcador reemplazado por un secreto aleatorio compartido entre raíz/API; pruebas de igualdad y longitud pasaron sin mostrar el secreto.
- La conexión a MySQL y arranque API no se verificaron. No se rotaron las contraseñas MySQL por riesgo de incompatibilidad con un volumen ya inicializado; los marcadores privados de contraseña se deben reemplazar antes de uso fuera del laboratorio.

## [2026-10-04] lint | Rotación de credenciales MySQL

- Se comprobó acceso a MySQL local por el puerto publicado 3307 y se autenticaron las cuentas de aplicación y root sin exponer credenciales.
- Rotadas ambas contraseñas en el servidor MySQL en ejecución y actualizados los `.env` privados de raíz/API.
- Reautenticación de ambas cuentas con los valores nuevos pasó; secretos no se imprimieron ni persistieron en la Wiki.
- El arranque de Spring Boot y flujo e2e siguen sin comprobarse.

## [2026-10-04] lint | Corrección de arranque y auth en Docker (agente Codex)

- Diagnóstico: contenedores antiguos estaban `Up` con `tail -f /dev/null`, no servidores; al recrear, Spring abortó por `CHAR(64)` de Flyway frente a `VARCHAR(64)` validado por Hibernate en `refresh_tokens.token_hash`.
- Corregido el mapeo de [`RefreshTokenEntity`](../../src/main/java/com/fcv/citas/adapter/out/persistence/RefreshTokenEntity.java) a `CHAR(64)`, sin alterar V1 ya aplicada.
- Suite Maven detectó potencial colisión de refresh JWT emitidos en el mismo segundo; añadido `jti` UUID a JWT. También se corrigió Compose web para reintentar instalación npm ante `ECONNRESET`.
- Al revisar logs se encontró usuario/clave de desarrollo autogenerado por Spring Security. Se excluyó esa auto-configuración y se desactivaron Basic/form login para dejar solo JWT; nuevo arranque no volvió a generar la clave. Prueba adicional verifica Basic → 401.
- `mvn -B -ntp test`: 10 pasaron, 0 fallaron, 0 errores. Spring inició en 8080; pruebas HTTP verificaron register 201/409, login 200/401, `/auth/me` 200/401 y OPTIONS CORS permitido.
- Reinicio Compose ejecutado sin `-v`; MySQL healthy, API/Vite iniciadas, reinicios observados 0. No se registraron valores de entorno.
- Incertidumbre: browser automatizado completó flujo pero notificó `ERR_ABORTED` para POST logout; refresh quedó revocado en DB y la llamada directa devolvió 204. Ver [contrato y diagnóstico](contrato-auth.md).

## [2026-10-04] test | Smoke HTTP después de ajuste JWT (agente Codex)

- Tras reiniciar API con Basic/form login desactivados, register devolvió 201, duplicado 409, login 200, `/auth/me` con JWT 200, sin token 401 y con Basic 401.
- Preflight CORS respondió 200 con origen permitido `http://localhost:5173`; logout respondió 204. Frontend `/login` respondió 200.
- Reinicio API sin error de arranque, reinicios observados 0; contraseña de Spring Security autogenerada ya no aparece en logs recientes.

## [2026-10-04] implement | Backend S3 y verificación dirigida (agente Codex)

- Corregidos dos defectos detectados al probar: un cierre de llave que impedía compilar y `findAppointmentForUpdate` que avanzaba el cursor JDBC dos veces y respondía 404 para una cita existente.
- Ajustado el test de slot fuera de disponibilidad y añadidos casos de fecha pasada, rechazo que libera dos slots y CRUD admin de perfiles. La suite dirigida auth/citas/seed pasa 21/21.
- Flyway informó versión 3 en MySQL 8.4; API respondió 401 correcto a `/auth/me` sin credenciales. La advertencia de compatibilidad de Flyway/MySQL 8.4 continúa documentada.
- El test aislado ejecuta el seed dos veces y verifica las cantidades objetivo. No se ejecutó el seed live sobre la base persistente.
- Cableadas variables de seed a Compose con defaults `APP_ENV=production` y `SEED_ON_START=false`; sitios demo usan nombres/direcciones ficticios.
- La disponibilidad/especialidades y el portal frontend se integran parcialmente; catálogo administrativo, historial, seed live, reglas restantes, A6 y e2e siguen abiertos. Consultar [contrato](contrato-citas.md) y [evidencias](evidencias-s3.md).

## [2026-10-04] test | verify tras cambios recientes de frontend

- Reejecutado el comando común `.\scripts\verify.ps1`: backend 22/22, build/typecheck frontend, tests frontend 6/6 y secret scan en PASS.
- Evidencia backend complementada con la salida exacta y los límites del smoke visual.
- Sin cambios backend funcionales en esta ejecución; S3 sigue parcial.
