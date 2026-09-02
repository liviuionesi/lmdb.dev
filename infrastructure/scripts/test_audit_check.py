#!/usr/bin/env python3
"""Tests for audit-check.py, the mechanical backlog-rule checker.

Every check in `audit-check.py` is asserted here against a synthetic issue
built in memory, so the suite does not call GitHub and does not go red when
somebody fixes an issue on the real board. Each test names the rule it
covers and the section of AGENTS.md or `.claude/skills/audit-artifacts/`
that states it.

The exit-code tests replace `fetch` with a stub for the same reason: the
contract under test is "non-zero when a problem is found", not "the board
currently has a problem".

Run: python3 -m unittest discover -s infrastructure/scripts -p 'test_*.py'
"""
import importlib.util
import io
import pathlib
import sys
import unittest
from contextlib import redirect_stdout

# audit-check.py is not an importable module name (the hyphen is not a legal
# identifier), so it is loaded by path rather than by `import`.
_SPEC = importlib.util.spec_from_file_location(
    "audit_check", pathlib.Path(__file__).with_name("audit-check.py"))
ac = importlib.util.module_from_spec(_SPEC)
_SPEC.loader.exec_module(ac)

TASK_BODY = """## Task
Add the checker.

## Acceptance Criteria
- [ ] The checker runs (`test_audit_check.py`)

## Estimate
**Hours:** 3
"""


def issue(**overrides):
    """A task issue that passes every check, with the given fields replaced.

    Tests mutate one field at a time from this baseline, so a failure names
    exactly the rule that broke rather than a pile of unrelated problems.
    """
    base = {
        "number": 900,
        "state": "OPEN",
        "title": "[TASK] Add the checker",
        "body": TASK_BODY,
        "milestone": {"title": "Sprint 9"},
        "labels": {"nodes": [{"name": "task"}, {"name": "P2-medium"}]},
        "assignees": {"nodes": [{"login": "liviuionesi"}]},
        "parent": {"number": 311},
        "subIssues": {"nodes": []},
    }
    base.update(overrides)
    return base


def labelled(*names):
    """A labels payload in GitHub's GraphQL shape."""
    return {"nodes": [{"name": n} for n in names]}


class BaselineTest(unittest.TestCase):
    """The baseline issue must be clean, or every other test proves nothing."""

    def test_a_conforming_task_reports_no_problems(self):
        """A task with one type label, one priority, an assignee, a parent,
        every template section and a proven criterion breaks no rule."""
        self.assertEqual(ac.check(issue()), [])


class LabelRuleTest(unittest.TestCase):
    """AGENTS.md: exactly one type label, exactly one priority label, an
    assignee, and no retired sprint-N label."""

    def test_two_type_labels_are_reported(self):
        """Two type labels leave the issue's type ambiguous, so the template
        that applies to it is undecidable."""
        bad = ac.check(issue(labels=labelled("task", "user-story", "P2-medium")))
        self.assertIn("type labels: ['task', 'user-story']", bad)

    def test_missing_type_label_is_reported(self):
        """No type label at all is the same ambiguity as two."""
        self.assertIn("type labels: none", ac.check(issue(labels=labelled("P2-medium"))))

    def test_missing_priority_label_is_reported(self):
        """Priority ordering drives which issue is picked next, so an issue
        without one cannot be ranked."""
        self.assertIn("priority labels: none", ac.check(issue(labels=labelled("task"))))

    def test_two_priority_labels_are_reported(self):
        """Two priorities rank the issue in two places at once."""
        bad = ac.check(issue(labels=labelled("task", "P0-critical", "P2-medium")))
        self.assertIn("priority labels: ['P0-critical', 'P2-medium']", bad)

    def test_unassigned_issue_is_reported(self):
        """Every issue is assigned to the repo owner."""
        self.assertIn("unassigned", ac.check(issue(assignees={"nodes": []})))

    def test_retired_sprint_label_is_reported(self):
        """sprint-N labels were replaced by milestones. A surviving one is a
        second copy of the sprint that drifts from the milestone."""
        bad = ac.check(issue(labels=labelled("task", "P2-medium", "sprint-3")))
        self.assertIn("carries a retired sprint-N label", bad)


