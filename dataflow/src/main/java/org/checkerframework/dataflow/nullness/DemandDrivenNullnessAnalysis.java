package org.checkerframework.dataflow.nullness;

import static org.checkerframework.dataflow.logic.PropositionalFormulas.and;
import static org.checkerframework.dataflow.logic.PropositionalFormulas.atom;
import static org.checkerframework.dataflow.logic.PropositionalFormulas.falseFormula;
import static org.checkerframework.dataflow.logic.PropositionalFormulas.not;
import static org.checkerframework.dataflow.logic.PropositionalFormulas.or;
import static org.checkerframework.dataflow.logic.PropositionalFormulas.substitute;
import static org.checkerframework.dataflow.logic.PropositionalFormulas.trueFormula;

import com.sun.source.tree.Tree;
import com.sun.source.tree.VariableTree;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeKind;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.dataflow.cfg.ControlFlowGraph;
import org.checkerframework.dataflow.cfg.UnderlyingAST;
import org.checkerframework.dataflow.cfg.block.Block;
import org.checkerframework.dataflow.cfg.block.ConditionalBlock;
import org.checkerframework.dataflow.cfg.node.ArrayAccessNode;
import org.checkerframework.dataflow.cfg.node.ArrayCreationNode;
import org.checkerframework.dataflow.cfg.node.AssignmentNode;
import org.checkerframework.dataflow.cfg.node.BitwiseAndNode;
import org.checkerframework.dataflow.cfg.node.BitwiseOrNode;
import org.checkerframework.dataflow.cfg.node.BooleanLiteralNode;
import org.checkerframework.dataflow.cfg.node.ConditionalAndNode;
import org.checkerframework.dataflow.cfg.node.ConditionalNotNode;
import org.checkerframework.dataflow.cfg.node.ConditionalOrNode;
import org.checkerframework.dataflow.cfg.node.EqualToNode;
import org.checkerframework.dataflow.cfg.node.FieldAccessNode;
import org.checkerframework.dataflow.cfg.node.InstanceOfNode;
import org.checkerframework.dataflow.cfg.node.LocalVariableNode;
import org.checkerframework.dataflow.cfg.node.MethodInvocationNode;
import org.checkerframework.dataflow.cfg.node.Node;
import org.checkerframework.dataflow.cfg.node.NotEqualNode;
import org.checkerframework.dataflow.cfg.node.NullLiteralNode;
import org.checkerframework.dataflow.cfg.node.ObjectCreationNode;
import org.checkerframework.dataflow.cfg.node.ReturnNode;
import org.checkerframework.dataflow.cfg.node.StringLiteralNode;
import org.checkerframework.dataflow.cfg.node.TernaryExpressionNode;
import org.checkerframework.dataflow.cfg.node.ThisNode;
import org.checkerframework.dataflow.cfg.node.TypeCastNode;
import org.checkerframework.dataflow.cfg.node.VariableDeclarationNode;
import org.checkerframework.dataflow.logic.PropositionalFormula;
import org.checkerframework.dataflow.logic.SatSolver;
import org.checkerframework.dataflow.logic.Z3SatSolver;
import org.checkerframework.javacutil.TreeUtils;

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
  private static final SatSolver DEFAULT_SOLVER = new Z3SatSolver();

  private final SatSolver solver;

  /**
   * The block at which a backwards path leaves the method, or null when paths that reach it are
   * never justified. Only the entry block may discharge a path using {@link #entryAssumption}.
   */
  private final @Nullable Block entryBlock;

  /**
   * What is assumed to hold on entry to the method, for example a contract's precondition on a
   * parameter. {@link #TRUE} for an unconditional query, in which case a satisfiable formula that
   * reaches the entry is never discharged.
   */
  private final PropositionalFormula entryAssumption;

  private int pathSteps;
  private long freshAtomId = -1;

  private DemandDrivenNullnessAnalysis(SatSolver solver) {
    this(solver, null, TRUE);
  }

  private DemandDrivenNullnessAnalysis(
      SatSolver solver, @Nullable Block entryBlock, PropositionalFormula entryAssumption) {
    this.solver = solver;
    this.entryBlock = entryBlock;
    this.entryAssumption = entryAssumption;
  }

  /**
   * Analyze a method-invocation, field-access, or array-access dereference.
   *
   * @param cfg the CFG for the method containing {@code dereference}
   * @param dereference the node that performs the dereference
   * @return whether its base has been proven non-null
   */
  public static Result analyze(ControlFlowGraph cfg, Node dereference) {
    return analyze(cfg, dereference, DEFAULT_SOLVER);
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
    Node base = getDereferenceBase(dereference);
    if (base == null) {
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
    return analyze(cfg, dereferenceTree, DEFAULT_SOLVER);
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
      if (getDereferenceBase(node) == null) {
        continue;
      }
      if (dereference != null) {
        return Result.UNKNOWN;
      }
      dereference = node;
    }
    return dereference == null ? Result.UNKNOWN : analyze(cfg, dereference, solver);
  }

  /**
   * Analyze an explicitly supplied base expression immediately before a dereference.
   *
   * @param cfg the CFG for the containing method
   * @param dereference the node immediately after the program point being queried
   * @param base the expression whose nullness is queried
   * @return whether {@code base} has been proven non-null
   */
  public static Result analyze(ControlFlowGraph cfg, Node dereference, Node base) {
    return analyze(cfg, dereference, base, DEFAULT_SOLVER);
  }

  /**
   * Analyze the nullness of a reference at an arbitrary program point.
   *
   * @param cfg the CFG for the containing method
   * @param programPoint the node immediately after the program point being queried
   * @param reference the reference whose nullness is queried
   * @return whether {@code reference} has been proven non-null
   */
  public static Result analyzeReference(ControlFlowGraph cfg, Node programPoint, Node reference) {
    return analyzeReference(cfg, programPoint, reference, DEFAULT_SOLVER);
  }

  /** Analyze a reference at an arbitrary program point using a caller-supplied SAT backend. */
  public static Result analyzeReference(
      ControlFlowGraph cfg, Node programPoint, Node reference, SatSolver solver) {
    return analyze(cfg, programPoint, reference, solver);
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

    Reference baseReference = createSymbolReference(base);
    if (baseReference == null) {
      return Result.UNKNOWN;
    }

    Block block = dereference.getBlock();
    assert block != null : "dereference must belong to a block in cfg";
    int index = identityIndexOf(block.getNodes(), dereference);
    assert index >= 0 : "dereference must occur in its block";
    assert cfg.getAllBlocks().contains(block) : "dereference block must belong to cfg";
    if (baseReference instanceof ThisReference) {
      return Result.SAFE;
    }

    DemandDrivenNullnessAnalysis analysis = new DemandDrivenNullnessAnalysis(solver);
    // Initial assumption: reference == null.
    PropositionalFormula nullAtDereference =
        atom(new PredicateAtom(PredicateKind.IS_NULL, baseReference));
    Set<Block> path = Collections.newSetFromMap(new IdentityHashMap<>());
    // Attempt to disprove.
    boolean allPathsContradictNull =
        analysis.allPathsUnsatisfiable(block, index, nullAtDereference, path);
    return allPathsContradictNull ? Result.SAFE : Result.UNKNOWN;
  }

  /**
   * Verify a conditional return contract: does the method return a non-null value on every normal
   * return, given that one boolean parameter had a particular value on entry?
   *
   * @param cfg the CFG for the method whose contract is being verified
   * @param parameterIndex the one-based position of the boolean parameter
   * @param parameterValue the parameter value under which the guarantee is claimed
   * @return whether every normal return has been proven non-null under that precondition
   */
  public static Result analyzeReturnsNonNullIf(
      ControlFlowGraph cfg, int parameterIndex, boolean parameterValue) {
    return analyzeReturnsNonNullIf(cfg, parameterIndex, parameterValue, DEFAULT_SOLVER);
  }

  /**
   * Verify a conditional return contract using a caller-supplied SAT backend.
   *
   * @param cfg the CFG for the method whose contract is being verified
   * @param parameterIndex the one-based position of the boolean parameter
   * @param parameterValue the parameter value under which the guarantee is claimed
   * @param solver the backend used to decide path formulas
   * @return whether every normal return has been proven non-null under that precondition
   */
  public static Result analyzeReturnsNonNullIf(
      ControlFlowGraph cfg, int parameterIndex, boolean parameterValue, SatSolver solver) {
    Objects.requireNonNull(cfg);
    Objects.requireNonNull(solver);

    if (!returnsAReference(cfg)) {
      // A void method, a constructor, or a primitive-returning method has no nullness to promise.
      // This matters because CFGBuilder emits no ReturnNode for a bare `return;`, so without this
      // check a void method would look like one that never returns.
      return Result.UNKNOWN;
    }
    VariableElement parameter = booleanParameter(cfg, parameterIndex);
    if (parameter == null) {
      return Result.UNKNOWN;
    }

    PropositionalFormula parameterHolds =
        atom(new PredicateAtom(PredicateKind.IS_TRUE, new VariableReference(parameter)));
    PropositionalFormula precondition = parameterValue ? parameterHolds : not(parameterHolds);
    DemandDrivenNullnessAnalysis analysis =
        new DemandDrivenNullnessAnalysis(solver, cfg.getEntryBlock(), precondition);

    for (Node node : cfg.getAllNodes()) {
      if (!(node instanceof ReturnNode returnNode)) {
        continue;
      }
      Node result = returnNode.getResult();
      if (result == null) {
        // A bare `return;` returns no value, so there is nothing for the contract to guarantee.
        return Result.UNKNOWN;
      }
      Block block = returnNode.getBlock();
      if (block == null) {
        return Result.UNKNOWN;
      }
      int index = identityIndexOf(block.getNodes(), returnNode);
      if (index < 0) {
        return Result.UNKNOWN;
      }
      // Initial assumption: this return statement returns null.
      Set<Block> path = Collections.newSetFromMap(new IdentityHashMap<>());
      if (!analysis.allPathsUnsatisfiable(block, index, analysis.nullnessFormula(result), path)) {
        return Result.UNKNOWN;
      }
    }
    // A method with no return statement always throws, so it has no returning path to falsify.
    return Result.SAFE;
  }

  /** Returns whether the method's declared return type is one that can hold null. */
  private static boolean returnsAReference(ControlFlowGraph cfg) {
    if (!(cfg.getUnderlyingAST() instanceof UnderlyingAST.CFGMethod method)) {
      return false;
    }
    ExecutableElement element = TreeUtils.elementFromDeclaration(method.getMethod());
    if (element == null) {
      return false;
    }
    // A constructor's element also reports VOID, so this rejects constructors too.
    return switch (element.getReturnType().getKind()) {
      case DECLARED, ARRAY, TYPEVAR -> true;
      default -> false;
    };
  }

  /**
   * Returns the method's boolean parameter at a one-based index, or null if there is no such one.
   */
  private static @Nullable VariableElement booleanParameter(
      ControlFlowGraph cfg, int parameterIndex) {
    if (!(cfg.getUnderlyingAST() instanceof UnderlyingAST.CFGMethod method)) {
      return null;
    }
    List<? extends VariableTree> parameters = method.getMethod().getParameters();
    if (parameterIndex < 1 || parameterIndex > parameters.size()) {
      return null;
    }
    VariableElement element = TreeUtils.elementFromDeclaration(parameters.get(parameterIndex - 1));
    if (element == null || element.asType().getKind() != TypeKind.BOOLEAN) {
      // A boxed Boolean is deliberately rejected: it can be null, so "the parameter is true" is
      // not the negation of "the parameter is false".
      return null;
    }
    return element;
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
        // A satisfiable assumption made it to an entry (or malformed dead-end) block. Every
        // intervening assignment has already been substituted, so the formula now speaks about the
        // state on entry and a contract's precondition can discharge it. A predecessor-less block
        // that is not the entry is left unjustified rather than assumed unreachable.
        return block == entryBlock && isUnsatisfiable(and(current, entryAssumption));
      }

      for (Block predecessor : predecessors) {
        // AND the current formula with the condition required to traverse from predecessor -->
        // successor. For non-conditional blocks, this is the trivial tautology formula
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
      Reference lhsReference = createSymbolReference(assignment.getTarget());
      if (lhsReference != null) {
        return substituteAssignment(formula, lhsReference, assignment.getExpression());
      }
      // The target may still be a field reached through an unsupported receiver (for example,
      // arr[0].field). Treat an assignment whose target cannot be represented as an unmodelled
      // write: it may alias any represented field access, so retaining field facts is unsound.
      return havocFields(formula);
    }
    if (isPotentiallySideEffectingCall(node)) {
      return havocFields(formula);
    }
    VariableElement caught = caughtException(node);
    if (caught != null) {
      // A caught value is never null: `throw null` throws a NullPointerException instead. A later
      // write to the parameter was substituted away by the assignment case above.
      return assumeNonNull(formula, new VariableReference(caught));
    }
    return formula;
  }

  /** Returns the exception parameter a node binds, or null if it binds none. */
  private static @Nullable VariableElement caughtException(Node node) {
    if (!(node instanceof VariableDeclarationNode declaration)) {
      return null;
    }
    VariableElement element = TreeUtils.elementFromDeclaration(declaration.getTree());
    return element != null && element.getKind() == ElementKind.EXCEPTION_PARAMETER ? element : null;
  }

  /**
   * Discharges the hypothesis that {@code reference} is null. A field reached through it is not.
   */
  private PropositionalFormula assumeNonNull(PropositionalFormula formula, Reference reference) {
    PredicateAtom isNull = new PredicateAtom(PredicateKind.IS_NULL, reference);
    return substitute(formula, key -> isNull.equals(key) ? FALSE : atom(key));
  }

  /** Computes the condition required to traverse {@code predecessor -> successor}. */
  private PropositionalFormula edgeCondition(Block predecessor, Block successor) {
    if (!(predecessor instanceof ConditionalBlock conditional)) {
      return TRUE;
    }

    Block thenSuccessor = conditional.getThenSuccessor();
    Block elseSuccessor = conditional.getElseSuccessor();

    // CFGBuilder may leave a conditional whose two branches join immediately. No branch
    // predicate is required when both successors are the same block.
    if (thenSuccessor == elseSuccessor) {
      return TRUE;
    }
    assert thenSuccessor == successor || elseSuccessor == successor;

    // This may look odd, but the ConditionalBlock does not contain the guard itself, it is only
    // used for branching. The predecessor of the ConditionalBlock is the block containing the
    // actual guard in its last node.
    Set<Block> conditionPredecessors = conditional.getPredecessors();
    assert conditionPredecessors.size() == 1;
    Node condition = conditionPredecessors.iterator().next().getLastNode();
    assert (condition != null);

    PropositionalFormula conditionFormula = booleanFormula(condition);
    return thenSuccessor == successor ? conditionFormula : not(conditionFormula);
  }

  /** Substitutes the value assigned to {@code lhsTarget} into a backwards path formula. */
  private PropositionalFormula substituteAssignment(
      PropositionalFormula formula, Reference lhsTarget, Node expression) {
    // Construct a symbolic reference for supported RHS access paths. Other expressions remain
    // opaque, but their nullness can still be represented by an unconstrained atom.
    Reference rhsReference = createSymbolReference(expression);
    Map<Object, PropositionalFormula> replacements = new HashMap<>();

    return substitute(
        formula,
        key ->
            replacements.computeIfAbsent(
                key, unused -> replacementForAssignment(key, lhsTarget, expression, rhsReference)));
  }

  /** Returns one predicate atom's value before an assignment to {@code lhsTarget}. */
  private PropositionalFormula replacementForAssignment(
      Object atomKey,
      Reference lhsTargetReference,
      Node rhsExpression,
      @Nullable Reference rhsReference) {
    if (!(atomKey instanceof PredicateAtom predicateAtom)) {
      return atom(atomKey);
    }

    // Check if the atom in the formula represents the LHS we are assigning to
    Reference atomReference = predicateAtom.reference;
    if (atomReference.equals(lhsTargetReference)) {
      // The predicate atom states which fact we are asking about (x is null, b == true).
      // Construct the appropriate formula for the RHS: Is the variable null? Does the boolean
      // evaluate to true?
      return switch (predicateAtom.predicateKind) {
        case IS_NULL -> nullnessFormula(rhsExpression);
        case IS_TRUE -> booleanFormula(rhsExpression);
      };
    }

    // For chained dereferences: a.b.foo(). We ask Null(a.b). If we encounter var a = h, then
    // the atom reference (a.b) contains the lhs (a) of the assignment.
    if (atomReference.containsSubreference(lhsTargetReference)) {
      // If the RHS is an unknown reference (e.g., non-variable, field, or this), forget the
      // previous fact.
      if (rhsReference == null) {
        return freshAtom();
      }
      // Else it was a reference supported: Substitute to check for (local) aliasing.
      Reference rewritten = atomReference.replaceSubreferenceWith(lhsTargetReference, rhsReference);
      return atom(new PredicateAtom(predicateAtom.predicateKind, rewritten));
    }

    if (lhsTargetReference instanceof FieldReference && atomReference.containsFieldAccess()) {
      // Receiver aliasing is not tracked for arbitrary field writes.
      return freshAtom();
    }

    return atom(atomKey);
  }

  /** Invalidates all facts involving fields across a possibly side-effecting method invocation. */
  private PropositionalFormula havocFields(PropositionalFormula formula) {
    Map<Object, PropositionalFormula> replacements = new HashMap<>();
    return substitute(
        formula,
        key -> {
          if (key instanceof PredicateAtom predicateAtom
              && predicateAtom.reference.containsFieldAccess()) {
            return replacements.computeIfAbsent(key, unused -> freshAtom());
          }
          return atom(key);
        });
  }

  /** Invalidates all facts that a write inside {@code root} may have changed. */
  private PropositionalFormula havocWritesWithin(Node root, PropositionalFormula formula) {
    PropositionalFormula result = havocWrite(root, formula);
    for (Node operand : root.getTransitiveOperands()) {
      result = havocWrite(operand, result);
    }
    return result;
  }

  /** Invalidates all facts that {@code node} may have changed, if it is an assignment. */
  private PropositionalFormula havocWrite(Node node, PropositionalFormula formula) {
    if (!(node instanceof AssignmentNode assignment)) {
      return formula;
    }
    Reference target = createSymbolReference(assignment.getTarget());
    if (target == null || target instanceof FieldReference) {
      // As in replacementForAssignment: a field write, or a write whose target cannot be
      // represented, may alias any represented field access.
      return havocFields(formula);
    }
    Map<Object, PropositionalFormula> replacements = new HashMap<>();
    return substitute(
        formula,
        key -> {
          if (key instanceof PredicateAtom predicateAtom
              && predicateAtom.reference.containsSubreference(target)) {
            return replacements.computeIfAbsent(key, unused -> freshAtom());
          }
          return atom(key);
        });
  }

  /**
   * Converts a supported boolean-valued CFG node to a formula that holds where the evaluation of
   * {@code original} completes.
   *
   * <p>The operands of {@code original} are evaluated at earlier program points than that one, so a
   * write performed while evaluating a later operand may already have invalidated the fact recorded
   * for an earlier one. Every reference the expression may write is therefore replaced by an
   * unconstrained atom, exactly as a call in the expression invalidates facts about fields.
   */
  private PropositionalFormula booleanFormula(Node original) {
    PropositionalFormula formula = booleanFormulaIgnoringSideEffects(original);
    if (containsPotentiallySideEffectingCall(original)) {
      formula = havocFields(formula);
    }

    // If a condition node itself contains an assignment (b && foo(x=null)) then havoc
    return havocWritesWithin(original, formula);
  }

  /** Converts a boolean node to a formula without accounting for side effects within the node. */
  private PropositionalFormula booleanFormulaIgnoringSideEffects(Node original) {
    Node node = unwrap(original);
    if (node instanceof BooleanLiteralNode literal) {
      return literal.getValue() ? TRUE : FALSE;
    }
    Reference ref = createSymbolReference(node);
    if (ref != null) {
      return atom(new PredicateAtom(PredicateKind.IS_TRUE, ref));
    }
    if (node instanceof ConditionalNotNode conditionalNot) {
      return not(booleanFormulaIgnoringSideEffects(conditionalNot.getOperand()));
    }
    if (node instanceof ConditionalAndNode conditionalAnd) {
      return and(
          booleanFormulaIgnoringSideEffects(conditionalAnd.getLeftOperand()),
          booleanFormulaIgnoringSideEffects(conditionalAnd.getRightOperand()));
    }
    if (node instanceof ConditionalOrNode conditionalOr) {
      return or(
          booleanFormulaIgnoringSideEffects(conditionalOr.getLeftOperand()),
          booleanFormulaIgnoringSideEffects(conditionalOr.getRightOperand()));
    }
    if (node instanceof BitwiseAndNode bitwiseAnd) {
      // A boolean context makes this the non-short-circuit `&`, so both operands are boolean.
      return and(
          booleanFormulaIgnoringSideEffects(bitwiseAnd.getLeftOperand()),
          booleanFormulaIgnoringSideEffects(bitwiseAnd.getRightOperand()));
    }
    if (node instanceof BitwiseOrNode bitwiseOr) {
      // Likewise the non-short-circuit `|`.
      return or(
          booleanFormulaIgnoringSideEffects(bitwiseOr.getLeftOperand()),
          booleanFormulaIgnoringSideEffects(bitwiseOr.getRightOperand()));
    }
    if (node instanceof InstanceOfNode instanceOf) {
      // `e instanceof T` is false when e is null, whatever T is (JLS 15.20.2). The type test
      // itself stays uninterpreted, so the else branch learns nothing.
      Reference operand = createSymbolReference(instanceOf.getOperand());
      if (operand != null) {
        return and(
            not(atom(new PredicateAtom(PredicateKind.IS_NULL, operand))),
            atom(new OpaqueAtom(node.getUid())));
      }
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
    return atom(new OpaqueAtom(node.getUid()));
  }

  /** Returns whether evaluating {@code root} may invoke user code that can mutate fields. */
  private static boolean containsPotentiallySideEffectingCall(Node root) {
    if (isPotentiallySideEffectingCall(root)) {
      return true;
    }
    for (Node operand : root.getTransitiveOperands()) {
      if (isPotentiallySideEffectingCall(operand)) {
        return true;
      }
    }
    return false;
  }

  /** Returns whether evaluating {@code node} invokes arbitrary method or constructor code. */
  private static boolean isPotentiallySideEffectingCall(Node node) {
    return node instanceof MethodInvocationNode || node instanceof ObjectCreationNode;
  }

  /** Returns a null-comparison formula, or null if the operands are not a supported comparison. */
  private @Nullable PropositionalFormula nullComparison(Node left, Node right) {
    @Nullable Node comparedExpression =
        unwrap(left) instanceof NullLiteralNode
            ? right
            : unwrap(right) instanceof NullLiteralNode ? left : null;
    if (comparedExpression == null) {
      return null;
    }
    Reference reference = createSymbolReference(comparedExpression);
    return reference == null ? null : atom(new PredicateAtom(PredicateKind.IS_NULL, reference));
  }

  /** Converts the nullness of an assignment RHS to a formula. */
  private PropositionalFormula nullnessFormula(Node original) {
    Node node = unwrap(original);
    if (node instanceof NullLiteralNode) {
      return TRUE;
    }
    Reference ref = createSymbolReference(node);
    if (ref != null) {
      return atom(new PredicateAtom(PredicateKind.IS_NULL, ref));
    }
    if (node instanceof ObjectCreationNode
        || node instanceof ArrayCreationNode
        || node instanceof StringLiteralNode) {
      return FALSE;
    }
    return atom(new OpaqueAtom(node.getUid()));
  }

  /** Removes transparent CFG nodes that can surround a supported reference or expression. */
  private static Node unwrap(Node node) {
    Node current = node;
    while (true) {
      if (current instanceof TypeCastNode typeCast) {
        current = typeCast.getOperand();
      } else if (current instanceof TernaryExpressionNode ternaryExpression) {
        // CFGBuilder assigns each ternary arm to this synthetic variable. Referring to it here
        // permits the normal assignment transfer and conditional-edge handling to distinguish the
        // two arms while walking backwards.
        current = ternaryExpression.getTernaryExpressionVar();
      } else if (current instanceof AssignmentNode assignment) {
        // An assignment used as a value, as in the chain `a = b = e`, evaluates to the value it
        // assigned. Its nullness is therefore the nullness of `e`. Note that this unwraps only an
        // assignment appearing in an *expression* position: `transfer` matches AssignmentNode
        // directly, so the write itself is still modelled.
        current = assignment.getExpression();
      } else {
        return current;
      }
    }
  }

  /** Converts a supported expression node to a stable symbolic reference. */
  private static @Nullable Reference createSymbolReference(Node original) {
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
      Reference receiver = createSymbolReference(field.getReceiver());
      return receiver == null ? null : new FieldReference(receiver, field.getElement());
    }
    return null;
  }

  /** Returns the expression whose value is dereferenced, or null for unsupported dereferences. */
  private static @Nullable Node getDereferenceBase(Node dereference) {
    if (dereference instanceof MethodInvocationNode invocation) {
      return invocation.getTarget().isStatic() ? null : invocation.getTarget().getReceiver();
    }
    if (dereference instanceof FieldAccessNode fieldAccess) {
      return fieldAccess.isStatic() ? null : fieldAccess.getReceiver();
    }
    if (dereference instanceof ArrayAccessNode arrayAccess) {
      return arrayAccess.getArray();
    }
    return null;
  }

  private PropositionalFormula freshAtom() {
    return atom(new OpaqueAtom(freshAtomId--));
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

  private sealed interface Reference permits VariableReference, ThisReference, FieldReference {
    boolean containsSubreference(Reference other);

    Reference replaceSubreferenceWith(Reference from, Reference to);

    boolean containsFieldAccess();
  }

  private record VariableReference(VariableElement element) implements Reference {
    @Override
    public boolean containsSubreference(Reference other) {
      return equals(other);
    }

    @Override
    public Reference replaceSubreferenceWith(Reference from, Reference to) {
      assert equals(from);
      return to;
    }

    @Override
    public boolean containsFieldAccess() {
      return false;
    }
  }

  private enum ThisReference implements Reference {
    INSTANCE;

    @Override
    public boolean containsSubreference(Reference other) {
      return this == other;
    }

    @Override
    public Reference replaceSubreferenceWith(Reference from, Reference to) {
      assert this == from;
      return to;
    }

    @Override
    public boolean containsFieldAccess() {
      return false;
    }
  }

  private record FieldReference(@Nullable Reference receiver, VariableElement field)
      implements Reference {
    @Override
    public boolean containsSubreference(Reference other) {
      return equals(other) || (receiver != null && receiver.containsSubreference(other));
    }

    @Override
    public Reference replaceSubreferenceWith(Reference from, Reference to) {
      if (equals(from)) {
        return to;
      }
      assert receiver != null && receiver.containsSubreference(from);
      return new FieldReference(receiver.replaceSubreferenceWith(from, to), field);
    }

    @Override
    public boolean containsFieldAccess() {
      return true;
    }
  }

  private enum PredicateKind {
    IS_NULL,
    IS_TRUE
  }

  /** A Boolean atom asking whether a reference has a particular predicate. */
  private record PredicateAtom(PredicateKind predicateKind, Reference reference) {}

  /** A stable but uninterpreted atom for an expression the analysis does not model. */
  private record OpaqueAtom(long id) {}
}
