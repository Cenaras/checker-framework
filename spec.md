# Goal
Implement a small, intra-procedual analysis using the Checker Framework dataflow infrastructure.

The analysis is **demand-driven**: It analyzes one particular dereference at a time rather than computing nullness information
for every expression in the method.

Given a dereference whose base expression is `e`, the analysis should determine whether `e` is guaranteed to be non-null
whenever execution reaches that dereference.

For example, for 
```java
x.foo();
```
the expression of interest is `x`.

The analysis has two possible results:
 - `SAFE`: The analysis has proven that the expression cannot be nul at the dereference
 - `UNKNOWN`: The analysis cannot prove that the epxression is non-null

`SAFE` must only be returned, when non-nullness has been established soundly. Any unsupported construct, ambiguity
or path one which `e == null` remains possible must result in `UNKNOWN`.

The analysis is therefore intentionally conservative.

# Input
The input is
 1) A method `m`
 2) A particular dereference of an expression `e` within `m`.

For the initial implementation, the expression may be restricted to local variables, parameters and fields.

Examples include:
```java
regionsStr.toLowerCase();
```
```java
future.isDone();
```
where the expressions of interest are `regionsStr` and `future` respectively.]

# Output
The output is one of
`SAFE`
`UNKNOWN`

No attempt needs to be made to distinguish between
 - A dereference that is actually unsafe and;
 - a safe dereference that the analysis is not powerful enough to prove
Both cases return `UNKNOWN`

# Analysis intuition

The analysis operates backwards from the dereference.

Suppose the target dereference is:

`e.foo();`

Immediately before the dereference, assume:
`e == null`

The analysis then propagates this assumption backwards through the control-flow graph, similar to a backwards symbolic 
execution engine.

The objective is to determine whether this assumption is inconsistent with every execution path that can reach the dereference.
Conceptually, for each backward path p reaching the dereference, the analysis constructs a path formula:

`e == null ∧ pathConditions(p)`

If this formula is unsatisfiable for every possible path reaching the dereference, then the dereference is SAFE.

If at least one path is satisfiable under `e == null;` or cannot be analyzed soundly by the supported analysis, then the result is `UNKNOWN`.

Equivalently:

`SAFE`
    iff
for every path reaching the dereference:
    `e == null ∧ pathConditions is UNSAT`

The implementation may use an SMT solver such as Z3 to determine satisfiability, although the initial supported formula language can be deliberately small.


# Initial supported reasoning

The initial implementation only needs to support enough symbolic reasoning to handle the examples below.

At minimum, formulas should support:

`x == null`
`x != null`

`b`
`!b`

`P && Q`
`P || Q`
`!P`

where x is a local variable or parameter and b is a local boolean variable.

The analysis should also be able to substitute simple local assignments when traversing backwards.

For example:

```java
boolean b = x != null;

if (b) {
x.foo();
}
```
When propagating backwards through
```java
b = x != null;
```
the fact `b` should become `x != null`

Together with the initial assumption `x == null` this is a contradiction (UNSAT) and therefore the dereference is `SAFE`.

# Control-flow reasoning
When traversing backwards over a conditional edge, add the condition required to take that edge.

For:
```java
if (condition) {
    A
} else {
    B
}
```

a path entering A gains:

`condition`

whereas a path entering B gains:

`!condition`

Nested if/else if structures must accumulate all relevant conditions.

For example:
```java
if (c1) {
        ...
} else if (c2) {
        ...
} else {
    target();
}
```

the final else is reached under:

`!c1 && !c2`

This control-flow information is essential to the second motivating example below.

# Required positive examples
The initial implementation must successfully prove the following examples are safe.
## Example 1: Boolean variable derived from a null check
```java

// regionsStr is @Nullable
boolean isRemoteRegionRequested = null != regionsStr && !regionsStr.isEmpty();
if (!isRemoteRegionRequested) {
        // ...
        } else {
regions = regionsStr.toLowerCase();
}
```
Target: `regionsStr.toLowerCase();`

Expected result: `SAFE`

Reasoning: 
To enter the `else` branch, `isRemoteRegionsRequested == true`. Propagating that backwards through its assignments gives:
`regionsStr != null && !regionsStr.isEmpty();`

As the analysis starts with `regionsStr == null` these contradict (UNSAT) so the analysis reports `SAFE`.
The analysis does not need to reason about semantics of `isEmpty()`.

Similarly, if any code later on in the method existed, the analysis does not need to reason about this.

## Example 2: Nullness implied by control-flow state
```java
if (future == null && !done) {
    future = submit();
}
else if (done) {
    finish();
}
else {
    future.isDone();
}
```
Target: `future.isDone();`.
Exected result: `SAFE`. 

Reasoning: 
To enter the final `else` branch, both preceding checks must have failed. Therefore
`!(future == null && done) && !(done)`

Assume, as required by the backwards analysis, that `future == null`. 

This is a contradiction (UNSAT). Therefore the dereference is `SAFE`.

# Required negative examples
These examples are important because the analysis must not infer non-nullness merely because it encounters a related null-check.
The analysis should only report `SAFE` if the dereference is *actually provably safe* under the supported operations.

## Negative Example 1: Disjunction does not establish non-nullness
```java
if (x != null || enabled) {
    x.foo();
}
```
Expected result: `UNKNOWN`

