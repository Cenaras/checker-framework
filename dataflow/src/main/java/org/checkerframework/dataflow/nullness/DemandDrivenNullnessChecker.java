package org.checkerframework.dataflow.nullness;

import com.sun.source.tree.ClassTree;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.Tree;
import com.sun.source.util.SourcePositions;
import com.sun.source.util.TreePathScanner;
import com.sun.source.util.Trees;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedOptions;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.tools.Diagnostic;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.dataflow.cfg.ControlFlowGraph;
import org.checkerframework.dataflow.cfg.builder.CFGBuilder;
import org.checkerframework.dataflow.cfg.node.Node;
import org.checkerframework.javacutil.BasicTypeProcessor;
import org.checkerframework.javacutil.TreeUtils;

/**
 * A command-line annotation processor for {@link DemandDrivenNullnessAnalysis}.
 *
 * <p>The processor verifies one {@code condition == true ==> expression != null} query. It is
 * intended to be run on a source tree reduced by Specimin, for example:
 *
 * <pre>{@code
 * checker/bin/javac \
 *   -processor org.checkerframework.dataflow.nullness.DemandDrivenNullnessChecker \
 *   -AdemandDrivenNullnessClass=com.example.Example \
 *   -AdemandDrivenNullnessMethod=methodName \
 *   -AdemandDrivenNullnessCondition=found \
 *   -AdemandDrivenNullnessExpression=value \
 *   -d build/classes \
 *   .../Example.java .../OtherSpeciminSources.java
 * }</pre>
 *
 * <p>The condition and expression are matched against source expressions after javac parsing. The
 * expression occurrence is counted in source order after the selected condition occurrence. The
 * occurrence options are zero-based and default to zero. They are useful when the reduced method
 * contains the same condition or expression more than once.
 *
 * <p>A successful proof produces a javac note and permits compilation to succeed. An inconclusive
 * proof, an invalid query, or an ambiguous target method produces a javac error, so callers can use
 * the compiler process's exit status as the verification result.
 */
@SupportedAnnotationTypes("*")
@SupportedOptions({
  DemandDrivenNullnessChecker.CLASS_OPTION,
  DemandDrivenNullnessChecker.METHOD_OPTION,
  DemandDrivenNullnessChecker.CONDITION_OPTION,
  DemandDrivenNullnessChecker.EXPRESSION_OPTION,
  DemandDrivenNullnessChecker.CONDITION_OCCURRENCE_OPTION,
  DemandDrivenNullnessChecker.EXPRESSION_OCCURRENCE_OPTION
})
public final class DemandDrivenNullnessChecker extends BasicTypeProcessor {

  /** Fully-qualified name of the class that contains the query. */
  public static final String CLASS_OPTION = "demandDrivenNullnessClass";

  /** Name of the method or constructor that contains the query. */
  public static final String METHOD_OPTION = "demandDrivenNullnessMethod";

  /** Source text of the boolean condition. */
  public static final String CONDITION_OPTION = "demandDrivenNullnessCondition";

  /** Source text of the reference expression. */
  public static final String EXPRESSION_OPTION = "demandDrivenNullnessExpression";

  /** Zero-based occurrence of the condition in the target method. */
  public static final String CONDITION_OCCURRENCE_OPTION =
      "demandDrivenNullnessConditionOccurrence";

  /** Zero-based occurrence of the expression after the selected condition. */
  public static final String EXPRESSION_OCCURRENCE_OPTION =
      "demandDrivenNullnessExpressionOccurrence";

  private @Nullable String targetClass;
  private @Nullable String targetMethod;
  private @Nullable String conditionText;
  private @Nullable String expressionText;
  private int conditionOccurrence;
  private int expressionOccurrence;
  private boolean configurationValid;
  private int matchingMethods;

  /** Creates a command-line processor. */
  public DemandDrivenNullnessChecker() {}

  @Override
  public void typeProcessingStart() {
    targetClass = requiredOption(CLASS_OPTION);
    targetMethod = requiredOption(METHOD_OPTION);
    conditionText = requiredOption(CONDITION_OPTION);
    expressionText = requiredOption(EXPRESSION_OPTION);
    conditionOccurrence = occurrenceOption(CONDITION_OCCURRENCE_OPTION);
    expressionOccurrence = occurrenceOption(EXPRESSION_OCCURRENCE_OPTION);
    configurationValid =
        targetClass != null
            && targetMethod != null
            && conditionText != null
            && expressionText != null
            && conditionOccurrence >= 0
            && expressionOccurrence >= 0;
  }

