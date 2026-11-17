# How LegalBAF implements a QBAF

This note maps the code to its theoretical sources. Both papers are in this folder:

- **Baroni, Rago & Toni (2019)**, *From fine-grained properties to broad principles for gradual argumentation: A principled spectrum*, International Journal of Approximate Reasoning 105:252–286. File: `Baroni-Rago-Toni-2019-QBAF-Gradual-Argumentation-IJAR.pdf`. This is the journal version of the AAAI 2018 paper that defined QBAFs; the file is the authors' accepted manuscript, CC BY-NC-ND 4.0. Cited below as **IJAR**.
- **Baroni, Romano, Toni, Aurisicchio & Bertanza (2015)**, *Automatic evaluation of design alternatives with quantitative argumentation*, Argument & Computation 6(1):24–49. File: `Baroni-et-al-2015-QuAD-Quantitative-Argumentation-Debates.pdf`. Cited below as **QuAD**.

## The framework (IJAR Definition 1)

A QBAF is a quadruple ⟨X, R⁻, R⁺, τ⟩:

| Paper | Code (`qbaf/QbafGraph.java`) |
|---|---|
| X, the arguments | `arguments()`: the claim C plus every argument the agents enter |
| R⁻, attack | an `Edge` with `Relation.ATTACK` |
| R⁺, support | an `Edge` with `Relation.SUPPORT` |
| τ, the base score in [0,1] | `Argument.baseScore`, set **only** by the judge (`score`) |
| σ, the strength (Def. 2) | computed by a `GradualSemantics` |

**Edges have no weights.** An argument's influence on its targets comes from its own final strength σ.

## The shape of the graph (QuAD Definition 2.1)

QuAD requires only that the relation is **acyclic**. It is not restricted to a tree: in QuAD Figure 3, P1 supports both A1 and A2, and C1 attacks both A2 and P1. The code follows that definition.

- **One argument can answer several nodes.** Each argument has one or more outgoing edges, to C and/or to earlier arguments.
- **One node can receive many attackers and supporters.**
- **The root is the central claim C**: "The interference with the applicant's freedom of expression violated Article 10."
  - τ(C) = 0.5, which QuAD §4.3 identifies as the right base score "when there is no a-priori attitude".
  - Only the arguments move it.

`QbafGraph` enforces these invariants, whatever the agents ask for:

| Invariant | How |
|---|---|
| No cycles | An edge is refused if its target already reaches its source (`canAddEdge`) |
| Every argument reaches C | Every argument keeps at least one outgoing edge |
| At most one edge between two nodes | An argument cannot both attack and support the same target |
| C answers nothing, and τ(C) is fixed | `addEdge` and `score` refuse |

The relation of each edge follows from the sides (`PartyAgent.relationTo`) and is not a free choice:
- applicant → C: support; government → C: attack;
- an argument → the other side's argument: attack;
- an argument → its own side's argument: support.

So one argument can attack one node and support another (for example, attack D2 and support C). That is allowed in a QBAF, where R⁻ and R⁺ are independent relations. It is more general than strict QuAD, where each argument is either a pro or a con for everything it points at.

## What is open to the agents

| Decision | Who | In the code |
|---|---|---|
| Which nodes an argument answers (one or several) | Party agents | `Draft.targets` |
| Reuse an earlier argument of its own against further nodes, instead of restating it | Party agents | `Draft.reuse` → `PartyAgent.reuse` → `QbafGraph.addEdge` |
| Which nodes each real submission answers (stage A) | Party agents | `Link.targets` → `structures/<case>.json` |
| Category and base score τ of each argument | Judge | `ArgumentScore.category`, `strength` |
| The extremes: τ = 0 (no effect) or τ = 1 (decisive) | Judge | see "Extreme values" below |
| Whether an argument merely restates an earlier one of the same side | Judge | `ArgumentScore.duplicateOf` → `QbafGraph.merge` |

Anything invalid is dropped, and the hearing continues: an unknown target, an edge that would close a cycle, a reuse of someone else's argument, or a duplicate flag that cannot be honoured.

### Duplicates: one argument is one node

**The papers do not define a pruning rule.** What they define is the shared node: when the same argument bears on several places, it is one node with several edges (QuAD Figure 3). designVUE keeps a "closed list" so such a node is scored once and reused (QuAD §6).

That matters numerically. Under DF-QuAD, two copies of an argument with strength v aggregate to 2v − v², so a repeated point would count more than once.

