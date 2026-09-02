#!/usr/bin/env bash
#
# Show or export the revision history of GitHub issue bodies.
#
# GitHub keeps every revision of an issue body. The GraphQL field
# userContentEdits exposes them, newest first. Its "diff" field holds the
# full body text at that revision, not a diff, so this script produces the
# diffs locally.
#
# Limits:
#   - Body text only. Title, label and milestone changes are timeline
#     events, shown by --events.
#   - The editor is whoever owns the token that made the edit. Edits made
#     by an agent through gh are recorded under the repo owner, so the
#     history cannot separate agent edits from human ones.
#
# Usage:
#   issue-history.sh 252              list revisions
#   issue-history.sh 252 --diff       unified diff between each revision
#   issue-history.sh 252 --rev 3      print revision 3 (1 = oldest)
#   issue-history.sh 252 --events     title/label/milestone/state changes
#   issue-history.sh --export DIR     write every issue body to DIR/<n>.md
#
set -euo pipefail

REPO="${ISSUE_HISTORY_REPO:-liviuionesi/lmdb.dev}"
OWNER="${REPO%%/*}"
NAME="${REPO##*/}"

die() { echo "error: $*" >&2; exit 1; }
command -v gh >/dev/null || die "gh not found"
command -v jq >/dev/null || die "jq not found"

# Fetch all revisions of one issue body, oldest first, as a JSON array of
# {editedAt, editor, body}.
revisions() {
  gh api graphql \
    -f query='query($o:String!,$r:String!,$n:Int!){
      repository(owner:$o,name:$r){ issue(number:$n){
        userContentEdits(first:100){ nodes{ editedAt editor{login} diff } } } } }' \
    -f o="$OWNER" -f r="$NAME" -F n="$1" \
    --jq '[.data.repository.issue.userContentEdits.nodes[]
           | {editedAt, editor: (.editor.login // "unknown"), body: .diff}]
          | reverse'
}

cmd_list() {
  revisions "$1" | jq -r --arg n "$1" '
    if length == 0 then "#\($n): no edits recorded (body is original)"
    else "#\($n): \(length) revisions\n" +
      ([range(0; length) as $i | "  \($i+1)  \(.[$i].editedAt)  \(.[$i].editor)  \(.[$i].body | length) chars"] | join("\n"))
    end'
}

cmd_diff() {
  local n="$1" tmp
  tmp=$(mktemp -d); trap 'rm -rf "$tmp"' RETURN
  revisions "$n" > "$tmp/revs.json"
  local count; count=$(jq 'length' "$tmp/revs.json")
  [ "$count" -lt 2 ] && { echo "#$n: fewer than 2 revisions, nothing to diff"; return 0; }
  for i in $(seq 0 $((count - 2))); do
    jq -r ".[$i].body"       "$tmp/revs.json" > "$tmp/a"
    jq -r ".[$((i+1))].body" "$tmp/revs.json" > "$tmp/b"
    echo "=== #$n revision $((i+1)) -> $((i+2))  ($(jq -r ".[$((i+1))].editedAt" "$tmp/revs.json")) ==="
    diff -u --label "rev$((i+1))" --label "rev$((i+2))" "$tmp/a" "$tmp/b" || true
    echo
  done
}

cmd_rev() {
  revisions "$1" | jq -r --argjson i "$2" '
    if $i < 1 or $i > length then error("revision \($i) out of range (1..\(length))")
    else .[$i-1].body end'
}

cmd_events() {
  gh api graphql \
    -f query='query($o:String!,$r:String!,$n:Int!){
      repository(owner:$o,name:$r){ issue(number:$n){
        timelineItems(first:100, itemTypes:[RENAMED_TITLE_EVENT,LABELED_EVENT,UNLABELED_EVENT,MILESTONED_EVENT,DEMILESTONED_EVENT,CLOSED_EVENT,REOPENED_EVENT]){
          nodes{ __typename
            ... on RenamedTitleEvent{ createdAt previousTitle currentTitle }
            ... on LabeledEvent{ createdAt label{name} }
            ... on UnlabeledEvent{ createdAt label{name} }
            ... on MilestonedEvent{ createdAt milestoneTitle }
            ... on DemilestonedEvent{ createdAt milestoneTitle }
            ... on ClosedEvent{ createdAt }
            ... on ReopenedEvent{ createdAt } } } } } }' \
    -f o="$OWNER" -f r="$NAME" -F n="$1" \
    --jq '.data.repository.issue.timelineItems.nodes[]
          | "\(.createdAt)  \(.__typename)  \(.label.name // .milestoneTitle // (if .previousTitle then "\(.previousTitle) -> \(.currentTitle)" else "" end))"'
}

# Write every issue body to DIR/<number>.md so the backlog can be tracked
# in git. Re-run and commit to record what changed since the last run.
cmd_export() {
  local dir="$1"
  mkdir -p "$dir"
  gh issue list --repo "$REPO" --state all --limit 500 \
    --json number,title,state,body,labels,milestone > "$dir/.raw.json"
  jq -r '.[].number' "$dir/.raw.json" | while read -r n; do
    jq -r --argjson n "$n" '.[] | select(.number==$n)
      | "<!-- number: \(.number)\n     state: \(.state)\n     labels: \([.labels[].name]|join(", "))\n     milestone: \(.milestone.title // "none") -->\n# \(.title)\n\n\(.body)"' \
      "$dir/.raw.json" > "$dir/$n.md"
  done
  rm -f "$dir/.raw.json"
  echo "exported $(ls -1 "$dir"/*.md | wc -l) issues to $dir"
}

[ $# -ge 1 ] || { sed -n '2,25p' "$0" | sed 's|^# \?||'; exit 1; }

case "${1}" in
  --export) [ $# -eq 2 ] || die "--export needs a directory"; cmd_export "$2" ;;
  *)
    n="$1"; shift || true
    [[ "$n" =~ ^[0-9]+$ ]] || die "expected an issue number, got '$n'"
    case "${1:-}" in
      "")       cmd_list "$n" ;;
      --diff)   cmd_diff "$n" ;;
      --events) cmd_events "$n" ;;
      --rev)    [ $# -eq 2 ] || die "--rev needs a number"; cmd_rev "$n" "$2" ;;
      *)        die "unknown option '$1'" ;;
    esac ;;
esac
