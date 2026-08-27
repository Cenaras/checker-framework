package org.checkerframework.dataflow.nullness;

import static org.checkerframework.dataflow.logic.PropositionalFormulas.and;
import static org.checkerframework.dataflow.logic.PropositionalFormulas.atom;
import static org.checkerframework.dataflow.logic.PropositionalFormulas.falseFormula;
import static org.checkerframework.dataflow.logic.PropositionalFormulas.not;
import static org.checkerframework.dataflow.logic.PropositionalFormulas.or;
import static org.checkerframework.dataflow.logic.PropositionalFormulas.substitute;
import static org.checkerframework.dataflow.logic.PropositionalFormulas.trueFormula;

import com.sun.source.tree.Tree;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import javax.lang.model.element.VariableElement;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.dataflow.cfg.ControlFlowGraph;
import org.checkerframework.dataflow.cfg.block.Block;
import org.checkerframework.dataflow.cfg.block.ConditionalBlock;
import org.checkerframework.dataflow.cfg.node.ArrayAccessNode;
import org.checkerframework.dataflow.cfg.node.ArrayCreationNode;
import org.checkerframework.dataflow.cfg.node.AssignmentNode;
import org.checkerframework.dataflow.cfg.node.BooleanLiteralNode;
import org.checkerframework.dataflow.cfg.node.ConditionalAndNode;
import org.checkerframework.dataflow.cfg.node.ConditionalNotNode;
import org.checkerframework.dataflow.cfg.node.ConditionalOrNode;
import org.checkerframework.dataflow.cfg.node.EqualToNode;
import org.checkerframework.dataflow.cfg.node.FieldAccessNode;
import org.checkerframework.dataflow.cfg.node.LocalVariableNode;
import org.checkerframework.dataflow.cfg.node.MethodInvocationNode;
import org.checkerframework.dataflow.cfg.node.NarrowingConversionNode;
import org.checkerframework.dataflow.cfg.node.Node;
import org.checkerframework.dataflow.cfg.node.NotEqualNode;
import org.checkerframework.dataflow.cfg.node.NullLiteralNode;
import org.checkerframework.dataflow.cfg.node.ObjectCreationNode;
import org.checkerframework.dataflow.cfg.node.StringLiteralNode;
import org.checkerframework.dataflow.cfg.node.ThisNode;
import org.checkerframework.dataflow.cfg.node.TypeCastNode;
import org.checkerframework.dataflow.cfg.node.WideningConversionNode;
import org.checkerframework.dataflow.logic.BoundedExhaustiveSatSolver;
import org.checkerframework.dataflow.logic.PropositionalFormula;
import org.checkerframework.dataflow.logic.SatSolver;

/**
 * A demand-driven analysis that proves that the base of one dereference is non-null.
 *
 * <p>The analysis starts immediately before the supplied dereference with the contrary assumption
 * that its base is null. It propagates that assumption backwards through the CFG, adding the
 * condition associated with every conditional edge and substituting simple assignments. The
 * dereference is {@link Result#SAFE} exactly when the resulting formula is unsatisfiable on every
 * path to the method entry.
 *
 * <p>This analysis is intentionally conservative. In particular, loops and expressions other than
 * local variables, parameters, and fields as dereference bases produce {@link Result#UNKNOWN}.
 * Unknown boolean and reference-valued expressions are represented by unconstrained atoms. Method
 * and constructor calls invalidate facts about fields, because a call may mutate them. Overloads
 * that accept a {@link SatSolver} permit a different SAT backend to be used without changing the
 * analysis.
 */
public final class DemandDrivenNullnessAnalysis {

  /** The possible results of the analysis. */
  public enum Result {
    /** The base expression has been proven non-null whenever the dereference is reached. */
    SAFE,

    /** Non-nullness could not be proven. */
    UNKNOWN
  }

  /** A limit that prevents an unexpectedly large CFG from causing path explosion. */
  private static final int MAX_PATH_STEPS = 10_000;

  private static final PropositionalFormula TRUE = trueFormula();
  private static final PropositionalFormula FALSE = falseFormula();

  private final SatSolver solver;
  private int pathSteps;
  private long freshAtomId = -1;

  private DemandDrivenNullnessAnalysis(SatSolver solver) {
    this.solver = solver;
  }

