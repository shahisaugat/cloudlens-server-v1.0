# CloudLens Server — DevOps Internship Project
A production-grade Spring Boot application deployed with a full DevOps pipeline, built as part of a 2-month DevOps internship program.

## Tech Stack

| Layer            | Technology                            |
| ---------------- | ------------------------------------- |
| Backend          | Spring Boot 4.0.6, Java 25            |
| Database         | PostgreSQL 16                         |
| Containerization | Docker, Docker Compose                |
| Orchestration    | Kubernetes (Minikube)                 |
| Cloud            | Microsoft Azure (VM)                  |
| CI/CD            | GitHub Actions                        |
| Monitoring       | Prometheus, Grafana                   |
| Security         | Spring Security, JWT, OAuth2 (GitHub) |

---

## Project Structure

```
cloudlens-server-v1.0/
├── src/                        # Spring Boot application source
├── k8s/                        # Kubernetes manifests
│   ├── secret.yaml             # Kubernetes secrets
│   ├── postgres.yaml           # PostgreSQL deployment + service
│   ├── app.yaml                # Spring app deployment + service
│   └── monitoring.yaml         # Prometheus + Grafana
├── .github/
│   └── workflows/
│       ├── ci.yml              # CI pipeline (build, test, Docker image)
│       └── deploy.yml          # CD pipeline (push to Docker Hub, deploy to Azure)
├── Dockerfile                  # Multi-stage Docker build
└── docker-compose.yml          # Local development setup
```

---

## Week-by-Week Progress

### Week 1-2 — Linux & Git

- Navigated file system, managed file permissions
- Wrote bash scripts for system info and log searching
- Initialized Git repository, created branches, submitted PRs
- Set up GitHub Actions CI pipeline

### Week 3 — Docker

- Wrote multi-stage Dockerfile using Maven build + Alpine JRE runtime
- Containerized Spring Boot application
- Used Docker Compose for local PostgreSQL + app orchestration

### Week 4 — Databases

- Connected Spring Boot to PostgreSQL using Spring Data JPA
- Hibernate auto-creates schema on startup
- CRUD operations across 10+ JPA repositories

### Week 5 — Azure Deployment

- Launched Azure VM, connected via SSH
- Installed Docker on the VM
- Deployed Dockerized app using Docker Compose on Azure

### Week 6 — Kubernetes

- Installed Minikube locally using Docker driver
- Wrote Kubernetes manifests for PostgreSQL and Spring app
- Stored all secrets using Kubernetes Secrets
- Deployed full stack to Minikube cluster
- Scaled Spring app to 3 replicas with a single command

### Week 7 — Monitoring

- Added `spring-boot-starter-actuator` and `micrometer-registry-prometheus`
- Exposed `/actuator/prometheus` metrics endpoint
- Deployed Prometheus to scrape app metrics every 15 seconds
- Connected Grafana to Prometheus
- Built custom dashboard with JVM memory, HTTP request rate, and CPU usage panels

### Week 8 — Final CI/CD Pipeline

- `ci.yml` — triggers on every push/PR: builds with Maven, runs tests, builds Docker image
- `deploy.yml` — triggers on merge to main: pushes image to Docker Hub, SSHs into Azure VM and redeploys with Docker Compose

---

## Running Locally

### Prerequisites

- Docker Desktop
- Java 25
- Maven

### 1. Clone the repo

```bash
git clone git@github.com:shahisaugat/cloudlens-server-v1.0.git
cd cloudlens-server-v1.0
```

### 2. Set up environment variables

```bash
cp .env.example .env
# Fill in your values in .env
```

### 3. Start with Docker Compose

```bash
docker compose up -d
```

App will be available at `http://localhost:8080`

---

## Kubernetes Deployment (Minikube)

```bash
# Start Minikube
minikube start --driver=docker

# Apply manifests
kubectl apply -f k8s/secret.yaml
kubectl apply -f k8s/postgres.yaml
kubectl apply -f k8s/app.yaml
kubectl apply -f k8s/monitoring.yaml

# Access the app
minikube service cloudlens-app --url

# Scale the app
kubectl scale deployment cloudlens-app --replicas=3

# Access monitoring
minikube service prometheus --url
minikube service grafana --url
```

---

## CI/CD Pipeline

```
Push to main
     │
     ▼
GitHub Actions (ci.yml)
  ├── Set up JDK 25
  ├── Build with Maven
  └── Build Docker image
     │
     ▼
GitHub Actions (deploy.yml)
  ├── Login to Docker Hub
  ├── Build & push shahicodesx/cloudlens:latest
  └── SSH into Azure VM
        ├── docker compose pull
        └── docker compose up -d
```

---

## Monitoring

Prometheus scrapes metrics from `/actuator/prometheus` every 15 seconds.

Grafana dashboard panels:

- **JVM Memory** — heap and non-heap usage over time
- **HTTP Request Rate** — requests per minute
- **CPU Usage** — process CPU utilization
- **ALERT Rules** - Alerts when the CPU usage cross 80%

---

## API Endpoints

| Method | Endpoint                       | Description               |
| ------ | ------------------------------ | ------------------------- |
| POST   | `/api/v1/auth/register`        | Register new user         |
| POST   | `/api/v1/auth/login`           | Login with email/password |
| GET    | `/oauth2/authorization/github` | GitHub OAuth login        |
| GET    | `/api/v1/slack/**`             | Slack integration         |
| GET    | `/actuator/health`             | Health check              |
| GET    | `/actuator/prometheus`         | Prometheus metrics        |

All other endpoints require JWT authentication.

---

## GitHub Actions Secrets Required

| Secret            | Description                  |
| ----------------- | ---------------------------- |
| `DOCKER_USERNAME` | Docker Hub username          |
| `DOCKER_PASSWORD` | Docker Hub access token      |
| `AZURE_VM_IP`     | Azure VM public IP address   |
| `AZURE_VM_USER`   | Azure VM SSH username        |
| `AZURE_SSH_KEY`   | Private SSH key for Azure VM |

---
