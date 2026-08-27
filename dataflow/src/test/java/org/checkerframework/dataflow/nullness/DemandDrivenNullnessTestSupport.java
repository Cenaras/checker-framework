package org.checkerframework.dataflow.nullness;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;
import org.checkerframework.dataflow.cfg.ControlFlowGraph;
import org.checkerframework.dataflow.cfg.node.ArrayAccessNode;
import org.checkerframework.dataflow.cfg.node.BooleanLiteralNode;
import org.checkerframework.dataflow.cfg.node.FieldAccessNode;
import org.checkerframework.dataflow.cfg.node.MethodInvocationNode;
import org.checkerframework.dataflow.cfg.node.Node;
import org.checkerframework.dataflow.cfg.visualize.CFGVisualizeLauncher;
import org.checkerframework.dataflow.logic.SatSolver;
import org.checkerframework.dataflow.logic.Z3SatSolver;

/** Shared assertions for demand-driven nullness tests. */
abstract class DemandDrivenNullnessTestSupport {

  /**
   * Assert that one source-level dereference has the expected analysis result through every API.
   */
  protected static void assertResult(
      String sourceFile,
      String className,
      String method,
      String targetMethod,
      DemandDrivenNullnessAnalysis.Result expected)
      throws URISyntaxException {
    ControlFlowGraph cfg = generateCfg(sourceFile, className, method);
    MethodInvocationNode target = findInvocation(cfg, method, targetMethod);
    assertResult(cfg, target, target.getTarget().getReceiver(), expected, true);
  }

  /** Assert that an invocation with an unsupported base returns unknown without consulting SAT. */
  protected static void assertUnsupportedBase(
      String sourceFile, String className, String method, String targetMethod)
      throws URISyntaxException {
    assertInvocationWithoutSolver(
        sourceFile, className, method, targetMethod, DemandDrivenNullnessAnalysis.Result.UNKNOWN);
  }

  /** Assert an invocation result that can be decided without consulting SAT. */
  protected static void assertInvocationWithoutSolver(
      String sourceFile,
      String className,
      String method,
      String targetMethod,
      DemandDrivenNullnessAnalysis.Result expected)
      throws URISyntaxException {
    ControlFlowGraph cfg = generateCfg(sourceFile, className, method);
    MethodInvocationNode target = findInvocation(cfg, method, targetMethod);
    assertResult(cfg, target, target.getTarget().getReceiver(), expected, false);
  }

  /** Assert the result for the unique instance field access with the given field name. */
  protected static void assertFieldAccessResult(
      String sourceFile,
      String className,
      String method,
      String fieldName,
      DemandDrivenNullnessAnalysis.Result expected)
      throws URISyntaxException {
    ControlFlowGraph cfg = generateCfg(sourceFile, className, method);
    FieldAccessNode target = findFieldAccess(cfg, method, fieldName);
    assertResult(cfg, target, target.getReceiver(), expected, true);
  }

  /** Assert the result for the method's unique array access. */
  protected static void assertArrayAccessResult(
      String sourceFile,
      String className,
      String method,
      DemandDrivenNullnessAnalysis.Result expected)
      throws URISyntaxException {
    ControlFlowGraph cfg = generateCfg(sourceFile, className, method);
    ArrayAccessNode target = findArrayAccess(cfg, method);
    assertResult(cfg, target, target.getArray(), expected, true);
  }

  /** Assert that a static field access is not treated as an instance dereference. */
  protected static void assertStaticFieldAccessUnsupported(
      String sourceFile, String className, String method, String fieldName)
      throws URISyntaxException {
    ControlFlowGraph cfg = generateCfg(sourceFile, className, method);
    FieldAccessNode target = findFieldAccess(cfg, method, fieldName, true);
    assertResult(
        cfg, target, target.getReceiver(), DemandDrivenNullnessAnalysis.Result.UNKNOWN, false);
  }

  /** Assert that a non-dereference CFG node is rejected without consulting SAT. */
  protected static void assertBooleanLiteralUnsupported(
      String sourceFile, String className, String method) throws URISyntaxException {
    ControlFlowGraph cfg = generateCfg(sourceFile, className, method);
    Node target = findUniqueNode(cfg, method, BooleanLiteralNode.class);
    assertEquals(
        DemandDrivenNullnessAnalysis.Result.UNKNOWN,
        DemandDrivenNullnessAnalysis.analyze(cfg, target, countingSolverThatMustNotBeCalled()));
    assertNotNull(target.getTree());
    assertEquals(
        DemandDrivenNullnessAnalysis.Result.UNKNOWN,
        DemandDrivenNullnessAnalysis.analyze(
            cfg, target.getTree(), countingSolverThatMustNotBeCalled()));
  }

  /** Assert that a source tree cannot be analyzed using an unrelated CFG. */
  protected static void assertTreeFromDifferentCfgUnsupported(
      String sourceFile,
      String className,
      String targetMethod,
      String otherMethod,
      String invocationName)
      throws URISyntaxException {
    ControlFlowGraph targetCfg = generateCfg(sourceFile, className, targetMethod);
    ControlFlowGraph otherCfg = generateCfg(sourceFile, className, otherMethod);
    MethodInvocationNode target = findInvocation(targetCfg, targetMethod, invocationName);
    assertNotNull(target.getTree());
    assertEquals(
        DemandDrivenNullnessAnalysis.Result.UNKNOWN,
        DemandDrivenNullnessAnalysis.analyze(
            otherCfg, target.getTree(), countingSolverThatMustNotBeCalled()));
  }