  /**
   * Analyze a method-invocation, field-access, or array-access dereference.
   *
   * @param cfg the CFG for the method containing {@code dereference}
   * @param dereference the node that performs the dereference
   * @return whether its base has been proven non-null
   */
  public static Result analyze(ControlFlowGraph cfg, Node dereference) {
    return analyze(cfg, dereference, new BoundedExhaustiveSatSolver());
  }

  /**
   * Analyze a dereference using a caller-supplied SAT backend.
   *
   * @param cfg the CFG for the method containing {@code dereference}
   * @param dereference the node that performs the dereference
   * @param solver the backend used to decide path formulas
   * @return whether its base has been proven non-null
   */
  public static Result analyze(ControlFlowGraph cfg, Node dereference, SatSolver solver) {
    Objects.requireNonNull(solver);
    Node base;
    if (dereference instanceof MethodInvocationNode invocation) {
      if (invocation.getTarget().isStatic()) {
        return Result.UNKNOWN;
      }
      base = invocation.getTarget().getReceiver();
    } else if (dereference instanceof FieldAccessNode fieldAccess) {
      if (fieldAccess.isStatic()) {
        return Result.UNKNOWN;
      }
      base = fieldAccess.getReceiver();
    } else if (dereference instanceof ArrayAccessNode arrayAccess) {
      base = arrayAccess.getArray();
    } else {
      return Result.UNKNOWN;
    }
    return analyze(cfg, dereference, base, solver);
  }

  /**
   * Analyze a dereference identified by its source tree.
   *
   * <p>This convenience overload returns {@link Result#UNKNOWN} when the tree has no unique
   * corresponding dereference node.
   *
   * @param cfg the CFG for the method containing {@code dereferenceTree}
   * @param dereferenceTree the tree that performs the dereference
   * @return whether its base has been proven non-null
   */
  public static Result analyze(ControlFlowGraph cfg, Tree dereferenceTree) {
    return analyze(cfg, dereferenceTree, new BoundedExhaustiveSatSolver());
  }

  /**
   * Analyze a source-tree dereference using a caller-supplied SAT backend.
   *
   * @param cfg the CFG for the method containing {@code dereferenceTree}
   * @param dereferenceTree the tree that performs the dereference
   * @param solver the backend used to decide path formulas
   * @return whether its base has been proven non-null
   */
  public static Result analyze(ControlFlowGraph cfg, Tree dereferenceTree, SatSolver solver) {
    Objects.requireNonNull(solver);
    Set<Node> nodes = cfg.getNodesCorrespondingToTree(dereferenceTree);
    if (nodes == null) {
      return Result.UNKNOWN;
    }
    Node dereference = null;
    for (Node node : nodes) {
      if (node instanceof MethodInvocationNode
          || node instanceof FieldAccessNode
          || node instanceof ArrayAccessNode) {
        if (dereference != null) {
          return Result.UNKNOWN;
        }
        dereference = node;
      }
    }
    return dereference == null ? Result.UNKNOWN : analyze(cfg, dereference, solver);
  }

  /**
   * Analyze an explicitly supplied base expression immediately before a dereference.
   *
   * <p>This overload is useful for dereference kinds not recognized by {@link
   * #analyze(ControlFlowGraph, Node)}. The dereference node must occur in {@code cfg}; the base
   * must be a local variable, parameter, or field.
   *
   * @param cfg the CFG for the containing method
   * @param dereference the node immediately after the program point being queried
   * @param base the expression whose nullness is queried
   * @return whether {@code base} has been proven non-null
   */
  public static Result analyze(ControlFlowGraph cfg, Node dereference, Node base) {
    return analyze(cfg, dereference, base, new BoundedExhaustiveSatSolver());
  }

