# OrderFlow MQ – Phase-wise Learning Plan

## Top-Level Overview

**Goal:** Teach RabbitMQ from zero to production concepts to a Spring Boot developer by building *OrderFlow MQ*, an event-driven order-processing system, one phase at a time.

**Stack:** Java 21, Spring Boot 3.x, RabbitMQ (Docker), PostgreSQL (Docker), Maven, Lombok, Spring Data JPA, Postman.

**Confirmed decisions**
| Topic | Decision |
|---|---|
| Repo layout | Maven multi-module monorepo, parent `pom.xml` at the root, shared `docker-compose.yml` at the root |
| Modules | `order-service`, `payment-service`, `inventory-service`, `notification-service` (Phase 1 uses a throwaway `rabbit-playground` module) |
| Event DTOs | Duplicated in each service (loose coupling). The trade-off of a shared `common-events` library is explained, not implemented |
| Persistence | PostgreSQL in Docker Compose + Spring Data JPA |
| Git | Branch per phase (`phase-XX-<topic>`) created from latest `main`, merged into `main` with `--no-ff` (or a PR) at phase end. Phase branches are **kept** as revisit-able snapshots. Tag `phase-XX-complete` on the merge commit. See "Git Workflow" below |
| Docs | `docs/phase-XX-<topic>.md` learning notes per phase (concepts, ASCII diagrams, Postman collection updates), pushed with each phase |
| Greenfield | Repo currently only has `README.md` and `.gitignore`, so there is no existing code to reuse |

**Target structure (end state)**
```
orderflow-mq/
├── pom.xml                      (parent, packaging=pom, dependencyManagement, Java 21)
├── docker-compose.yml           (rabbitmq:management, postgres)
├── README.md
├── docs/
│   ├── phase-01-fundamentals.md ... phase-12-advanced-design.md
│   └── postman/orderflow-mq.postman_collection.json
├── order-service/
├── payment-service/
├── inventory-service/
└── notification-service/
```

**Teaching rules applied to EVERY step (non-negotiable)**
1. Explain the concept before coding: WHY, WHAT problem it solves, and HOW RabbitMQ handles it internally.
2. ASCII diagram wherever it clarifies flow.
3. Explain every annotation, every `pom.xml` dependency, and every `application.yml` property that is introduced.
4. Give production-grade code (constructor injection, Lombok, validation, logging, config via properties).
5. **Stop and wait for the user's confirmation after each step** before starting the next.
6. Terminology is introduced when first needed: Broker, Queue, Exchange, Binding, Routing Key, Producer, Consumer, Acknowledgement, Durable Queue, Dead Letter Queue, Retry Queue, Fanout/Direct/Topic Exchange.

**Design notes that shape the phases**
- Phases 2–5 route through a single minimal **direct exchange** (`orderflow.exchange`, routing key `order.created`) with one queue per service (`payment.queue`, `inventory.queue`, `notification.queue`). Phase 1 uses the default exchange. This avoids the trap where services sharing one queue would *steal* messages from each other, and it is used as a teaching moment in Phase 4.
- Phase 6 then explains the exchange types in depth and refactors the topology deliberately.
- Failures are simulated by config flags (payment gateway) and by really stopping the Postgres container (DB failure).

---

## Sub-Tasks

### Phase 1 – RabbitMQ Fundamentals (`rabbit-playground`)
**Intent:** Build an intuition for message queues and see the first message travel end to end.
**Expected Outcomes:**
- User can explain a queue using real-world analogies (post office, restaurant kitchen ticket rail).
- RabbitMQ runs in Docker; the Management UI is understood.
- A REST call produces a message, a consumer receives it, and the user observes it in the UI.

