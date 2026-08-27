package org.checkerframework.dataflow.logic;

/** A backend that decides the satisfiability of solver-independent propositional formulas. */
@FunctionalInterface
public interface SatSolver {

  /** The possible outcomes of a SAT query. */
  enum Result {
    /** The formula has a satisfying assignment. */
    SATISFIABLE,

    /** The formula has no satisfying assignment. */
    UNSATISFIABLE,

    /** The backend could not decide the query. */
    UNKNOWN
  }

  /**
   * Determine whether {@code formula} is satisfiable.
   *
   * @param formula the formula to decide
   * @return the solver result
   */
  Result solve(PropositionalFormula formula);
}
