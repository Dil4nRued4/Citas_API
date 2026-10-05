# Responsabilidades y requisitos backend

El PRD y el prompt backend asignan al backend la lógica de negocio, la API REST/JSON, persistencia, seguridad/autorización y reglas de citas. Auth S2 está implementado y verificado; el bloque S3 tiene implementación parcial, con gaps descritos en [estado local](estado-del-repositorio.md).

## Arquitectura objetivo

- Dominio y aplicación independientes de Spring/JPA/HTTP.
- Casos de uso en aplicación; puertos para dependencias; REST y persistencia como adaptadores.
- Controladores traducen HTTP y no concentran negocio.
- No acoplar backend a React/Angular ni editar frontend desde agente backend.
- Cambios al esquema requieren migración Flyway y justificación.
- Secretos por entorno; no registrar tokens ni passwords.

## Reglas funcionales asignadas

Backend es autoridad para reglas de disponibilidad/reserva; slots consecutivos 30/60 minutos; aprobación automática de cita general y decisión administrativa para especializada; reserva y liberación de slots; cancelación/reprogramación; auditoría; autorización por rol y ownership. El detalle autoritativo es el [PRD](../../../PRD.md) y resumen en [Wiki global](../../../wiki/wiki/alcance-funcional.md).

## Integración cross-repo

El frontend consume directamente la API Spring Boot por REST, sin BFF. El contrato debe definirse durante el proyecto. Cuando cambie, actualizar documentación backend, cliente y [síntesis global](../../../wiki/wiki/index.md), enlazando la [Wiki de `citas-web`](../../../citas-web/wiki/wiki/index.md).

Para S2 se implementaron y verificaron `POST /auth/register`, `POST /auth/login`, `GET /auth/me`, `POST /auth/refresh` y `POST /auth/logout`; el contrato está en [`contrato-auth.md`](contrato-auth.md). S3 adopta el alcance/historias derivadas en [`docs/wiki/scrum/`](../../docs/wiki/scrum/README.md), con base en PRD RF-07—RF-12. La implementación parcial de catálogo, profesionales, disponibilidad, citas, decisiones y seed está en [`contrato-citas.md`](contrato-citas.md); última suite `verify` 22/22 y gaps en [`evidencias-s3.md`](evidencias-s3.md).

## Decisiones y límites S3

- El usuario autorizó derivar HU/DoD del PRD porque la carpeta Scrum solo contenía `.gitkeep`; las historias quedan en estado `En desarrollo`/`Bloqueada`, no se presentan como historias previas aprobadas.
- La duración se define por especialidad y será 30 o 60 minutos según PRD RF-09, tras decisión explícita del usuario de priorizar PRD ante la petición alternativa de duración por doctor general.
- Se adopta `PATIENT` en el modelo de roles solicitado para S3; el PRD denomina al mismo actor `USER`. No había rol persistido en S2; V2 añade `users.role` con default `PATIENT` para las cuentas existentes.
- El seed usa datos `@example.com`, nombres sintéticos, sedes/direcciones ficticias y contraseña de entorno; el test H2 prueba idempotencia/cantidades. No se ejecutó en el volumen MySQL persistente.
- Git no existe en el directorio (`git rev-parse` falla); el usuario rechazó inicializarlo. No se pueden crear commits ni demostrar hook de pre-commit bajo esta restricción.
