#!/bin/sh
# One-shot ILM policy + index template bootstrap for the local ELK stack
# (issue #24). Runs once per `docker-compose up`, then exits; Logstash and
# Kibana don't need it again until the policy/template themselves change.
set -eu

ES_URL="http://elasticsearch:9200"

echo "Waiting for Elasticsearch at $ES_URL..."
until curl -s -o /dev/null "$ES_URL"; do
  sleep 2
done

echo "Applying ILM policy: filmpire-logs-policy (delete after 7d)"
curl -sf -X PUT "$ES_URL/_ilm/policy/filmpire-logs-policy" \
  -H 'Content-Type: application/json' \
  --data-binary @/setup/ilm-policy.json
echo

echo "Applying index template: filmpire-logs-template (filmpire-logs-*)"
curl -sf -X PUT "$ES_URL/_index_template/filmpire-logs-template" \
  -H 'Content-Type: application/json' \
  --data-binary @/setup/index-template.json
echo

echo "ELK setup complete."
