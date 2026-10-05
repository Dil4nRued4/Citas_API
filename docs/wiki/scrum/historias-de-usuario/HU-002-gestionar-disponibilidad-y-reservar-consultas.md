---
id: HU-002
tipo: historia-de-usuario
titulo: "Gestionar disponibilidad y reservar consultas"
estado: "En desarrollo"
epica: "[[EP-001-agendamiento-de-citas]]"
esfuerzo: "Muy alto"
dependencias:
  - "[[HU-001-administrar-profesionales-y-especialidades]]"
relacionadas:
  - "[[HU-003-resolver-solicitudes-especializadas]]"
---

# HU-002 — Gestionar disponibilidad y reservar consultas

## Historia de usuario

**COMO** PROFESSIONAL y paciente  
**QUIERO** publicar bloques válidos, buscar horarios libres y agendar una consulta  
**PARA** acordar una cita sin conflictos de agenda.

## Alcance y reglas

Derivada de PRD RF-08 a RF-12 y RN-01 a RN-08, más alcance S3. Los bloques son de lunes a viernes en desarrollo seed; producción admite fechas/hora compatibles con PRD. La grilla base es de 30 minutos. Duración solicitada se deriva de la especialidad activa y es 30 o 60 minutos. Se rechazan duración inválida, bloque desalineado, solapamiento y fechas pasadas. La disponibilidad debe estar asignada al profesional y sede según PRD.

Las citas `REQUESTED` retienen su horario provisionalmente por regla explícita de S3 y PRD RF-12; `REJECTED` libera la retención. La cita general se crea `APPROVED`; la especializada `REQUESTED`. El tipo de cita se deduce del profesional.

## Criterios de aceptación

### CA-01 — Propiedad de agenda
**Dado** un PROFESSIONAL autenticado **cuando** crea, consulta, edita o elimina un bloque propio **entonces** solo opera su agenda; intentar actuar sobre agenda ajena responde 403.

### CA-02 — Restricciones de bloque
**Dado** un bloque **cuando** está pasado, mal alineado a la grilla o se solapa con otro bloque del profesional **entonces** la API devuelve error 400/409 claro y no crea la disponibilidad.

### CA-03 — Disponibilidad consultable
**Dado** un profesional activo y disponibilidad **cuando** el paciente filtra por fecha, especialidad o tipo **entonces** solo ve franjas libres que completan la duración entera.

### CA-04 — Aprobación automática/especializada
**Dado** un paciente que confirma un horario libre **cuando** el profesional es GENERAL **entonces** se crea `APPROVED`; si es ESPECIALISTA, se crea `REQUESTED` reteniendo el horario.

### CA-05 — Doble reserva
**Dado** dos solicitudes concurrentes para el mismo horario **cuando** ambas intentan reservar **entonces** no pueden confirmarse las dos; una falla con 409 usando protección transaccional/índice único persistente.

### CA-06 — Slots válidos
**Dado** solicitud de cita **cuando** duración no es 30/60, el bloque no permite slots consecutivos o la fecha ya pasó **entonces** la solicitud se rechaza sin modificar la agenda.

## Definition of Done

- [ ] Todas las CA tienen pruebas backend, incluidos intentos concurrentes y casos inválidos.
- [ ] El flujo REST se consume desde la UI, con estados carga/vacío/error/éxito.
- [ ] Identidad visual/login S2 conservados y rutas/acciones seguras.
- [ ] Seed no es prerequisito de las pruebas automatizadas.
- [ ] Build, typecheck, tests y red local exigida por S3 pasan.
- [ ] Wiki y tabla de trazabilidad se actualizan.

## Evidencia

| Criterio | Resultado | Evidencia | Observación |
|---|---|---|---|
| CA-01 | Parcial | `AppointmentApiIntegrationTest.professionalCannotEditAnotherProfessionalsBlock` | La edición ajena da 403; falta cubrir el CRUD propio de extremo a extremo. |
| CA-02 | Parcial | `rejectsInvalidGridAndOverlappingBlock`, `rejectsPastAvailabilityAndAppointmentStarts` | Grilla, solapamiento y fechas pasadas se rechazan; falta completar cobertura de límites de duración. |
| CA-03 | Verificado API parcial | `availabilityRangeNeverReturnsIncompleteSixtyMinuteSlots` | GET disponibilidad se prueba con especialidad de 60 min y no devuelve franjas cuyo fin sobrepasa el rango; filtros completos y flujo UI siguen pendientes. |
| CA-04 | Verificado API | `bookingInfersGeneralApprovalAndRejectsDuplicateSlot`, `specializedBookingIsRequestedAndAdminDecisionIsOneWay` | General `APPROVED`; especialista `REQUESTED`; reserva general duplicada da 409. |
| CA-05 | Verificado API | `concurrentRequestsCannotReserveTheSameSlot` | Dos requests concurrentes: uno 201, otro 409 y una sola cita. |
| CA-06 | Parcial | `specializedBookingIsRequestedAndAdminDecisionIsOneWay`, `rejectsPastAvailabilityAndAppointmentStarts`, `availabilityRangeNeverReturnsIncompleteSixtyMinuteSlots` | Duración viene del catálogo; 60 min reserva dos slots y el rango no expone franjas incompletas. Falta cubrir todos los escenarios y límites de duración inválida. |

Estado global: **Parcial; no Done**. Falta verificar filtros completos y CRUD propio completo, flujo UI autenticado, estados de UI y e2e.

## Fuente

PRD RF-08 a RF-12, RN-01 a RN-08 y guía GOAL 02 de solicitud especializada end-to-end.
