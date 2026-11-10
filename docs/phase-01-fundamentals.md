# Phase 1 – RabbitMQ Fundamentals

Module: `rabbit-playground` (throwaway; removed in Phase 2)
Branch: `phase-01-fundamentals` · Tag: `phase-01-complete`

## Goal
Build an intuition for message queues and watch the first message travel end to end:
REST call → producer → RabbitMQ → consumer.

## 1. Why message queues?

Synchronous call chains couple services in time (the callee must be up), add latency
(sum of all calls) and add knowledge (the caller knows every callee).

```
 SYNCHRONOUS (phone call)            ASYNCHRONOUS (voicemail / letter)
 A ──"hello?"──► B                   A ──message──► [ mailbox ] ──► B
 A waits, B must be online           A moves on; B reads it when ready
```

Analogies: **post office** (sender, post office, recipient) and **restaurant kitchen
ticket rail** (waiter, rail, cook).

| Term | Meaning |
|---|---|
| **Broker** | Server that receives, stores and delivers messages (RabbitMQ) |
| **Producer** | Application that sends messages |
| **Queue** | Ordered (FIFO) buffer inside the broker |
| **Consumer** | Application that receives and processes messages |

```
 ┌──────────┐     ┌──────────── BROKER ───────────┐     ┌──────────┐
 │ PRODUCER │─msg─►  Exchange ──► [m3][m2][m1]    │─msg─► CONSUMER │
 └──────────┘     └───────────────  QUEUE  ───────┘     └──────────┘
                                                  ◄─ack─
```

Benefits: decoupling, buffering, resilience, scalability.
Trade-offs: eventual consistency, an extra system to run, possible duplicate delivery
(consumers must be idempotent, Phase 10).

## 2. RabbitMQ in a container

`docker-compose.yml` (works with Docker or Podman; on macOS with Podman run
`podman machine start` first).

| Property | Meaning |
|---|---|
| `image: docker.io/library/rabbitmq:3-management` | Official image with the Management plugin (UI). Fully-qualified name because Podman has no default registry |
| `container_name` | Stable name for `exec`/`logs` |
| `hostname` | RabbitMQ names its data dir after the node name; a fixed hostname keeps data findable across container recreation |
| `ports 5672` | AMQP (applications) |
| `ports 15672` | Management UI and HTTP API (humans) |
| `RABBITMQ_DEFAULT_USER/PASS` | Initial credentials (dev only) |
| `volumes: rabbitmq_data` | Named volume for queue definitions and persisted messages |
| `healthcheck` | `rabbitmq-diagnostics -q ping`; 10 s interval, 5 s timeout, 5 retries, 20 s grace period |
| `restart: unless-stopped` | Come back after crashes/reboots unless stopped manually |

**AMQP 0-9-1** is the wire protocol on port 5672. A client opens one TCP connection and
multiplexes lightweight **channels** over it. The `guest` user can only connect from
localhost; never use it in production.

```bash
podman machine start        # macOS + Podman only
podman compose up -d
podman compose ps
podman compose down         # keep data
podman compose down -v      # wipe data
```

## 3. Management UI (http://localhost:15672, guest/guest)

| Tab | Shows |
|---|---|
| Overview | Totals (Ready/Unacked/Total), publish/deliver/ack rates, node memory/disk, ports |
| Connections | One row per client TCP connection |
| Channels | Sessions inside connections; prefetch, unacked, unconfirmed |
| Exchanges | Built-ins: default (blank name), `amq.direct`, `amq.fanout`, `amq.topic`, `amq.headers`, `amq.match`, `amq.rabbitmq.trace`. Has a Publish form |
| Queues and Streams | Per-queue counters, consumers, Get messages, Publish, Purge, Bindings |
| Admin | Users, virtual hosts, policies, limits |

```
 Ready (waiting)  +  Unacked (delivered, not acked)  =  Total
```

## 4. Maven setup

Parent `pom.xml`: Spring Boot parent 3.5.16 (curated versions, plugin defaults),
`packaging=pom`, `java.version=21`, `<modules>`.

`rabbit-playground/pom.xml` dependencies:

| Dependency | Why |
|---|---|
| `spring-boot-starter-amqp` | Spring AMQP + RabbitMQ client; auto-configures `ConnectionFactory`, `RabbitTemplate`, `RabbitAdmin` |
| `spring-boot-starter-web` | Embedded Tomcat + MVC for the REST endpoint |
| `spring-boot-starter-validation` | Hibernate Validator so `@NotBlank`/`@Valid` actually run |
| `lombok` (optional) | Compile-time boilerplate removal |
| `spring-boot-starter-test` (test) | JUnit 5, Mockito, AssertJ, MockMvc |
| `spring-boot-maven-plugin` | Executable jar and `spring-boot:run` (Lombok excluded from the jar) |

## 5. Configuration (`application.yml`)

