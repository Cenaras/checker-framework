package org.checkerframework.dataflow.logic;

import static org.checkerframework.dataflow.logic.PropositionalFormulas.and;
import static org.checkerframework.dataflow.logic.PropositionalFormulas.atom;
import static org.checkerframework.dataflow.logic.PropositionalFormulas.not;
import static org.checkerframework.dataflow.logic.PropositionalFormulas.or;
import static org.junit.Assert.assertEquals;

import org.junit.Test;

/** Tests {@link BoundedExhaustiveSatSolver}. */
public class BoundedExhaustiveSatSolverTest {

  @Test
  public void reportsSatisfiableAndUnsatisfiable() {
    PropositionalFormula a = atom("a");
    PropositionalFormula b = atom("b");
    SatSolver solver = new BoundedExhaustiveSatSolver();

    assertEquals(SatSolver.Result.SATISFIABLE, solver.solve(or(a, b)));
    assertEquals(SatSolver.Result.UNSATISFIABLE, solver.solve(and(or(a, b), and(not(a), not(b)))));
  }

  @Test
  public void reportsUnknownWhenBudgetIsExhausted() {
    PropositionalFormula a = atom("a");
    PropositionalFormula b = atom("b");
    SatSolver solver = new BoundedExhaustiveSatSolver(1);

    assertEquals(SatSolver.Result.UNKNOWN, solver.solve(and(a, b)));
  }
}
