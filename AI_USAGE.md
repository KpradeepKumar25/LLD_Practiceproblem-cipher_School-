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

## 7. Manual testing found three real scoring bugs the unit tests missed

**What happened:** After the initial build, manually submitting adversarial input through the
running UI (an unclosed brace, pure gibberish with no Java keywords at all, a mix of both)
revealed that `StructuralEvaluator` scored these at 74, 74, and 49 out of 100 — far too generous.
The root cause: several checks (god class, instanceof chain, public field, "is any abstraction
shared") only fire when there's *something* to flag; an empty or unparseable submission trivially
passed all of them and defaulted to a clean 100 on 4 of 6 criteria.

**Decision:** Accepted the diagnosis each time and fixed `StructuralEvaluator` directly: parser
warnings now become findings instead of being silently dropped; a type with zero fields and zero
methods is flagged `MAJOR`, not ignored; and when *no* type parses at all, all four structural
criteria take an explicit `CRITICAL` hit instead of defaulting to 100. Two regression tests
(`emptyOrGarbageSubmissionScoresLowAndIsntFalselyPraised`,
`pureGibberishWithNoJavaSyntaxAlsoScoresLow`) lock this in.

**Why:** This is the clearest example in the whole session of AI-assisted work needing a human
in the loop: the existing automated tests all used realistic (if flawed) submissions, so nobody
had tried "what if the input is just noise" until a person did it by hand. The fix came from
reading the evaluator's actual logic and finding the gap, not from any model "guessing better" —
which is also why no LLM key or model training was ever relevant to this, despite it looking at
first like an AI-quality problem.

---

## Appendix: a real prompt/response pair

For transparency, here is an actual prompt `PromptBuilder` generated for the weak Vending
Machine sample, and the actual (trimmed) response `MockLlmClient` returned for it — not a
fabricated example, captured by running the real code:

**Prompt (excerpt — full prompt also includes the complete requirements list and code):**
```
You are reviewing a learner's Low-Level Design (LLD) practice submission.

IMPORTANT: Multiple different designs can be equally valid for the same problem. Judge whether
THIS design is internally sound, not whether it matches any particular model answer.

Problem: Vending Machine
...
A deterministic rule-based reviewer already found these issues. Do NOT repeat them; focus on
things rules cannot judge (whether responsibilities are well placed, whether the reasoning in
the trade-offs holds up, whether abstractions make sense):
- [MINOR] Concept described but not implemented: a pluggable payment strategy
- [MAJOR] No shared abstraction between types
- [MAJOR] Possible god class: VendingMachine
- [MAJOR] Empty class: Coin
- [MAJOR] Empty class: Card
- [MAJOR] Empty class: Note
- [MAJOR] Type-checking via instanceof: VendingMachine
- [MAJOR] Public mutable field(s) in VendingMachine
...
Respond with STRICT JSON ONLY, no markdown fences, no commentary, matching exactly this shape: ...
Rules: never use severity CRITICAL (use MAJOR instead). Give at most 6 findings. Every finding
must cite real evidence from the submission above, not a generic template.
```

**Mock response (offline, no API key):**
```json
{"summary":"Offline heuristic review (no live AI model was called). This is a conservative,
low-confidence pass over what was written, not a full design review.",
 "findings":[{"criterionId":"abstraction","severity":"INFO",
   "title":"Heuristic note on class outline",
   "evidence":"One VendingMachine class handles items, payments, balance and stock.",
   "suggestion":"Double-check that each listed class has exactly one clear responsibility.",
   "alternatives":[]}]}
```

Two things worth noting from this real example: the prompt hands the LLM the deterministic
findings already found so it doesn't repeat "god class" or "public field" — it's explicitly
steered toward judgement calls (abstraction, responsibility, reasoning) instead. And the mock
response is deliberately weak tea (`INFO` severity, a generic double-check suggestion) — that's
the honest ceiling of a heuristic with no real understanding; set `ANTHROPIC_API_KEY` to get a
genuine model response in its place instead.