  /**
   * Analyze an explicitly supplied base expression using a caller-supplied SAT backend.
   *
   * @param cfg the CFG for the containing method
   * @param dereference the node immediately after the program point being queried
   * @param base the expression whose nullness is queried
   * @param solver the backend used to decide path formulas
   * @return whether {@code base} has been proven non-null
   */
  public static Result analyze(
      ControlFlowGraph cfg, Node dereference, Node base, SatSolver solver) {
    Objects.requireNonNull(cfg);
    Objects.requireNonNull(dereference);
    Objects.requireNonNull(base);
    Objects.requireNonNull(solver);

    Reference reference = reference(base);
    if (reference == null || dereference.getBlock() == null) {
      return Result.UNKNOWN;
    }
    Block block = dereference.getBlock();
    int index = identityIndexOf(block.getNodes(), dereference);
    if (index < 0 || !cfg.getAllBlocks().contains(block)) {
      return Result.UNKNOWN;
    }
    if (reference instanceof ThisReference) {
      return Result.SAFE;
    }

    DemandDrivenNullnessAnalysis analysis = new DemandDrivenNullnessAnalysis(solver);
    // Initial assumption: reference == null.
    PropositionalFormula nullAtDereference = atom(new PropertyKey(Property.NULL, reference));
    Set<Block> path = Collections.newSetFromMap(new IdentityHashMap<>());
    // Attempt to disprove.
    boolean allPathsContradictNull =
        analysis.allPathsUnsatisfiable(block, index, nullAtDereference, path);
    return allPathsContradictNull ? Result.SAFE : Result.UNKNOWN;
  }

  /** Returns true only if all backward paths from this program point are unsatisfiable. */
  private boolean allPathsUnsatisfiable(
      Block block, int nodesBeforePoint, PropositionalFormula formula, Set<Block> path) {
    if (++pathSteps > MAX_PATH_STEPS) {
      return false;
    }
    if (!path.add(block)) {
      // A repeated block denotes a loop. The initial analysis does not model loop iterations.
      return false;
    }

    try {
      List<Node> nodes = block.getNodes();
      PropositionalFormula current = formula;
      for (int i = nodesBeforePoint - 1; i >= 0; --i) {
        current = transfer(nodes.get(i), current);
        if (isUnsatisfiable(current)) {
          return true;
        }
      }

      Set<Block> predecessors = block.getPredecessors();
      if (predecessors.isEmpty()) {
        // A satisfiable assumption made it to an entry (or malformed dead-end) block.
        return false;
      }

      for (Block predecessor : predecessors) {
        PropositionalFormula predecessorFormula = and(current, edgeCondition(predecessor, block));
        if (isUnsatisfiable(predecessorFormula)) {
          // This edge contradicts the null hypothesis; all other incoming edges still matter.
          continue;
        }
        if (!allPathsUnsatisfiable(
            predecessor, predecessor.getNodes().size(), predecessorFormula, path)) {
          // This predecessor has a path on which null remains possible or cannot be ruled out.
          return false;
        }
      }
      return true;
    } finally {
      path.remove(block);
    }
  }

  /** Applies the backwards transfer for a node. */
  private PropositionalFormula transfer(Node node, PropositionalFormula formula) {
    if (node instanceof AssignmentNode assignment) {
      Reference target = reference(assignment.getTarget());
      if (target != null) {
        return substituteAssignment(formula, target, assignment.getExpression());
      }
    }
    if (isPotentiallySideEffectingCall(node)) {
      return havocFields(formula);
    }
    return formula;
  }

  /** Computes the condition required to traverse {@code predecessor -> successor}. */
  private PropositionalFormula edgeCondition(Block predecessor, Block successor) {
    if (!(predecessor instanceof ConditionalBlock conditional)) {
      return TRUE;
    }

    boolean thenEdge = conditional.getThenSuccessor() == successor;
    boolean elseEdge = conditional.getElseSuccessor() == successor;
    if (thenEdge == elseEdge) {
      // Either both arms have been collapsed to one successor or this is not a recognized edge.
      return TRUE;
    }

    Node condition = null;
    for (Block conditionPredecessor : conditional.getPredecessors()) {
      Node candidate = conditionPredecessor.getLastNode();
      if (candidate == null) {
        continue;
      }
      if (condition != null && condition != candidate) {
        // This does not occur in CFGs produced by CFGBuilder.  Dropping the condition is a safe
        // over-approximation for a custom CFG.
        return TRUE;
      }
      condition = candidate;
    }
    if (condition == null) {
      return TRUE;
    }

    PropositionalFormula result = booleanFormula(condition);
    return thenEdge ? result : not(result);
  }

