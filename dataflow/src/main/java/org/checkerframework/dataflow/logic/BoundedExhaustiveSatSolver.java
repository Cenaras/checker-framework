package org.checkerframework.dataflow.logic;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * A small dependency-free SAT backend that exhaustively searches propositional assignments.
 *
 * <p>This implementation is appropriate for small formulas. It returns {@link Result#UNKNOWN}
 * rather than making a claim when its decision budget is exhausted. A production backend, such as
 * an adapter for Z3, can implement {@link SatSolver} without changing analysis clients.
 */
public final class BoundedExhaustiveSatSolver implements SatSolver {

  /** The default maximum number of branching decisions per query. */
  public static final int DEFAULT_MAX_DECISIONS = 100_000;

  private final int maxDecisions;

  /** Creates a solver with {@link #DEFAULT_MAX_DECISIONS}. */
  public BoundedExhaustiveSatSolver() {
    this(DEFAULT_MAX_DECISIONS);
  }

  /**
   * Creates a solver with the given decision budget.
   *
   * @param maxDecisions a positive maximum number of branching decisions per query
   */
  public BoundedExhaustiveSatSolver(int maxDecisions) {
    if (maxDecisions <= 0) {
      throw new IllegalArgumentException("maxDecisions must be positive: " + maxDecisions);
    }
    this.maxDecisions = maxDecisions;
  }

  @Override
  public Result solve(PropositionalFormula formula) {
    Objects.requireNonNull(formula);
    LinkedHashSet<Object> symbols = new LinkedHashSet<>();
    collectAtoms(formula, symbols);
    SearchResult result =
        search(formula, new ArrayList<>(symbols), 0, new HashMap<>(), new DecisionBudget());
    return switch (result) {
      case SATISFIABLE -> Result.SATISFIABLE;
      case UNSATISFIABLE -> Result.UNSATISFIABLE;
      case EXHAUSTED -> Result.UNKNOWN;
    };
  }

  private SearchResult search(
      PropositionalFormula formula,
      List<Object> symbols,
      int index,
      Map<Object, Boolean> assignment,
      DecisionBudget budget) {
    Truth value = evaluate(formula, assignment);
    if (value == Truth.TRUE) {
      return SearchResult.SATISFIABLE;
    }
    if (value == Truth.FALSE) {
      return SearchResult.UNSATISFIABLE;
    }
    if (index >= symbols.size() || ++budget.decisions > maxDecisions) {
      return SearchResult.EXHAUSTED;
    }

    Object symbol = symbols.get(index);
    assignment.put(symbol, false);
    SearchResult whenFalse = search(formula, symbols, index + 1, assignment, budget);
    if (whenFalse == SearchResult.SATISFIABLE) {
      assignment.remove(symbol);
      return whenFalse;
    }

    assignment.put(symbol, true);
    SearchResult whenTrue = search(formula, symbols, index + 1, assignment, budget);
    assignment.remove(symbol);
    if (whenTrue == SearchResult.SATISFIABLE) {
      return whenTrue;
    }
    return whenFalse == SearchResult.EXHAUSTED || whenTrue == SearchResult.EXHAUSTED
        ? SearchResult.EXHAUSTED
        : SearchResult.UNSATISFIABLE;
  }

  private enum SearchResult {
    SATISFIABLE,
    UNSATISFIABLE,
    EXHAUSTED
  }

  private enum Truth {
    TRUE,
    FALSE,
    UNKNOWN
  }

  private static Truth evaluate(PropositionalFormula formula, Map<Object, Boolean> assignment) {
    return formula.accept(new EvaluationVisitor(assignment));
  }

  private static final class EvaluationVisitor implements PropositionalFormula.Visitor<Truth> {
    private final Map<Object, Boolean> assignment;

    EvaluationVisitor(Map<Object, Boolean> assignment) {
      this.assignment = assignment;
    }

    @Override
    public Truth visitConstant(boolean value) {
      return value ? Truth.TRUE : Truth.FALSE;
    }

    @Override
    public Truth visitAtom(Object symbol) {
      @Nullable Boolean value = assignment.get(symbol);
      return value == null ? Truth.UNKNOWN : value ? Truth.TRUE : Truth.FALSE;
    }

    @Override
    public Truth visitNot(PropositionalFormula operand) {
      Truth value = operand.accept(this);
      return value == Truth.TRUE ? Truth.FALSE : value == Truth.FALSE ? Truth.TRUE : Truth.UNKNOWN;
    }

    @Override
    public Truth visitAnd(PropositionalFormula leftFormula, PropositionalFormula rightFormula) {
      Truth left = leftFormula.accept(this);
      Truth right = rightFormula.accept(this);
      if (left == Truth.FALSE || right == Truth.FALSE) {
        return Truth.FALSE;
      }
      return left == Truth.TRUE && right == Truth.TRUE ? Truth.TRUE : Truth.UNKNOWN;
    }

    @Override
    public Truth visitOr(PropositionalFormula leftFormula, PropositionalFormula rightFormula) {
      Truth left = leftFormula.accept(this);
      Truth right = rightFormula.accept(this);
      if (left == Truth.TRUE || right == Truth.TRUE) {
        return Truth.TRUE;
      }
      return left == Truth.FALSE && right == Truth.FALSE ? Truth.FALSE : Truth.UNKNOWN;
    }
  }

  private static void collectAtoms(PropositionalFormula formula, Set<Object> result) {
    formula.accept(new AtomCollector(result));
  }

  private static final class AtomCollector implements PropositionalFormula.Visitor<Void> {
    private final Set<Object> result;

    AtomCollector(Set<Object> result) {
      this.result = result;
    }

    @Override
    public @Nullable Void visitConstant(boolean value) {
      return null;
    }

    @Override
    public @Nullable Void visitAtom(Object symbol) {
      result.add(symbol);
      return null;
    }

    @Override
    public @Nullable Void visitNot(PropositionalFormula operand) {
      operand.accept(this);
      return null;
    }

    @Override
    public @Nullable Void visitAnd(PropositionalFormula left, PropositionalFormula right) {
      left.accept(this);
      right.accept(this);
      return null;
    }

    @Override
    public @Nullable Void visitOr(PropositionalFormula left, PropositionalFormula right) {
      left.accept(this);
      right.accept(this);
      return null;
    }
  }

  private static final class DecisionBudget {
    int decisions;
  }
}
