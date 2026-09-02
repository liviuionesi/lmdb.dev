#!/usr/bin/env python3
"""Check GitHub issues against the repo's backlog standard.

The standard lives in AGENTS.md and `.claude/skills/audit-artifacts/`. Most
of it is prose a human has to judge. The parts below are mechanical, so
they are checked here instead of being re-read by hand on every audit.

What this cannot check: whether a claim is true, whether a named test
actually proves the criterion, and whether the prose reads well. Those stay
with the auditor.

Usage:
  audit-check.py 251 --tree     # an issue and everything under it
  audit-check.py 83 84 85       # specific issues
  audit-check.py --all          # every issue on the board
  audit-check.py --all --quiet  # counts only
"""
import json, re, subprocess, sys

REPO = "liviuionesi/lmdb.dev"
OWNER, NAME = REPO.split("/")
TYPES = ("epic", "user-story", "task", "bug")
PRIOS = ("P0-critical", "P1-high", "P2-medium", "P3-low")
# A criterion is "proven" if it names a test, a command, or says explicitly
# that it cannot be. `audited` covers the audit backlog's own criteria,
# whose proof is the audited issue itself.
# `(#123)` counts: naming the child issue that carries the work is a proof
# for a parent's criterion, the same way a test name is for a leaf's.
PROOF = re.compile(r"`[^`]+`|\(manual:|\(no test|Backend CI|Frontend CI"
                   r"|\baudited\b|\(#\d+|#\d+(?:,|\s+and)\s*#\d+", re.I)
DATE = re.compile(r"\b20\d{2}-\d{2}-\d{2}\b")
# Sections that hold a second copy of a GitHub field. AGENTS.md: "Do not
# restate in the body what a GitHub field already holds." The copy drifts from
# the field, and which one gets read depends on who is reading.
BANNED_SECTIONS = {
    "## Child Stories": "the native sub-issue links",
    "## Technical Tasks": "the native sub-issue links",
    "## Sprint": "the Milestone field",
}
SECTIONS = {
    "epic":       ["## Epic", "## Business Value", "## Product Goal alignment"],
    "user-story": ["## User Story", "## Acceptance Criteria", "## Definition of Ready",
                   "## Story Points", "## Definition of Done"],
    "task":       ["## Task", "## Acceptance Criteria", "## Estimate"],
    "bug":        ["## Bug", "## Severity vs. Priority", "## Acceptance Criteria"],
}
# `## Notes` is deliberately absent from the lists above. The templates mark it
# "only if something genuinely needs flagging", and Gate 1 says an empty section
# is deleted rather than left as a bare heading, so requiring it would push every
# issue towards an empty one.


def fetch():
    """Fetch every issue on the board. Returns a dict keyed by issue number."""
    q = """query($owner:String!,$name:String!,$endCursor:String){
      repository(owner:$owner,name:$name){
        issues(first:50, after:$endCursor, states:[OPEN,CLOSED]){
          pageInfo{hasNextPage endCursor}
          nodes{ number state title body milestone{title}
            labels(first:20){nodes{name}} assignees(first:5){nodes{login}}
            parent{number} subIssues(first:100){nodes{number state}} } } } }"""
    out = subprocess.run(["gh", "api", "graphql", "--paginate", "-f", "query=" + q,
                          "-f", "owner=" + OWNER, "-f", "name=" + NAME,
                          "--jq", ".data.repository.issues.nodes[]"],
                         capture_output=True, text=True, check=True).stdout
    return {r["number"]: r for r in (json.loads(l) for l in out.splitlines() if l.strip())}


def labels(issue):
    """Label names on an issue, as a set."""
    return {l["name"] for l in issue["labels"]["nodes"]}


def issue_type(issue):
    """The issue's single type label, or None."""
    found = labels(issue) & set(TYPES)
    return found.pop() if len(found) == 1 else None


def criteria(body):
    """Acceptance-criteria lines, each as one string including continuations."""
    sec = re.search(r"## Acceptance Criteria[^\n]*\n(.*?)(?=\n## |\Z)", body or "", re.S)
    if not sec:
        return []
    return [m.group(0) for m in re.finditer(r"^- \[[ xX]\].*?(?=\n- \[|\Z)",
                                            sec.group(1), re.S | re.M)]


