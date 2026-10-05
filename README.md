# citas-api

Backend Java 21 / Spring Boot 3.5.x del laboratorio. Implementa el primer vertical slice de registro/login con JWT y persistencia MySQL/Flyway.

## Documentación compartida
- `docs/wiki/scrum/`: épicas/HU generadas con la Skill Scrum.
- `wiki/`: Wiki local del backend, mantenida por el agente backend y enlazada con `../wiki/` y `../citas-web/wiki/`.
- `../wiki/`: Wiki global del workspace, mantenida por el agente orquestador de Codex.
- `automations/n8n/`: JSON exportados en S5/S6.

## Requisitos

- Java 21 y Maven.
- MySQL 8.4 disponible en `localhost:3307` por el Compose del workspace.
- Variables del backend definidas según `.env.example`; Spring Boot no carga un archivo `.env` automáticamente.

## Ejecutar

Para ejecutar Spring Boot directamente en el host, copia `.env.example` a `.env`, sustituye los marcadores y carga las variables en el proceso de PowerShell. Spring Boot no carga `.env` automáticamente:

```powershell
Get-Content .env | ForEach-Object {
    if ($_ -match '^\s*([^#=]+)=(.*)$') {
        [Environment]::SetEnvironmentVariable($Matches[1].Trim(), $Matches[2].Trim(), 'Process')
    }
}
mvn spring-boot:run
```

Para ejecutar pruebas de integración con H2:

```powershell
mvn test
```

Migraciones Flyway se aplican al iniciar contra MySQL. En el workspace raíz, `docker compose --profile dev up -d` también ejecuta `mvn spring-boot:run` en el contenedor API; revisa el arranque con `docker compose --profile dev logs -f citas-api-dev`. Una respuesta vacía en `localhost:8080` indica que no hay servidor atendiendo; el registro es `POST /auth/register`, no una página navegable por GET.

La sesión actual no pudo arrancar este backend porque Java/Maven y el CLI de Docker no están disponibles en PATH.
