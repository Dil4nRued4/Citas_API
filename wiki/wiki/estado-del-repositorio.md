# Estado de `citas-api`

## Hecho verificado

En la inspección inicial, el README local describía un repositorio backend todavía sin implementación. En la sesión 2 se añadieron `pom.xml`, aplicación Spring Boot, estructura por puertos/adaptadores, migraciones Flyway, seguridad, controladores, repositorios JPA y pruebas de integración. El último `verify` pasó con **26 pruebas backend, 0 fallos y 0 errores**; Flyway versión 4 quedó aplicada en MySQL local. Detalle en [evidencias S3](evidencias-s3.md).

La carpeta `automations/n8n/` contiene tres guías Markdown de workflows y `.gitkeep`, no exports JSON observados. `docs/wiki/scrum/` contenía solo `.gitkeep` en la inspección inicial; con autorización del usuario se añadieron cinco HU derivadas del PRD, vinculadas desde su [índice](../../docs/wiki/scrum/README.md).

El 2026-10-04, con autorización explícita del usuario, se derivaron HU/DoD de S3 desde PRD RF-07—RF-12 y A6; están en [`docs/wiki/scrum/README.md`](../../docs/wiki/scrum/README.md). No son HU preexistentes. Backend S3 parcial implementa catálogo, profesionales, disponibilidad, reserva, decisión admin y seed; el frontend tiene vistas iniciales de paciente/admin/profesional. Persisten gaps documentados y ninguna HU se declara Done.

## Requisitos y alcance verificado

Stack: Java 21, Spring Boot 3.5.6, Maven, arquitectura hexagonal, JPA, MySQL/Flyway, Spring Security/JWT y REST/JSON. Se probaron registro/login, `/auth/me`, refresh/logout, BCrypt, CORS y errores esperados 400/401/409. La verificación HTTP se realizó contra la instancia local Compose y no constituye certificación de despliegue.

La infraestructura y síntesis compartida están en la [Wiki global](../../../wiki/wiki/index.md). Para requisitos backend, véase [responsabilidades](responsabilidades.md). El perfil Compose `dev` inicia Spring Boot; el puerto API se publica como `localhost:8080`.

## Diagnóstico y límites

El síntoma original de respuesta vacía se vinculó primero con contenedores antiguos cuya orden era `tail -f /dev/null`, pese a estar `Up`. Al recrearlos, Spring abortó arranque por discrepancia entre `CHAR(64)` de Flyway y `VARCHAR(64)` validado por Hibernate; se corrigió el mapeo de entidad. La [Wiki global de infraestructura](../../../wiki/wiki/infraestructura-y-herramientas.md) registra evidencia y comandos.

La suite Maven dirigida pasó 26/26 (13 appointments, 11 auth, 2 seed). La prueba del seed comprueba idempotencia, los ocho perfiles, nombres, tipos, 40 reglas semanales de lunes a viernes entre 07:00 y 16:00 `America/Bogota`, sedes seleccionadas y citas. La API local respondió; `/auth/me` sin token devolvió 401 y Flyway reportó versión 4 en MySQL 8.4. Flyway advirtió soporte probado hasta MySQL 8.1. Estos hechos no validan despliegue externo ni booking live autenticado. No hay Actuator/health HTTP.

## Verificación local S3 sin Git

Este GOAL prohíbe inicializar Git, instalar hooks y crear commits. Se añadió `scripts/verify.ps1` con backend, build/typecheck/tests frontend y scanner local; `verify` se probó en PASS, con una prueba rota y con un secreto ficticio. Salidas exactas en [evidencias S3](evidencias-s3.md). El runner funciona sin Git y puede llamarse desde un `pre-commit` futuro, pero no es un hook.

Compose pasa `APP_ENV`, `SEED_ON_START`, `SEED_WEEKLY_SCHEDULES_ON_START` y `SEED_DEFAULT_PASSWORD` a la API. El 2026-10-04 se aplicó una vez el modo enfocado de horarios semanales a la DB local; creó 40 reglas para los ocho médicos y cinco días, dejó intactos los conteos de usuarios/citas y se volvió a fijar el flag en `false`. No se reejecutó el seed completo ni se tocó `.env`; no se ejecutó `docker compose down -v`.

El panel ADMIN requiere que el usuario a convertir en profesional ya exista; no se ha implementado un endpoint de gestión de cuentas. El catálogo de EPS/planes/especialidades tampoco forma parte de este bloque S3. No se ejecutó todavía el flujo autenticado completo en browser ni el recorrido end-to-end de reserva. Un ciclo Red→Green de regla nueva y las funcionalidades S4 siguen pendientes.
