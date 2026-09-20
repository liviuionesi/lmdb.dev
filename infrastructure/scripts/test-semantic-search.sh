#!/bin/bash
#
# Runs 20 useful search queries against the running stack and checks each answer. It uses the real
# model, the real TMDB data and the real Wikidata award lists, so it finds problems the unit tests
# cannot: a model that misreads a query, a catalog gap, a slow lookup.
#
# The same 20 queries, with fake data, run as a unit test in
# backend/ai-service/src/test/java/dev/lmdb/ai/service/SemanticSearchScenariosTest.java.
#
# Needs the stack running (start-infrastructure.sh), plus curl and jq. It creates one throwaway user
# to log in with. The first query for a person or franchise is slower, because the catalog fetches
# it from TMDB and saves it; a second run is fast.
#
# Usage: infrastructure/scripts/test-semantic-search.sh [gateway-url]
# Exit code: 0 if every query passed, 1 otherwise.

set -u

GATEWAY="${1:-http://localhost:8080}"
THIS_YEAR="$(date +%Y)"
GREEN='\033[0;32m'
RED='\033[0;31m'
NC='\033[0m'

failures=0
TOKEN=""
RESPONSE=""

# Registers a throwaway user and logs in. Sets TOKEN.
log_in() {
  local user="semtest$RANDOM$RANDOM"
  local password="Aa1$(head -c 9 /dev/urandom | base64 | sed 's/[^A-Za-z0-9]//g')"
  curl -s -o /dev/null -X POST -H 'Content-Type: application/json' \
    -d "{\"username\":\"$user\",\"email\":\"$user@example.com\",\"password\":\"$password\"}" \
    "$GATEWAY/api/v1/auth/register"
  TOKEN="$(curl -s -X POST -H 'Content-Type: application/json' \
    -d "{\"username\":\"$user\",\"password\":\"$password\"}" "$GATEWAY/api/v1/auth/login" \
    | jq -r '.data.accessToken // .accessToken // empty')"
  if [ -z "$TOKEN" ]; then
    echo -e "${RED}Could not log in through $GATEWAY. Is the stack running?${NC}"
    exit 1
  fi
}

# Each check reads $RESPONSE and prints nothing on success, or a short reason on failure.

# $1 — the fewest results allowed
check_min() { jq -r --argjson n "$1" 'if (.results|length) >= $n then empty else "expected at least \($n) results, got \(.results|length)" end' <<< "$RESPONSE"; }

# $1 — the exact number of results
check_exact() { jq -r --argjson n "$1" 'if (.results|length) == $n then empty else "expected exactly \($n) results, got \(.results|length)" end' <<< "$RESPONSE"; }

