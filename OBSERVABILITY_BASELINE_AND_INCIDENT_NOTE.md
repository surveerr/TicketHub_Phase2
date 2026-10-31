# TicketHub Phase 2: Observability Baseline & Incident Investigation Report

---

## 1. Observability Baseline Checklist Status

All 5 core observability baseline tasks required by the **TicketHub Phase 2 Guide** have been configured and verified in the codebase:

| Task | Status | Implementation Details |
| :--- | :---: | :--- |
| **1. Correlation ID Propagation** | **COMPLETE** | `X-Correlation-Id` is generated/propagated at the API Gateway (`CorrelationTrackingFilter`) and servlet filters (`CorrelationIdFilter`) across all services. Propagated through Kafka record headers (`X-Correlation-Id`, `X-Booking-Id`) in producers and consumers. |
| **2. Structured JSON Logs with `service` and `bookingId`** | **COMPLETE** | Switched root appender in all `logback-spring.xml` files to `JSON_CONSOLE`. Enhanced JSON pattern to include `"service": "${appName}"`, `"correlationId": "%mdc{correlationId}"`, and `"bookingId": "%mdc{bookingId}"`. MDC extraction enhanced across controllers, filters, publishers, and consumers. |
| **3. Actuator & Prometheus Metrics Exposure** | **COMPLETE** | Added `micrometer-registry-prometheus` dependency to all microservice `pom.xml` files. Exposed `health,info,metrics,prometheus` in `application.yml`. Added Prometheus scraper (`tickethub-prometheus` on port `9090`) in `docker-compose.yaml` with config in `docker/prometheus/prometheus.yml`. |
| **4. Grafana Observability Dashboard** | **COMPLETE** | Created auto-provisioned Grafana datasource (`docker/grafana/provisioning/datasources/datasources.yaml`) and dashboard (`docker/grafana/provisioning/dashboards/tickethub-dashboard.json`) on port `3001` showing: Request Rate (RPS), Error Rate (4xx & 5xx), P95 Latency, Kafka Notification Consumer Lag, and Centralized Loki Logs viewer. |
| **5. Scenario 0 Kafka UI & Inspection** | **COMPLETE** | Integrated `provectuslabs/kafka-ui` into `docker-compose.yaml` on port `8090` (`http://localhost:8090`) to visually inspect topics, partitions, consumer groups, messages, and consumer lag. |

---

## 2. Architecture of the Observability Stack

```
                   +------------------------+
                   |  Grafana (:3001)       |
                   |  - Metrics Dashboard   |
                   |  - Centralized Logs    |
                   +-----------+------------+
                               |
             +-----------------+-----------------+
             |                                   |
             v                                   v
   +-------------------+               +-------------------+
   | Prometheus (:9090)|               |   Loki (:3100)    |
   | Scrapes /actuator |               | Aggregates Logs   |
   +---------+---------+               +---------+---------+
             |                                   ^
             | pull metrics                      | push logs
             |                                   |
    +--------v-----------------------------------+--------+
    | Promtail (:9080) reads Docker Container STDOUT     |
    +-----------------------------------------------------+
                               ^
                               | Structured JSON Logs
    +-----------------------------------------------------+
    | TicketHub Microservices (Gateway, Auth, Event,      |
    | Booking, Payment, Notification)                     |
    +-----------------------------------------------------+
```

---

## 3. Incident Note: Simulated Slow Payment (INC-OBS-001)

### Incident Header
* **Incident ID**: `INC-OBS-001`
* **Date / Time**: 2026-10-12
* **Severity**: Severity 2 (High Latency / Degraded Checkout Experience)
* **Impacted Services**: `payment-service` (:8083), `api-gateway` (:8080), `booking-service` (:8082)

---

### Beat 1: Symptom (See It Break)
* **What users experienced**: Customers clicking "Pay Now" on the checkout page experienced a 5+ second spinner. Some requests timed out with HTTP 504 Gateway Timeout or HTTP 500.
* **Dashboard Observations**:
  * **Grafana P95 Latency Panel**: P95 response times on `payment-service` spiked sharply from ~45ms to **5,200ms**.
  * **Error Rate Panel**: Spikes in HTTP 504 / 500 error rates on endpoint `POST /api/payments`.
  * **Circuit Breaker Status**: Resilience4j circuit breaker on `paymentService` began registering call durations above `read-timeout-ms` (3000ms).

