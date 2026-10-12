# AI Usage

This project was built in a single session with Claude (Claude Code) doing the implementation
end-to-end from a detailed brief. Below are real decision points from that session — what was
suggested, what was kept or changed, and why. **Please edit this into your own words before
submitting** — it's recorded here as a factual log, not as something to hand in verbatim.

## 1. Git repo scope — caught before it became a problem

**What happened:** On startup, the environment's git repository root resolved to the entire
`C:\Users\velma` home directory, not the project folder — almost certainly unintentional (it
would have tracked unrelated personal files, credentials directories, browser profiles, etc.).

**Decision:** Flagged it immediately and initialized a new, separate `git init` scoped to just
the project folder, without touching or committing anything in the home-directory repo.

**Why:** Committing or pushing from a repo rooted at the home directory risked leaking unrelated
personal data. The safe, reversible move was a fresh scoped repo, not trying to "fix" the
existing one.

## 2. Abstraction scoring was too lenient for a design with zero abstractions

**What happened:** The first version of `StructuralEvaluator`'s abstraction check only looked for
*declared-but-unused* interfaces/abstract classes. Running it against the deliberately weak
Parking Lot sample (plain classes, no shared supertype at all, `instanceof` chains instead of
polymorphism) showed it scoring **100/100 on the abstraction criterion** — clearly wrong, since
that design has no abstraction to speak of.

**Decision:** Added a second, broader check: do *any* two submitted types have a real
extends/implements relationship at all? If not, that's a `MAJOR` finding regardless of whether an
interface was ever declared.

**Why:** A rubric criterion that can't catch the *total absence* of the thing it's named after is
worse than no check — it would have told a learner their design was fine on exactly the dimension
where it was weakest.

## 3. A regex bug that silently zeroed out a whole finding category

**What happened:** A JUnit test (`countsPublicMutableFieldsOnly`) expected a `public int mutable`
field to be flagged, but the extractor reported zero public mutable fields. Tracing it down: the
field-detection regex had `\s*` (any whitespace, including newlines) right after the optional
`public|private|protected` group. That let a "match attempt" starting on a *blank* line absorb
the next line's leading `public` keyword into the field's *type* instead of its modifier, so the
modifier group came back empty for the very first field after a blank line.

**Decision:** Narrowed that one `\s*` to `[ \t]*` (same-line whitespace only), forcing the
modifier to be checked against the actual start of its own line.

**Why:** This is exactly the kind of bug a human reviewer would catch by reading output, not by
reading the regex — writing the test first (per the brief's own emphasis on tests for "important
behaviour") is what surfaced it instead of shipping a quietly-wrong encapsulation score.

## 4. COMPLETED vs. PARTIALLY_COMPLETED: tie it to data completeness, not score

**What happened:** While wiring `EvaluationService`, the natural-seeming first instinct was
"COMPLETED if the score is good, PARTIALLY_COMPLETED if it's mediocre." That's wrong: the brief's
own wording ties `PARTIALLY_COMPLETED` specifically to *the AI evaluator failing*, not to the
submission being low-quality.

**Decision:** `COMPLETED` means both evaluators ran; `PARTIALLY_COMPLETED` means only the
deterministic one did (`!pipelineResult.degraded()`). A submission can score 20/100 and still be
`COMPLETED` — the status is about whether evaluation finished, not about whether the design is good.

**Why:** Conflating "we have full feedback" with "the feedback is positive" would have made the
status field useless for its actual purpose (knowing whether to trust/retry the AI portion), and
would have required a somewhat arbitrary score cutoff that the brief never asked for.

## 5. Maven wasn't installed — added the Maven Wrapper rather than avoiding Maven

**What happened:** The brief's run instructions assume `mvn test` / `mvn package` work, but
neither Maven nor a wrapper existed in the environment or the (empty) project folder.

**Decision:** Downloaded Apache Maven locally to get a working dev loop, then ran
`mvn wrapper:wrapper` to generate `mvnw` / `mvnw.cmd` and commit them to the repo, so anyone
cloning it can run `./mvnw test` without installing Maven themselves.

**Why:** Keeping the README's run instructions honest (`mvn test` actually works everywhere, not
just in this one dev environment) mattered more than avoiding one extra dependency-management
step; the wrapper is the standard way to do that for a Java/Maven project.

## 6. A speculative API that turned into dead code

**What happened:** Early on, `Feedback` got a `fullyPassed(int passThreshold)` helper, written in
anticipation of using a score threshold to decide COMPLETED vs. PARTIALLY_COMPLETED (see #4).
Once that approach was replaced with the degraded-flag rule, nothing called `fullyPassed` anymore.

**Decision:** Removed it rather than leaving it in "in case it's useful later."

**Why:** Per the project's own stated engineering standard (no dead code, no speculative
abstractions), a public method with zero callers is a maintenance liability, not a convenience —
easy to re-add later if an actual caller shows up.
