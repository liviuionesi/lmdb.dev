# Definition of Done

A Story or Task is only Done when every gate below is genuinely true —
verified against real evidence, never assumed from old notes. Referenced
from every issue's "Definition of Done" checkbox, not restated per issue.
This is the layered, defense-in-depth quality bar — no single check here
guarantees a bug-free change, but a bug has to slip past all of them at
once, which is the actual industry-standard approach, not a promise of
zero bugs.

- [ ] Code implemented, with tests written alongside it (not after, as an
      afterthought).
- [ ] All tests green.
- [ ] Static analysis (SonarQube) clean — no new blocker/critical issues.
- [ ] Linting/formatting clean (Checkstyle/Spotless for Java, ESLint/
      Prettier for TypeScript).
- [ ] Test coverage meets the project threshold for touched code (JaCoCo /
      Istanbul).
- [ ] Dependency/security scan clean for any new or changed dependency
      (OWASP Dependency-Check / `npm audit`).
- [ ] Documentation complete — Javadoc/TSDoc per this repo's standing
      documentation standard (see `CLAUDE.md`), architecture docs updated
      if this changed a decision.
- [ ] Code reviewed by an independent AI pass (a second, adversarial pass
      for high-risk changes — security, shared code, large diffs).
- [ ] Tests reviewed separately from "do they pass" — would they actually
      catch it if the implementation were subtly wrong?
- [ ] Issue body resynced to match real implementation state and closed
      with evidence, not on a technicality.
- [ ] **Cascading closure & story focus verified**: when closing a Task,
      checked if its parent Story has open Tasks remaining (if so, those Tasks
      must be started next; if none remain, close the Story and mark it as
      checked in its parent Epic); when closing a Story, verified if its parent
      Epic is now ready to close.
- [ ] Committed with a message referencing this issue, pushed to
      `develop` (and for a completed Story, merged `develop` → `main`).

Any Non-Functional Requirement that applies to this change (see
[NON_FUNCTIONAL_REQUIREMENTS.md](NON_FUNCTIONAL_REQUIREMENTS.md)) is also
part of Done — not optional polish.