| Property | Meaning |
|---|---|
| `spring.rabbitmq.host/port/username/password/virtual-host` | Connection settings; `${ENV:default}` lets the same jar run anywhere |
| `spring.rabbitmq.listener.simple.acknowledge-mode: auto` | Container acks on success, rejects on exception |
| `...simple.prefetch: 10` | Max delivered-but-unacked messages per consumer |
| `...simple.concurrency / max-concurrency: 1` | Number of consumer threads (ordering is lost with more) |
| `playground.queue.name` | Custom key bound to `PlaygroundQueueProperties` |

## 6. Code walkthrough

```
 POST /messages ─► MessageController ─► MessageProducer ─► RabbitTemplate
                       (@Valid)                              │ exchange "" , key = queue name
                                                             ▼
                                              default exchange ─► playground.queue
                                                                        │ push
                                                                        ▼
                                          MessageConsumer (@RabbitListener, thread ...Container#0-1)
```

| Class | Role |
|---|---|
| `RabbitMQConfig` | Declares a durable `Queue` bean; `RabbitAdmin` creates it on the broker |
| `PlaygroundQueueProperties` | Type-safe, fail-fast config record |
| `MessageProducer` | `rabbitTemplate.convertAndSend("", queueName, message)` |
| `MessageConsumer` | `@RabbitListener(queues = "${playground.queue.name}")` |
| `SendMessageRequest` | Validated request record |
| `MessageController` | `POST /messages` → `202 Accepted` |
| `GlobalExceptionHandler` | 400 for validation, 503 when the broker is unreachable (RFC 7807 `ProblemDetail`) |

Key annotations: `@SpringBootApplication`, `@ConfigurationPropertiesScan`,
`@ConfigurationProperties`, `@Configuration`, `@Bean`, `@Component`, `@Slf4j`,
`@RequiredArgsConstructor`, `@RabbitListener`, `@RestController`, `@RequestMapping`,
`@PostMapping`, `@RequestBody`, `@Valid`, `@ResponseStatus`, `@RestControllerAdvice`,
`@ExceptionHandler`.

Listener internals: at startup a `SimpleMessageListenerContainer` opens a channel, sets
`basic.qos(prefetch)`, then `basic.consume`. The broker **pushes** messages; the consumer
thread calls your method; on normal return the container sends `basic.ack`.

Behaviours worth remembering:
- Spring AMQP connects **lazily**: a queue bean is declared when the connection is first
  used. A `@RabbitListener` forces it at startup.
- A handler that throws gets the message **requeued** by default, which can loop forever
  (fixed in Phases 8-9).
- `202` means "handed to the socket", not "the broker has it". Publisher confirms
  (Phase 7) close that gap.

## 7. Default exchange

```
 Producer ──(exchange="", routing_key="playground.queue")──► DEFAULT EXCHANGE ──► playground.queue
                                                           (direct, name "")
```

- Pre-declared direct exchange with an empty name.
- Every queue is automatically bound to it with its own name as binding key.
- A routing key matching no queue is **silently dropped** (`{"routed": false}`), unless
  `mandatory` + publisher returns are enabled (Phase 7).
- Limitation: the producer must know the consumer's queue name. From Phase 2 we use a
  named exchange so producers stop knowing about consumers.

## 8. Experiment results (observed on a real broker)

| Action | Observed |
|---|---|
| Publish 5 messages with the app down | `ready=5 unacked=0 total=5 consumers=0` |
| Start the app | Log shows `pile-up-1..5` in order; `ready=0 unacked=0` |
| Counters after drain | `publish=5 deliver=5 ack=5 redeliver=0` |
| Channel details | 1 connection, 1 channel, `prefetch=10`, 1 consumer |
| Publish with an unmatched routing key | `{"routed": false}` |
| `POST /messages` | `202 {"status":"accepted"}` |
| `POST /messages` with a blank body | `400` with a problem detail |
| `POST /messages` with the broker stopped | `503` |

## 9. Postman / curl

```
POST http://localhost:8080/messages
Content-Type: application/json

{ "message": "My first RabbitMQ message" }
```

```bash
curl -i -X POST localhost:8080/messages -H 'Content-Type: application/json' -d '{"message":"hello"}'
```

Run with `mvn -pl rabbit-playground spring-boot:run` (RabbitMQ must be up).

## 10. Terminology introduced

Broker, Queue, Producer, Consumer, AMQP, Acknowledgement (intro), Default Exchange,
Routing Key, Durable Queue (intro), Prefetch, Virtual host, Connection vs Channel.

## 11. Self-check questions

1. Why does an order still succeed when the notification service is down?
2. Difference between a connection and a channel?
3. What happens to a message if the listener method throws?
4. Why is `202` the honest status for an asynchronous endpoint?
5. What does a producer see when it publishes to the default exchange with a mistyped routing key?
6. Why will we stop using the default exchange from Phase 2?