**Todo List (each a separate step with a confirmation gate):**
1. Step 1 – Message queue concepts with real-world examples; synchronous vs asynchronous; Broker, Producer, Consumer, Queue; ASCII diagram.
2. Step 2 – Create root `docker-compose.yml` with `rabbitmq:3-management`; explain every compose property (image, ports 5672/15672, env vars, volume, healthcheck); start it and verify. Explain AMQP and the default `guest` user.
3. Step 3 – Management UI tour: Overview, Connections, Channels, Exchanges, Queues, Admin; explain what each shows.
4. Step 4 – Create parent `pom.xml` and the `rabbit-playground` module; explain `spring-boot-starter-amqp`, `spring-boot-starter-web`, `lombok`, and the `spring-boot-maven-plugin`. Write the `RabbitMQConfig` (queue declaration) and the producer (`RabbitTemplate`). Explain `application.yml` connection properties.
5. Step 5 – Add the consumer with `@RabbitListener`; explain the annotation and what happens inside (consumer thread, prefetch, auto-ack).
6. Step 6 – Add `POST /messages` REST endpoint to send the first message; test with Postman.
7. Step 7 – Observe the flow in the UI (Ready / Unacked / Total, message rates, stop the consumer and watch messages pile up, restart it and watch them drain). Explain the default exchange.
8. Wrap-up: write `docs/phase-01-fundamentals.md`, `.gitignore` additions, update `README.md`; **commit and push to `main`**.

**Relevant Context:** [`README.md`](README.md), [`.gitignore`](.gitignore) (already ignores `target/`; add IDE entries such as `.idea/`, `*.iml`, `.vscode/` if needed).
**Status:** [x] done. Branch `phase-01-fundamentals` (kept), merged into `main` with `--no-ff`, tag `phase-01-complete`. Notes: [`docs/phase-01-fundamentals.md`](docs/phase-01-fundamentals.md).
**Context for Phase 2:**
- Environment: macOS, **Podman** (not Docker). Run `podman machine start`, then `podman compose up -d`. Java 21, Maven 3.9.11.
- Root [`pom.xml`](pom.xml) is the parent (`com.orderflow:orderflow-mq-parent:0.0.1-SNAPSHOT`, Spring Boot parent 3.5.16, `packaging=pom`). Its `<modules>` currently lists only `rabbit-playground`; Phase 2 replaces it with `order-service`.
- [`docker-compose.yml`](docker-compose.yml) has one service, `rabbitmq` (`docker.io/library/rabbitmq:3-management`, container `orderflow-rabbitmq`, ports 5672/15672, named volume `rabbitmq_data`, healthcheck, `guest/guest`). Phase 2 adds Postgres here; use fully-qualified image names for Podman.
- Package layout used in the playground, to be mirrored: `config` (`RabbitMQConfig`, `@ConfigurationProperties` records), `producer`, `consumer`, `api` (controller, request record, `GlobalExceptionHandler` returning `ProblemDetail`). Constructor injection via Lombok `@RequiredArgsConstructor`; config via `application.yml` with `${ENV:default}`.
- The playground sends `String` payloads through the default exchange (`""`, routing key = queue name) using the `SimpleMessageConverter`. Phase 2 must introduce a named exchange `orderflow.exchange` (key `order.created`) and the JSON converter.
- Observed facts worth reusing: Spring AMQP connects lazily; a handler that throws is requeued by default (retry/DLQ come in Phases 8-9); `202 Accepted` does not prove the broker stored the message (publisher confirms in Phase 7); an unroutable message on the default exchange is silently dropped (`routed:false`).
- Testing style: `@WebMvcTest` + `@MockitoBean` for controllers; plain Mockito for the producer. `mvn clean verify` is green.
- Housekeeping: `.gitignore` now ignores `.idea/`, `*.iml`, `.vscode/`. A progress table lives in `README.md` and must be updated at each phase end.
- Delete the `rabbit-playground` module in Phase 2 (and remove it from the parent pom).

---

### Phase 2 – Order Service
**Intent:** Replace the playground with the real `order-service` that publishes an `OrderCreated` event.
**Expected Outcomes:**
- `POST /orders` with `{ "customerId": 101, "product": "Laptop", "amount": 50000 }` saves the order in Postgres and publishes `OrderCreatedEvent` as JSON.
- User understands DTO design, serialization, message conversion and the JSON payload (visible in the UI "Get messages").

**Todo List:**
1. Remove the `rabbit-playground` module (explain it was throwaway); add `order-service` module to the parent pom.
2. Add Postgres to `docker-compose.yml` (explain each property); add `spring-boot-starter-data-jpa`, the PostgreSQL driver, `spring-boot-starter-validation` and explain each.
3. Explain the layers: request DTO vs entity vs event DTO (why they must be separate), versioning and an event envelope (`eventId`, `occurredAt`, `orderId`, payload).
4. Explain serialization: Java object → JSON bytes → AMQP body; `SimpleMessageConverter` vs `Jackson2JsonMessageConverter`; `content_type` header; `__TypeId__` header pitfalls and how to avoid them with type mapping.
5. Implement the `Order` entity/repository/service/controller with validation and global exception handling.
6. Implement `RabbitMQConfig` (exchange, queue, binding, JSON converter) and `OrderEventPublisher`.
7. Test with Postman; inspect the JSON payload in the UI.
8. Write `docs/phase-02-order-service.md`; add the first Postman collection; **commit and push**.

