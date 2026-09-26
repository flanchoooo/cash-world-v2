# Administration deployment

The package consists of the Spring Boot backend and React build served by Nginx. The backend connects to an existing MySQL database; Compose does not start a database container.

## Local development package

```sh
APP_PORT=18080 docker compose up --build -d
```

Open `http://localhost:8089` for the admin app or `http://localhost:18080` for the backend API with the port override above. Without the override, the API is at `http://localhost:8011`. This deployment uses the dev profile, seeded configuration and mock biller. The Compose frontend proxies `/api` to the backend service, keeping browser requests same-origin and avoiding CORS configuration. When running Vite outside Compose, its default proxy target is `http://51.222.205.225:8011`; override `BACKEND_URL` to use a different backend. Compose uses the frontend origin `http://localhost:8089`.

## Production deployment

The single `docker-compose.yml` is for local development and testing. Production deployment needs a separately managed HTTPS frontend/backend environment, database, secrets, backups and restricted network access. Keep browser API traffic behind the frontend Nginx proxy by setting its `BACKEND_URL` to a backend root URL reachable from the frontend container.

Nginx runs as an unprivileged user, sets browser security headers, caches hashed assets and returns the SPA for deep links. Both build contexts exclude node_modules, local environment files and generated development artifacts.

## Provider boundary

The UI can call every implemented backend workflow. It does not turn the mock biller into a live integration. The production profile rejects unconfigured provider purchases/reversals. Live provider idempotency, reconciliation, funding reservations, crash recovery, external FX settlement and the compliance work described in the root README must be completed before real-money service launch.
