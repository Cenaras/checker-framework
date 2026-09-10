package org.checkerframework.dataflow.nullness;

import java.net.URISyntaxException;
import org.junit.Test;

/** Cases in which null remains possible or the supported analysis cannot prove otherwise. */
public class DemandDrivenNullnessUnknownTest extends DemandDrivenNullnessTestSupport {

  private static final String SOURCE = "DemandDrivenNullnessUnknownCases.java";
  private static final String CLASS = "DemandDrivenNullnessUnknownCases";

  @Test
  public void unguardedDereference() throws URISyntaxException {
    assertUnknown("unguardedDereference", "foo");
  }

  @Test
  public void disjunction() throws URISyntaxException {
    assertUnknown("disjunction", "foo");
  }

  @Test
  public void booleanDisjunction() throws URISyntaxException {
    assertUnknown("booleanDisjunction", "foo");
  }

  @Test
  public void wrongBranch() throws URISyntaxException {
    assertUnknown("wrongBranch", "foo");
  }

  @Test
  public void nonDominatingCheck() throws URISyntaxException {
    assertUnknown("nonDominatingCheck", "foo");
  }

  @Test
  public void unrelatedCheck() throws URISyntaxException {
    assertUnknown("unrelatedCheck", "foo");
  }

  @Test
  public void reassignedToNull() throws URISyntaxException {
    assertUnknown("reassignedToNull", "foo");
  }

  @Test
  public void staleBooleanAfterReassignment() throws URISyntaxException {
    assertUnknown("staleBooleanAfterReassignment", "foo");
  }

  @Test
  public void onlyOnePathInitializes() throws URISyntaxException {
    assertUnknown("onlyOnePathInitializes", "foo");
  }

  @Test
  public void ternaryOperatorWrongArm() throws URISyntaxException {
    assertUnknown("ternaryOperatorWrongArm", "foo");
  }

  @Test
  public void unknownReassignment() throws URISyntaxException {
    assertUnknown("unknownReassignment", "foo");
  }

  @Test
  public void catchParameterAssignedNull() throws URISyntaxException {
    assertUnknown("catchParameterAssignedNull", "getMessage");
  }

  @Test
  public void fieldOfCaughtExceptionIsUnknown() throws URISyntaxException {
    assertUnknown("fieldOfCaughtExceptionIsUnknown", "getMessage");
  }

  @Test
  public void loopIsUnsupported() throws URISyntaxException {
    assertUnknown("loopIsUnsupported", "foo");
  }

  @Test
  public void counterGuardedLoopIsUnsupported() throws URISyntaxException {
    assertUnknown("counterGuardedLoopIsUnsupported", "foo");
  }

  @Test
  public void alwaysNullingLoopIsUnsupported() throws URISyntaxException {
    assertUnknown("alwaysNullingLoopIsUnsupported", "foo");
  }

  @Test
  public void callGuardedLoopIsUnsupported() throws URISyntaxException {
    assertUnknown("callGuardedLoopIsUnsupported", "foo");
  }

  @Test
  public void fieldChangedByCall() throws URISyntaxException {
    assertUnknown("fieldChangedByCall", "foo");
  }

  @Test
  public void fieldChangedInCondition() throws URISyntaxException {
    assertUnknown("fieldChangedInCondition", "foo");
  }

  @Test
  public void fieldChangedInBooleanAssignment() throws URISyntaxException {
    assertUnknown("fieldChangedInBooleanAssignment", "foo");
  }

  @Test
  public void fieldChangedByConstructor() throws URISyntaxException {
    assertUnknown("fieldChangedByConstructor", "foo");
  }

  @Test
  public void possiblyAliasedFieldWrite() throws URISyntaxException {
    assertUnknown("possiblyAliasedFieldWrite", "foo");
  }

  @Test
  public void possiblyAliasedIntermediateFieldWrite() throws URISyntaxException {
    assertUnknown("possiblyAliasedIntermediateFieldWrite", "foo");
  }

  @Test
  public void conjunctionFailure() throws URISyntaxException {
    assertUnknown("conjunctionFailure", "foo");
  }

  @Test
  public void negatedConjunction() throws URISyntaxException {
    assertUnknown("negatedConjunction", "foo");
  }

  @Test
  public void splitConjunction() throws URISyntaxException {
    assertUnknown("splitConjunction", "foo");
  }

