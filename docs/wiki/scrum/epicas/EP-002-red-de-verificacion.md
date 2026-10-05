---
id: EP-002
tipo: epica
titulo: "Red automatizada de verificación"
estado: "Pendiente de aprobación"
fuente: "Restricciones técnicas y alcance S3 A6"
---

# EP-002 — Red automatizada de verificación

## Objetivo

Evitar aceptar cambios que rompan pruebas, build/typecheck u otras verificaciones del workspace, y detectar secretos antes de integrar cambios.

## Historias de usuario

- [[HU-005-bloquear-cambios-invalidos]]

## Bloqueador conocido

No existe `.git` en el workspace y el usuario rechazó inicializar un repositorio. No se puede instalar/configurar un hook de Git ni ejecutar commits de prueba sin autorización posterior. La historia no puede cerrarse mientras ese impedimento persista.