def check(issue):
    """Run every mechanical check on one issue. Returns a list of problems."""
    body = issue["body"] or ""
    lab = labels(issue)
    bad = []

    # 1. Exactly one type label and one priority label.
    if len(lab & set(TYPES)) != 1:
        bad.append(f"type labels: {sorted(lab & set(TYPES)) or 'none'}")
    if len(lab & set(PRIOS)) != 1:
        bad.append(f"priority labels: {sorted(lab & set(PRIOS)) or 'none'}")

    # 2. Assigned, and no retired sprint-N label.
    if not issue["assignees"]["nodes"]:
        bad.append("unassigned")
    if any(l.startswith("sprint-") for l in lab):
        bad.append("carries a retired sprint-N label")

    # 3. Hierarchy is native only.
    for s, holder in BANNED_SECTIONS.items():
        if re.search(r"\n" + re.escape(s) + r"\n", "\n" + body):
            bad.append(f"has {s}, duplicating {holder}")
    if re.search(r"^Parent:\s*#\d+", body, re.M):
        bad.append("has a markdown Parent: #N line")

    # 4. A non-epic needs a parent link or a stated reason for having none.
    t = issue_type(issue)
    if t in ("user-story", "task") and not issue.get("parent") and "## No parent" not in body:
        bad.append("no parent link and no '## No parent' section")

    # 5. Template sections present.
    for s in SECTIONS.get(t, []):
        if s not in body:
            bad.append(f"missing section {s}")

    # 6. Criteria: present, none dated, each naming a proof.
    crit = criteria(body)
    if t in ("user-story", "task", "bug") and not crit:
        bad.append("no acceptance criteria")
    for c in crit:
        first = c.split("\n")[0][:58]
        if DATE.search(c):
            bad.append(f"criterion dated, not proven: {first}")
        elif not PROOF.search(c):
            bad.append(f"criterion names no proof: {first}")

    # 7. A Bug's priority label must match the priority its body states.
    if t == "bug":
        m = re.search(r"\*\*Priority[^\n]*:\*\*\s*(P[0-3])", body)
        if m:
            want = {"P0": "P0-critical", "P1": "P1-high",
                    "P2": "P2-medium", "P3": "P3-low"}[m.group(1)]
            if want not in lab:
                bad.append(f"body states {m.group(1)} but label is "
                           f"{sorted(lab & set(PRIOS)) or 'none'}")

    # 8. A closed issue may not have unchecked criteria or open children.
    if issue["state"] == "CLOSED":
        if any(c.startswith("- [ ]") for c in crit):
            bad.append("closed with an unchecked criterion")
        openk = [k["number"] for k in issue["subIssues"]["nodes"] if k["state"] == "OPEN"]
        if openk:
            bad.append(f"closed with open children {openk}")
    return bad


def main():
    args = sys.argv[1:]
    quiet = "--quiet" in args
    tree_mode = "--tree" in args
    nums = [int(a) for a in args if a.isdigit()]
    data = fetch()

    if "--all" in args:
        targets = sorted(data)
    elif tree_mode:
        targets, stack = set(), list(nums)
        while stack:
            n = stack.pop()
            if n in targets or n not in data:
                continue
            targets.add(n)
            stack += [k["number"] for k in data[n]["subIssues"]["nodes"]]
        targets = sorted(targets)
    else:
        targets = nums
    if not targets:
        print(__doc__)
        return 2

    failed = 0
    for n in targets:
        if n not in data:
            print(f"#{n}: not found")
            failed += 1
            continue
        bad = check(data[n])
        if bad:
            failed += 1
            if not quiet:
                print(f"#{n} [{data[n]['state']}] {data[n]['title'][:62]}")
                for b in bad:
                    print(f"    - {b}")
    print(f"\nchecked {len(targets)}, clean {len(targets) - failed}, with problems {failed}")
    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main())
