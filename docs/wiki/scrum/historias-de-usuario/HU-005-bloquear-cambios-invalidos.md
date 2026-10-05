---
id: HU-005
tipo: historia-de-usuario
titulo: "Bloquear cambios que incumplen verificaciones"
estado: "Done"
epica: "[[EP-002-red-de-verificacion]]"
esfuerzo: "Alto"
dependencias: []
relacionadas: []
---

# HU-005 — Bloquear cambios que incumplen verificaciones

## Historia de usuario

**COMO** equipo de desarrollo sin Git habilitado  
**QUIERO** un comando local que ejecute pruebas y análisis de secretos  
**PARA** detectar cambios que rompan las reglas o incluyan credenciales antes de adoptar Git.

## Criterios de aceptación

### CA-01 — Verificaciones locales
**Dado** el workspace **cuando** se ejecuta `.\scripts\verify.ps1` **entonces** corre pruebas backend, build/typecheck/tests frontend y escaneo de secretos, y devuelve código distinto de cero ante cualquier fallo.

### CA-02 — Falla y recuperación
**Dado** un cambio que rompe pruebas **cuando** se ejecuta verify **entonces** el comando falla; al restaurar la prueba y corregir el cambio, verify pasa.

### CA-03 — Secreto sintético
**Dado** un patrón de secreto ficticio en un archivo de proyecto **cuando** se ejecuta verify **entonces** el scanner lo bloquea; al retirarlo, scan pasa y no queda rastro en el árbol. No se usa Git ni se afirma comprobación de historial.

## Definition of Done

- [x] Runner `scripts/verify.ps1` y scanner local, desacoplados de Git.
- [x] Runner FAIL/PASS con prueba automatizada temporalmente fallida.
- [x] Secreto ficticio bloqueado por verify, eliminado y búsqueda final sin coincidencias.
- [x] `.env` privado excluido del escaneo y no registrado; documentación prepara su invocación por un `pre-commit` futuro sin instalarlo.

## Evidencia ejecutada

| Criterio | Resultado | Evidencia | Observación |
|---|---|---|---|
| CA-01 | Verificado | `scripts/verify.ps1`: backend 22/22, frontend build/typecheck/tests 4/4 y secret scan en PASS | La salida completa está en [evidencias S3](../../../wiki/wiki/evidencias-s3.md). |
| CA-02 | Verificado | Expectativa temporalmente rota: verify exit 1; restaurada: verify exit 0 | Verifica que el runner se detiene; no es Red→Green de una regla funcional nueva. |
| CA-03 | Verificado | Fixture ficticio detectado dentro de verify (exit 1); fixture eliminado; verify posterior exit 0 | `rg` no encontró patrón ni archivo residual. No se revisó historial Git. |

Estado HU-005: **Done en su alcance sin Git**. Esto no implica que la red funcional de S3 (Red→Green de una regla nueva) ni las demás HU estén completas.
