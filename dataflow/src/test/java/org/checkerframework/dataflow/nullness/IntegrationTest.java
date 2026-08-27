package org.checkerframework.dataflow.nullness;

import java.net.URISyntaxException;
import org.checkerframework.dataflow.cfg.ControlFlowGraph;
import org.checkerframework.dataflow.cfg.node.MethodInvocationNode;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

/** Integration tests that run the analysis against reduced real-world source packages. */
@RunWith(Parameterized.class)
public class IntegrationTest extends DemandDrivenNullnessTestSupport {

  private static final String SOURCE_ROOT = "/org/checkerframework/dataflow/nullness/integration/";
  private static final String EUREKA18_ROOT = SOURCE_ROOT + "eureka-18/";
  private static final String EUREKA19_ROOT = SOURCE_ROOT + "eureka-19/";

  @Parameterized.Parameters(name = "{0}")
  public static Object[][] cases() {
    return new Object[][] {
      {"eureka-18", EUREKA18_ROOT, "getContainers"},
      {"eureka-19", EUREKA19_ROOT, "getContainerDifferential"}
    };
  }

  private final String sourceRoot;
  private final String method;

  public IntegrationTest(String name, String sourceRoot, String method) {
    this.sourceRoot = sourceRoot;
    this.method = method;
  }

  @Test
  public void regionsStringIsSafe() throws URISyntaxException {
    String sourceFile = sourceRoot + "com/netflix/eureka/resources/ApplicationsResource.java";
    ControlFlowGraph cfg =
        generateCfg(
            sourceFile,
            "com.netflix.eureka.resources.ApplicationsResource",
            method,
            sourceRoot);
    MethodInvocationNode target = findInvocation(cfg, method, "toLowerCase");
    Assert.assertEquals(
        DemandDrivenNullnessAnalysis.Result.SAFE, DemandDrivenNullnessAnalysis.analyze(cfg, target));
  }
}
