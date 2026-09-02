# Writing standard

Applies to issues, commit bodies and skill docs in this repo.

Target: C1 English. Full command of grammar and vocabulary, used for
precision. Not simplified English. Not literary English.

Gate 2 of `/audit-artifacts` calibrates against this file. Correct it when
it is wrong. The correction then applies to every later audit.

## Principles

1. Write literally what is meant. No metaphor, no analogy, no image.
2. State facts, conditions and results. If a sentence carries none of
   those, delete it.
3. Give numbers, not adjectives.
4. One idea per sentence.
5. Use a bullet list when the content is a list. Use prose when the
   content is an argument with dependent steps.

## Cut

**Intensifiers and filler.** actually, genuinely, really, truly, simply,
quietly, silently, clearly, obviously, of course, it turns out, the thing
is, worth noting, importantly.

**Rhetorical shapes.**

- Closing lines that restate the paragraph for emphasis.
- "Not X, but Y" used for contrast rather than for correcting an error.
- Rhetorical questions.
- Sentence fragments used as punchlines.
- Em dashes used as a dramatic pause. Use a comma, parentheses, or a new
  sentence. The `- [ ] #N — label` separator the templates require is not
  prose; leave it.

**Corporate vocabulary.** leverage, ensure, robust, seamless, holistic,
streamline, align, drive, enable, unlock, surface (as a verb), journey,
stakeholder, going forward, at scale, best-in-class.

Replacements: use, make sure, reliable, works, complete, simplify, match,
cause, allow, find, show.

**Vague quantifiers.** many, several, various, a number of, significant,
substantial, most. Give the number or drop the claim.

## Keep

- Exact identifiers: issue numbers, file paths, class names, commands,
  version numbers, dates.
- Subordinate clauses that carry a condition or a cause. Structure is
  allowed. Decoration is not.
- Technical terms in their precise sense. Do not soften a term so a
  sentence reads more smoothly.
- Length where length is required. A short sentence is not automatically
  better. Do not shorten if it costs a fact.

## Before and after

Each "before" is real text from this board, written by an agent.

**Effect instead of fact**

Before:
> An issue that claims done without proof is worse than an open issue — it
> stops anyone looking. Tying each criterion to a test means the board goes
> red when the code does, instead of staying green while the system rots.

After:
> Closing an issue without proof removes it from review, so nobody
> re-checks it. If each criterion names a test, CI fails when the behaviour
> breaks. Without a test, the break is not detected.

**Punchline**

Before:
> A test that passes no matter what proves nothing.

After:
> Break the implementation, confirm the test fails, then restore it. A test
> that still passes with the implementation broken does not verify the
> behaviour.

**Adjectives instead of numbers**

Before:
> The board has grown large and many issues no longer match the code.

After:
> 193 issues: 12 epics, 38 stories, 127 tasks, 12 bugs. 15 carry a
> `sprint-N` label that contradicts their milestone. 3 have no type label.

**Corporate vocabulary**

Before:
> Ensure comprehensive test coverage across all touched modules.

After:
> Run the tests for every module the change touches.

## Check before publishing

- Every sentence carries a fact, a condition, or an instruction.
- Every quantity is a number.
- No word from the cut lists appears.
- Deleting any sentence would lose information.