class HierarchyRuleTest(unittest.TestCase):
    """AGENTS.md: hierarchy lives in native sub-issue links and nowhere else.
    An issue with no parent keeps a `## No parent` section saying why."""

    def test_markdown_child_list_is_reported(self):
        """`## Technical Tasks` duplicates the sub-issues panel. A stale
        checkbox in one hid the open task #120 under a closed story."""
        body = TASK_BODY + "\n## Technical Tasks\n- [ ] #120\n"
        self.assertIn("has ## Technical Tasks, duplicating the native sub-issue links",
                      ac.check(issue(body=body)))

    def test_child_stories_section_is_reported(self):
        """`## Child Stories` is the epic-level form of the same duplicate."""
        body = TASK_BODY + "\n## Child Stories\n- [ ] #77\n"
        self.assertIn("has ## Child Stories, duplicating the native sub-issue links",
                      ac.check(issue(body=body)))

    def test_sprint_section_is_reported(self):
        """`## Sprint` is a second copy of the Milestone field. #253 removed it
        from the story template for that reason."""
        body = TASK_BODY + "\n## Sprint\nSprint 9\n"
        self.assertIn("has ## Sprint, duplicating the Milestone field",
                      ac.check(issue(body=body)))

    def test_a_sprint_heading_with_a_number_is_not_a_section(self):
        """Only a bare `## Sprint` line is the retired section. Prose that
        happens to name a sprint is left alone."""
        body = TASK_BODY + "\n## Sprint 9 report\nWhat shipped.\n"
        self.assertEqual(ac.check(issue(body=body)), [])

    def test_markdown_parent_line_is_reported(self):
        """`Parent: #N` is a hand-synced copy of the native parent link."""
        self.assertIn("has a markdown Parent: #N line",
                      ac.check(issue(body="Parent: #311\n" + TASK_BODY)))

    def test_orphan_without_a_stated_reason_is_reported(self):
        """A missing parent is either deliberate or an oversight, and only a
        `## No parent` section can tell the two apart."""
        self.assertIn("no parent link and no '## No parent' section",
                      ac.check(issue(parent=None)))

    def test_orphan_with_a_stated_reason_passes(self):
        """A deliberate orphan is the one fact a native link cannot carry."""
        body = TASK_BODY + "\n## No parent\nStandalone tooling work.\n"
        self.assertEqual(ac.check(issue(parent=None, body=body)), [])

    def test_an_epic_needs_no_parent(self):
        """Epics sit at the top of the hierarchy, so the parent rule does not
        apply to them."""
        body = "## Epic\nx\n\n## Business Value\nx\n\n## Product Goal alignment\nx\n"
        bad = ac.check(issue(parent=None, body=body,
                             labels=labelled("epic", "P2-medium")))
        self.assertEqual(bad, [])


class TemplateSectionTest(unittest.TestCase):
    """Gate 1 of the audit skill: the body carries every section its template
    has, and the required set differs per type."""

    def test_missing_task_section_is_reported(self):
        """A task without `## Estimate` carries no size, so it cannot be
        planned into a sprint."""
        body = TASK_BODY.replace("## Estimate\n**Hours:** 3\n", "")
        self.assertIn("missing section ## Estimate", ac.check(issue(body=body)))

    def test_story_sections_are_required(self):
        """A story is held to the story template, not the task one."""
        bad = ac.check(issue(body=TASK_BODY, labels=labelled("user-story", "P2-medium")))
        self.assertIn("missing section ## User Story", bad)
        self.assertIn("missing section ## Story Points", bad)

    def test_notes_is_not_required(self):
        """The templates mark `## Notes` optional and Gate 1 deletes an empty
        section, so requiring it would push every issue towards an empty one."""
        self.assertNotIn("missing section ## Notes",
                         ac.check(issue(body=TASK_BODY)))


