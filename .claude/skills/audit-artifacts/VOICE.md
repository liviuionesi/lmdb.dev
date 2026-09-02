# Voice profile — Liviu

How issues on this repo should read. Derived from Liviu's own messages,
because every issue body on the board was written by an agent and none of
it is a usable sample.

Correct this file when it gets something wrong. It is the reference Gate 2
calibrates against, so fixing it here fixes every future audit.

## The short version

Short sentences. Plain words. Say it once. State what has to happen and
why, then stop.

## Rules

**Sentence length.** Most sentences under 20 words. If one runs longer,
split it at the "and" or the comma.

**No em dashes in prose.** Not one. An em dash is almost always two
sentences that were afraid to separate. Use a period.

One exception, and only one. The child-list format the templates require
is `- [ ] #N — short label`. That dash is a separator, not punctuation.
Leave it. Checking a rewrite with `grep -c "—"` should return only the
count of those list lines.

**No semicolons. No colons for drama.** A colon is fine before a real
list. It is not fine as a drum roll.

**No closing zingers.** Do not end a paragraph with a short punchy line
that restates the point for effect. Say the point once, in the middle,
plainly, and move on.

**No contrast-for-effect.** "This is not X, it is Y" is a rhetorical
shape, not information. Write what it is.

**Plain vocabulary.** use, check, make sure, break, fix, wrong, done,
missing, works. Not: ensure, ascertain, leverage, robust, comprehensive,
holistic, surface (as a verb), rigorous, seamless.

**Obligation is direct.** "must", "has to", "we need to". Not "it is
recommended that" or "consideration should be given to".

**Purpose is stated flatly.** "The goal is to ...", "so that ...".

**Sequence with "then".** first, then, after that. Not "subsequently",
"thereafter", "in parallel with which".

**One idea per bullet.** No sub-clauses hanging off the end.

**Keep the precision.** Simple language is not vague language. Issue
numbers, file paths, class names and commands all stay exact. Shortening
a sentence must never cost a fact.

**Do not copy mistakes.** Liviu writes fast and leaves typos. Match his
sentence length, rhythm and word choice. Do not reproduce spelling errors,
missing apostrophes or lowercase sentence starts in project artifacts.
This profile copies how he thinks, not how his keyboard behaves.

## Before and after

All three "before" examples are real text an agent wrote on this board.

**1.**

Before:
> An issue that claims done without proof is worse than an open issue — it
> stops anyone looking. Tying each criterion to a test means the board goes
> red when the code does, instead of staying green while the system rots.

After:
> An issue that says done with no proof is worse than an open one. Nobody
> looks at it again. If every criterion has a test, the board goes red when
> the code breaks. Right now it stays green while things break quietly.

**2.**

Before:
> One Task owns all four on purpose. Epic #15 parents two of this batch's
> Stories (#85, #86) and Epic #22 parents three (#87, #88, #89), so if each
> Story-audit Task also rewrote its Epic, two or three Tasks would
> overwrite the same body.

After:
> One task does all four epics. Epic #15 has two stories in this batch,
> epic #22 has three. If every story task also rewrote its epic, two or
> three tasks would write over each other.

**3.**

Before:
> Runs after #255-#261, because an Epic's Child Stories list is only honest
> once its Stories are.

After:
> Runs after #255 to #261. The child stories list is only correct once the
> stories are done.

## Smell test

Read the rewritten text out loud. If it sounds like a consultant wrote it,
it is wrong. If it sounds like someone explaining the job to a colleague
who already knows the codebase, it is right.