Reason:
The branch is entered with `x != null ∨ enabled`. 
Assuming `x == null`, as required by the analysis, this is still SAT (exactly when `enabled == true`).
That is: `x == null && (x != null || enabled)` is SAT. So the analysis must report `UNKNOWN`.

## Negative Example 2: Boolean variable does not necessarily imply non-nullness
```java
boolean shouldRun = x != null || force;
if (shouldRun) {
    x.foo();
}
```
Expected result: `UNKNOWN`. After substitution, `shouldRun` becomes `x != null || force`. 
Together with the initial assumption `x == null` this is still satisfiable: `x == null && (x != null || force)`.
As such the analysis must report `UNKNOWN`.

## Negative Example 2: The wrong branch of a null check
```java
boolean present = x != null;

if (!present) {
    x.foo();
}
```
Expected result: `UNKNOWN`.

The branch establishes `x == null` in fact. Together with the initial assumption `x == null` this is still satisfiable -- specifically `x == null && x == null` is SAT.

## Negative Example 4: A check does not dominate the dereference
```java
if (x != null) {
    use(x);
}

x.foo();
```
Expected result: `UNKNOWN`.

The earlier test does not constrain the execution at the later dereference. Both `x == null` and `x != null` can reach 
the dereference.

## Negative Example 5: Reassingment invalidates an eariler check
```java
if (x != null) {
    x = null; // or even x = getNullableValue();
    x.foo();
}
```
Expected result: `UNKNOWN`.

Even though the condition happens in the branch that establishes `x != null`, the re-assignment invalidates this.

The analysis must reason according to program order, rather than treating `x != null` as a permanent property of the variable.

## Negative Example 6: Only one path establishes non-nullness
```java
x = null;
if (condition) {
    x = new Object();
}
x.foo();
```
Expected result: `UNKNOWN`.

There exists a path with `condition == false` that reaches the dereference. Safety must hold **on all paths**.

# Unsuppored constructs in the initial implementation
The initial implementation should remain deliberately small.

When sound reasoning would require functionality outside the supported domain, return `UNKNOWN`. 

In particular, the first version does not need to reason about:
 - side effects or arbitrary calls
 - inter-procedual method behavior
 - aliasing between distinct variables
etc...

These restrictions may be relaxted in later iterations. For now, focus on providing the minimal implementation that 
satisfies the provided examples.


# Examples that may be safe, but are initially out of scope
These examples are out of scope, but eventually we should include them in the tool as well.

## Repeated expression / alias-like reasoning
```java
// entity.movement() is @Nullable
IMovementController mvmtControl = entity.movement();
if (mvmtControl != null) {
    GravityForce force = new GravityForce(entity, this.getGravity(), Direction.DOWN);
    force.setIdentifier(GRAVITY_IDENTIFIER);
    entity.movement().apply(force);
}
```
A stronger analysis will eventually be able to reason that the second `entity.movement()` returns the same value as 
`mvmtControl` -- assuming purity. The initial implementation should return `UNKNOWN` here. 

## Inter-procedual postcondition reasoning
```java
boolean complete = true;
try {
    complete = task.cancel || task.update();
} catch (...) {
    ...
}

if (complete) {
    if (task.cancel) {
        return ;
    }
    addAsset(task.asset);
}
```
With 
```java
public boolean update() {
    return this.asset != null;
}
```
A more advanced analysis can reason as follows:
`complete && !task.cancel` combined with `complete = task.cancel || task.update()` implies `task.update() == true`.
If an inter-procedual contract established:
`task.update() ==> true ==> task.asset != null`
Then `task.asset` is not null.

The required method summary or contract reasoning for this is outside the scope of the initial implementation.

# Soundness requirements
The analysis must be conservative. 

In particular, it must **not**:
 - assume that an expression is non-null merely because it was previously checked.
 - assume that two syntactically similar method calls return the same object
 - assume that method calls have no side effects (however we may add an option later to assume this)
 - ignore assignments that may change the value being tracked
 - ignore feasible control-flow paths
 - treat failture to find a counterexample as proof of safety
 - infer facts from unsupported operations.

The intended rule is:
```
Return SAFE only after proving that e == null is inconsistent with every supported path reaching the dereference. 
Otherwise return UNKNOWN.
```
Use the examples provided to test your implementation.

# Initial implementation milestones
The first implementation should focus narrowly on:
1) Use the Checker Framework CFG for the enclosing method
2) starting a backwards analysis from the selected dereference
3) tracking the hypothesis that its base expression is null
4) collecting branch predicates while traversing predecessor edges
5) propagating through simple local-variable and boolean assignments
6) handling boolean negation, conjunction, disjunction and null comparisons,
7) checking whether the resulting path conditions are satisfiable
8) returning `SAFE` only if every backwards path contradicts the null hypothesis

The implementation should be designed so that the symbolic domain can later be extended with:
 - aliases;
 - fields;
 - method summaries;
 - Checker Framework contracts;
 - pure/stable method calls / assumptions
 - additional expression forms.

These extensions are not a requirement for the first implementation.

# Inspiration for implementation
Have a look at `dataflow/src/test/java/livevar/LiveVariable.java` to see how a LiveVariable analysis is implemented 
in the Checker Framework. 

Follow the same coding conventions as are present in the current framework, and keep the implementation simple!
