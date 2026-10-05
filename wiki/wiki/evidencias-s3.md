# Evidencias de implementación S3

Registro de verificaciones ejecutadas en el entorno local el 2026-10-04. La fecha horaria de los logs de contenedor aparece en UTC. Estas salidas no son una declaración de que S3 esté terminado.

## Backend: integración, reglas y seed

Comando ejecutado dentro del contenedor API con la app detenida para evitar dos procesos Spring simultáneos:

```powershell
docker compose --profile dev stop citas-api-dev
docker compose --profile dev run --rm --no-deps citas-api-dev mvn -B -ntp "-Dtest=AuthApiIntegrationTest,AppointmentApiIntegrationTest,DevelopmentSeedIntegrationTest" test
docker compose --profile dev up -d citas-api-dev
```

Resultado observado por Maven:

```text
Tests run: 9, Failures: 0, Errors: 0, Skipped: 0 -- AppointmentApiIntegrationTest
Tests run: 11, Failures: 0, Errors: 0, Skipped: 0 -- AuthApiIntegrationTest
Tests run: 1, Failures: 0, Errors: 0, Skipped: 0 -- DevelopmentSeedIntegrationTest
Tests run: 21, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

Cobertura verificada en esas clases: general autoaprobada, cita especializada `REQUESTED`, autorización de decisiones, CRUD admin de perfil/asignaciones y conversión de PATIENT a PROFESSIONAL, doble reserva secuencial y concurrente, aprobación, rechazo con motivo y liberación de dos slots, segunda transición inválida, bloque fuera de grilla, solapamiento, fecha pasada, edición de disponibilidad ajena y reserva fuera de disponibilidad. La duración no se acepta desde el body: se deriva del catálogo. El test del seed lo ejecuta dos veces en la base H2 aislada y verifica 1 ADMIN, 5 PATIENT, 8 PROFESSIONAL, 3 profesionales generales, 5 especialistas, 3 citas generales aprobadas, 3 `REQUESTED`, 1 `REJECTED`, 98 bloques y un profesional sin disponibilidad.

En una ejecución previa de esta suite, el nuevo test de rechazo esperaba reservar a las 14:00, hora que excedía el bloque fixture; Maven devolvió 1 fallo (HTTP esperado 201, recibido 409). Se corrigió el horario del fixture a las 12:00, que cabe en el bloque, y la ejecución final indicada arriba pasó 21/21. Este fallo de fixture no es ni se cuenta como la prueba intencional Red→Green de A6.

**No** hay evidencia todavía de prueba intencionalmente roja del requerimiento (Red → Green). Una compilación/test fallida durante desarrollo no cuenta como la prueba intencional pedida.

## Horario recurrente y consulta de Mis citas — 2026-10-04

- Añadida migración Flyway V4 para `professional_weekly_schedules`. La prueba H2 verifica 40 filas (ocho médicos × cinco días), lunes–viernes, 07:00–16:00 `America/Bogota`, en la sede de agenda seleccionada por médico; repetir seed no duplica reglas.
- `AppointmentApiIntegrationTest` pasó 13 casos, incluidos slots locales de 30 y 60 minutos, fin de semana sin disponibilidad, reserva a las 07:00 y rechazo a las 16:00. Después de reservar, `GET /api/appointments/mine` devolvió la cita con estado `APPROVED`.
- En MySQL 8.4, Flyway aplicó versión 4. Se activó una vez el flag enfocado `SEED_WEEKLY_SCHEDULES_ON_START` con `APP_ENV=development` y `SEED_ON_START=false`; se verificaron 40 reglas, ocho profesionales, cinco días, horario/zona uniformes. Los conteos de usuarios y citas no cambiaron; el flag quedó otra vez en `false`. No se ejecutó el seed completo ni se creó una cita real.
- `.\scripts\verify.ps1`: backend 26/26, build/typecheck frontend, tests frontend 9/9 y secret scan PASS.
- `/auth/me` anónimo respondió 401 después de reiniciar la API. No se hizo consulta live de disponibilidad con token PATIENT ni booking en browser; esto queda pendiente.

## Base local y probe HTTP durante la revisión del seed de nombres (antes de V4)

La observación de schema versión 3 que sigue es histórica y precede la migración V4; la sección de horario recurrente arriba registra el estado posterior.

En el log de inicio de Spring/Flyway se observó:

```text
Database: jdbc:mysql://mysql:3306/citas_fcv_training (MySQL 8.4)
Current version of schema `citas_fcv_training`: 3
Started CitasApiApplication in 44.049 seconds
```

Flyway además advirtió que su versión declara soporte probado hasta MySQL 8.1, no 8.4. API y MySQL quedaron `Up`; MySQL reportó `healthy`.

Probe sin credenciales:

```text
curl.exe -sS -i --max-time 15 http://127.0.0.1:8080/auth/me
HTTP/1.1 401
{"status":401,"message":"Authentication required"}
```

El primer `Invoke-WebRequest` a `/auth/me` excedió su timeout durante el arranque; el reintento con `curl.exe` después del mensaje `Started CitasApiApplication` devolvió el 401 esperado. El timeout no se presenta como una respuesta de aplicación.

## Frontend

Comandos ejecutados desde `citas-web`:

```text
npm run build
✓ built in 625ms

