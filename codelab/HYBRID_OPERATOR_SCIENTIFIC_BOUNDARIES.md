# Δ Hybrid Operator Lab — scientific boundaries

Contract: `DELTA_HYBRID2_TYPED_VIEW_V1`

Status: `EXPERIMENTAL_CONTRACT / ADDITIVE_ONLY / FAIL_CLOSED / NO_LANGUAGE_PROMOTION`

This layer is a bounded view over the existing typed-operator work. It does **not** define a new universal number field and does **not** promote new SIGMA syntax.

## Bounded composition

- Hybrid depth: `1..2` operators only in this application-facing layer.
- Finite matrix grade: `1..8`.
- Finite integer exponent window: `-9..9`.
- `α→-∞`, `α→0-`, `α=0`, `α→0+`, `α→+∞` are regime labels. They are not ordinary finite exponent values and `0-`/`0+` are not additional scalar elements.
- A depth-2 hybrid is admitted only after both constituent operators are independently typed. Unsupported combinations remain symbolic or fail closed.

## Domain routing

| Domain | Structure used here | Executable status in this lab |
|---|---|---|
| `N` | commutative semiring | discrete/non-negative power lane; negative power requires embedding |
| `Z` | commutative ring | discrete integer lane; inverse/resolvent may require embedding |
| `Q` | ordered field | exact 2×2 rational matrix lane |
| `Q(sqrt2)⊂Qbar` | executed algebraic subfield harness | contract only in this app increment |
| `R` | real-closed ordered field | typed contract only here; no new floating implementation is substituted |
| `C` | algebraically closed field | contract only; one-sided real limits require an external real parameter/path |
| `H` | noncommutative division algebra | ordered multiplication contract only; quaternion order must be preserved |
| `Qp` | non-Archimedean field | p-adic valuation/topology contract only; no binary64 substitution |
| `GF(5), GF(7)` | finite fields | finite-field power/Frobenius lane; no Archimedean 0± limit semantics |
| Hyperreal | model-dependent non-Archimedean ordered field | symbolic/model-dependent |
| Surreal | proper-class ordered real-closed field structure | symbolic only; no finite-machine universal implementation claim |

## Exact finite checks added with this increment

The JVM test harness covers 22 fail-closed cases, including:

- depth `>2` rejected;
- missing second operator at depth 2 rejected;
- negative powers in `N/Z` require explicit embedding;
- exact rational 2×2 matrix powers and inverse;
- singular negative powers rejected;
- exact commutator distinguishes commuting/noncommuting fixtures;
- exact resolvent/power commutation fixture for commuting rational matrices;
- finite-field Frobenius routed separately;
- `Qp`, quaternion, hyperreal and surreal branches are not silently evaluated as binary64;
- one-sided and infinite limit regimes are kept as typed labels.

An independent exact-arithmetic stress calculation used all 81 matrices in `{-1,0,1}^{2×2}` and all 6,561 ordered pairs. There were 817 commuting pairs. For every commuting pair for which `(I - A/2)^{-1}` existed, the identity

`(I - A/2)^(-1) B^n = B^n (I - A/2)^(-1)`

was checked for `n=0..4`: **3,995 exact cases, 0 failures**. This is a finite verification of fixtures, not a proof by testing. Algebraically, the identity follows because `AB=BA` implies `B` commutes with `I-λA`, and therefore with its inverse whenever that inverse exists.

Additional exact independent checks:

- `GF(5)`: `x^5=x` for every field element — pass.
- `GF(7)`: `x^7=x` for every field element — pass.
- rational `3`-adic valuation sample: 1,444 exact multiplicative cases with `v3(xy)=v3(x)+v3(y)`, 0 failures; 1,406 nonzero-sum cases with `v3(x+y)≥min(v3(x),v3(y))`, 0 failures.
- quaternion basis/sign stress: 512 associativity fixtures, 0 failures; `ij=k` and `ji=-k` explicitly preserve noncommutativity.

These checks do not replace the previously materialized typed-hybrid harness and do not establish physical validation or a new theorem family.

## Mandatory epistemic boundaries

`OPERATOR_LAB != SIGMA_LANGUAGE_SEMANTICS`

`FINITE_FIXTURE != LIMIT_THEOREM`

`FINITE_EXHAUSTIVE_ENVELOPE != ALL_FORMULAS`

`Qp_TOPOLOGY != ARCHIMEDEAN_ORDER`

`FINITE_FIELD_POWER != REAL_RADIAL_DYNAMICS`

`QUATERNION_ORDER_PRESERVED`

`HYPERREAL/SURREAL = MODEL_DEPENDENT_OR_SYMBOLIC`

`NO_NEW_FIELD_CLAIM`

`VALIDATION_TRANSFER = NONE`

`PHYSICAL_VALIDATION = NONE`