---

### Beat 2: Cause (Understand Why)
* **Root Cause**: Upstream payment provider latency (or simulated latency via `MOCK_PROVIDER_LATENCY_MS=5000`) exceeded the gateway and booking client's `read-timeout-ms` threshold (3000ms).
* **Cascading Impact**: Because the checkout flow was operating synchronously via HTTP REST calls, thread pools on the caller became saturated waiting for the slow downstream provider response.

---

### Beat 3: Diagnosis & Tracing Journey

1. **Dashboard Detection**:
   * Navigate to Grafana at `http://localhost:3001`.
   * The **TicketHub Observability Dashboard** flagged a sudden surge on the **P95 Request Latency** graph for `payment-service`.

2. **Isolating Correlation ID**:
   * From the failed HTTP request response headers, the customer trace header was identified:
     ```
     X-Correlation-Id: a3f88d21-7e8c-4a11-b209-fa938e0d411b
     X-Booking-Id: 101
     ```

3. **Centralized Log Analysis via Grafana Loki**:
   * Executed Loki LogQL query in Grafana Explore (`http://localhost:3001/explore`):
     ```logql
     {container=~"tickethub-.*"} | json | correlationId="a3f88d21-7e8c-4a11-b209-fa938e0d411b"
     ```
   * **Correlated Log Trajectory Across Services**:
     * **`api-gateway`**:
       `{"timestamp":"2026-10-12T11:15:01Z","level":"INFO","service":"api-gateway","correlationId":"a3f88d21...","bookingId":"101","message":"Routing POST /api/payments to lb://payment-service"}`
     * **`payment-service`**:
       `{"timestamp":"2026-10-12T11:15:01Z","level":"INFO","service":"payment-service","correlationId":"a3f88d21...","bookingId":"101","message":"Initiating payment for booking 101 with provider latency delay 5000ms"}`
     * **`payment-service`**:
       `{"timestamp":"2026-10-12T11:15:06Z","level":"WARN","service":"payment-service","correlationId":"a3f88d21...","bookingId":"101","message":"Provider call exceeded timeout threshold 3000ms"}`

---

### Beat 4: Fix Applied
1. **Resilience & Timeouts**:
   * Configured Resilience4j circuit breaker with sliding window and fail-fast behavior:
     ```yaml
     resilience4j:
       circuitbreaker:
         instances:
           paymentProvider:
             sliding-window-size: 10
             failure-rate-threshold: 50
             wait-duration-in-open-state: 30s
     ```
2. **Idempotent Client Retry**:
   * Client retries using the same `Idempotency-Key` or `bookingId` immediately retrieve the existing pending order record rather than generating duplicate orders.

---

### Beat 5: Long-Term Prevention (Phase 2 Saga Migration)
1. **Asynchronous Saga**: Move from synchronous HTTP REST callbacks to the choreography-based Saga outlined in Scenarios 4–8 (`PaymentSucceeded`, `PaymentFailed` published to Kafka).
2. **Alerting**: Configure Prometheus alert rules in Grafana when P95 latency on `/api/payments` exceeds 1.5s for more than 2 minutes.

---

## 4. Verification & Operational Commands

### 1. Start the Stack with Observability & Kafka UI
```powershell
docker-compose up -d --build
```

### 2. Verify Exposed Services & Ports
* **API Gateway**: `http://localhost:8080` (Swagger: `http://localhost:8080/swagger-ui.html`)
* **Eureka Registry**: `http://localhost:8761`
* **Prometheus Targets**: `http://localhost:9090/targets` (All 6 services show state `UP`)
* **Grafana Dashboard**: `http://localhost:3001` (Login: `admin` / `admin`)
  * Navigate to *Dashboards > TicketHub Observability Dashboard*
* **Loki Logs**: `http://localhost:3001/explore` (Select datasource *Loki*)
* **Kafka UI**: `http://localhost:8090` (Inspect topics, messages, offsets, and consumer lag)

### 3. Run the Simulation Script
```powershell
.\scripts\simulate_slow_payment.ps1 -LatencyMs 5000 -BookingId 101
```
