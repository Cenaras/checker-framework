package org.checkerframework.dataflow.nullness;

import java.net.URISyntaxException;
import org.junit.Test;

/** Cases in which the analysis must prove that the dereference is safe. */
public class DemandDrivenNullnessSafeTest extends DemandDrivenNullnessTestSupport {

  private static final String SOURCE = "DemandDrivenNullnessSafeCases.java";
  private static final String CLASS = "DemandDrivenNullnessSafeCases";

  @Test
  public void directGuard() throws URISyntaxException {
    assertSafe("directGuard", "foo");
  }

  @Test
  public void guardReturns() throws URISyntaxException {
    assertSafe("guardReturns", "foo");
  }

  @Test
  public void booleanFromNullCheck() throws URISyntaxException {
    assertSafe("booleanFromNullCheck", "toLowerCase");
  }

  @Test
  public void controlFlowState() throws URISyntaxException {
    assertSafe("controlFlowState", "isDone");
  }

  @Test
  public void assignmentOnNullPath() throws URISyntaxException {
    assertSafe("assignmentOnNullPath", "foo");
  }

  @Test
  public void localAliasAssignment() throws URISyntaxException {
    assertSafe("localAliasAssignment", "foo");
  }

  @Test
  public void localPreservedAcrossCall() throws URISyntaxException {
    assertSafe("localPreservedAcrossCall", "foo");
  }

  @Test
  public void nestedGuards() throws URISyntaxException {
    assertSafe("nestedGuards", "foo");
  }

  @Test
  public void directFieldGuard() throws URISyntaxException {
    assertSafe("directFieldGuard", "foo");
  }

  @Test
  public void fieldControlFlow() throws URISyntaxException {
    assertSafe("fieldControlFlow", "isDone");
  }

  @Test
  public void negatedDisjunction() throws URISyntaxException {
    assertSafe("negatedDisjunction", "foo");
  }

  @Test
  public void combinedConditions() throws URISyntaxException {
    assertSafe("combinedConditions", "foo");
  }

  @Test
  public void multiStepBooleanSubstitution() throws URISyntaxException {
    assertSafe("multiStepBooleanSubstitution", "foo");
  }

  @Test
  public void unconditionalSafe() throws URISyntaxException {
    assertSafe("unconditionalSafe", "foo");
  }

  @Test
  public void doubleBoolean() throws URISyntaxException {
    assertSafe("doubleBoolean", "foo");
  }

  @Test
  public void booleanAndControlFlow() throws URISyntaxException {
    assertSafe("booleanAndControlFlow", "foo");
  }

  @Test
  public void dereferenceUnreachableAfterReassignment() throws URISyntaxException {
    assertSafe("dereferenceUnreachableAfterReassignment", "foo");
  }

  @Test
  public void ternaryOperator() throws URISyntaxException {
    assertSafe("ternaryOperator", "foo");
  }

  @Test
  public void castReceiver() throws URISyntaxException {
    assertSafe("castReceiver", "foo");
  }

  @Test
  public void castAssignment() throws URISyntaxException {
    assertSafe("castAssignment", "foo");
  }

  @Test
  public void castNullLiteral() throws URISyntaxException {
    assertSafe("castNullLiteral", "foo");
  }

  @Test
  public void trueBooleanLiteral() throws URISyntaxException {
    assertSafe("trueBooleanLiteral", "foo");
  }

  @Test
  public void falseBooleanLiteral() throws URISyntaxException {
    assertSafe("falseBooleanLiteral", "foo");
  }

  @Test
  public void stringLiteralAssignment() throws URISyntaxException {
    assertSafe("stringLiteralAssignment", "length");
  }

  @Test
  public void guardedFieldAccess() throws URISyntaxException {
    assertFieldAccessResult(
        SOURCE, CLASS, "guardedFieldAccess", "value", DemandDrivenNullnessAnalysis.Result.SAFE);
  }

  @Test
  public void guardedArrayAccess() throws URISyntaxException {
    assertArrayAccessResult(
        SOURCE, CLASS, "guardedArrayAccess", DemandDrivenNullnessAnalysis.Result.SAFE);
  }

