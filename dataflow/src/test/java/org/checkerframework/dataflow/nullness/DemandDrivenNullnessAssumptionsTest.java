package org.checkerframework.dataflow.nullness;

import static org.checkerframework.dataflow.nullness.DemandDrivenNullnessAnalysis.Assumptions.NONE;
import static org.checkerframework.dataflow.nullness.DemandDrivenNullnessAnalysis.Assumptions.PURE_CALLS;
import static org.checkerframework.dataflow.nullness.DemandDrivenNullnessAnalysis.Result.SAFE;
import static org.checkerframework.dataflow.nullness.DemandDrivenNullnessAnalysis.Result.UNKNOWN;
import static org.junit.Assert.assertEquals;

import java.net.URISyntaxException;
import java.util.Set;
import org.checkerframework.dataflow.cfg.ControlFlowGraph;
import org.checkerframework.dataflow.cfg.node.MethodInvocationNode;
import org.junit.Test;

/** What assuming that calls are side-effect-free does, and what it deliberately does not do. */
public class DemandDrivenNullnessAssumptionsTest extends DemandDrivenNullnessTestSupport {

  private static final String SOURCE = "DemandDrivenNullnessAssumptionCases.java";
  private static final String CLASS = "DemandDrivenNullnessAssumptionCases";

  /** Claims that both overloads of the fixture's `trusted` never return null. */
  private static final DemandDrivenNullnessAnalysis.Assumptions TRUSTED_RETURNS =
      NONE.withNonNullReturns(Set.of(CLASS + "#trusted"));

  @Test
  public void callBetweenGuardAndDereference() throws URISyntaxException {
    assertDereference("callBetweenGuardAndDereference", UNKNOWN, SAFE, UNKNOWN);
  }

  @Test
  public void callBetweenGuardAndNestedDereference() throws URISyntaxException {
    assertDereference("callBetweenGuardAndNestedDereference", UNKNOWN, SAFE, UNKNOWN);
  }

  @Test
  public void localIsUnaffectedByCalls() throws URISyntaxException {
    assertDereference("localIsUnaffectedByCalls", SAFE, SAFE, SAFE);
  }

  @Test
  public void callResultStaysNullable() throws URISyntaxException {
    assertDereference("callResultStaysNullable", UNKNOWN, UNKNOWN, UNKNOWN);
  }

  @Test
  public void repeatedCallIsNotStable() throws URISyntaxException {
    assertDereference("repeatedCallIsNotStable", UNKNOWN, UNKNOWN, UNKNOWN);
  }

  @Test
  public void writeInsideArgumentsStillApplies() throws URISyntaxException {
    assertDereference("writeInsideArgumentsStillApplies", UNKNOWN, UNKNOWN, UNKNOWN);
  }

  @Test
  public void returnContractHonoursTheAssumption() throws URISyntaxException {
    ControlFlowGraph cfg = generateCfg(SOURCE, CLASS, "returnsFieldAfterCall");
    assertEquals(
        "without assumptions",
        UNKNOWN,
        DemandDrivenNullnessAnalysis.analyzeReturnsNonNullIf(cfg, 1, true, NONE));
    assertEquals(
        "assuming calls are side-effect-free",
        SAFE,
        DemandDrivenNullnessAnalysis.analyzeReturnsNonNullIf(cfg, 1, true, PURE_CALLS));
  }

  @Test
  public void assumedCallResultIsNonNull() throws URISyntaxException {
    assertDereference("assumedCallResultIsNonNull", UNKNOWN, UNKNOWN, SAFE);
  }

  @Test
  public void assumedCallDereferencedDirectly() throws URISyntaxException {
    assertDereference("assumedCallDereferencedDirectly", UNKNOWN, UNKNOWN, SAFE);
  }

  @Test
  public void unlistedCallStaysNullable() throws URISyntaxException {
    assertDereference("unlistedCallStaysNullable", UNKNOWN, UNKNOWN, UNKNOWN);
  }

  @Test
  public void sameNameInAnotherOwnerIsUnaffected() throws URISyntaxException {
    assertDereference("sameNameInAnotherOwnerIsUnaffected", UNKNOWN, UNKNOWN, UNKNOWN);
  }

  @Test
  public void assumedReturnDoesNotImplyPurity() throws URISyntaxException {
    assertDereference("assumedReturnDoesNotImplyPurity", UNKNOWN, SAFE, UNKNOWN);
  }

  @Test
  public void returnContractHonoursTheReturnClaim() throws URISyntaxException {
    ControlFlowGraph cfg = generateCfg(SOURCE, CLASS, "returnsTrustedCall");
    assertEquals(
        "without assumptions",
        UNKNOWN,
        DemandDrivenNullnessAnalysis.analyzeReturnsNonNullIf(cfg, 1, true, NONE));
    assertEquals(
        "assuming the claimed return",
        SAFE,
        DemandDrivenNullnessAnalysis.analyzeReturnsNonNullIf(cfg, 1, true, TRUSTED_RETURNS));
  }

  /** Assert the result for the method's `foo()` dereference under each assumption set. */
  private static void assertDereference(
      String method,
      DemandDrivenNullnessAnalysis.Result withoutAssumptions,
      DemandDrivenNullnessAnalysis.Result assumingPureCalls,
      DemandDrivenNullnessAnalysis.Result assumingTrustedReturns)
      throws URISyntaxException {
    ControlFlowGraph cfg = generateCfg(SOURCE, CLASS, method);
    MethodInvocationNode target = findInvocation(cfg, method, "foo");
    assertEquals(
        "without assumptions",
        withoutAssumptions,
        DemandDrivenNullnessAnalysis.analyzeReference(
            cfg, target, target.getTarget().getReceiver(), NONE));
    assertEquals(
        "assuming calls are side-effect-free",
        assumingPureCalls,
        DemandDrivenNullnessAnalysis.analyzeReference(
            cfg, target, target.getTarget().getReceiver(), PURE_CALLS));
    assertEquals(
        "assuming the claimed returns",
        assumingTrustedReturns,
        DemandDrivenNullnessAnalysis.analyzeReference(
            cfg, target, target.getTarget().getReceiver(), TRUSTED_RETURNS));
    // The default overloads must keep assuming nothing.
    assertEquals(
        "default overload",
        withoutAssumptions,
        DemandDrivenNullnessAnalysis.analyze(cfg, target, target.getTarget().getReceiver()));
  }
}