  /** Substitutes the value assigned to {@code target} into a backwards path formula. */
  private PropositionalFormula substituteAssignment(
      PropositionalFormula formula, Reference target, Node expression) {
    Reference rhsReference = reference(expression);
    PropositionalFormula rhsNullness = nullnessFormula(expression);
    PropositionalFormula rhsBoolean = booleanFormula(expression);
    Map<Object, PropositionalFormula> replacements = new HashMap<>();

    return substitute(
        formula,
        key -> {
          PropositionalFormula prior = replacements.get(key);
          if (prior != null) {
            return prior;
          }
          PropositionalFormula replacement = null;
          if (key instanceof PropertyKey propertyKey) {
            Reference referenced = propertyKey.reference;
            if (referenced.equals(target)) {
              replacement = propertyKey.property == Property.NULL ? rhsNullness : rhsBoolean;
            } else if (referenced.contains(target)) {
              if (rhsReference == null) {
                replacement = freshAtom();
              } else {
                Reference rewritten = referenced.replace(target, rhsReference);
                replacement = atom(new PropertyKey(propertyKey.property, rewritten));
              }
            } else if (target instanceof FieldReference && referenced.hasField()) {
              // Receiver aliasing is not tracked for arbitrary field writes.
              replacement = freshAtom();
            }
          }
          if (replacement == null) {
            replacement = atom(key);
          }
          replacements.put(key, replacement);
          return replacement;
        });
  }

  /** Invalidates all facts involving fields across a possibly side-effecting method invocation. */
  private PropositionalFormula havocFields(PropositionalFormula formula) {
    Map<Object, PropositionalFormula> replacements = new HashMap<>();
    return substitute(
        formula,
        key -> {
          if (key instanceof PropertyKey propertyKey && propertyKey.reference.hasField()) {
            return replacements.computeIfAbsent(key, unused -> freshAtom());
          }
          return atom(key);
        });
  }

  /** Converts a supported boolean-valued CFG node to a formula. */
  private PropositionalFormula booleanFormula(Node original) {
    PropositionalFormula formula = booleanFormulaIgnoringCallSideEffects(original);
    return containsPotentiallySideEffectingCall(original) ? havocFields(formula) : formula;
  }

  /** Converts a boolean node to a formula without accounting for effects of calls in the node. */
  private PropositionalFormula booleanFormulaIgnoringCallSideEffects(Node original) {
    Node node = unwrap(original);
    if (node instanceof BooleanLiteralNode literal) {
      return literal.getValue() ? TRUE : FALSE;
    }
    Reference ref = reference(node);
    if (ref != null) {
      return atom(new PropertyKey(Property.BOOLEAN, ref));
    }
    if (node instanceof ConditionalNotNode conditionalNot) {
      return not(booleanFormulaIgnoringCallSideEffects(conditionalNot.getOperand()));
    }
    if (node instanceof ConditionalAndNode conditionalAnd) {
      return and(
          booleanFormulaIgnoringCallSideEffects(conditionalAnd.getLeftOperand()),
          booleanFormulaIgnoringCallSideEffects(conditionalAnd.getRightOperand()));
    }
    if (node instanceof ConditionalOrNode conditionalOr) {
      return or(
          booleanFormulaIgnoringCallSideEffects(conditionalOr.getLeftOperand()),
          booleanFormulaIgnoringCallSideEffects(conditionalOr.getRightOperand()));
    }
    if (node instanceof EqualToNode equalTo) {
      PropositionalFormula nullComparison =
          nullComparison(equalTo.getLeftOperand(), equalTo.getRightOperand());
      if (nullComparison != null) {
        return nullComparison;
      }
    }
    if (node instanceof NotEqualNode notEqual) {
      PropositionalFormula nullComparison =
          nullComparison(notEqual.getLeftOperand(), notEqual.getRightOperand());
      if (nullComparison != null) {
        return not(nullComparison);
      }
    }
    return atom(new OpaqueKey(node.getUid()));
  }

  /** Returns whether evaluating {@code root} may invoke user code that can mutate fields. */
  private static boolean containsPotentiallySideEffectingCall(Node root) {
    Set<Node> visited = Collections.newSetFromMap(new IdentityHashMap<>());
    Deque<Node> worklist = new ArrayDeque<>();
    worklist.add(root);
    while (!worklist.isEmpty()) {
      Node node = worklist.removeLast();
      if (!visited.add(node)) {
        continue;
      }
      if (isPotentiallySideEffectingCall(node)) {
        return true;
      }
      worklist.addAll(node.getOperands());
    }
    return false;
  }

