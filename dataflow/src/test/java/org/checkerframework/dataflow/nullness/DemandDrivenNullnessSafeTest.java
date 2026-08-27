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

  private static void assertSafe(String method, String targetMethod) throws URISyntaxException {
    assertResult(SOURCE, CLASS, method, targetMethod, DemandDrivenNullnessAnalysis.Result.SAFE);
  }
}