The code realises "one argument, one node" in two ways:
- **Reuse** (party agent): a new edge from the existing node.
- **Merge** (judge): `QbafGraph.merge(duplicate, original)` deletes the duplicate and moves its edges to the original.
  - Whoever attacked or supported the duplicate now attacks or supports the original.
  - The duplicate's targets become targets of the original.
  - An edge that would be a self-loop, a repeat, or a cycle is dropped.
  - The merge is refused, and the argument is weighed normally, if the two arguments are on different sides, if the "original" is not the earlier one, or if some argument would be left with no edge.

### Extreme values (QuAD §4.1)

- An attacker or supporter with final strength **0** "has no effect on the final score and could be ignored". All three semantics behave this way.
- A final strength of **1** saturates: nothing else on that side matters.
- QuAD notes that extremes should enter "only by a deliberate choice of the expert providing the base scores". The judge's prompt says the same.

## The semantics

IJAR Table 1 lists three semantics for a general QBAF on [0,1]. All three are implemented, selectable with the environment variable `LEGALSIM_SEMANTICS` (`df-quad`, `quad`, `euler`). Every verdict also reports σ(C) under all three; if the verdict changes between them, the case is fragile.

| Semantics | Source | Balanced | Monotonic | Code |
|---|---|---|---|---|
| **DF-QuAD** (default) | IJAR §5.8, eqs. 27–34 | yes | yes (Prop. 37) | `DfQuadSemantics` |
| QuAD | QuAD §3; IJAR eqs. 17–26 | **no** | yes (Prop. 36) | `QuadSemantics` |
| Euler-based | IJAR §5.9, eqs. 35–36 | yes | strictly (Prop. 38) | `EulerSemantics` |

DF-QuAD:

```
σ(α) = c(τ(α), F'(σ(attackers)), F'(σ(supporters)))
F'(v1..vn) = 1 − Π(1 − vi)                      (F'() = 0)
c(v0, va, vs) = v0 − v0·|vs − va|        if va ≥ vs
c(v0, va, vs) = v0 + (1 − v0)·|vs − va|  if va < vs
```

DF-QuAD is the default because QuAD has a discontinuity: an argument with strong attackers and no supporter can fall to 0, and one weak supporter lifts it to at least τ/2 (QuAD §4.2). DF-QuAD was designed to remove that.

### The principles are checked, not assumed

IJAR §4 reduces its 11 group properties to two principles. `qbaf/Principles.java` implements both checks, using the set comparison of Definition 3 in its σ/⊥ form (zero-strength arguments do not count):

- **Balance** (Principle 1): equivalent attackers and supporters ⇒ σ = τ; stronger attackers ⇒ σ ≤ τ; stronger supporters ⇒ σ ≥ τ.
- **Monotonicity** (Principle 2): a shaping triple (τ, supporters, attackers) that boosts at least as much ⇒ a strength at least as high.

`PrinciplesTest` runs them on 400 random acyclic graphs with multi-target arguments. DF-QuAD and Euler must satisfy both; QuAD must satisfy monotonicity. `QbafSemanticsTest` reproduces the papers' worked examples, including the one showing QuAD is not balanced.

## The verdict

`qbaf/QbafSemantics.java` reads the verdict off σ(C):
- above 0.5 means **violation**;
- below 0.5 means **no violation**;
- exactly 0.5 means **undecided** (under a balanced semantics, the attacks and supports on C cancel out).

During a hearing the ruling is computed on `QbafGraph.scoredSubgraph()`: the arguments the judge has already weighed, and the edges among them.

## Who does what

| Role | Does | Never does |
|---|---|---|
| Prosecution / defence agents | Write arguments, choose what each answers, reuse their own | Set scores |
| Judge | Category and τ for each argument; flags exact restatements | Add arguments or edges, or pick the winner |
| Semantics | Computes σ for every node and the verdict | — |

## What the papers define that is not used here

- **Several answer nodes.** QuAD compares alternative answers to one issue. Here the issue is binary, so one claim C is enough: σ(C) against its neutral base score.
- **Attack-only or support-only frameworks, and fixed base scores** (IJAR §3). These are restrictions of the general QBAF; the semantics that exist only for them (h-categoriser, social models, and others in IJAR §5) do not apply to a bipolar graph with judged base scores.
- **Cycles.** Both DF-QuAD and the restricted Euler semantics are defined for acyclic graphs, so the graph refuses them.

## Consequences for stage B

The stage-A plan calibrated θ per category with a **linear program**, which assumed the old "sum of edge weights" decision. None of the three semantics is linear in τ, so `WeightCalibrator` needs a different method. Options include searching over θ, gradient-based fitting, or an LP over a linearised semantics. The θ hook remains: τ = judge's strength × θ(category).
