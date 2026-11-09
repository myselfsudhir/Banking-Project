# Local Observability Stack

This overlay adds Prometheus, Grafana, Jaeger, OpenTelemetry Collector, and Splunk to the Banking Project Docker Compose environment.

## Prerequisites
- Docker Desktop running
- Banking Project infrastructure and service compose files
- A local .env file copied from .env.example

## Start
~~~bash
cp .env.example .env
# Change local Splunk and Grafana passwords in .env
docker compose -f docker-compose.yml -f docker-compose-services.yml -f docker-compose-observability.yml up -d --build
~~~

If the frontend compose file is not part of the branch or is not needed, omit it from the command.

## UIs
- Grafana: http://localhost:3001 (user admin)
- Prometheus: http://localhost:9090
- Jaeger: http://localhost:16686
- Splunk: http://localhost:8000

Splunk HEC is published on local port 8088. The compose overlay uses Docker's Splunk logging driver to forward service stdout/stderr to Splunk HEC. The OpenTelemetry Collector accepts OTLP traces and metrics; it exports traces to Jaeger and OTLP logs to Splunk HEC when an application emits OTLP log records.

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
