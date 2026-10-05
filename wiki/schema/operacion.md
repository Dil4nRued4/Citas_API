# Operación de la Wiki backend

Las reglas normativas están en [`../AGENTS.md`](../AGENTS.md).

- **INGEST**: leer la fuente y verificar alcance backend; sintetizar hechos y requisitos por separado; enlazar fuentes, Wiki global y frontend cuando corresponda; actualizar índice y añadir al log.
- **QUERY**: empezar por el índice local, consultar contexto global y contrastar con código/configuración/pruebas del backend. Explicitar lo no verificado.
- **LEARN**: registrar solo decisiones explícitamente aprobadas, preferencias declaradas y hechos técnicos comprobables. No guardar transcripts.
- **LINT**: comprobar páginas huérfanas, enlaces rotos, contradicciones con la Wiki global, datos sensibles y distinción entre objetivo e implementación.

Formato de documentación API: método/ruta, request, respuestas HTTP, autenticación, validación y evidencia de prueba. Documentar variables de entorno por nombre y propósito, nunca valores secretos.

La ruta `docs/wiki/scrum/` pertenece a la Skill de especificación y no se sustituye por esta Wiki.
