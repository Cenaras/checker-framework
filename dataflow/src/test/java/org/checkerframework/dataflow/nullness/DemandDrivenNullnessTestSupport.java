package org.checkerframework.dataflow.nullness;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;
import org.checkerframework.dataflow.cfg.ControlFlowGraph;
import org.checkerframework.dataflow.cfg.node.MethodInvocationNode;
import org.checkerframework.dataflow.cfg.node.Node;
import org.checkerframework.dataflow.cfg.visualize.CFGVisualizeLauncher;
import org.checkerframework.dataflow.logic.BoundedExhaustiveSatSolver;
import org.checkerframework.dataflow.logic.SatSolver;

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
    URL resource = DemandDrivenNullnessTestSupport.class.getResource(sourceFile);
    assertNotNull("missing test resource " + sourceFile, resource);
    Path source = Path.of(resource.toURI());
    ControlFlowGraph cfg =
        CFGVisualizeLauncher.generateMethodCFG(
            source.toString(), method, className, /* analysis= */ null);
    cfg.checkInvariants();

    MethodInvocationNode target = findInvocation(cfg, method, targetMethod);
    Node base = target.getTarget().getReceiver();

    assertEquals(expected, DemandDrivenNullnessAnalysis.analyze(cfg, target));
    assertEquals(expected, DemandDrivenNullnessAnalysis.analyze(cfg, target, base));

    assertNotNull(target.getTree());
    assertEquals(expected, DemandDrivenNullnessAnalysis.analyze(cfg, target.getTree()));

    AtomicInteger queryCount = new AtomicInteger();
    SatSolver delegate = new BoundedExhaustiveSatSolver();
    SatSolver recordingSolver =
        formula -> {
          queryCount.incrementAndGet();
          return delegate.solve(formula);
        };
    assertEquals(expected, DemandDrivenNullnessAnalysis.analyze(cfg, target, recordingSolver));
    assertTrue("the injected SAT solver was not queried", queryCount.get() > 0);

    // An inconclusive SAT backend must never cause the analysis to claim safety.
    assertEquals(
        DemandDrivenNullnessAnalysis.Result.UNKNOWN,
        DemandDrivenNullnessAnalysis.analyze(cfg, target, formula -> SatSolver.Result.UNKNOWN));
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
}
