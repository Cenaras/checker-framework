package org.checkerframework.dataflow.nullness;

import static org.junit.Assert.assertEquals;

import java.net.URISyntaxException;
import org.checkerframework.dataflow.cfg.ControlFlowGraph;
import org.junit.Test;

/** Tests the conditional return contract query: non-null when a boolean parameter has a value. */
public class DemandDrivenNullnessContractTest extends DemandDrivenNullnessTestSupport {

  private static final String SOURCE = "DemandDrivenNullnessContractCases.java";
  private static final String CLASS = "DemandDrivenNullnessContractCases";

  @Test
  public void requiredBranchSuppliesAValue() throws URISyntaxException {
    assertSafe("findOrCreate", 2, true);
    assertUnknown("findOrCreate", 2, false);
  }

  @Test
  public void throwingIsNotAReturn() throws URISyntaxException {
    assertSafe("findOrThrow", 1, true);
    assertUnknown("findOrThrow", 1, false);
  }

  @Test
  public void unconditionalNonNullProvesEitherValue() throws URISyntaxException {
    assertSafe("alwaysNonNull", 1, true);
    assertSafe("alwaysNonNull", 1, false);
  }

  @Test
  public void booleanLocalCarriesThePrecondition() throws URISyntaxException {
    assertSafe("viaBooleanLocal", 1, true);
    assertUnknown("viaBooleanLocal", 1, false);
  }

  @Test
  public void invertedGuardProvesTheFalseCase() throws URISyntaxException {
    assertSafe("invertedGuard", 1, false);
    assertUnknown("invertedGuard", 1, true);
  }

  @Test
  public void reassigningTheParameterDoesNotStrengthenTheContract() throws URISyntaxException {
    assertUnknown("reassignsParameter", 1, true);
    assertUnknown("reassignsParameter", 1, false);
  }

  @Test
  public void callResultStaysOpaque() throws URISyntaxException {
    assertUnknown("returnsCallResult", 1, true);
    assertSafe("returnsCallResult", 1, false);
  }

  @Test
  public void boxedBooleanParameterIsUnsupported() throws URISyntaxException {
    assertUnknown("boxedParameter", 1, true);
  }

  @Test
  public void voidMethodHasNothingToGuarantee() throws URISyntaxException {
    assertUnknown("noReturnValue", 1, true);
  }

  @Test
  public void primitiveReturnHasNothingToGuarantee() throws URISyntaxException {
    assertUnknown("primitiveReturn", 1, true);
  }

  @Test
  public void parameterIndexOutOfRangeIsUnsupported() throws URISyntaxException {
    assertUnknown("alwaysNonNull", 2, true);
    assertUnknown("alwaysNonNull", 0, true);
  }

  @Test
  public void nonBooleanParameterIsUnsupported() throws URISyntaxException {
    assertUnknown("findOrCreate", 1, true);
  }

  @Test
  public void aCaughtExceptionCarriesThePrecondition() throws URISyntaxException {
    assertSafe("createOrRecordFailure", 1, true);
    assertSafe("createOrRecordFailure", 1, false);
  }

  private static void assertSafe(String method, int parameterIndex, boolean parameterValue)
      throws URISyntaxException {
    assertResult(method, parameterIndex, parameterValue, DemandDrivenNullnessAnalysis.Result.SAFE);
  }

  private static void assertUnknown(String method, int parameterIndex, boolean parameterValue)
      throws URISyntaxException {
    assertResult(
        method, parameterIndex, parameterValue, DemandDrivenNullnessAnalysis.Result.UNKNOWN);
  }

  private static void assertResult(
      String method,
      int parameterIndex,
      boolean parameterValue,
      DemandDrivenNullnessAnalysis.Result expected)
      throws URISyntaxException {
    ControlFlowGraph cfg = generateCfg(SOURCE, CLASS, method);
    assertEquals(
        method + " #" + parameterIndex + " == " + parameterValue,
        expected,
        DemandDrivenNullnessAnalysis.analyzeReturnsNonNullIf(cfg, parameterIndex, parameterValue));
  }
}