**Relevant Context:** new `order-service/`, root `pom.xml`, `docker-compose.yml`.
**Status:** [ ] pending

---

### Phase 3 – Payment Service
**Intent:** Add a separate consumer service that reacts to `OrderCreated`.
**Expected Outcomes:**
- `payment-service` consumes from `payment.queue`, deserializes the JSON into its own `OrderCreatedEvent` DTO, simulates payment processing and logs the result.
- User understands the listener mechanism, message deserialization and consumer lifecycle.

**Todo List:**
1. Explain the listener container internals: `SimpleMessageListenerContainer`, consumer threads, channels, prefetch, `concurrency`, start/stop lifecycle, auto-acknowledge mode (flagged for Phase 7).
2. Create the `payment-service` module; duplicate the event DTO (explain why, with the shared-library trade-off).
3. Configure the consumer-side queue, binding and JSON converter; explain `@RabbitListener`, `@Payload`, `@Header`.
4. Implement `PaymentProcessor` (simulated delay and success/fail result) and persist a `Payment` record in Postgres.
5. Explain what happens on startup, on shutdown and when the broker is down (connection recovery).
6. End-to-end test with Postman → order → payment log; observe in the UI.
7. Write `docs/phase-03-payment-service.md`; **commit and push**.

**Relevant Context:** new `payment-service/`, parent pom modules list.
**Status:** [ ] pending

---

### Phase 4 – Inventory Service
**Intent:** Add another consumer and teach competing consumers and queue sharing.
**Expected Outcomes:**
- `inventory-service` reduces stock in Postgres upon `OrderCreated` (seed product table).
- User sees both patterns: each service with its own queue (all get the message) vs multiple instances on one queue (messages are split round-robin).

**Todo List:**
1. Explain multiple consumers, the competing consumers pattern, round-robin dispatch, prefetch and fair dispatch.
2. Create the `inventory-service` module with `Product`/stock entity, seed data and a transactional stock reduction (with optimistic locking explained).
3. Demo A: run two `inventory-service` instances on different ports against the same queue and watch the load split.
4. Demo B (pitfall): if payment and inventory shared one queue, each order would reach only one of them. This motivates one queue per service.
5. Write `docs/phase-04-inventory-service.md`; **commit and push**.

**Relevant Context:** new `inventory-service/`.
**Status:** [ ] pending

---

### Phase 5 – Notification Service
**Intent:** Show event-driven architecture and loose coupling by adding a consumer without touching the producer.
**Expected Outcomes:**
- `notification-service` simulates an email on `OrderCreated`.
- User can articulate that `order-service` did not change and does not know who consumes its events.

**Todo List:**
1. Explain event-driven architecture, events vs commands, loose coupling, temporal coupling and the request/response comparison, using ASCII diagrams.
2. Create the `notification-service` module with a `NotificationQueue` binding and a simulated `EmailSender`.
3. Demonstrate stopping a service and bringing it back (decoupling in time).
4. Write `docs/phase-05-notification-service.md`; **commit and push**.

**Relevant Context:** new `notification-service/`.
**Status:** [ ] pending

---

### Phase 6 – Exchanges
**Intent:** Explain and implement Direct, Fanout and Topic exchanges with practical examples.
**Expected Outcomes:**
- Routing, bindings and routing keys are understood; the user can choose the right exchange type.
- Practical examples are running for all three types.

**Todo List:**
1. Explain how the exchange, binding and routing key work internally (the routing table), and the default exchange as a special direct exchange.
2. Direct: route `order.payment` style keys (e.g. payment-only commands) to specific queues. Show the exact-match behavior.
3. Fanout: `order.broadcast.exchange` delivers to every bound queue and ignores the routing key (notification and audit broadcast). Add an `audit` consumer queue to prove it.
4. Topic: `order.events` exchange with patterns such as `order.created`, `order.paid`, `order.cancelled`, with `order.*` and `order.#` bindings; explain `*` vs `#`.
5. Refactor the topology cleanly (a single place per service declaring its own queues and bindings via `Declarables`/`@Bean`), and explain who should own what.
6. Postman scenarios to prove each routing behavior; use the UI "Publish message" tool to publish manually.
7. Write `docs/phase-06-exchanges.md` with a comparison table and diagrams; **commit and push**.

