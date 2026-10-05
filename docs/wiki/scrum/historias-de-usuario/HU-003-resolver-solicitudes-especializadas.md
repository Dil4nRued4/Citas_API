---
id: HU-003
tipo: historia-de-usuario
titulo: "Resolver solicitudes especializadas"
estado: "En desarrollo"
epica: "[[EP-001-agendamiento-de-citas]]"
esfuerzo: "Medio"
dependencias:
  - "[[HU-002-gestionar-disponibilidad-y-reservar-consultas]]"
relacionadas: []
---

# HU-003 — Resolver solicitudes especializadas

## Historia de usuario

**COMO** ADMIN  
**QUIERO** revisar y aprobar o rechazar citas especializadas solicitadas  
**PARA** confirmar la atención y liberar horarios no aceptados.

## Reglas de negocio

Derivada de PRD RF-12, RF-18 y RN-03/RN-04/RN-09/RN-11. Solo citas especializadas en estado `REQUESTED` pueden decidirse. Aprobar cambia a `APPROVED`; rechazar exige motivo, cambia a `REJECTED` y libera el slot retenido. La cita general no se presenta en esta bandeja.

## Criterios de aceptación

### CA-01 — Aprobación autorizada
**Dado** cita especializada `REQUESTED` y ADMIN **cuando** aprueba **entonces** el estado se vuelve `APPROVED` y el slot permanece ocupado.

### CA-02 — Rechazo con motivo y liberación
**Dado** cita especializada `REQUESTED` y ADMIN **cuando** rechaza con motivo **entonces** el estado queda `REJECTED`, el motivo se muestra al paciente y la franja queda disponible.

### CA-03 — Acceso y transición inválida
**Dado** usuario sin rol ADMIN o cita que no está `REQUESTED` **cuando** intenta decidir **entonces** la API devuelve 403 o 409, respectivamente, sin cambiar la cita.

## Definition of Done

- [ ] CA-01–03 probadas con datos aislados, incluyendo usuario de rol incorrecto.
- [ ] Frontend de admin refleja las decisiones y el paciente ve estado/motivo actualizado.
- [ ] Build/typecheck/tests, secret scan y hook aplicables pasan.
- [ ] Wiki y trazabilidad se actualizan.

## Evidencia

| Criterio | Resultado | Evidencia | Observación |
|---|---|---|---|
| CA-01 | Verificado API | `specializedBookingIsRequestedAndAdminDecisionIsOneWay` | Paciente obtiene 403; admin cambia `REQUESTED` a `APPROVED`. |
| CA-02 | Verificado API | `adminRejectionReleasesReservedSlots` | Rechazo con motivo pasa a `REJECTED` y elimina dos slots reservados. |
| CA-03 | Verificado API | `specializedBookingIsRequestedAndAdminDecisionIsOneWay` | Nueva decisión fuera de `REQUESTED` recibe 409. |

Estado global: **Parcial; no Done**. La API pasa CA-01–03; falta validar end-to-end las acciones de UI y reflejo actualizado para paciente, además del hook de S3.

## Fuente

PRD RF-12, RF-18, RF-19 y reglas S3.