class CriterionProofTest(unittest.TestCase):
    """Gate 4: every acceptance criterion names the test or command that
    proves it, and a date is never a proof."""

    def test_dated_criterion_is_reported(self):
        """`verified 2026-01-01` describes the past. It cannot go red when the
        behaviour breaks, so the box stays ticked while the code rots."""
        body = TASK_BODY.replace("(`test_audit_check.py`)", "(verified 2026-01-01)")
        bad = ac.check(issue(body=body))
        self.assertTrue(any(b.startswith("criterion dated, not proven") for b in bad), bad)

    def test_criterion_without_a_proof_is_reported(self):
        """A criterion naming nothing re-checkable is a promise, not a fact."""
        body = TASK_BODY.replace(" (`test_audit_check.py`)", "")
        bad = ac.check(issue(body=body))
        self.assertTrue(any(b.startswith("criterion names no proof") for b in bad), bad)

    def test_manual_proof_is_accepted(self):
        """`(manual: steps)` is a last resort the templates allow, because
        being explicit about it beats pretending a test exists."""
        body = TASK_BODY.replace("(`test_audit_check.py`)",
                                 "(manual: open the board and read the Status column)")
        self.assertEqual(ac.check(issue(body=body)), [])

    def test_no_test_proof_is_accepted(self):
        """`(no test: reason)` covers a criterion untestable in principle."""
        body = TASK_BODY.replace("(`test_audit_check.py`)",
                                 "(no test: a documentation statement)")
        self.assertEqual(ac.check(issue(body=body)), [])

    def test_naming_the_child_issue_is_a_proof(self):
        """A parent's criterion is proven by the child that carries the work,
        the same way a leaf's is proven by a test name."""
        body = TASK_BODY.replace("(`test_audit_check.py`)", "(#319)")
        self.assertEqual(ac.check(issue(body=body)), [])

    def test_missing_criteria_section_is_reported(self):
        """A task, story or bug with no criteria has nothing to close against."""
        body = "## Task\nx\n\n## Estimate\n**Hours:** 3\n"
        bad = ac.check(issue(body=body))
        self.assertIn("no acceptance criteria", bad)

    def test_a_criterion_spanning_lines_is_read_whole(self):
        """Criteria wrap across lines. Reading only the first line would miss
        a proof, or a date, sitting on the continuation."""
        body = """## Task
x

## Acceptance Criteria
- [ ] Given the board, when the checker runs, then it reports every
      problem it finds (`test_audit_check.py`)

## Estimate
**Hours:** 3
"""
        self.assertEqual(len(ac.criteria(body)), 1)
        self.assertEqual(ac.check(issue(body=body)), [])


class BugPriorityTest(unittest.TestCase):
    """The bug template: the priority written in the body and the priority
    label must agree."""

    BUG_BODY = """## Bug
It breaks.

## Severity vs. Priority
**Severity (technical impact):** Major
**Priority (business urgency):** P0

## Acceptance Criteria
- [ ] Fixed (`SomeRegressionTest`)
"""

    def test_disagreeing_priority_is_reported(self):
        """Two copies of the priority mean one of them is ignored, and which
        one gets read depends on who is reading."""
        bad = ac.check(issue(body=self.BUG_BODY, parent=None,
                             labels=labelled("bug", "P2-medium")))
        self.assertIn("body states P0 but label is ['P2-medium']", bad)

    def test_agreeing_priority_passes(self):
        """Bugs sit outside the hierarchy, so no parent section is expected."""
        self.assertEqual(ac.check(issue(body=self.BUG_BODY, parent=None,
                                        labels=labelled("bug", "P0-critical"))), [])


class ClosedIssueTest(unittest.TestCase):
    """AGENTS.md: never close an issue with an unchecked criterion, and never
    close a parent while a child is open."""

    def test_closed_with_an_unchecked_criterion_is_reported(self):
        """#255 was closed with all six criteria unchecked. This is the check
        that found it."""
        self.assertIn("closed with an unchecked criterion",
                      ac.check(issue(state="CLOSED")))

    def test_closed_with_every_criterion_checked_passes(self):
        """The same issue closed honestly breaks no rule."""
        body = TASK_BODY.replace("- [ ]", "- [x]")
        self.assertEqual(ac.check(issue(state="CLOSED", body=body)), [])

    def test_closed_with_an_open_child_is_reported(self):
        """An exhausted parent hides its open children from every list that
        filters on open issues."""
        body = TASK_BODY.replace("- [ ]", "- [x]")
        bad = ac.check(issue(state="CLOSED", body=body,
                             subIssues={"nodes": [{"number": 120, "state": "OPEN"}]}))
        self.assertIn("closed with open children [120]", bad)

    def test_an_open_issue_may_have_unchecked_criteria(self):
        """Work in progress is the normal state of an open issue."""
        self.assertEqual(ac.check(issue()), [])


