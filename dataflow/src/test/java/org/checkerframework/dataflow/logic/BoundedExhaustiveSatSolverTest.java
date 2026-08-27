package org.checkerframework.dataflow.logic;

import static org.checkerframework.dataflow.logic.PropositionalFormulas.and;
import static org.checkerframework.dataflow.logic.PropositionalFormulas.atom;
import static org.checkerframework.dataflow.logic.PropositionalFormulas.falseFormula;
import static org.checkerframework.dataflow.logic.PropositionalFormulas.not;
import static org.checkerframework.dataflow.logic.PropositionalFormulas.or;
import static org.checkerframework.dataflow.logic.PropositionalFormulas.substitute;
import static org.checkerframework.dataflow.logic.PropositionalFormulas.trueFormula;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

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

  @Test(expected = IllegalArgumentException.class)
  public void rejectsNonPositiveBudget() {
    new BoundedExhaustiveSatSolver(0);
  }

  @Test
  public void solvesConstants() {
    SatSolver solver = new BoundedExhaustiveSatSolver();

    assertEquals(SatSolver.Result.SATISFIABLE, solver.solve(trueFormula()));
    assertEquals(SatSolver.Result.UNSATISFIABLE, solver.solve(falseFormula()));
  }

  @Test
  public void factoriesApplyElementarySimplifications() {
    PropositionalFormula trueFormula = trueFormula();
    PropositionalFormula falseFormula = falseFormula();
    PropositionalFormula a = atom("a");

    assertSame(falseFormula, not(trueFormula));
    assertSame(trueFormula, not(falseFormula));
    assertSame(a, not(not(a)));

    assertSame(falseFormula, and(falseFormula, a));
    assertSame(falseFormula, and(a, falseFormula));
    assertSame(a, and(trueFormula, a));
    assertSame(a, and(a, trueFormula));
    assertSame(a, and(a, a));
    assertSame(falseFormula, and(a, not(a)));

    assertSame(trueFormula, or(trueFormula, a));
    assertSame(trueFormula, or(a, trueFormula));
    assertSame(a, or(falseFormula, a));
    assertSame(a, or(a, falseFormula));
    assertSame(a, or(a, a));
    assertSame(trueFormula, or(a, not(a)));
  }

  @Test
  public void substitutionTraversesEveryFormulaKind() {
    PropositionalFormula formula = and(not(atom("a")), or(atom("b"), atom("c")));
    PropositionalFormula replaced =
        substitute(
            formula,
            symbol ->
                switch ((String) symbol) {
                  case "a", "b" -> falseFormula();
                  case "c" -> trueFormula();
                  default -> throw new AssertionError(symbol);
                });

    assertSame(trueFormula(), replaced);
    assertSame(trueFormula(), substitute(trueFormula(), symbol -> falseFormula()));
  }
}