**Relevant Context:** `*/config/RabbitMQConfig.java` in each service.
**Status:** [ ] pending

---

### Phase 7 – Reliability
**Intent:** Make sure no message is lost: persistence, durability, publisher confirms and consumer acknowledgements.
**Expected Outcomes:**
- Messages survive a RabbitMQ restart (demonstrated with `docker restart`).
- Publisher confirms and returns are handled; consumers use manual or container-managed acks correctly.

**Todo List:**
1. Explain durable vs transient queues/exchanges, persistent delivery mode, and what is written to disk (and when).
2. Enable publisher confirms (`publisher-confirm-type: correlated`) and `publisher-returns` with `mandatory`; implement a `ConfirmCallback` and `ReturnsCallback`; explain the unroutable message case.
3. Consumer acknowledgements: auto vs manual, `ack`/`nack`/`reject`, requeue, and what happens if a consumer dies mid-processing (message redelivery, `redelivered` flag).
4. Add a Docker volume for RabbitMQ data; demo: publish → restart the broker → messages still there; compare with a transient queue.
5. Explain the whole restart timeline (connection drop, Spring AMQP auto-recovery, consumers re-attach).
6. Write `docs/phase-07-reliability.md`; **commit and push**.

**Relevant Context:** `docker-compose.yml`, each service's `application.yml` and `RabbitMQConfig`.
**Status:** [ ] pending

---

### Phase 8 – Retry Mechanism
**Intent:** Handle transient failures (payment gateway down, DB down) with retries and backoff.
**Expected Outcomes:**
- Simulated failures trigger retries with an increasing delay.
- User understands in-memory retry (Spring Retry) vs broker-level retry queues with TTL.

**Todo List:**
1. Create failure scenarios: a `payment.gateway.fail-rate`-style config flag in `payment-service`, and a real Postgres stop in `inventory-service`.
2. Explain transient vs permanent failures and why requeue-forever is dangerous (poison message / hot loop).
3. Approach A: Spring `spring.rabbitmq.listener.simple.retry.*` (initial interval, multiplier, max attempts) and explain the limitations (blocks the consumer thread).
4. Approach B: Retry queue pattern: `payment.retry.queue` with `x-message-ttl` and `x-dead-letter-exchange` to route back to the main queue after the delay (delayed processing); the `x-death` header and attempt counting.
5. Explain backoff strategies: fixed, exponential, jitter; implement tiered retry queues (e.g. 5s / 30s / 2m).
6. Distinguish retryable vs non-retryable exceptions.
7. Write `docs/phase-08-retry.md`; **commit and push**.

**Relevant Context:** `payment-service`, `inventory-service` configs and listeners.
**Status:** [ ] pending

---

### Phase 9 – Dead Letter Queue
**Intent:** Park messages that exhaust retries so they are neither lost nor blocking.
**Expected Outcomes:**
- After max attempts, messages land in a DLQ with failure metadata.
- User can inspect, requeue (shovel or republish) and debug DLQ messages.

**Todo List:**
1. Explain why DLQs exist, the dead-letter triggers (reject without requeue, TTL expiry, queue length limit) and the message lifecycle diagram.
2. Configure `payment.dlq` / `inventory.dlq` with a dead-letter exchange and routing; use `RepublishMessageRecoverer` or `x-death` based routing, and explain the differences.
3. Add a small DLQ consumer or admin endpoint for inspection/replay, and add failure reason headers.
4. Production debugging techniques: check `x-death`, queue depth growth, logs with correlation IDs, UI "Get messages", requeue from the UI, alerting on DLQ depth.
5. Write `docs/phase-09-dlq.md`; **commit and push**.

**Relevant Context:** `RabbitMQConfig` and listener error handling in `payment-service` and `inventory-service`.
**Status:** [ ] pending

---

### Phase 10 – Production Concepts
**Intent:** Teach the concepts that decide whether a RabbitMQ system survives production, and implement idempotency.
**Expected Outcomes:**
- User can explain queue depth, throughput, consumer lag, horizontal scaling and delivery guarantees.
- Consumers are idempotent: duplicate messages do not double-charge or double-deduct stock.

