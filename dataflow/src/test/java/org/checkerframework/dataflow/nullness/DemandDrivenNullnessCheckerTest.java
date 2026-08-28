package org.checkerframework.dataflow.nullness;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.StandardLocation;
import javax.tools.ToolProvider;
import org.junit.Test;

/** Tests the javac annotation-processor interface to the demand-driven analysis. */
public class DemandDrivenNullnessCheckerTest {

  @Test
  public void safeQueryPermitsCompilation() throws IOException, URISyntaxException {
    CompilationResult result =
        compile("DemandDrivenNullnessCliSafe.java", "DemandDrivenNullnessCliSafe");

    assertTrue(result.output, result.success);
    assertTrue(result.output, result.output.contains("[demand-driven-nullness] SAFE"));
  }

  @Test
  public void unknownQueryFailsCompilation() throws IOException, URISyntaxException {
    CompilationResult result =
        compile("DemandDrivenNullnessCliUnknown.java", "DemandDrivenNullnessCliUnknown");

    assertFalse(result.output, result.success);
    assertTrue(result.output, result.output.contains("[demand-driven-nullness] UNKNOWN"));
  }

  private static CompilationResult compile(String resourceName, String className)
      throws IOException, URISyntaxException {
    JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
    assertNotNull("tests require a JDK", compiler);

    Path source = Path.of(DemandDrivenNullnessCheckerTest.class.getResource(resourceName).toURI());
    Path classes = Files.createTempDirectory("demand-driven-nullness-cli");
    classes.toFile().deleteOnExit();
    DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
    boolean success;
    try {
      try (StandardJavaFileManager fileManager =
          compiler.getStandardFileManager(diagnostics, Locale.ROOT, StandardCharsets.UTF_8)) {
        fileManager.setLocationFromPaths(StandardLocation.CLASS_OUTPUT, List.of(classes));
        Iterable<? extends JavaFileObject> sources =
            fileManager.getJavaFileObjectsFromPaths(List.of(source));
        JavaCompiler.CompilationTask task =
            compiler.getTask(
                null,
                fileManager,
                diagnostics,
                List.of(
                    "-A" + DemandDrivenNullnessChecker.CLASS_OPTION + "=" + className,
                    "-A" + DemandDrivenNullnessChecker.METHOD_OPTION + "=target",
                    "-A" + DemandDrivenNullnessChecker.CONDITION_OPTION + "=found",
                    "-A" + DemandDrivenNullnessChecker.EXPRESSION_OPTION + "=value"),
                null,
                sources);
        task.setProcessors(List.of(new DemandDrivenNullnessChecker()));
        success = task.call();
      }
    } finally {
      try (var paths = Files.walk(classes)) {
        for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
          Files.delete(path);
        }
      }
    }

    StringBuilder output = new StringBuilder();
    for (Diagnostic<? extends JavaFileObject> diagnostic : diagnostics.getDiagnostics()) {
      output.append(diagnostic.getKind()).append(": ").append(diagnostic.getMessage(Locale.ROOT));
      output.append(System.lineSeparator());
    }
    return new CompilationResult(success, output.toString());
  }

  private static final class CompilationResult {
    final boolean success;
    final String output;

    CompilationResult(boolean success, String output) {
      this.success = success;
      this.output = output;
    }
  }
}
