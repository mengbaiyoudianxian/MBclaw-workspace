# MBclaw Server — Production-ready deployment

The **MBclaw Server** is the production deployment of MBclaw-Lite with:

- **Admin Panel** — Web dashboard for configuration, monitoring, and management
- **API Gateway** — Unified entry point with rate limiting, auth, and routing
- **Monitoring** — Health checks, metrics, alerting (Prometheus + Grafana)
- **Deployment** — Docker Compose + Kubernetes production manifests

## Quick Start

```bash
# Docker Compose (recommended for single server)
cd deploy
docker-compose -f docker-compose.prod.yml up -d

# Kubernetes
kubectl apply -k k8s/overlays/production
```

## Components

| Component | Description | Port |
|-----------|-------------|------|
| MBclaw API | Core FastAPI server | 8000 |
| Admin Panel | Web management UI | 8080 |
| Prometheus | Metrics collection | 9090 |
| Grafana | Monitoring dashboards | 3000 |

## Configuration

Copy `.env.example` to `.env` and edit:

```bash
LLM_ENABLED=true
LLM_PROVIDER=openai
LLM_API_KEY=sk-...
SERVER_HOST=0.0.0.0
SERVER_PORT=8000
ADMIN_USERNAME=admin
ADMIN_PASSWORD=change-me
```
