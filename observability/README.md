# Local Observability Stack

This overlay adds Prometheus, Grafana, Jaeger, OpenTelemetry Collector, and Splunk to the Banking Project Docker Compose environment.

## Prerequisites
- Docker Desktop running
- Java 17 and Maven
- Banking Project source checkout on feature/observability-foundation
- A local .env file copied from .env.example

## Prepare the application jars (PowerShell)
~~~powershell
$services = @('account-service','api-gateway','authentication-service','fraud-detection-service','notification-service','payment-service','transaction-service')
foreach ($service in $services) { mvn -f "$service/pom.xml" clean package -DskipTests }
~~~

Tests are skipped for this first observability bootstrap because the existing CI pipeline skips them. We will add reliable unit/integration test gates in the CI phase.

## Start the full local stack
~~~powershell
Copy-Item .env.example .env
# Edit .env and replace sample local passwords / payment test credentials
docker compose -f docker-compose.yml -f docker-compose-services.yml -f docker-compose-local.yml -f docker-compose-observability.yml config
docker compose -f docker-compose.yml -f docker-compose-services.yml -f docker-compose-local.yml -f docker-compose-observability.yml up -d --build
~~~

## UIs
- Grafana: http://localhost:3001 (user admin)
- Prometheus: http://localhost:9090
- Jaeger: http://localhost:16686
- Splunk: http://localhost:8000

Splunk HEC is published on local port 8088. The compose overlay uses Docker's Splunk logging driver to forward service stdout/stderr to Splunk HEC. Prometheus scrapes each service's internal Actuator endpoint on port 9090. The OpenTelemetry Collector accepts OTLP traces and exports them to Jaeger. Application stdout/stderr is sent to Splunk HEC by the Docker logging driver.

## Test
1. In Prometheus, open Status > Targets. Banking services should be UP once started.
2. Open Grafana and view the provisioned Banking Project dashboard.
3. Open Jaeger and search for api-gateway or another service.
4. In Splunk Search, try index=main and search by service/container names.

## Notes
- This is a local learning stack, not a hardened production configuration.
- Do not use the sample credentials or HEC token outside your laptop.
- Trace sampling defaults to 10%; the local .env.example sets it to 100% for learning.
- Splunk Enterprise is resource-heavy. If Docker Desktop memory is constrained, start application services and Prometheus/Grafana/Jaeger first, then start Splunk separately.
- Management endpoints use port 9090 inside the Docker network. Do not publish this port to the host or expose it through a public load balancer.
- Local compose uses the Docker Splunk logging driver. If the driver is unavailable in your Docker Desktop setup, remove the per-service logging overrides and forward stdout logs through a Fluent Bit or OTel filelog collector instead.
