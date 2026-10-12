# Research Note: LLD Practice Platform

## 1. The learner problem

Low-Level Design is a core interview and job skill (translating a fuzzy prompt like "design a
parking lot" into classes, interfaces and relationships), but it's unusually hard to practice
alone, for a reason that doesn't apply to most other practice material:

- **There is no single correct answer.** Unlike a DSA problem with a clear pass/fail test suite,
  two LLD solutions to the same prompt can both be "correct" while looking nothing alike — one
  might use a Strategy interface for pricing, another an abstract class with a template method.
  A learner has no reliable way to tell whether their design is *actually* good or just *different*.
- **Self-review requires knowing what to look for.** A beginner can tell their code "runs," but
  judging whether a class has too many responsibilities, whether an abstraction is premature or
  missing, or whether a trade-off was reasoned about or just asserted, needs a vocabulary most
  learners are still building.
- **Feedback loops are slow and inconsistent.** Mentor or peer review is the gold standard but
  doesn't scale to "practice this 10 times this week," and quality varies a lot reviewer to reviewer.
- **Practice tends to be one-shot.** A learner designs a Parking Lot once, reads a model solution,
  and moves on — there's rarely an easy way to retry the *same* problem later and see concretely
  whether they've improved, which is what turns practice into a skill instead of a one-off exercise.

## 2. Existing approaches researched

| Tool / approach | What it offers | Gap for this problem |
|---|---|---|
| **Educative's "Grokking the Object Oriented Design Interview"** | A large catalog of LLD problems with a written model solution and UML per problem. | Static content only — there is no way to submit *your own* design and get feedback on it; you read the model answer and self-assess by eye. |
| **DesignGurus "Grokking the Low Level Design"** (similarly positioned course) | Similar: structured problems + one worked solution + video/text walkthrough. *(exact current feature set not independently verified here — to verify.)* | Same shape: a reading experience, not an evaluation loop. No submission, no feedback, no history. |
| **Scaler / AlgoMaster LLD question banks and YouTube walkthroughs** | Free, large collections of "here's how to design X" write-ups and videos, often with a GitHub repo of one implementation. | Again one-directional: consume a model answer. Several of these are community-maintained GitHub repos of worked solutions rather than products with a submit-and-evaluate loop. |
| **LeetCode** | Excellent for DSA (judge runs your code against test cases, pass/fail is objective, has an attempt history per problem). | Has essentially no LLD category, because the thing that makes LeetCode's model work — a deterministic test suite — doesn't exist for "is this a good class design?" There's no analogous evaluation primitive to borrow directly. |
| **General code review (human mentor / peer, or ad-hoc ChatGPT prompting)** | Can give nuanced, specific feedback including on reasoning and trade-offs. | Not repeatable/consistent, doesn't track attempts over time, and an unstructured chat prompt has no rubric, no deterministic floor, and no defence against just asking the model to "say it's good." |

**Pattern across all of these:** the market has plenty of *content* (problems + one worked
solution each) and essentially no *evaluation* of a learner's own, different submission — which is
exactly the gap LeetCode fills for DSA but LLD has never had, for the structural reason above
(no single correct answer to diff against).

## 3. Key gaps this product targets

1. **No tool evaluates *your* design** — every resource above hands you a model answer instead of
   judging your submission.
2. **No tool tolerates multiple valid designs.** Anything that *did* try to auto-grade LLD would
   be tempted to diff against one reference solution, which would unfairly fail a differently
   -structured but equally valid design.
3. **No attempt history / improvement loop.** Practice-and-compare (submit, see concrete feedback,
   retry the same problem, and see what actually changed) doesn't exist as a product shape for LLD.
4. **No clarity on what should be rule-based vs. judgement-based.** Existing AI-wrapped coding
   tools tend to send everything to an LLM and hope for consistency; that's expensive, slow, and
   non-reproducible for things that are actually mechanical (counting fields, detecting
   `instanceof` chains, checking whether an interface is ever implemented).

## 4. Product direction

Build the smallest version of the missing loop — **choose a problem → submit a structured design
→ get rubric-based feedback that explicitly tolerates multiple valid answers → retry and compare**
— and split evaluation deliberately:

- A **deterministic structural evaluator** owns everything mechanical and reproducible:
  requirement coverage (via a multi-name `Concept` match, not a single keyword), abstraction
  usage, god classes, long methods, `instanceof`/`switch` chains, public mutable fields, unused
  interfaces. This is the floor every attempt can trust, and it's free and instant.
- An **LLM reviewer** is layered on top only for the things that genuinely need judgement — is a
  responsibility placed sensibly, does a stated trade-off actually hold up — and is explicitly
  advisory: it can't fail an attempt, can't produce a `CRITICAL` finding, and the platform works
  with zero API key via an offline heuristic fallback.
- **History and comparison** are first-class from the start (not a "v2" feature), because the
  product's whole thesis is that LLD needs a *practice loop*, not a one-shot answer key.

This scopes out building an LMS, an auth system, or a general assessment platform — the MVP is
strictly the learner's practice loop for three well-known problems (Parking Lot, Elevator System,
Vending Machine), with the domain model and evaluation approach designed to extend to more
problems and submission formats later without a rewrite.
