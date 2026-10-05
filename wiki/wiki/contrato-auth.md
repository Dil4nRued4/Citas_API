# Contrato de autenticación (sesión 2)

Estado: rutas/DTOs implementados y verificados en la instancia Docker local el 2026-10-04. `mvn -B -ntp test` pasó 10 pruebas (0 fallos, 0 errores). Los resultados no certifican un despliegue externo.

## `POST /auth/register`

Público. JSON:

```json
{"name":"Ana Pérez","email":"ana@example.test","password":"Password123"}
```

- `201 Created`: `{"id":1,"name":"Ana Pérez","email":"ana@example.test"}`. No serializa el hash ni la contraseña.
- `400 Bad Request`: JSON inválido, nombre/email faltante, email inválido o contraseña menor de 8 caracteres/mayor de 72 caracteres/72 bytes UTF-8.
- `409 Conflict`: correo previamente registrado, tras normalizarlo a minúsculas.

## `POST /auth/login`

Público. JSON `{"email":"ana@example.test","password":"Password123"}`.

- `200 OK`: `{"accessToken":"…","refreshToken":"…","tokenType":"Bearer","expiresIn":900}`.
- `400 Bad Request`: body o campos inválidos.
- `401 Unauthorized`: correo o contraseña incorrectos, con mensaje genérico.

El access JWT usa `sub` con el id y claim `type=access`; incluye `iat`, `exp` y `jti` UUID. El refresh JWT usa `sub`, `type=refresh` y `jti`; su valor completo no se guarda en DB: se persiste SHA-256, junto con expiración y revocación. `jti` distingue tokens emitidos para un mismo usuario dentro del mismo segundo.

## `GET /auth/me`

Protegido con `Authorization: Bearer <accessToken>`.

- `200 OK`: `{"id":1,"name":"Ana Pérez","email":"ana@example.test"}`.
- `401 Unauthorized`: falta un token, es inválido/expiró o no corresponde a un usuario.

## `POST /auth/refresh`

Público con `{"refreshToken":"…"}`. Comprueba tipo/expiración y credencial vigente en DB, revoca el refresh anterior y entrega una nueva pareja de tokens con forma de la respuesta de login. Credencial inválida/reutilizada: `401`.

## `POST /auth/logout`

Público con body opcional `{"refreshToken":"…"}`. Revoca la credencial de refresh si está registrada. Respuesta `204 No Content`. No revoca access tokens ya emitidos; permanecen válidos hasta expirar.

## Persistencia y seguridad

Migración Flyway V1 crea `users` (id, nombre, email único, hash BCrypt y created_at) y `refresh_tokens` (user_id, hash único, expires_at, revoked_at, created_at). CORS lee `CORS_ALLOWED_ORIGINS` y por defecto permite `http://localhost:5173`.

Variables: `SERVER_PORT` (8080), `DB_URL` (por defecto MySQL local puerto 3307), `DB_USER`, `DB_PASSWORD`, `JWT_SECRET` (obligatorio, al menos 32 bytes para HS256), `JWT_ACCESS_TTL_SECONDS` (900), `JWT_REFRESH_TTL_SECONDS` (604800), `CORS_ALLOWED_ORIGINS`. `.env.example` contiene valores de referencia, no credenciales de despliegue. Spring Boot no carga `.env` automáticamente.

El `.env.example` de raíz contiene valores de Compose y un `DB_URL` host para ejecución manual; Compose reemplaza el DB URL de API por `mysql:3306`. El `.env.example` de `citas-api` es para ejecutar backend desde host con `localhost:3307`. Spring no carga `.env` por sí mismo; al ejecutar Maven desde PowerShell, sus variables deben exportarse al proceso. No registrar valores de archivos `.env` en esta Wiki.

En la revisión del 2026-10-04, MySQL respondió en `localhost:3307`; las cuentas de aplicación y root se rotaron y revalidaron sin mostrar valores. Después se inició Spring en Compose y Flyway ejecutó la migración sobre la DB. La primera instancia abortó en validación Hibernate por diferencia de tipo en `token_hash`; tras alinear entidad y migración, el servidor inició en puerto 8080.

## Diagnóstico de conexión local

