package org.checkerframework.dataflow.nullness;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

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
import org.junit.Test;

/** Tests {@link DemandDrivenNullnessAnalysis} on CFGs generated from Java source. */
public class DemandDrivenNullnessAnalysisTest {

  @Test
  public void requiredExamples() throws URISyntaxException {
    assertResult("booleanFromNullCheck", "toLowerCase", DemandDrivenNullnessAnalysis.Result.SAFE);
    assertResult("controlFlowState", "isDone", DemandDrivenNullnessAnalysis.Result.SAFE);
    assertResult("disjunction", "foo", DemandDrivenNullnessAnalysis.Result.UNKNOWN);
    assertResult("booleanDisjunction", "foo", DemandDrivenNullnessAnalysis.Result.UNKNOWN);
    assertResult("wrongBranch", "foo", DemandDrivenNullnessAnalysis.Result.UNKNOWN);
    assertResult("nonDominatingCheck", "foo", DemandDrivenNullnessAnalysis.Result.UNKNOWN);
    assertResult("reassignedToNull", "foo", DemandDrivenNullnessAnalysis.Result.UNKNOWN);
    assertResult("onlyOnePathInitializes", "foo", DemandDrivenNullnessAnalysis.Result.UNKNOWN);
    assertResult("assignmentOnBothPaths", "foo", DemandDrivenNullnessAnalysis.Result.SAFE);
    assertResult("unknownReassignment", "foo", DemandDrivenNullnessAnalysis.Result.UNKNOWN);
    assertResult("fieldControlFlow", "isDone", DemandDrivenNullnessAnalysis.Result.SAFE);
    assertResult("fieldChangedByCall", "foo", DemandDrivenNullnessAnalysis.Result.UNKNOWN);
    assertResult("fieldChangedInCondition", "foo", DemandDrivenNullnessAnalysis.Result.UNKNOWN);
    assertResult(
        "fieldChangedInBooleanAssignment", "foo", DemandDrivenNullnessAnalysis.Result.UNKNOWN);
    assertResult("fieldChangedByConstructor", "foo", DemandDrivenNullnessAnalysis.Result.UNKNOWN);
    assertResult("possiblyAliasedFieldWrite", "foo", DemandDrivenNullnessAnalysis.Result.UNKNOWN);
  }

  private static void assertResult(
      String method, String targetMethod, DemandDrivenNullnessAnalysis.Result expected)
      throws URISyntaxException {
    URL resource = DemandDrivenNullnessAnalysisTest.class.getResource("DemandDrivenCases.java");
    assertNotNull(resource);
    Path source = Path.of(resource.toURI());
    ControlFlowGraph cfg =
        CFGVisualizeLauncher.generateMethodCFG(
            source.toString(), method, "DemandDrivenCases", /* analysis= */ null);

    MethodInvocationNode target = null;
    for (Node node : cfg.getAllNodes()) {
      if (node instanceof MethodInvocationNode invocation
          && invocation.getTarget().getMethod().getSimpleName().contentEquals(targetMethod)) {
        if (target != null) {
          throw new AssertionError("multiple calls to " + targetMethod + " in " + method);
        }
        target = invocation;
      }
    }
    assertNotNull("no call to " + targetMethod + " in " + method, target);
    assertEquals(expected, DemandDrivenNullnessAnalysis.analyze(cfg, target));
    assertNotNull(target.getTree());
    assertEquals(expected, DemandDrivenNullnessAnalysis.analyze(cfg, target.getTree()));

    AtomicInteger queryCount = new AtomicInteger();
    SatSolver delegate = new BoundedExhaustiveSatSolver();
    SatSolver injectedSolver =
        formula -> {
          queryCount.incrementAndGet();
          return delegate.solve(formula);
        };
    assertEquals(expected, DemandDrivenNullnessAnalysis.analyze(cfg, target, injectedSolver));
    if (queryCount.get() == 0) {
      throw new AssertionError("the injected SAT solver was not queried");
    }
    assertEquals(
        DemandDrivenNullnessAnalysis.Result.UNKNOWN,
        DemandDrivenNullnessAnalysis.analyze(cfg, target, formula -> SatSolver.Result.UNKNOWN));
  }
}
