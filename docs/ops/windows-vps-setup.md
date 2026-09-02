# Windows VPS setup (no Docker)

Host TestPilot Delivery Portal on a Windows Server VPS using RDP — same skills as a normal Windows PC.

## Recommended machine

- Windows Server 2022
- 8 vCPU, **32 GB RAM** (16 GB minimum)
- 100+ GB SSD
- Public IPv4 + DNS name (e.g. `portal.yourdomain.com`)

## One-time installs (RDP in)

1. **JDK 21+** (Temurin or Oracle) — add `java` to PATH.
2. **Maven 3.9+** — add `mvn` to PATH.
3. **Google Chrome** (Selenium/driver needs a browser when dry-run is off).
4. **Ollama for Windows** — install from https://ollama.com ; pull a model later.
5. **Git** (optional) — to clone the repo.

## Deploy the app

1. Copy or clone this repository to e.g. `C:\apps\TestPilot`.
2. Edit `src\main\resources\application.properties`:
   - Change `delivery.admin-password` immediately.
   - Keep `delivery.dry-run=true` until Ollama works.
   - Paths `delivery.store-root` / `delivery.work-dir` can stay relative or use `C:\apps\TestPilot\delivery-store`.
3. Open PowerShell in the repo root and run:

```bat
start-portal.bat
```

Or:

```bat
mvn -Dmaven.compiler.release=21 spring-boot:run
```

4. Open `http://SERVER_IP:8080`. Sign in with the admin email/password from properties.
5. Use **Account** to change your password/email.
6. Use **Users** to invite people (optionally email them), view accounts, and delete users.
7. To send real invite emails, set in `application.properties`:
   - `delivery.mail-enabled=true`
   - `delivery.public-base-url=https://your-domain`
   - `delivery.mail-from=...`
   - `spring.mail.host` / `port` / `username` / `password` (SMTP)

## Enable real conversion (optional)

1. In Ollama, pull your model (example): `ollama pull qwen2.5:latest`
2. Set `delivery.dry-run=false` and restart the portal.
3. Confirm `delivery.llm-base-url=http://127.0.0.1:11434`

## HTTPS (recommended)

- Point DNS A record to the VPS.
- Use **Win-ACME** or **Caddy for Windows** as a reverse proxy to `localhost:8080`.
- Open Windows Firewall for **443** (and **3389** only from your IPs if possible).

## Survive reboot

Use [NSSM](https://nssm.cc/) or WinSW to run:

```text
mvn -Dmaven.compiler.release=21 spring-boot:run
```

from `C:\apps\TestPilot` as a Windows Service, or package a fat jar with `spring-boot:repackage` and run `java -jar …`.

## Backups

Copy regularly:

- `delivery-store\` (framework versions, ZIPs, H2 `portal-db*`)
- `application.properties` (or your secrets file)

## Security checklist

- [ ] Change default admin password
- [ ] Restrict RDP to your IP
- [ ] HTTPS on public access
- [ ] Do not commit real customer passwords
- [ ] Keep `webapp.properties` out of delivery ZIPs (packager already enforces example-only)
