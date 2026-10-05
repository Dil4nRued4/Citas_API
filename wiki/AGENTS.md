# Instrucciones para la Wiki de `citas-api`

Esta Wiki contiene conocimiento duradero y específico del backend. La mantiene el agente backend de **Codex**; no mantener una copia alternativa en `docs/wiki/`. Lee primero [`wiki/index.md`](wiki/index.md), luego la [Wiki global](../../wiki/wiki/index.md) para contexto cross-repo y la documentación fuente relevante.

## Capas

- `raw/`: fuentes locales curadas, inmutables. La documentación compartida en la raíz sigue siendo la autoridad mientras no se archive aquí expresamente.
- `wiki/`: páginas sobre implementación, arquitectura, persistencia, contratos, decisiones aprobadas, pruebas y preguntas de backend.
- `schema/`: convenciones y operaciones de esta Wiki.
- Mantener `wiki/index.md` como catálogo completo y `wiki/log.md` como registro cronológico append-only.
- Referenciar las páginas pertinentes de la [Wiki global](../../wiki/wiki/index.md) y, para contratos/decisiones cross-repo, la [Wiki de `citas-web`](../../citas-web/wiki/wiki/index.md).

## Evidencia

Distinguir HECHO VERIFICADO, REQUISITO ESPECIFICADO, DECISIÓN APROBADA, PREFERENCIA y PREGUNTA ABIERTA. No presentar PRD, SQL de referencia, documentación ni migraciones sin verificar como implementación actual. Inspeccionar código y pruebas antes de actualizar estado.

No registrar secretos, tokens, credenciales, datos personales, `.env` ni conversaciones. No modificar los archivos fuente durante ingestión. No inventar decisiones o requisitos.

Después de cambios backend sustanciales, actualizar las páginas locales y el índice/log; si afecta contrato, reglas compartidas o integración web, coordinar el cambio con Codex/orquestador y reflejar síntesis y enlaces cruzados en la Wiki global y la Wiki frontend pertinente.

El Scrum generator solo escribe en `../docs/wiki/scrum/`; esa carpeta no es esta Wiki.