**Todo List:**
1. Explain queue depth, throughput, consumer lag (and the RabbitMQ-specific meaning), prefetch tuning and horizontal scaling.
2. Explain delivery semantics: at-most-once, at-least-once and the challenges of exactly-once, with diagrams of where duplicates come from (consumer crash before ack, publisher retries).
3. Implement idempotency: `processed_messages` table (unique `eventId`) in the payment and inventory services, checked in the same DB transaction as the business change.
4. Demo duplicates: republish the same event twice from the UI and confirm that it is processed once.
5. Simple load test (e.g. script or Postman runner) to observe throughput and scaling consumers up.
6. Write `docs/phase-10-production-concepts.md`; **commit and push**.

**Relevant Context:** `payment-service`, `inventory-service` JPA layer.
**Status:** [ ] pending

---

### Phase 11 – Monitoring
**Intent:** Read the health of the system from RabbitMQ.
**Expected Outcomes:**
- User can read queue stats, unacked counts and consumer counts and diagnose common symptoms.

**Todo List:**
1. Management UI deep dive: Ready vs Unacked vs Total, publish/deliver/ack rates, consumer utilisation, connection and channel counts, memory and disk alarms.
2. Create symptom scenarios: stop a consumer (growing Ready), slow consumer (growing Unacked), scale consumers (drain).
3. Show the management HTTP API (`/api/queues`) and enable the Spring Boot Actuator health for RabbitMQ; mention the Prometheus plugin (port 15692) as a next step.
4. Write a troubleshooting cheat-sheet in `docs/phase-11-monitoring.md`; **commit and push**.

**Relevant Context:** `docker-compose.yml`, each service's `application.yml`.
**Status:** [ ] pending

---

### Phase 12 – Advanced Design
**Intent:** Connect everything to real microservice architecture patterns.
**Expected Outcomes:**
- User understands Saga, choreography and orchestration, with a working choreography flow.

**Todo List:**
1. Explain event-driven microservices, the distributed transaction problem, and why 2PC is avoided.
2. Implement a choreography Saga: `OrderCreated` → payment → `PaymentCompleted`/`PaymentFailed` → inventory reserve → `OrderConfirmed`; `PaymentFailed` → compensation (release stock, `OrderCancelled`); `order-service` updates order status from the events.
3. Walk through (design only, with diagrams) the orchestration alternative with an orchestrator service and a comparison table (coupling, visibility, complexity).
4. Discuss the outbox pattern, event versioning and the final production checklist.
5. Final README with architecture diagram, run instructions and a learning-path summary; **commit and push**.

**Relevant Context:** all services, and `docs/phase-12-advanced-design.md`.
**Status:** [ ] pending

---

## Git Workflow (applies to every phase; "commit and push" in the phases above means this)
Branch names: `phase-01-fundamentals`, `phase-02-order-service`, `phase-03-payment-service`, `phase-04-inventory-service`, `phase-05-notification-service`, `phase-06-exchanges`, `phase-07-reliability`, `phase-08-retry`, `phase-09-dlq`, `phase-10-production-concepts`, `phase-11-monitoring`, `phase-12-advanced-design`.

1. **Start of phase:** `git checkout main && git pull`, then `git checkout -b phase-XX-<topic>`.
2. **During the phase:** commit after each confirmed teaching step (message format: `phase-XX step-N: <summary>`), so step-level history is revisit-able.
3. **End of phase:** push the branch (`git push -u origin phase-XX-<topic>`), then merge into `main` with `git merge --no-ff phase-XX-<topic>` (or open a PR and merge it on GitHub), and push `main`.
4. **Tag:** `git tag phase-XX-complete` on the merge commit and `git push origin phase-XX-complete`.
5. **Never delete phase branches** and never add later fixes to an old phase branch; fix bugs on the current phase branch so older branches remain accurate snapshots.
6. To revisit: `git checkout phase-XX-<topic>` (or `git checkout phase-XX-complete`).

## Execution Protocol (when implementing)
- One phase = one subtask. Within a phase, go step by step and **wait for the user's confirmation after every step**.
- After each phase: update the `Status` line above to `[x] done`, add any context the next phase needs, and follow the Git Workflow above (branch merged into `main`, tag pushed, branch kept).
- Do not start the next phase until the user approves the merged phase.
