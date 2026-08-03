#!/bin/sh
# One-shot ILM policy + index template + Kibana dashboard bootstrap for the
# local ELK stack (issue #24). Runs once per `docker-compose up`, then
# exits; Logstash and Kibana don't need it again until the policy/template/
# dashboard themselves change. The Kibana import is idempotent
# (overwrite=true), so re-running this on every `up` is safe and keeps the
# checked-in dashboard in sync instead of drifting from manual UI edits.
set -eu

ES_URL="http://elasticsearch:9200"
KIBANA_URL="http://kibana:5601"

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

echo "Waiting for Kibana at $KIBANA_URL..."
until curl -s -o /dev/null "$KIBANA_URL/api/status"; do
  sleep 2
done

echo "Importing Kibana dashboard: Filmpire Overview (data view, 4 visualizations, 1 saved search, 1 dashboard)"
curl -sf -X POST "$KIBANA_URL/api/saved_objects/_import?overwrite=true" \
  -H 'kbn-xsrf: true' \
  -F 'file=@/setup/filmpire-overview.ndjson;type=application/ndjson'
echo

echo "ELK setup complete."