  @Override
  protected TreePathScanner<?, ?> createTreePathScanner(CompilationUnitTree root) {
    return new QueryScanner(root);
  }

  @Override
  public void typeProcessingOver() {
    if (configurationValid && matchingMethods == 0) {
      error(
          String.format("no method named '%s' was found in class '%s'", targetMethod, targetClass));
    } else if (configurationValid && matchingMethods > 1) {
      error(
          String.format(
              "found %d methods named '%s' in class '%s'; run Specimin with an exact method target",
              matchingMethods, targetMethod, targetClass));
    }
    super.typeProcessingOver();
  }

  @Override
  public SourceVersion getSupportedSourceVersion() {
    return SourceVersion.latestSupported();
  }

  /** Scans one fully-attributed compilation unit for the requested method. */
  private final class QueryScanner extends TreePathScanner<Void, Void> {
    private final CompilationUnitTree root;
    private @Nullable ClassTree currentTargetClass;

    QueryScanner(CompilationUnitTree root) {
      this.root = root;
    }

    @Override
    public Void visitClass(ClassTree tree, Void unused) {
      ClassTree previousTargetClass = currentTargetClass;
      TypeElement element = TreeUtils.elementFromDeclaration(tree);
      if (element != null && element.getQualifiedName().contentEquals(targetClass)) {
        currentTargetClass = tree;
      }
      try {
        return super.visitClass(tree, unused);
      } finally {
        currentTargetClass = previousTargetClass;
      }
    }

    @Override
    public Void visitMethod(MethodTree tree, Void unused) {
      ClassTree classTree = currentTargetClass;
      if (!configurationValid || classTree == null) {
        return super.visitMethod(tree, unused);
      }
      ExecutableElement element = TreeUtils.elementFromDeclaration(tree);
      if (element == null || !matchesTargetMethod(element)) {
        return super.visitMethod(tree, unused);
      }

      matchingMethods++;
      verifyQuery(root, classTree, tree, element);
      return super.visitMethod(tree, unused);
    }

    /** Returns whether the executable has the requested method or constructor name. */
    private boolean matchesTargetMethod(ExecutableElement element) {
      if (element.getSimpleName().contentEquals(targetMethod)) {
        return true;
      }
      if (element.getKind() != ElementKind.CONSTRUCTOR || targetClass == null) {
        return false;
      }
      int separator = Math.max(targetClass.lastIndexOf('.'), targetClass.lastIndexOf('$'));
      return targetClass.substring(separator + 1).equals(targetMethod);
    }
  }

  /** Builds the method CFG, resolves the query's source expressions, and runs the analysis. */
  private void verifyQuery(
      CompilationUnitTree root,
      ClassTree classTree,
      MethodTree methodTree,
      ExecutableElement methodElement) {
    ControlFlowGraph cfg;
    try {
      cfg = CFGBuilder.build(root, methodTree, classTree, processingEnv);
    } catch (Throwable throwable) {
      error(
          methodElement,
          "could not construct the control-flow graph: "
              + throwable.getClass().getSimpleName()
              + messageSuffix(throwable));
      return;
    }

    Trees trees = Trees.instance(processingEnv);
    SourcePositions positions = trees.getSourcePositions();
    List<NodeRange> conditions = matchingNodes(cfg, root, positions, conditionText);
    List<NodeRange> conditionRanges = distinctRanges(conditions);
    if (conditionOccurrence >= conditionRanges.size()) {
      error(
          methodElement,
          String.format(
              "condition '%s' occurrence %d was not found (found %d occurrence(s))",
              conditionText, conditionOccurrence, conditionRanges.size()));
      return;
    }

    NodeRange condition = conditionRanges.get(conditionOccurrence);
    List<NodeRange> expressionNodes = matchingNodes(cfg, root, positions, expressionText);
    expressionNodes.removeIf(nodeRange -> nodeRange.start < condition.end);
    List<NodeRange> expressionRanges = distinctRanges(expressionNodes);
    if (expressionOccurrence >= expressionRanges.size()) {
      error(
          methodElement,
          String.format(
              "expression '%s' occurrence %d after condition '%s' was not found "
                  + "(found %d occurrence(s))",
              expressionText, expressionOccurrence, conditionText, expressionRanges.size()));
      return;
    }

    NodeRange selectedRange = expressionRanges.get(expressionOccurrence);
    DemandDrivenNullnessAnalysis.Result result = DemandDrivenNullnessAnalysis.Result.UNKNOWN;
    Node selectedNode = null;
    for (NodeRange nodeRange : expressionNodes) {
      if (nodeRange.start == selectedRange.start && nodeRange.end == selectedRange.end) {
        selectedNode = nodeRange.node;
        DemandDrivenNullnessAnalysis.Result nodeResult =
            DemandDrivenNullnessAnalysis.analyzeReference(cfg, nodeRange.node, nodeRange.node);
        if (nodeResult == DemandDrivenNullnessAnalysis.Result.SAFE) {
          result = nodeResult;
          break;
        }
      }
    }

    if (selectedNode == null) {
      error(methodElement, "internal error: selected expression has no CFG node");
    } else if (result == DemandDrivenNullnessAnalysis.Result.SAFE) {
      trees.printMessage(
          Diagnostic.Kind.NOTE,
          String.format(
              "[demand-driven-nullness] SAFE: '%s' is non-null when '%s' is true",
              expressionText, conditionText),
          selectedNode.getTree(),
          root);
    } else {
      trees.printMessage(
          Diagnostic.Kind.ERROR,
          String.format(
              "[demand-driven-nullness] UNKNOWN: could not prove that '%s' is non-null when '%s' "
                  + "is true",
              expressionText, conditionText),
          selectedNode.getTree(),
          root);
    }
  }

