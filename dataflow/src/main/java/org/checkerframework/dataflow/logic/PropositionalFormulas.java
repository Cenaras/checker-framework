package org.checkerframework.dataflow.logic;

import java.util.Objects;
import java.util.function.Function;

/** Factory and transformation operations for {@link PropositionalFormula}. */
public final class PropositionalFormulas {

  private static final PropositionalFormula TRUE = new ConstantFormula(true);
  private static final PropositionalFormula FALSE = new ConstantFormula(false);

  private PropositionalFormulas() {}

  /** Returns the true formula. */
  public static PropositionalFormula trueFormula() {
    return TRUE;
  }

  /** Returns the false formula. */
  public static PropositionalFormula falseFormula() {
    return FALSE;
  }

  /** Returns an atom identified by {@code symbol}. */
  public static PropositionalFormula atom(Object symbol) {
    return new AtomFormula(Objects.requireNonNull(symbol));
  }

  /** Returns the negation of {@code formula}, applying elementary simplifications. */
  public static PropositionalFormula not(PropositionalFormula formula) {
    Objects.requireNonNull(formula);
    if (formula == TRUE) {
      return FALSE;
    }
    if (formula == FALSE) {
      return TRUE;
    }
    if (formula instanceof NotFormula negated) {
      return negated.operand;
    }
    return new NotFormula(formula);
  }

  /** Returns {@code left && right}, applying elementary simplifications. */
  public static PropositionalFormula and(PropositionalFormula left, PropositionalFormula right) {
    Objects.requireNonNull(left);
    Objects.requireNonNull(right);
    if (left == FALSE || right == FALSE) {
      return FALSE;
    }
    if (left == TRUE) {
      return right;
    }
    if (right == TRUE || left.equals(right)) {
      return left;
    }
    if (not(left).equals(right) || not(right).equals(left)) {
      return FALSE;
    }
    return new AndFormula(left, right);
  }

  /** Returns {@code left || right}, applying elementary simplifications. */
  public static PropositionalFormula or(PropositionalFormula left, PropositionalFormula right) {
    Objects.requireNonNull(left);
    Objects.requireNonNull(right);
    if (left == TRUE || right == TRUE) {
      return TRUE;
    }
    if (left == FALSE) {
      return right;
    }
    if (right == FALSE || left.equals(right)) {
      return left;
    }
    if (not(left).equals(right) || not(right).equals(left)) {
      return TRUE;
    }
    return new OrFormula(left, right);
  }

  /**
   * Replaces every atom using {@code replacement}, preserving formula structure and simplifying the
   * result.
   */
  public static PropositionalFormula substitute(
      PropositionalFormula formula, Function<Object, PropositionalFormula> replacement) {
    Objects.requireNonNull(formula);
    Objects.requireNonNull(replacement);
    return formula.accept(new SubstitutionVisitor(replacement));
  }

  /**
   * A single visitor instance performs a complete substitution traversal.
   */
    private record SubstitutionVisitor(Function<Object, PropositionalFormula> replacement)
        implements PropositionalFormula.Visitor<PropositionalFormula> {

    @Override
      public PropositionalFormula visitConstant(boolean value) {
        return value ? TRUE : FALSE;
      }

      @Override
      public PropositionalFormula visitAtom(Object symbol) {
        return Objects.requireNonNull(replacement.apply(symbol));
      }

      @Override
      public PropositionalFormula visitNot(PropositionalFormula operand) {
        return not(operand.accept(this));
      }

      @Override
      public PropositionalFormula visitAnd(PropositionalFormula left, PropositionalFormula right) {
        return and(left.accept(this), right.accept(this));
      }

      @Override
      public PropositionalFormula visitOr(PropositionalFormula left, PropositionalFormula right) {
        return or(left.accept(this), right.accept(this));
      }
    }

  private record ConstantFormula(boolean value) implements PropositionalFormula {
    @Override
    public <R> R accept(Visitor<R> visitor) {
      return visitor.visitConstant(value);
    }
  }

  private record AtomFormula(Object symbol) implements PropositionalFormula {
    @Override
    public <R> R accept(Visitor<R> visitor) {
      return visitor.visitAtom(symbol);
    }
  }

  private record NotFormula(PropositionalFormula operand) implements PropositionalFormula {
    @Override
    public <R> R accept(Visitor<R> visitor) {
      return visitor.visitNot(operand);
    }
  }

  private record AndFormula(PropositionalFormula left, PropositionalFormula right)
      implements PropositionalFormula {
    @Override
    public <R> R accept(Visitor<R> visitor) {
      return visitor.visitAnd(left, right);
    }
  }

  private record OrFormula(PropositionalFormula left, PropositionalFormula right)
      implements PropositionalFormula {
    @Override
    public <R> R accept(Visitor<R> visitor) {
      return visitor.visitOr(left, right);
    }
  }
}