El servicio Compose `citas-api-dev` pertenece al perfil `dev` y ejecuta `mvn -B -ntp spring-boot:run`; iniciar solo MySQL no inicia la API. Se observó que contenedores viejos mostraban estado `Up` pero ejecutaban `tail -f /dev/null`, dejando el puerto publicado sin servidor HTTP. Un contenedor recreado luego abortó Spring por validación del esquema; ambos hechos pudieron producir la respuesta vacía. Revisar `docker compose --profile dev ps` y `docker compose --profile dev logs --tail 200 citas-api-dev`; no inferir disponibilidad del estado `Up` solamente. No se encontró reverse proxy configurado en Compose.

## Cómo ejecutar y probar

Requiere Java 21, Maven y MySQL para ejecución desde host. Desde `citas-api`, exportar variables del `.env` al proceso PowerShell (Spring no lee `.env` automáticamente) y ejecutar `mvn spring-boot:run`; para pruebas, `mvn -B -ntp test`, que usa H2. En Docker, desde la raíz, iniciar con `docker compose --profile dev up -d` y consultar logs con `docker compose --profile dev logs -f citas-api-dev`. Para evitar competir por memoria con la JVM de Spring, parar API, correr pruebas en un contenedor aislado y volver a levantarla:

```powershell
docker compose --profile dev stop citas-api-dev
docker compose --profile dev run --rm --no-deps citas-api-dev mvn -B -ntp test
docker compose --profile dev up -d citas-api-dev
```

En esta sesión la suite terminó con 10 pruebas, 0 fallos, 0 errores.

La suite cubre registro sin exponer password/hash, correo duplicado (case-insensitive), login correcto/incorrecto, claims `sub`/`type`/`exp`, validación (incluido límite BCrypt UTF-8), `/auth/me` con y sin token, rechazo de Basic Auth y rotación/reutilización del refresh. Pasó durante la sesión de diagnóstico.

## Decisiones y límites

- Se usa BCrypt; la longitud máxima aceptada de 72 caracteres evita truncamiento/excepción del encoder.
- Se decidió persistir hash de refresh para poder revocarlo/rotarlo; no se almacena el token en claro.
- En este incremento se usa tabla propia mínima de cuentas, no el modelo de referencia completo.
- En el volumen local existente, no editar migraciones Flyway ya aplicadas para corregir mapping ORM; alinear entidad con el esquema aplicado y validar al iniciar.
- Se deshabilitaron Basic Auth y form login y la auto-configuración de usuario por defecto de Spring; el API autentica rutas protegidas solo con JWT y no genera una credencial dev en logs.
- El logout invalida refresh, no access JWT; no hay lista de revocación para access tokens.

## Evidencia HTTP y CORS (2026-10-04)

Se verificó POST `/auth/register` con 201; repetición del email con 409; POST `/auth/login` correcto con 200 e incorrecto con 401; GET `/auth/me` con access token con 200 y sin token con 401. OPTIONS `/auth/register` con `Origin: http://localhost:5173`, método POST y header `content-type` devolvió respuesta CORS permitida. Browser completó el recorrido registro → login → dashboard → logout. La automatización anotó `net::ERR_ABORTED` para POST logout aunque se observó la revocación en DB y una llamada directa al endpoint devolvió 204; se deja como incertidumbre de la herramienta/browser, no se afirma que el Network log real esté limpio.

En el diagnóstico posterior de `net::ERR_FAILED` en login, GET anónimo `/auth/me` respondió 401 JSON; el preflight para `POST /auth/login` desde `http://127.0.0.1:5173` respondió 200 con CORS permitido; y un POST browser con body `{}` alcanzó el endpoint y recibió 400 por `email` faltante. Esto verifica conectividad y CORS en ese momento, no un login con credenciales válidas. El error de red original no se reprodujo y su causa permanece desconocida.

Comandos diagnóstico desde raíz: `docker compose --profile dev ps`, `docker compose --profile dev logs --tail 200 citas-api-dev`. Para listar nombres de variables inyectadas sin filtrar secretos: `docker compose --profile dev exec citas-api-dev sh -c 'printenv | cut -d= -f1'`. No registrar salida con valores sensibles.
