# Deployment

The application is packaged as a Docker-based multi-instance service.

## Requirements

Linux server with:

- Docker
- Docker Compose plugin
- Git

## Clone

```bash
git clone <YOUR_GITHUB_REPOSITORY_URL>
cd seat-reservation-service
```

## Configure JWT secret

```bash
export JWT_SECRET='replace-with-a-long-secure-random-secret'
```

The secret must be at least 32 bytes.

## Start

```bash
docker compose -f docker/docker-compose.full.yml up --build -d
```

## Check containers

```bash
docker compose -f docker/docker-compose.full.yml ps
```

Expected services:

```text
seat-postgres
seat-redis
seat-app1
seat-app2
seat-gateway
```

## Readiness

```bash
curl http://127.0.0.1:8080/actuator/health/readiness
```

Expected:

```json
{
  "status": "UP"
}
```

## Prometheus

```bash
curl http://127.0.0.1:8080/actuator/prometheus
```

## Public network

Expose only the gateway port publicly.

Do not expose PostgreSQL or Redis directly to the internet.

The architecture should be:

```text
Internet
   |
   v
Reverse Proxy / Gateway
   |
   +----------------+
   |                |
   v                v
app1              app2
   \                /
    \              /
      PostgreSQL
         +
        Redis
```

## HTTPS

For a production-style deployment, terminate TLS using:

- cloud load balancer
- Caddy
- Nginx with Let's Encrypt
- hosting platform TLS

## README

After deployment, add:

```text
Live API: https://your-domain.example
```

to the root README.