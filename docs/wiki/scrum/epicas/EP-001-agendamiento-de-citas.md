---
id: EP-001
tipo: epica
titulo: "Agendamiento de citas"
estado: "En desarrollo"
fuente: "PRD v1.0 RF-07 a RF-12 y alcance S3 autorizado"
---

# EP-001 — Agendamiento de citas

## Objetivo

Permitir administrar profesionales y catálogos de especialidad, publicar y consultar disponibilidad, y agendar consultas con la aprobación que corresponde al tipo de profesional.

## Alcance derivado

- Administración de profesional, usuario asociado, tipo y especialidades.
- Disponibilidad por bloques laborales y slots consecutivos de 30 minutos.
- Consulta de disponibilidad por especialidad/profesional/fecha.
- Reserva de cita general en `APPROVED` y especializada en `REQUESTED`.
- Aprobación/rechazo administrativo de solicitudes especializadas.
- Seed sintético e idempotente para desarrollo.

## Historias de usuario

- [[HU-001-administrar-profesionales-y-especialidades]]
- [[HU-002-gestionar-disponibilidad-y-reservar-consultas]]
- [[HU-003-resolver-solicitudes-especializadas]]
- [[HU-004-cargar-datos-ficticios-de-desarrollo]]

## Fuente de autoridad

El [PRD](../../../../../PRD.md) gobierna el catálogo de especialidades y las duraciones: cada especialidad define 30 o 60 minutos. Las historias derivadas no amplían ni contradicen ese modelo.