npm run typecheck
tsc -b

npm run test
Test Files  1 passed (1)
Tests       4 passed (4)

npm run lint
oxlint
```

Los tests de componentes comprueban los campos/aviso de Login, discrepancia de contraseñas en Register, redirección de PATIENT desde ruta ADMIN y selección/estado de un slot libre. El browser local abrió `/admin` sin sesión y acabó en `/login`; `GET /admin` al servidor Vite devolvió `200` para el documento SPA. Esto no prueba la experiencia autenticada ni el recorrido completo de reserva.

## Seed en desarrollo: resultado de la verificación inicial

En la verificación inicial, `DevelopmentSeedIntegrationTest` probaba repetición/idempotencia sobre H2 aislada y cantidades esperadas, pero el seed no se había invocado en la base persistente de Compose. Ese estado cambió después de la autorización para cargar los médicos; el resultado actualizado se registra en [Nombres de médicos en seed](#nombres-de-médicos-en-seed--2026-10-04). No se ejecutó `docker compose down -v`.

## Verificación local sin Git — 2026-10-04

Este GOAL reemplaza el hook Git por `scripts/verify.ps1`; el workspace sigue sin `.git` y no se inicializó ni configuró hook.

### verify FAIL al romper un test, luego PASS

Para demostrar que el comando detiene el flujo ante una regresión, se cambió temporalmente la expectativa de la prueba concurrente de `[201, 409]` a `[201, 201]`. No se borró ni debilitó la prueba; se restauró su aserción original antes de la pasada verde.

```text
Expecting actual:
  [201, 409]
to contain exactly in any order:
  [201, 201]
Tests run: 21, Failures: 1, Errors: 0, Skipped: 0
BUILD FAILURE
[FAIL] Backend tests (exit 1)
verify intentional-failure exit: 1
```

Después de restaurar `[201, 409]`, `verify` completó todas las etapas:

```text
Tests run: 21, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
[PASS] Backend tests
✓ built in 674ms
[PASS] Frontend production build
[PASS] Frontend typecheck
Tests       4 passed (4)
[PASS] Frontend tests
Secret scan passed; no configured secret patterns found.
[PASS] Secret scan
All local verification checks passed.
verify after secret removal exit: 0
```

### Secreto ficticio

Se creó temporalmente `scripts/secret-scan-fixture.txt` con un valor explícitamente ficticio bajo una asignación `JWT_SECRET`. `verify` terminó con código 1 después de que backend y frontend pasaran:

```text
Secret scan failed. Review and remove credentials; private .env files are excluded.
[FAIL] scripts\secret-scan-fixture.txt matches secret pattern: (?i)\b(?:JWT_SECRET|DB_PASSWORD|MYSQL_PASSWORD|MYSQL_ROOT_PASSWORD|SEED_DEFAULT_PASSWORD|API_KEY|SECRET_KEY)\b...
[FAIL] Secret scan (exit 1)
verify fake-secret exit: 1
```

El archivo fue eliminado. La nueva ejecución completa terminó `All local verification checks passed.` / exit 0. Búsqueda ejecutada sobre `scripts README.md wiki citas-api citas-web` para el valor/patrón y el nombre del fixture:

```text
No fake secret or fixture path remains.
```

No se usa `git grep` ni se afirma inspección de historial: Git no existe por instrucción explícita. El allowlist y las limitaciones del escáner están en la [Wiki de infraestructura global](../../../wiki/wiki/infraestructura-y-herramientas.md).

### Pendiente de S3/S4 (excepto seed live, cargado después)

El test modificado intencionalmente demuestra el control del runner, no un ciclo Red→Green de una nueva regla funcional. Siguen pendientes un ciclo de regla nueva, E2E de reserva completo, reglas S4, loops Builder/Verifier y cierre DoD. El seed live sí se ejecutó posteriormente; véase la sección final de esta página. S3/S4 no se declara terminado.

## Red → Green: límite del rango en slots de 60 minutos — 2026-10-04

Se añadió primero `AppointmentApiIntegrationTest.availabilityRangeNeverReturnsIncompleteSixtyMinuteSlots`. El caso solicita una ventana de 30 minutos para una especialidad de 60 y exige lista vacía; la implementación inicial devolvió un inicio cuya hora de término excedía el límite:

```text
expected: 0
 but was: 1