  @Test
  public void assignmentOverwrite() throws URISyntaxException {
    assertUnknown("assignmentOverwrite", "foo");
  }

  @Test
  public void validationInOnlyOneBranch() throws URISyntaxException {
    assertUnknown("validationInOnlyOneBranch", "foo");
  }

  @Test
  public void unrelatedFinalCheck() throws URISyntaxException {
    assertUnknown("unrelatedFinalCheck", "foo");
  }

  @Test
  public void fieldWriteThroughUnmodelledReceiver() throws URISyntaxException {
    assertUnknown("fieldWriteThroughUnmodelledReceiver", "foo");
  }

  @Test
  public void assignmentInsideCondition() throws URISyntaxException {
    assertUnknown("assignmentInsideCondition", "foo");
  }

  @Test
  public void assignmentInsideConditionWithoutCall() throws URISyntaxException {
    assertUnknown("assignmentInsideConditionWithoutCall", "foo");
  }

  @Test
  public void fieldAssignmentInsideCondition() throws URISyntaxException {
    assertUnknown("fieldAssignmentInsideCondition", "foo");
  }

  @Test
  public void assignmentInsideBooleanVariableCondition() throws URISyntaxException {
    assertUnknown("assignmentInsideBooleanVariableCondition", "foo");
  }

  @Test
  public void instanceofWrongBranch() throws URISyntaxException {
    assertUnknown("instanceofWrongBranch", "toString");
  }

  @Test
  public void instanceofUnrelatedOperand() throws URISyntaxException {
    assertUnknown("instanceofUnrelatedOperand", "toString");
  }

  @Test
  public void instanceofPatternBinding() throws URISyntaxException {
    assertUnknown("instanceofPatternBinding", "foo");
  }

  @Test
  public void nonShortCircuitDisjunction() throws URISyntaxException {
    assertUnknown("nonShortCircuitDisjunction", "foo");
  }

  @Test
  public void exclusiveOr() throws URISyntaxException {
    assertUnknown("exclusiveOr", "foo");
  }

  @Test
  public void chainedAssignmentOfNullableValue() throws URISyntaxException {
    assertUnknown("chainedAssignmentOfNullableValue", "foo");
  }

  @Test
  public void chainedAssignmentOfNull() throws URISyntaxException {
    assertUnknown("chainedAssignmentOfNull", "foo");
  }

  @Test
  public void unsupportedEquality() throws URISyntaxException {
    assertUnknown("unsupportedEquality", "foo");
  }

  @Test
  public void unsupportedDereferenceBase() throws URISyntaxException {
    assertUnsupportedBase(SOURCE, CLASS, "unsupportedDereferenceBase", "foo");
  }

  @Test
  public void unsupportedFieldReceiver() throws URISyntaxException {
    assertUnsupportedBase(SOURCE, CLASS, "unsupportedFieldReceiver", "foo");
  }

  @Test
  public void nestedFieldUnknownReassignment() throws URISyntaxException {
    assertUnknown("nestedFieldUnknownReassignment", "foo");
  }

  @Test
  public void staticInvocationIsNotDereference() throws URISyntaxException {
    assertUnsupportedBase(SOURCE, CLASS, "staticInvocationIsNotDereference", "arbitraryCall");
  }

  @Test
  public void staticFieldAccessIsNotDereference() throws URISyntaxException {
    assertStaticFieldAccessUnsupported(
        SOURCE, CLASS, "staticFieldAccessIsNotDereference", "staticField");
  }

  @Test
  public void booleanLiteralIsNotDereference() throws URISyntaxException {
    assertBooleanLiteralUnsupported(SOURCE, CLASS, "booleanLiteralIsNotDereference");
  }

  @Test
  public void treeFromDifferentCfgIsUnsupported() throws URISyntaxException {
    assertTreeFromDifferentCfgUnsupported(
        SOURCE, CLASS, "unguardedDereference", "disjunction", "foo");
  }

  @Test
  public void unsupportedNullComparisonLeft() throws URISyntaxException {
    assertUnknown("unsupportedNullComparisonLeft", "foo");
  }

  @Test
  public void unsupportedNullComparisonRight() throws URISyntaxException {
    assertUnknown("unsupportedNullComparisonRight", "foo");
  }

  private static void assertUnknown(String method, String targetMethod) throws URISyntaxException {
    assertResult(SOURCE, CLASS, method, targetMethod, DemandDrivenNullnessAnalysis.Result.UNKNOWN);
  }
}
