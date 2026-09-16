# Administration deployment

The verified package consists of the existing Spring Boot application, MySQL and the React build served by Nginx. Production deployment is pending a hosting target and HTTPS domain.

## Local development package

```sh
# If 3306/8080 are already occupied:
MYSQL_PORT=3307 APP_PORT=18080 docker compose up --build -d
```

Open `http://localhost:8088`. This deployment uses the dev profile, seeded configuration and mock biller. It is separate from running Vite on port 5173. To connect Vite to a Compose backend, set that backend's `ADMIN_ORIGIN=http://localhost:5173` instead.

## Production package

1. Provision MySQL with backup/recovery and TLS. Create separate migration and runtime users using the root README's database permissions. Production does not load development seed records.
2. Supply `DB_URL`, `DB_USER`, `DB_PASSWORD`, `FLYWAY_USER`, `FLYWAY_PASSWORD`, `JWT_SECRET` (at least 32 bytes) and an exact HTTPS `ADMIN_ORIGIN`, such as `https://admin.your-domain.example`. Keep secrets outside version control. Optional initial bootstrap uses `ADMIN_USERNAME`/`ADMIN_PASSWORD`; remove those values after provisioning.
3. Configure `REMITTANCE_RATES` only for supported, operationally funded corridors. Configure currencies, system wallets and fees through the administration application before use.
4. Run `docker compose --env-file /secure/path/production.env -f compose.production.yml up --build -d` on the approved server.
5. Terminate TLS on that server or an approved load balancer and forward the configured domain to loopback port 8088. Nginx serves the SPA and forwards `/api` to the internal backend. Preserve the original Origin header. Secure refresh cookies require HTTPS. Restrict direct database/backend access.
6. Verify `/healthz`, sign-in, refresh after reload, restricted roles, a nonfinancial workflow and the operational monitoring/backup arrangements. Test financial flows in a dedicated staging database first.

Nginx runs as an unprivileged user, sets browser security headers, caches hashed assets and returns the SPA for deep links. Both build contexts exclude node_modules, local environment files and generated development artifacts. The production Compose file fails when mandatory settings are absent and does not publish the backend or database port.

## Provider boundary

The UI can call every implemented backend workflow. It does not turn the mock biller into a live integration. The production profile rejects unconfigured provider purchases/reversals. Live provider idempotency, reconciliation, funding reservations, crash recovery, external FX settlement and the compliance work described in the root README must be completed before real-money service launch.