Tests run: 1, Failures: 1, Errors: 0, Skipped: 0
BUILD FAILURE
red test exit: 1
```

Se corrigió el límite del ciclo de candidatos para exigir `candidate + duration <= to`. La misma prueba enfocada pasó:

```text
Tests run: 1, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
green test exit: 0
```

Finalmente, `verify` pasó con la nueva prueba incluida: `AppointmentApiIntegrationTest` 10/10, `AuthApiIntegrationTest` 11/11, `DevelopmentSeedIntegrationTest` 1/1; total 22, cero fallos/errores. Build/typecheck frontend, cuatro tests y scanner también pasaron. El cambio solo recorta candidatos para que toda la duración quede dentro del rango solicitado.

## verify tras añadir el inicio de paciente — 2026-10-04

Se volvió a ejecutar `.\scripts\verify.ps1` después de los cambios recientes del frontend. La salida observada fue:

```text
Tests run: 22, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
[PASS] Backend tests
✓ built in 825ms
[PASS] Frontend production build
[PASS] Frontend typecheck
Test Files  1 passed (1)
Tests       6 passed (6)
[PASS] Frontend tests
Secret scan passed; no configured secret patterns found.
[PASS] Secret scan
All local verification checks passed.
```

El navegador compartido mostró `/patient` con el saludo, especialidades, profesionales y navegación al usar la sesión existente y respuestas API interceptadas/sintéticas. Después de reiniciar el servicio Vite para que sirviera el módulo actual, esa pestaña dejó de estar disponible. Una pestaña nueva no tenía sesión autenticada y recibió 401; las capturas de prueba resultaron ser solo estados `Loading…`, por lo que se descartaron y no se enlazan como evidencia. No se completó E2E autenticado contra API/seed, ni se verificó el viewport real a 390 px y escritorio con capturas válidas. Esta ejecución de verify valida build/tests, no completa S3/S4 ni el DoD visual/browser.

## Nombres de médicos y seed inicial (antes del horario semanal) — 2026-10-04

En esta revisión se reemplazaron los nombres de los primeros seis usuarios profesionales del seed, preservando ocho profesionales en total, sus categorías/asignaciones preexistentes y los dos especialistas adicionales. La prueba de seed de esa revisión comprobaba los seis nombres exactos, tipos, bloques fechados para cada uno en 14 días hábiles y cantidades previas; invocaba `populate()` dos veces. Esa evidencia de bloques fechados corresponde al seed anterior a la migración V4 y no describe el horario actual. Vea la sección posterior [Horario recurrente](#horario-semanal-recurrente-y-reserva-visible-en-mis-citas--2026-10-04).

La ejecución común `.\scripts\verify.ps1` después del cambio registró:

```text
Tests run: 22, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
[PASS] Backend tests
[PASS] Frontend production build
[PASS] Frontend typecheck
Tests  8 passed (8)
[PASS] Frontend tests
Secret scan passed; no configured secret patterns found.
[PASS] Secret scan
All local verification checks passed.
```

Antes de aplicar el seed, la sesión paciente obtuvo `GET /api/professionals` → `200` con lista vacía; Compose estaba en `APP_ENV=production`, `SEED_ON_START=false` y sin contraseña de seed configurada. Tras autorización expresa para cargar la DB local, se estableció una contraseña privada en el `.env` raíz (no se mostró ni se registró), se habilitó `APP_ENV=development`/`SEED_ON_START=true` y se recreó solo el contenedor API. Spring arrancó sin error. Luego se volvió a fijar `SEED_ON_START=false` y se recreó el API otra vez para que sus futuras reinicializaciones no reejecuten el seed. MySQL y la cuenta PATIENT existente no se borraron ni reinicializaron.

Desde la sesión PATIENT del browser, `GET /api/professionals` devolvió 200 y ocho profesionales. Además, se consultó `GET /api/availability` autenticado para cada médico objetivo y la primera franja disponible en el rango siguiente fue:

| Médico | Tipo | Especialidad consultada | Sede | Slots libres |
|---|---|---|---|---:|
| Dr. Mateo Silvestre Fernández | GENERAL | Medicina General | HIC | 111 |
| Dra. Valeria Sofía Montenegro | GENERAL | Medicina Familiar | ICV | 95 |
| Dr. Damián Alejandro Cruz | GENERAL | Consulta Particular | HIC | 111 |
| Dra. Lucía Beatriz Navarro | ESPECIALISTA | Cardiología | HIC | 111 |
| Dr. Esteban Gabriel Pineda | ESPECIALISTA | Dermatología | ICV | 111 |
| Dra. Camila Victoria Sotomayor | ESPECIALISTA | Pediatría | ICV | 111 |

La UI fue recargada y mostró los datos del catálogo y tarjetas de médicos; no se creó ninguna cita durante esta comprobación. Los slots son resultado de consultar disponibilidad libre para el rango de 21 días usado en la prueba, no una promesa de horarios permanentes.