  @Test
  public void arrayCreationAssignment() throws URISyntaxException {
    assertArrayAccessResult(
        SOURCE, CLASS, "arrayCreationAssignment", DemandDrivenNullnessAnalysis.Result.SAFE);
  }

  @Test
  public void nestedFieldAlias() throws URISyntaxException {
    assertSafe("nestedFieldAlias", "foo");
  }

  @Test
  public void nestedCastReceiver() throws URISyntaxException {
    assertSafe("nestedCastReceiver", "foo");
  }

  @Test
  public void staticFieldGuard() throws URISyntaxException {
    assertSafe("staticFieldGuard", "foo");
  }

  @Test
  public void thisReceiver() throws URISyntaxException {
    assertInvocationWithoutSolver(
        SOURCE, CLASS, "thisReceiver", "instanceMethod", DemandDrivenNullnessAnalysis.Result.SAFE);
  }

  @Test
  public void emptyConditional() throws URISyntaxException {
    assertSafe("emptyConditional", "foo");
  }

  @Test
  public void unrelatedAssignmentInsideCondition() throws URISyntaxException {
    assertSafe("unrelatedAssignmentInsideCondition", "foo");
  }

  @Test
  public void unrelatedIncrementInsideCondition() throws URISyntaxException {
    assertSafe("unrelatedIncrementInsideCondition", "foo");
  }

  @Test
  public void instanceofGuard() throws URISyntaxException {
    assertSafe("instanceofGuard", "toString");
  }

  @Test
  public void instanceofPatternGuardsItsOperand() throws URISyntaxException {
    assertSafe("instanceofPatternGuardsItsOperand", "toString");
  }

  @Test
  public void instanceofFieldGuard() throws URISyntaxException {
    assertSafe("instanceofFieldGuard", "toString");
  }

  @Test
  public void negatedInstanceofGuard() throws URISyntaxException {
    assertSafe("negatedInstanceofGuard", "toString");
  }

  @Test
  public void instanceofBooleanVariable() throws URISyntaxException {
    assertSafe("instanceofBooleanVariable", "toString");
  }

  @Test
  public void nonShortCircuitConjunction() throws URISyntaxException {
    assertSafe("nonShortCircuitConjunction", "foo");
  }

  @Test
  public void nonShortCircuitConjunctionOfChecks() throws URISyntaxException {
    assertSafe("nonShortCircuitConjunctionOfChecks", "foo");
  }

  @Test
  public void negatedNonShortCircuitDisjunction() throws URISyntaxException {
    assertSafe("negatedNonShortCircuitDisjunction", "foo");
  }

  @Test
  public void chainedAssignment() throws URISyntaxException {
    assertSafe("chainedAssignment", "foo");
  }

  @Test
  public void chainedAssignmentInGuardedBranch() throws URISyntaxException {
    assertSafe("chainedAssignmentInGuardedBranch", "foo");
  }

  @Test
  public void chainedAssignmentOfNonNullLocal() throws URISyntaxException {
    assertSafe("chainedAssignmentOfNonNullLocal", "foo");
  }

  @Test
  public void assignmentAsCondition() throws URISyntaxException {
    assertSafe("assignmentAsCondition", "foo");
  }

  @Test
  public void caughtExceptionIsNonNull() throws URISyntaxException {
    assertSafe("caughtExceptionIsNonNull", "getMessage");
  }

  @Test
  public void catchRecordsTheFailure() throws URISyntaxException {
    assertSafe("catchRecordsTheFailure", "foo");
  }

  @Test
  public void reassignedCatchParameterStaysNonNull() throws URISyntaxException {
    assertSafe("reassignedCatchParameterStaysNonNull", "getMessage");
  }

  @Test
  public void multiCatchIsNonNull() throws URISyntaxException {
    assertSafe("multiCatchIsNonNull", "getMessage");
  }

  @Test
  public void caughtExceptionThroughAlias() throws URISyntaxException {
    assertSafe("caughtExceptionThroughAlias", "getMessage");
  }

  private static void assertSafe(String method, String targetMethod) throws URISyntaxException {
    assertResult(SOURCE, CLASS, method, targetMethod, DemandDrivenNullnessAnalysis.Result.SAFE);
  }
}
