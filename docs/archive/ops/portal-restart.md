# Portal restart (pick up conversion fixes)

The Delivery portal loads Java classes once at JVM start. Editing convert code and recompiling does **not** update a portal that is already running on port 8080.

## When you must restart

After any change under `src/main/java/delivery/**` that should affect live jobs.

## Steps (Windows)

Package the fat jar first when Java or resources changed (this does **not** start the portal):

```bat
rebuild-portal.cmd
```

`start-portal.cmd` stops a previous Keel `java.exe` on port 8080, waits briefly, then starts the portal from `keel-portal\target\keel-portal-1.0-SNAPSHOT.jar` when that jar is a Spring Boot executable. It does not kill other programs on that port.

```bat
start-portal.cmd
```

Manual alternative:

```bat
netstat -ano | findstr :8080
taskkill /PID <pid> /F
mvn -pl keel-portal -am -Dmaven.compiler.release=21 spring-boot:run
```

Confirm log: `SemanticPassGate: intent/action checks only…`

Ollama health check (`curl.exe http://127.0.0.1:11434/api/tags`) is **optional** — see [ollama-local.md](ollama-local.md).
