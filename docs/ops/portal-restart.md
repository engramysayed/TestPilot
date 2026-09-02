# Portal restart (pick up conversion fixes)

The Delivery portal loads Java classes once at JVM start. Editing convert code and recompiling does **not** update a portal that is already running on port 8080.

## When you must restart

After any change under `src/main/java/delivery/**` that should affect live jobs.

## Steps (Windows)

`start-portal.bat` now **kills whatever is listening on 8080**, waits briefly, then starts Spring Boot. Prefer:

```bat
start-portal.bat
```

Manual alternative:

```bat
netstat -ano | findstr :8080
taskkill /PID <pid> /F
mvn -Dmaven.compiler.release=21 spring-boot:run
```

Confirm log: `SemanticPassGate: intent/action checks only…`

Ollama health check (`curl.exe http://127.0.0.1:11434/api/tags`) is **optional** — see [ollama-local.md](ollama-local.md).
