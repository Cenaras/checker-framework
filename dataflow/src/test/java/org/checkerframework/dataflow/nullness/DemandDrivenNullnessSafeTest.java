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





  private static void assertSafe(String method, String targetMethod) throws URISyntaxException {
    assertResult(SOURCE, CLASS, method, targetMethod, DemandDrivenNullnessAnalysis.Result.SAFE);
  }
}
