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

echo "Applying ILM policy: lmdb-logs-policy (delete after 7d)"
curl -sf -X PUT "$ES_URL/_ilm/policy/lmdb-logs-policy" \
  -H 'Content-Type: application/json' \
  --data-binary @/setup/ilm-policy.json
echo

echo "Applying index template: lmdb-logs-template (lmdb-logs-*)"
curl -sf -X PUT "$ES_URL/_index_template/lmdb-logs-template" \
  -H 'Content-Type: application/json' \
  --data-binary @/setup/index-template.json
echo

echo "Waiting for Kibana at $KIBANA_URL..."
until curl -s -o /dev/null "$KIBANA_URL/api/status"; do
  sleep 2
done

echo "Importing Kibana dashboard: LMDB Overview (data view, 4 visualizations, 1 saved search, 1 dashboard)"
curl -sf -X POST "$KIBANA_URL/api/saved_objects/_import?overwrite=true" \
  -H 'kbn-xsrf: true' \
  -F 'file=@/setup/lmdb-overview.ndjson;type=application/ndjson'
echo

# Imported after lmdb-overview.ndjson: re-asserts the same data view, now
# with level field-formatting, and adds the 3 Lens dashboards. The Error
# Triage dashboard's "Recent Errors" panel references the saved search from
# lmdb-overview.ndjson by id, so that file must import first.
echo "Importing Kibana Lens dashboards: Service Health, Error Triage, Per-Service Deep-Dive"
curl -sf -X POST "$KIBANA_URL/api/saved_objects/_import?overwrite=true" \
  -H 'kbn-xsrf: true' \
  -F 'file=@/setup/lmdb-lens-suite.ndjson;type=application/ndjson'
echo

echo "Importing Kibana homepage: links to the 3 Lens dashboards (#112)"
curl -sf -X POST "$KIBANA_URL/api/saved_objects/_import?overwrite=true" \
  -H 'kbn-xsrf: true' \
  -F 'file=@/setup/lmdb-homepage.ndjson;type=application/ndjson'
echo

# Makes the homepage the actual landing page instead of just another entry
# in the dashboard list (#112) — an advanced setting, not a saved object, so
# it's set directly via the settings API rather than through the ndjson
# import above.
echo "Setting Kibana default route to the LMDB homepage dashboard"
curl -sf -X POST "$KIBANA_URL/api/kibana/settings" \
  -H 'kbn-xsrf: true' \
  -H 'Content-Type: application/json' \
  --data '{"changes": {"defaultRoute": "/app/dashboards#/view/lmdb-homepage"}}'
echo

# Kibana always exports alerting rules disabled (a safety default so importing
# into a new environment doesn't silently start firing them) - explicitly
# re-enable them after every import so `docker-compose up` leaves the rules
# actually running, matching the rest of this script's checked-in-state-wins
# idempotency.
echo "Importing Kibana alerting: Server Log connector, 2 rules"
curl -sf -X POST "$KIBANA_URL/api/saved_objects/_import?overwrite=true" \
  -H 'kbn-xsrf: true' \
  -F 'file=@/setup/lmdb-alerting.ndjson;type=application/ndjson'
echo
for rule_id in 27446a28-74e4-429c-b0bf-0782a5aff15a e49c2876-79d4-4666-9d89-6dc8e704828e; do
  curl -sf -X POST "$KIBANA_URL/api/alerting/rule/$rule_id/_enable" -H 'kbn-xsrf: true'
done
echo "Alerting rules enabled."

echo "ELK setup complete."