  private static void assertResult(
      ControlFlowGraph cfg,
      Node target,
      Node base,
      DemandDrivenNullnessAnalysis.Result expected,
      boolean expectSolverQuery) {
    assertEquals(expected, DemandDrivenNullnessAnalysis.analyze(cfg, target));
    assertEquals(expected, DemandDrivenNullnessAnalysis.analyze(cfg, target, base));

    assertNotNull(target.getTree());
    assertEquals(expected, DemandDrivenNullnessAnalysis.analyze(cfg, target.getTree()));

    AtomicInteger queryCount = new AtomicInteger();
    SatSolver delegate = new Z3SatSolver();
    SatSolver recordingSolver =
        formula -> {
          queryCount.incrementAndGet();
          return delegate.solve(formula);
        };
    assertEquals(expected, DemandDrivenNullnessAnalysis.analyze(cfg, target, recordingSolver));
    if (expectSolverQuery) {
      assertTrue("the injected SAT solver was not queried", queryCount.get() > 0);
    } else {
      assertEquals(
          "SAT should not be queried for a solver-independent result", 0, queryCount.get());
    }

    // An inconclusive SAT backend must never justify safety, but solver-independent results remain
    // unchanged.
    assertEquals(
        expectSolverQuery ? DemandDrivenNullnessAnalysis.Result.UNKNOWN : expected,
        DemandDrivenNullnessAnalysis.analyze(cfg, target, formula -> SatSolver.Result.UNKNOWN));
  }

  /** Generate and validate the CFG for one fixture method. */
  private static ControlFlowGraph generateCfg(String sourceFile, String className, String method)
      throws URISyntaxException {
    URL resource = DemandDrivenNullnessTestSupport.class.getResource(sourceFile);
    assertNotNull("missing test resource " + sourceFile, resource);
    Path source = Path.of(resource.toURI());
    ControlFlowGraph cfg =
        CFGVisualizeLauncher.generateMethodCFG(
            source.toString(), method, className, /* analysis= */ null);
    cfg.checkInvariants();
    return cfg;
  }

  /** A solver for paths that must finish before performing a SAT query. */
  private static SatSolver countingSolverThatMustNotBeCalled() {
    return formula -> {
      throw new AssertionError("SAT should not be queried");
    };
  }

  /** Find the unique invocation of {@code targetMethod} in {@code method}. */
  private static MethodInvocationNode findInvocation(
      ControlFlowGraph cfg, String method, String targetMethod) {
    MethodInvocationNode result = null;
    for (Node node : cfg.getAllNodes()) {
      if (node instanceof MethodInvocationNode invocation
          && invocation.getTarget().getMethod().getSimpleName().contentEquals(targetMethod)) {
        if (result != null) {
          throw new AssertionError("multiple calls to " + targetMethod + " in " + method);
        }
        result = invocation;
      }
    }
    assertNotNull("no call to " + targetMethod + " in " + method, result);
    return result;
  }

  /** Find the unique instance access to {@code fieldName} in {@code method}. */
  private static FieldAccessNode findFieldAccess(
      ControlFlowGraph cfg, String method, String fieldName) {
    return findFieldAccess(cfg, method, fieldName, false);
  }

  /** Find the unique access of the requested kind to {@code fieldName} in {@code method}. */
  private static FieldAccessNode findFieldAccess(
      ControlFlowGraph cfg, String method, String fieldName, boolean staticAccess) {
    FieldAccessNode result = null;
    for (Node node : cfg.getAllNodes()) {
      if (node instanceof FieldAccessNode fieldAccess
          && fieldAccess.isStatic() == staticAccess
          && fieldAccess.getElement().getSimpleName().contentEquals(fieldName)) {
        if (result != null) {
          throw new AssertionError("multiple accesses to " + fieldName + " in " + method);
        }
        result = fieldAccess;
      }
    }
    assertNotNull("no access to " + fieldName + " in " + method, result);
    return result;
  }

  /** Find the unique node of {@code nodeClass} in {@code method}. */
  private static <T extends Node> T findUniqueNode(
      ControlFlowGraph cfg, String method, Class<T> nodeClass) {
    T result = null;
    for (Node node : cfg.getAllNodes()) {
      if (nodeClass.isInstance(node)) {
        if (result != null) {
          throw new AssertionError("multiple " + nodeClass.getSimpleName() + " nodes in " + method);
        }
        result = nodeClass.cast(node);
      }
    }
    assertNotNull("no " + nodeClass.getSimpleName() + " node in " + method, result);
    return result;
  }

  /** Find the unique array access in {@code method}. */
  private static ArrayAccessNode findArrayAccess(ControlFlowGraph cfg, String method) {
    ArrayAccessNode result = null;
    for (Node node : cfg.getAllNodes()) {
      if (node instanceof ArrayAccessNode arrayAccess) {
        if (result != null) {
          throw new AssertionError("multiple array accesses in " + method);
        }
        result = arrayAccess;
      }
    }
    assertNotNull("no array access in " + method, result);
    return result;
  }
}