  /** Returns whether evaluating {@code node} invokes arbitrary method or constructor code. */
  private static boolean isPotentiallySideEffectingCall(Node node) {
    return node instanceof MethodInvocationNode || node instanceof ObjectCreationNode;
  }

  /** Returns a null-comparison formula, or null if the operands are not a supported comparison. */
  private @Nullable PropositionalFormula nullComparison(Node left, Node right) {
    if (unwrap(left) instanceof NullLiteralNode) {
      Reference ref = reference(right);
      return ref == null ? null : atom(new PropertyKey(Property.NULL, ref));
    }
    if (unwrap(right) instanceof NullLiteralNode) {
      Reference ref = reference(left);
      return ref == null ? null : atom(new PropertyKey(Property.NULL, ref));
    }
    return null;
  }

  /** Converts the nullness of an assignment RHS to a formula. */
  private PropositionalFormula nullnessFormula(Node original) {
    Node node = unwrap(original);
    if (node instanceof NullLiteralNode) {
      return TRUE;
    }
    Reference ref = reference(node);
    if (ref != null) {
      return atom(new PropertyKey(Property.NULL, ref));
    }
    if (node instanceof ObjectCreationNode
        || node instanceof ArrayCreationNode
        || node instanceof StringLiteralNode) {
      return FALSE;
    }
    return atom(new OpaqueKey(node.getUid()));
  }

  /** Removes value-preserving conversions that can surround a supported expression. */
  private static Node unwrap(Node node) {
    Node current = node;
    while (true) {
      if (current instanceof TypeCastNode typeCast) {
        current = typeCast.getOperand();
      } else if (current instanceof WideningConversionNode widening) {
        current = widening.getOperand();
      } else if (current instanceof NarrowingConversionNode narrowing) {
        current = narrowing.getOperand();
      } else {
        return current;
      }
    }
  }

  /** Converts a supported expression node to a stable symbolic reference. */
  private static @Nullable Reference reference(Node original) {
    Node node = unwrap(original);
    if (node instanceof LocalVariableNode local) {
      return new VariableReference(local.getElement());
    }
    if (node instanceof ThisNode) {
      return ThisReference.INSTANCE;
    }
    if (node instanceof FieldAccessNode field) {
      if (field.isStatic()) {
        return new FieldReference(null, field.getElement());
      }
      Reference receiver = reference(field.getReceiver());
      return receiver == null ? null : new FieldReference(receiver, field.getElement());
    }
    return null;
  }

  private PropositionalFormula freshAtom() {
    return atom(new OpaqueKey(freshAtomId--));
  }

  private static int identityIndexOf(List<Node> nodes, Node sought) {
    for (int i = 0; i < nodes.size(); ++i) {
      if (nodes.get(i) == sought) {
        return i;
      }
    }
    return -1;
  }

  /** Returns true only when the configured SAT backend proves the formula unsatisfiable. */
  private boolean isUnsatisfiable(PropositionalFormula formula) {
    return solver.solve(formula) == SatSolver.Result.UNSATISFIABLE;
  }

  private interface Reference {
    boolean contains(Reference other);

    Reference replace(Reference from, Reference to);

    boolean hasField();
  }

  private record VariableReference(VariableElement element) implements Reference {
    @Override
    public boolean contains(Reference other) {
      return equals(other);
    }

    @Override
    public Reference replace(Reference from, Reference to) {
      return equals(from) ? to : this;
    }

    @Override
    public boolean hasField() {
      return false;
    }
  }

  private enum ThisReference implements Reference {
    INSTANCE;

    @Override
    public boolean contains(Reference other) {
      return this == other;
    }

    @Override
    public Reference replace(Reference from, Reference to) {
      return this == from ? to : this;
    }

    @Override
    public boolean hasField() {
      return false;
    }
  }

  private record FieldReference(@Nullable Reference receiver, VariableElement field)
      implements Reference {
    @Override
    public boolean contains(Reference other) {
      return equals(other) || (receiver != null && receiver.contains(other));
    }

    @Override
    public Reference replace(Reference from, Reference to) {
      if (equals(from)) {
        return to;
      }
      return receiver == null ? this : new FieldReference(receiver.replace(from, to), field);
    }

    @Override
    public boolean hasField() {
      return true;
    }
  }

  private enum Property {
    NULL,
    BOOLEAN
  }

  private record PropertyKey(Property property, Reference reference) {}

  private record OpaqueKey(long id) {}
}