class ExitCodeTest(unittest.TestCase):
    """The script gates a run, so its exit status is part of its contract."""

    def run_main(self, argv, board):
        """Run main() against a stubbed board, returning (exit code, output).

        `fetch` is replaced so the result depends on the board passed in, not
        on the live repo.
        """
        real_fetch, real_argv = ac.fetch, sys.argv
        ac.fetch = lambda: board
        sys.argv = ["audit-check.py"] + argv
        try:
            buf = io.StringIO()
            with redirect_stdout(buf):
                code = ac.main()
            return code, buf.getvalue()
        finally:
            ac.fetch, sys.argv = real_fetch, real_argv

    def test_a_problem_exits_non_zero(self):
        """The gate: a board with a problem must fail the run."""
        board = {900: issue(assignees={"nodes": []})}
        code, out = self.run_main(["900"], board)
        self.assertEqual(code, 1)
        self.assertIn("unassigned", out)

    def test_a_clean_issue_exits_zero(self):
        """A clean board must not fail the run, or the gate is useless."""
        code, out = self.run_main(["900"], {900: issue()})
        self.assertEqual(code, 0)
        self.assertIn("with problems 0", out)

    def test_an_unknown_number_exits_non_zero(self):
        """A number that names no issue is a problem with the command, not a
        pass. Silently reporting clean would hide a typo in an audit."""
        code, out = self.run_main(["404"], {900: issue()})
        self.assertEqual(code, 1)
        self.assertIn("#404: not found", out)

    def test_tree_walks_every_descendant(self):
        """--tree is how an audit covers a story and its tasks in one run."""
        board = {
            900: issue(number=900, subIssues={"nodes": [{"number": 901, "state": "OPEN"}]}),
            901: issue(number=901, assignees={"nodes": []},
                       subIssues={"nodes": [{"number": 902, "state": "OPEN"}]}),
            902: issue(number=902),
        }
        code, out = self.run_main(["900", "--tree"], board)
        self.assertEqual(code, 1)
        self.assertIn("checked 3,", out)
        self.assertIn("#901", out)

    def test_tree_survives_a_cycle(self):
        """A sub-issue loop must not hang the walk."""
        board = {
            900: issue(number=900, subIssues={"nodes": [{"number": 901, "state": "OPEN"}]}),
            901: issue(number=901, subIssues={"nodes": [{"number": 900, "state": "OPEN"}]}),
        }
        code, out = self.run_main(["900", "--tree"], board)
        self.assertEqual(code, 0)
        self.assertIn("checked 2,", out)

    def test_all_checks_every_issue_on_the_board(self):
        """--all is the board-wide sweep."""
        board = {900: issue(number=900), 901: issue(number=901)}
        code, out = self.run_main(["--all"], board)
        self.assertEqual(code, 0)
        self.assertIn("checked 2,", out)

    def test_quiet_prints_counts_without_the_detail(self):
        """--quiet is for a summary line in a log, so it keeps the exit code
        and the totals but drops the per-problem lines."""
        board = {900: issue(assignees={"nodes": []})}
        code, out = self.run_main(["900", "--quiet"], board)
        self.assertEqual(code, 1)
        self.assertNotIn("unassigned", out)
        self.assertIn("with problems 1", out)

    def test_no_arguments_prints_usage_and_exits_two(self):
        """A run with no target is a usage error, distinct from a failed
        check, so it exits 2 rather than 1."""
        code, out = self.run_main([], {900: issue()})
        self.assertEqual(code, 2)
        self.assertIn("--tree", out)


if __name__ == "__main__":
    unittest.main()
