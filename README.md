# orderflow-mq

A hands-on RabbitMQ learning project. We build *OrderFlow MQ*, an event-driven order
processing system, one phase at a time (Java 21, Spring Boot 3.x, Maven, RabbitMQ,
PostgreSQL). The full roadmap is in [`orderflow-mq-plan.md`](orderflow-mq-plan.md).

## Prerequisites
- Java 21 and Maven 3.9+
- A container engine with Compose: Docker, or Podman (on macOS run `podman machine start` first)

## Quick start (Phase 1)
```bash
podman compose up -d                          # or: docker compose up -d
mvn -pl rabbit-playground spring-boot:run     # in a second terminal
curl -i -X POST localhost:8080/messages \
  -H 'Content-Type: application/json' -d '{"message":"hello"}'
```
RabbitMQ Management UI: http://localhost:15672 (`guest` / `guest`).

## Progress
| Phase | Topic | Status | Notes |
|---|---|---|---|
| 1 | RabbitMQ fundamentals | Done | [docs/phase-01-fundamentals.md](docs/phase-01-fundamentals.md), tag `phase-01-complete` |
| 2-12 | Order service through advanced design | Pending | See the plan |

## Git workflow
One branch per phase (`phase-XX-<topic>`), merged into `main` with `--no-ff` and tagged
`phase-XX-complete`. Branches are kept so any phase can be revisited:
`git checkout phase-01-fundamentals` or `git checkout phase-01-complete`.
