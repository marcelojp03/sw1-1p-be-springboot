---
applyTo: "**"
---

# Instrucciones del Agente — Spring Boot Backend

Estas instrucciones aplican automáticamente cuando trabajas en este repositorio (`sw1-1p-be-springboot/`).

---

## Regla de finalización — OBLIGATORIA

Antes de declarar cualquier tarea como completada, **siempre** debes ejecutar los siguientes pasos en orden:

### 1. Compilar el proyecto

Ejecuta desde la raíz del repositorio:

```bash
./mvnw compile -q
```

- Si hay errores de compilación, corrígelos antes de continuar.
- No declares la tarea como terminada si el proyecto no compila.

### 2. Documentar endpoints en `API.md`

Cada vez que **crees, modifiques o elimines** un endpoint en cualquier `*Controller.java`:

- Actualiza `.github/API.md` (en la carpeta raíz del workspace, **un nivel arriba** de este repositorio: `../.github/API.md`).
- Usa el siguiente formato por endpoint:

```markdown
### METHOD /ruta/completa
**Descripción:** Qué hace el endpoint.  
**Roles permitidos:** ADMIN | OFFICER | CLIENT | Público  
**Request body:** `NombreDTO { campo1, campo2 }` (omitir si no aplica)  
**Response:** `NombreDTO { campo1, campo2 }`  
**Códigos:** `200 OK`, `201 Created`, `400 Bad Request`, `401 Unauthorized`, `404 Not Found`
```

- Organiza por secciones: `## Auth`, `## Organization`, `## Users`, `## Clients`, `## Policies`, `## Procedures`, `## Tasks`, `## Notifications`, `## Mobile`.
- Si eliminas un endpoint, elimina su entrada del archivo.
- **Cuando modifiques o elimines un endpoint**, agrega al final de su entrada en `API.md` una nota de cambio:

```markdown
> ⚠️ **Cambio:** [fecha] — [descripción del cambio]. Repos afectados: Angular / Flutter / FastAPI.
```

  Esto permite que los otros agentes detecten qué actualizar cuando lean `API.md`.

### 3. Hacer commit y push a Git

Ejecuta desde la raíz del repositorio:

```bash
git add .
git commit -m "feat(backend): <descripción breve de lo implementado>"
git push
```

- Usa prefijos de commits convencionales: `feat`, `fix`, `refactor`, `docs`, `chore`.
- El mensaje debe describir qué se implementó, no solo "cambios".

---

## Reglas de arquitectura

- Paquete base: `sw1.p1`
- Arquitectura **package-by-feature**: cada feature tiene subcarpetas `api/`, `application/`, `domain/`, `dto/`.
- Cross-cutting en paquetes raíz: `config/`, `security/`, `exception/`, `shared/`.
- `procedure_history` es `@Document` separado — **nunca** embebido en `procedures`.
- `policySnapshot` **sí** va embebido en `Procedure`.
- `WorkflowEngineService` es el núcleo. Modifícalo con cuidado.
- FastAPI solo se llama desde `AIIntegrationService` vía `WebClient`, nunca directo desde controllers.
- Endpoints móviles van en `/api/mobile/**` con acceso solo para rol `CLIENT`.

---

## Referencias

- Plan general: `.github/PLANNING.md` (carpeta raíz del workspace)
- Instrucciones globales: `.github/copilot-instructions.md` (carpeta raíz del workspace)
- Endpoints documentados: `.github/API.md` (carpeta raíz del workspace)
