---
id: HU-001
tipo: historia-de-usuario
titulo: "Administrar profesionales y especialidades"
estado: "En desarrollo"
epica: "[[EP-001-agendamiento-de-citas]]"
esfuerzo: "Alto"
dependencias: []
relacionadas:
  - "[[HU-002-gestionar-disponibilidad-y-reservar-consultas]]"
---

# HU-001 — Administrar profesionales y especialidades

## Historia de usuario

**COMO** ADMIN  
**QUIERO** administrar profesionales, asignaciones y sus especialidades  
**PARA** que solo profesionales habilitados puedan ofrecer consultas.

## Contexto y alcance

Derivada de PRD RF-07 y de A1/A3. Incluye asignación a un usuario PROFESSIONAL, tipo GENERAL/ESPECIALISTA, catálogo de especialidades y sus duraciones, descripción y datos sintéticos. La especialidad primaria y sede son requisitos del PRD; el alcance S3 pide CRUD de asignaciones y doctor.

## Reglas de negocio

- Solo ADMIN administra profesionales, sus usuarios y asignaciones.
- Una especialidad define una duración única de 30 o 60 minutos; el doctor no la modifica (PRD RF-09).
- El tipo de cita se deriva del doctor. El paciente no lo envía como decisión libre.
- Catálogos inactivos no se ofrecen para nuevas reservas (PRD RF-06/RF-08).

## Criterios de aceptación

### CA-01 — Permiso administrativo
**Dado** un usuario sin rol ADMIN **cuando** intenta crear, editar o eliminar un profesional/asignación **entonces** recibe 403 y no se modifica la información.

### CA-02 — Asignaciones coherentes
**Dado** un profesional y especialidad **cuando** ADMIN asigna la especialidad **entonces** queda asociada al usuario profesional y es consultable; las reglas de sede/especialidad se aplican al publicar agenda.

### CA-03 — Validación de catálogo
**Dado** una especialidad **cuando** ADMIN la guarda con duración distinta de 30 o 60 minutos **entonces** la API responde 400; si está referenciada, no se elimina físicamente.

## Definition of Done

- [ ] Todas las CA tienen evidencia de API y autorización.
- [ ] Persistencia y migraciones compatibles con datos existentes.
- [ ] Pruebas backend pasan y frontend admin puede realizar operaciones autorizadas.
- [ ] Pruebas, build/typecheck, escaneo y hook exigidos por S3 pasan.
- [ ] Wiki y tabla de trazabilidad se actualizan.

## Evidencia

| Criterio | Resultado | Evidencia | Observación |
|---|---|---|---|
| CA-01 | Parcial | `AuthApiIntegrationTest.patientCannotAccessAdminAppointmentQueue`; browser role guard | Se verificó control ADMIN en cola/guardia, pero no hay prueba por endpoint CRUD de profesional. |
| CA-02 | Parcial | `AppointmentApiIntegrationTest.adminCanCreateAndUpdateProfessionalAssignments` y vista ADMIN | Se comprueba creación/actualización de perfil/asignaciones; el usuario debe existir. |
| CA-03 | Pendiente | — | No existe CRUD administrativo del catálogo ni prueba de duración no soportada. |

Estado global: **Parcial; no Done**. El panel, el backend y la cobertura del CRUD completo requieren más pruebas; el backend no ofrece crear cuentas desde la vista administrativa.

## Fuente y decisión

Derivada de PRD RF-06/RF-07/RF-09 y alcance S3. Duración según especialidad se conserva por respuesta explícita del usuario ante la contradicción con la regla de duración por doctor.
