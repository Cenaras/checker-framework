package org.checkerframework.dataflow.logic;

/**
 * A solver-independent propositional formula.
 *
 * <p>Formula implementations are intentionally hidden. A SAT backend translates a formula by
 * implementing {@link Visitor}; it does not need to depend on the formula factory's internal AST
 * classes. Atom symbols are opaque to the formula and solver APIs and are compared using {@link
 * Object#equals(Object)}.
 */
public interface PropositionalFormula {

  /** A visitor over every kind of propositional formula. */
  interface Visitor<R> {

    /** Visit a boolean constant. */
    R visitConstant(boolean value);

    /** Visit an atom with an opaque, client-defined symbol. */
    R visitAtom(Object symbol);

    /** Visit a negation. */
    R visitNot(PropositionalFormula operand);

    /** Visit a conjunction. */
    R visitAnd(PropositionalFormula left, PropositionalFormula right);

    /** Visit a disjunction. */
    R visitOr(PropositionalFormula left, PropositionalFormula right);
  }

  /**
   * Dispatch to a formula visitor.
   *
   * @param visitor the visitor
   * @param <R> the visitor's result type
   * @return the result of visiting this formula
   */
  <R> R accept(Visitor<R> visitor);
}
