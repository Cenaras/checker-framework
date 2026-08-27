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
  public void unknownReassignment() throws URISyntaxException {
    assertUnknown("unknownReassignment", "foo");
  }

  @Test
  public void loopIsUnsupported() throws URISyntaxException {
    assertUnknown("loopIsUnsupported", "foo");
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

  private static void assertUnknown(String method, String targetMethod) throws URISyntaxException {
    assertResult(SOURCE, CLASS, method, targetMethod, DemandDrivenNullnessAnalysis.Result.UNKNOWN);
  }
}