# Every movie was released in the range. $1 — first year, $2 — last year ("" for no limit)
check_years() {
  jq -r --argjson from "$1" --argjson to "${2:-9999}" '
    [.results[] | select(((.releaseDate // "")[0:4] | tonumber? // 0) as $y | $y < $from or $y > $to) | .title]
    | if length == 0 then empty else "released outside \($from)-\($to): \(.[0:3] | join(", "))" end' <<< "$RESPONSE"
}

# The movies are in order. $1 — rating, revenue, newest
check_sorted() {
  jq -r --arg by "$1" '
    (.results | map(if $by == "rating" then (.voteAverage // -1)
                    elif $by == "revenue" then (.revenue // -1)
                    else ((.releaseDate // "0000")[0:10]) end)) as $v
    | if $v == ($v | sort | reverse) then empty else "not sorted by \($by) (highest first)" end' <<< "$RESPONSE"
}

# Some title contains each given text. $@ — the texts
check_has() {
  local text
  for text in "$@"; do
    jq -r --arg t "$text" 'if any(.results[]; .title | ascii_downcase | contains($t | ascii_downcase)) then empty else "missing a title containing \"\($t)\"" end' <<< "$RESPONSE"
  done
}

# No title contains any given text. $@ — the texts
check_has_not() {
  local text
  for text in "$@"; do
    jq -r --arg t "$text" 'if any(.results[]; .title | ascii_downcase | contains($t | ascii_downcase)) then "should not contain \"\($t)\"" else empty end' <<< "$RESPONSE"
  done
}

# Every movie has at least this rating. $1 — the rating
check_rated() { jq -r --argjson r "$1" '[.results[] | select((.voteAverage // 0) < $r) | .title] | if length == 0 then empty else "rated below \($r): \(.[0:3] | join(", "))" end' <<< "$RESPONSE"; }

# Every title contains the text. $1 — the text
check_all_titles() { jq -r --arg t "$1" '[.results[] | select(.title | ascii_downcase | contains($t | ascii_downcase) | not) | .title] | if length == 0 then empty else "title without \"\($t)\": \(.[0:3] | join(", "))" end' <<< "$RESPONSE"; }

# Runs one query. $1 — the query, the rest — checks as "function args" strings.
run() {
  local query="$1"
  shift
  local started ended problems=""
  started="$(date +%s)"
  RESPONSE="$(curl -s --max-time 240 -X POST -H "Authorization: Bearer $TOKEN" \
    -H 'Content-Type: application/json' -d "$(jq -cn --arg q "$query" '{query: $q}')" \
    "$GATEWAY/api/v1/ai/search/execute")"
  ended="$(date +%s)"

  if ! jq -e '.results | type == "array"' <<< "$RESPONSE" > /dev/null 2>&1; then
    problems="no results list in the reply: $(echo "$RESPONSE" | head -c 160)"
  else
    local spec
    for spec in "$@"; do
      # shellcheck disable=SC2086
      problems+="$(eval "check_$spec")"$'\n'
    done
  fi
  problems="$(echo "$problems" | sed '/^$/d')"

  if [ -z "$problems" ]; then
    echo -e "${GREEN}PASS${NC}  $query  ($((ended - started))s)"
  else
    echo -e "${RED}FAIL${NC}  $query  ($((ended - started))s)"
    echo "$problems" | sed 's/^/        - /'
    failures=$((failures + 1))
  fi
  jq -r '"        \(.results|length) results" + (if (.relaxedCriteria|length) > 0 then ", dropped: \(.relaxedCriteria|join(", "))" else "" end),
         (.results[0:5][] | "          \((.releaseDate // "????")[0:4]) \(.title)  [rating \(.voteAverage // "?")\(if .revenue then ", revenue $\(.revenue)" else "" end)]")' \
    <<< "$RESPONSE" 2> /dev/null
}

log_in
echo "Gateway: $GATEWAY    Year: $THIS_YEAR"
echo

# --- People, time and order --------------------------------------------------------------------
run "Tom Cruise movies from the last 20 years sorted by rating" \
  "min 8" "years $((THIS_YEAR - 20))" "sorted rating" "has 'Top Gun: Maverick'"
run "Tom Cruise movies from the last 20 years sorted by revenue" \
  "min 8" "years $((THIS_YEAR - 20))" "sorted revenue" "has 'Top Gun: Maverick'"
run "Leonardo DiCaprio movies in the 2010s" \
  "min 5" "years 2010 2019" "has 'The Revenant'"
run "heist movies with Brad Pitt" \
  "min 1" "has \"Ocean's\""

# --- Directors, producers, negation and rating -------------------------------------------------
run "movies directed by Christopher Nolan sorted by rating" \
  "min 8" "sorted rating" "has 'Inception' 'Interstellar' 'The Dark Knight'"
run "top 5 highest rated movies directed by Quentin Tarantino" \
  "exact 5" "sorted rating" "has 'Pulp Fiction'"
run "movies produced by Steven Spielberg after 2000" \
  "min 5" "years 2000"
run "movies with Brad Pitt and Edward Norton" \
  "min 1" "has 'Fight Club'"
run "films Quentin Tarantino didn't direct" \
  "min 3" "has_not 'Pulp Fiction' 'Django Unchained'"
run "Meryl Streep movies rated above 7" \
  "min 10" "rated 7"

# --- Franchises --------------------------------------------------------------------------------
run "James Bond movies after 2000" \
  "min 4" "years 2000" "has 'Casino Royale' 'Skyfall'"
run "Star Wars movies sorted by release date newest first" \
  "min 6" "sorted newest"
run "Harry Potter movies sorted by revenue" \
  "min 6" "sorted revenue" "all_titles 'Harry Potter'"
run "Daniel Craig as James Bond" \
  "min 4" "has 'Casino Royale' 'Skyfall'" "has_not 'Knives Out'"
run "Mission: Impossible movies sorted by rating" \
  "min 6" "sorted rating" "all_titles 'Mission: Impossible'"

# --- Genre, years and count, with no name at all -----------------------------------------------
run "best action movies from 2015 to 2020" \
  "min 10" "years 2015 2020" "sorted rating"
run "top 10 highest rated comedies of the 1990s" \
  "exact 10" "years 1990 1999" "sorted rating"
run "horror movies from the last 5 years sorted by rating" \
  "min 10" "years $((THIS_YEAR - 5))" "sorted rating"

# --- Awards ------------------------------------------------------------------------------------
run "all the movies that won the oscar for the best actor" \
  "min 50" "has 'Gladiator' 'The Whale'"
run "Oscar best picture winners from the last 20 years" \
  "min 15" "years $((THIS_YEAR - 20))" "has 'Parasite' 'Oppenheimer'"

echo
if [ "$failures" -eq 0 ]; then
  echo -e "${GREEN}All 20 queries passed.${NC}"
  exit 0
fi
echo -e "${RED}$failures of 20 queries failed.${NC}"
exit 1