  /** Returns CFG nodes whose source tree has the requested text, ordered by source position. */
  private static List<NodeRange> matchingNodes(
      ControlFlowGraph cfg,
      CompilationUnitTree root,
      SourcePositions positions,
      @Nullable String requestedText) {
    String normalizedText = normalize(requestedText);
    List<NodeRange> result = new ArrayList<>();
    for (Node node : cfg.getAllNodes()) {
      Tree tree = node.getTree();
      if (tree == null || !normalize(tree.toString()).equals(normalizedText)) {
        continue;
      }
      long start = positions.getStartPosition(root, tree);
      long end = positions.getEndPosition(root, tree);
      if (start >= 0 && end >= start) {
        result.add(new NodeRange(node, start, end));
      }
    }
    result.sort(
        Comparator.comparingLong((NodeRange range) -> range.start).thenComparingLong(r -> r.end));
    return result;
  }

  /** Collapses the possibly many CFG nodes produced for one source range. */
  private static List<NodeRange> distinctRanges(List<NodeRange> nodes) {
    List<NodeRange> result = new ArrayList<>();
    long previousStart = Long.MIN_VALUE;
    long previousEnd = Long.MIN_VALUE;
    for (NodeRange node : nodes) {
      if (node.start != previousStart || node.end != previousEnd) {
        result.add(node);
        previousStart = node.start;
        previousEnd = node.end;
      }
    }
    return result;
  }

  private @Nullable String requiredOption(String name) {
    String value = processingEnv.getOptions().get(name);
    if (value == null || value.isBlank()) {
      error("missing required option -A" + name + "=<value>");
      return null;
    }
    return value;
  }

  private int occurrenceOption(String name) {
    String value = processingEnv.getOptions().get(name);
    if (value == null) {
      return 0;
    }
    try {
      int result = Integer.parseInt(value);
      if (result < 0) {
        throw new NumberFormatException();
      }
      return result;
    } catch (NumberFormatException ignored) {
      error("option -A" + name + " must be a non-negative integer, but was '" + value + "'");
      return -1;
    }
  }

  private void error(String message) {
    processingEnv
        .getMessager()
        .printMessage(Diagnostic.Kind.ERROR, "[demand-driven-nullness] " + message);
  }

  private void error(ExecutableElement method, String message) {
    processingEnv
        .getMessager()
        .printMessage(Diagnostic.Kind.ERROR, "[demand-driven-nullness] " + message, method);
  }

  private static String normalize(@Nullable String text) {
    return text == null ? "" : text.replaceAll("\\s+", "");
  }

  private static String messageSuffix(Throwable throwable) {
    String message = throwable.getMessage();
    return message == null ? "" : ": " + message;
  }

  /** A CFG node and the half-open source range of its underlying tree. */
  private static final class NodeRange {
    final Node node;
    final long start;
    final long end;

    NodeRange(Node node, long start, long end) {
      this.node = node;
      this.start = start;
      this.end = end;
    }
  }
}
