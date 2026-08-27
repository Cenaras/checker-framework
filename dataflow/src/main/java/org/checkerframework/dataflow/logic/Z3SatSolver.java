package org.checkerframework.dataflow.logic;

import com.microsoft.z3.BoolExpr;
import com.microsoft.z3.Context;
import com.microsoft.z3.Solver;
import com.microsoft.z3.Status;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/** A SAT backend that decides solver-independent propositional formulas using Z3. */
public final class Z3SatSolver implements SatSolver {

  @Override
  public Result solve(PropositionalFormula formula) {
    Objects.requireNonNull(formula);
    try (Context context = new Context()) {
      BoolExpr expression = formula.accept(new FormulaTranslator(context));
      Solver solver = context.mkSolver();
      solver.add(new BoolExpr[] {expression});
      Status status = solver.check();
      return switch (status) {
        case SATISFIABLE -> Result.SATISFIABLE;
        case UNSATISFIABLE -> Result.UNSATISFIABLE;
        case UNKNOWN -> Result.UNKNOWN;
      };
    }
  }

  /** Translates one solver-independent formula into the corresponding Z3 expression. */
  private static final class FormulaTranslator implements PropositionalFormula.Visitor<BoolExpr> {
    private final Context context;
    private final Map<Object, BoolExpr> atoms = new HashMap<>();

    FormulaTranslator(Context context) {
      this.context = context;
    }

    @Override
    public BoolExpr visitConstant(boolean value) {
      return context.mkBool(value);
    }

    @Override
    public BoolExpr visitAtom(Object symbol) {
      return atoms.computeIfAbsent(symbol, unused -> context.mkBoolConst("atom!" + atoms.size()));
    }

    @Override
    public BoolExpr visitNot(PropositionalFormula operand) {
      return context.mkNot(operand.accept(this));
    }

    @Override
    public BoolExpr visitAnd(PropositionalFormula left, PropositionalFormula right) {
      return context.mkAnd(left.accept(this), right.accept(this));
    }

    @Override
    public BoolExpr visitOr(PropositionalFormula left, PropositionalFormula right) {
      return context.mkOr(left.accept(this), right.accept(this));
    }
  }
}
