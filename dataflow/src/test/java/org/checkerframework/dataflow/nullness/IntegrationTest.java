package org.checkerframework.dataflow.nullness;

import java.net.URISyntaxException;
import org.checkerframework.dataflow.cfg.ControlFlowGraph;
import org.checkerframework.dataflow.cfg.node.FieldAccessNode;
import org.checkerframework.dataflow.cfg.node.MethodInvocationNode;
import org.checkerframework.dataflow.cfg.node.Node;
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
  private static final String ERROR10_ROOT = SOURCE_ROOT + "error-10/";

  @Parameterized.Parameters(name = "{0}")
  public static Object[][] cases() {
    return new Object[][] {
      {"eureka-18", EUREKA18_ROOT, "getContainers"},
      {"eureka-19", EUREKA19_ROOT, "getContainerDifferential"},
      {"error-10-store", ERROR10_ROOT, "storeFullRegistry"},
      {"error-10-reconcile", ERROR10_ROOT, "reconcileAndLogDifference"}
    };
  }

  private final String sourceRoot;
  private final String method;

  public IntegrationTest(String name, String sourceRoot, String method) {
    this.sourceRoot = sourceRoot;
    this.method = method;
  }

  @Test
  public void targetIsSafe() throws URISyntaxException {
    String sourceFile =
        sourceRoot
            + (sourceRoot.endsWith("error-10/")
                ? "com/netflix/eureka/registry/RemoteRegionRegistry.java"
                : "com/netflix/eureka/resources/ApplicationsResource.java");
    String className =
        sourceRoot.endsWith("error-10/")
            ? "com.netflix.eureka.registry.RemoteRegionRegistry"
            : "com.netflix.eureka.resources.ApplicationsResource";
    ControlFlowGraph cfg = generateCfg(sourceFile, className, method, sourceRoot);
    MethodInvocationNode target;
    Node reference;
    if (sourceRoot.endsWith("error-10/")) {
      target = findApplicationsSet(cfg, method);
      reference = target.getArguments().get(0);
    } else {
      target = findInvocation(cfg, method, "toLowerCase");
      reference = target.getTarget().getReceiver();
    }
    Assert.assertEquals(
        DemandDrivenNullnessAnalysis.Result.SAFE,
        DemandDrivenNullnessAnalysis.analyzeReference(cfg, target, reference));
  }

  private static MethodInvocationNode findApplicationsSet(ControlFlowGraph cfg, String method) {
    MethodInvocationNode result = null;
    for (Node node : cfg.getAllNodes()) {
      if (node instanceof MethodInvocationNode invocation
          && invocation.getTarget().getMethod().getSimpleName().contentEquals("set")
          && invocation.getTarget().getReceiver() instanceof FieldAccessNode field
          && field.getElement().getSimpleName().contentEquals("applications")) {
        Assert.assertNull("multiple applications.set calls in " + method, result);
        result = invocation;
      }
    }
    Assert.assertNotNull("no applications.set call in " + method, result);
    return result;
  }
}
