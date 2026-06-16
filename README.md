# Kodkod/Alloy Isomorphic Duplication Bug MRE

This repository contains a minimal reproducible example (MRE) demonstrating a bug where Kodkod's `IterableSolver` combined with its incomplete static symmetry breaking yields structurally identical (isomorphic) solutions during iteration, which Alloy canonicalization then masks, leading to duplicate output instances.

## How to Run

Just execute the provided bash script, which compiles the Java MRE and runs it against the bundled `alloy.jar`:

```bash
./run.sh
```

### Expected Output
```
=========================================
Solution 0:
=========================================
RAW KODKOD ASSIGNMENTS (The actual boolean SAT mapping):
  this/Node -> [[Node$1]]
  this/Node.succ -> [[Node$1, Node$1]]

CANONICALIZED ALLOY OUTPUT (What the user sees in the GUI/XML):
  this/Node -> {Node$0}
  this/Node.succ -> {Node$0->Node$0}

=========================================
Solution 1:
=========================================
RAW KODKOD ASSIGNMENTS (The actual boolean SAT mapping):
  this/Node -> [[Node$2]]
  this/Node.succ -> [[Node$2, Node$2]]

CANONICALIZED ALLOY OUTPUT (What the user sees in the GUI/XML):
  this/Node -> {Node$0}
  this/Node.succ -> {Node$0->Node$0}
```

## The Bug Explanation

When enumerating solutions via `A4Solution.next()`, the SAT solver correctly returns what *it* considers to be mathematically distinct boolean assignments (e.g., using boolean variable `Node$2` instead of `Node$1`). 

The issue originates in `kodkod.engine.AbstractKodkodSolver.SolutionIterator.nextNonTrivialSolution()`:
```java
// Kodkod creates a blocking clause to ban the current boolean assignment
final int[] notModel = new int[primaryVars];
for (int i = 1; i <= primaryVars; ++i) {
    notModel[i - 1] = (cnf.valueOf(i) ? (-i) : i);
}
cnf.addClause(notModel); // Adds the block to the SAT solver
```

Because Kodkod's static symmetry breaking is not exhaustive, this naive `notModel` blocking clause ONLY blocks the specific boolean assignment. The SAT solver simply permutes the symmetric uninterpreted atoms (`Node$2` instead of `Node$1`), satisfying the `notModel` clause, and yields a new boolean solution. 

These boolean solutions represent **isomorphic graphs**. When the solution bubbles up to Alloy, `A4Solution.rename()` canonicalizes the atoms purely based on relational ordering (both `Node$1` and `Node$2` get deterministically renamed to `Node$0`). This masks the underlying boolean differences and causes Alloy to spit out the exact same structure repeatedly.

## Proposed Fix (Dynamic Symmetry Breaking)

To properly fix this, Kodkod needs to utilize **Solution-Based Symmetry Breaking** during iteration. 
Inside `nextNonTrivialSolution()`, instead of adding a single blocking clause for the exact boolean assignment, Kodkod should calculate the valid symmetry group permutations $\pi$ from the `Bounds` equivalence classes. For each solution found, it should generate and add a blocking clause for *every* permutation (or a generating subset) that is isomorphic to the current solution.

```java
Set<IntSet> symmetries = transl.bounds().symmetries(); 
List<int[]> symmetricBlockingClauses = generateSymmetricClauses(transl, currentInstance, symmetries);
for (int[] clause : symmetricBlockingClauses) {
    cnf.addClause(clause);
}
```
This guarantees the solver prunes the entire isomorphism class from the search tree rather than just a single boolean mapping.
