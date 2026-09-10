package org.checkerframework.dataflow.nullness;

import com.sun.source.tree.ClassTree;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.LineMap;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.VariableTree;
import com.sun.source.util.SourcePositions;
import com.sun.source.util.TreePathScanner;
import com.sun.source.util.Trees;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedOptions;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeKind;
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
 * <p>The processor verifies one of two queries.
 *
 * <p><b>Expression query.</b> Is {@code expression} non-null on every path that reaches the given
 * source position? For example:
 *
 * <pre>{@code
 * checker/bin/javac \
 *   -processor org.checkerframework.dataflow.nullness.DemandDrivenNullnessChecker \
 *   -AdemandDrivenNullnessClass=com.example.Example \
 *   -AdemandDrivenNullnessMethod=methodName \
 *   -AdemandDrivenNullnessExpression=value \
 *   -AdemandDrivenNullnessExpressionPosition=27:16 \
 *   -d build/classes \
 *   .../Example.java .../OtherSourcesItNeeds.java
 * }</pre>
 *
 * <p>The position is a one-based {@code line:column} pair naming where the queried expression
 * starts, and it identifies the program point of the query. The expression text is matched against
 * the source expression found there, so a position that does not denote {@code expression} is
 * reported as an error rather than analyzed. Both are needed: several CFG nodes share the start
 * position of {@code value.length()}, and the text is what picks {@code value} out of them.
 *
 * <p>Positions must refer to the sources handed to this compilation. A caller that reads them from
 * a different copy of the program -- one reduced by a tool such as Specimin, say -- is responsible
 * for translating them first.
 *
 * <p><b>Conditional return query.</b> Does the method return a non-null value on every normal
 * return, given that one boolean parameter had a particular value on entry? This is the contract
 * JetBrains writes as {@code @Contract("_,true->!null")}. Select it by naming the parameter instead
 * of an expression:
 *
 * <pre>{@code
 * checker/bin/javac \
 *   -processor org.checkerframework.dataflow.nullness.DemandDrivenNullnessChecker \
 *   -AdemandDrivenNullnessClass=com.example.Example \
 *   -AdemandDrivenNullnessMethod=find \
 *   -AdemandDrivenNullnessParameter=#2 \
 *   -AdemandDrivenNullnessParameterValue=true \
 *   -d build/classes \
 *   .../Example.java
 * }</pre>
 *
 * <p>The parameter is one-based and written {@code #i}, matching how a contract names it. The two
 * queries are mutually exclusive: supply either an expression and its position, or a parameter and
 * its value.
 *
 * <p>A successful proof produces a javac note and permits compilation to succeed. An inconclusive
 * proof, an invalid query, or an ambiguous target method produces a javac error, so callers can use
 * the compiler process's exit status as the verification result.
 */
@SupportedAnnotationTypes("*")
@SupportedOptions({
  DemandDrivenNullnessChecker.CLASS_OPTION,
  DemandDrivenNullnessChecker.METHOD_OPTION,
  DemandDrivenNullnessChecker.EXPRESSION_OPTION,
  DemandDrivenNullnessChecker.EXPRESSION_POSITION_OPTION,
  DemandDrivenNullnessChecker.PARAMETER_OPTION,
  DemandDrivenNullnessChecker.PARAMETER_VALUE_OPTION,
  DemandDrivenNullnessChecker.ASSUME_PURE_CALLS_OPTION,
  DemandDrivenNullnessChecker.ASSUME_NON_NULL_RETURN_OPTION
})
public final class DemandDrivenNullnessChecker extends BasicTypeProcessor {

  /** Fully-qualified name of the class that contains the query. */
  public static final String CLASS_OPTION = "demandDrivenNullnessClass";

  /** Name of the method or constructor that contains the query. */
  public static final String METHOD_OPTION = "demandDrivenNullnessMethod";

  /** Source text of the reference expression. */
  public static final String EXPRESSION_OPTION = "demandDrivenNullnessExpression";

  /**
   * One-based {@code line:column} source position at which the queried expression starts. It names
   * the program point of the query.
   */
  public static final String EXPRESSION_POSITION_OPTION = "demandDrivenNullnessExpressionPosition";

  /**
   * One-based position of the boolean parameter a conditional return contract is about, written
   * {@code #i}. Its presence selects the conditional return query.
   */
  public static final String PARAMETER_OPTION = "demandDrivenNullnessParameter";

  /** The value of {@link #PARAMETER_OPTION} under which the return guarantee is claimed. */
  public static final String PARAMETER_VALUE_OPTION = "demandDrivenNullnessParameterValue";

  /**
   * Assume that no method or constructor call mutates a field, so a fact about a field survives a
   * call. Applies to both queries, and defaults to off.
   *
   * <p>This is an assumption about side effects only, and it is not checked. It does not assume
   * anything about what a call returns: {@code x = foo()} still leaves {@code x} possibly null.
   */
  public static final String ASSUME_PURE_CALLS_OPTION = "demandDrivenNullnessAssumePureCalls";

  /**
   * A comma-separated list of methods to assume never return null, each written {@code
   * <owner>#<method>} with the owner fully qualified, for example {@code
   * retrofit2.OkHttpCall#createRawCall}. Applies to both queries, and defaults to empty.
   *
   * <p>The assumption is not checked. It covers every overload of that name declared by that owner,
   * and matches the method the compiler resolved a call to, so an assumption about an
   * implementation does not reach a call made through an interface. It says nothing about side
   * effects: use {@link #ASSUME_PURE_CALLS_OPTION} for those.
   */
  public static final String ASSUME_NON_NULL_RETURN_OPTION =
      "demandDrivenNullnessAssumeNonNullReturn";

  /** Which of the two queries this invocation verifies. */
  private enum QueryKind {
    /** Is an expression non-null at a source position? */
    EXPRESSION,

    /** Does the method return non-null whenever a boolean parameter has a given value? */
    RETURNS_NON_NULL_IF
  }

  private @Nullable String targetClass;
  private @Nullable String targetMethod;
  private QueryKind queryKind = QueryKind.EXPRESSION;
  private @Nullable String expressionText;
  private @Nullable SourcePosition expressionPosition;
  private int parameterIndex;
  private @Nullable Boolean parameterValue;
  private boolean configurationValid;
  private int matchingMethods;
  private DemandDrivenNullnessAnalysis.Assumptions assumptions =
      DemandDrivenNullnessAnalysis.Assumptions.NONE;

  /** Creates a command-line processor. */
  public DemandDrivenNullnessChecker() {}

  @Override
  public void typeProcessingStart() {
    targetClass = requiredOption(CLASS_OPTION);
    targetMethod = requiredOption(METHOD_OPTION);
    boolean targetValid = targetClass != null && targetMethod != null;
    // Both queries accept this, so it is read before the query kind is selected.
    assumptions =
        (flagOption(ASSUME_PURE_CALLS_OPTION)
                ? DemandDrivenNullnessAnalysis.Assumptions.PURE_CALLS
                : DemandDrivenNullnessAnalysis.Assumptions.NONE)
            .withNonNullReturns(methodListOption(ASSUME_NON_NULL_RETURN_OPTION));

    if (processingEnv.getOptions().containsKey(PARAMETER_OPTION)) {
      queryKind = QueryKind.RETURNS_NON_NULL_IF;
      parameterIndex = parameterOption(PARAMETER_OPTION);
      parameterValue = booleanOption(PARAMETER_VALUE_OPTION);
      configurationValid =
          targetValid
              && parameterIndex > 0
              && parameterValue != null
              && rejectOption(EXPRESSION_OPTION, PARAMETER_OPTION)
              && rejectOption(EXPRESSION_POSITION_OPTION, PARAMETER_OPTION);
      return;
    }

    queryKind = QueryKind.EXPRESSION;
    expressionText = requiredOption(EXPRESSION_OPTION);
    expressionPosition = positionOption(EXPRESSION_POSITION_OPTION);
    configurationValid =
        targetValid
            && expressionText != null
            && expressionPosition != null
            && rejectOption(PARAMETER_VALUE_OPTION, EXPRESSION_OPTION);
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
    if (queryKind == QueryKind.RETURNS_NON_NULL_IF) {
      verifyReturnsNonNullIf(root, methodTree, methodElement, cfg, trees);
      return;
    }

    SourcePositions positions = trees.getSourcePositions();
    SourcePosition position = expressionPosition;
    assert position != null : "@AssumeAssertion(nullness): the configuration was validated";
    long offset = position.offsetIn(root.getLineMap());
    if (offset < 0) {
      error(
          methodElement,
          String.format("position %s is outside the compiled source file", position));
      return;
    }

    // Several CFG nodes can start at one position: for `value.length()` the receiver, the method
    // access, and the invocation all start at `value`. The expression text is what selects one.
    List<MatchedNode> atPosition = nodesStartingAt(cfg, root, positions, offset);
    List<MatchedNode> selectedNodes = new ArrayList<>();
    for (MatchedNode matched : atPosition) {
      if (normalize(matched.text).equals(normalize(expressionText))) {
        selectedNodes.add(matched);
      }
    }
    if (selectedNodes.isEmpty()) {
      String found =
          atPosition.isEmpty()
              ? "no expression"
              : atPosition.stream()
                  .map(matched -> "'" + matched.text + "'")
                  .distinct()
                  .collect(Collectors.joining(", "));
      error(
          methodElement,
          String.format(
              "expression '%s' was not found at %s in '%s'; found %s there",
              expressionText, position, targetMethod, found));
      return;
    }

    // One source range can correspond to several CFG nodes at distinct program points, because
    // CFGBuilder duplicates a finally block once per exit path that runs it. Safety is required on
    // every path reaching the dereference, so the query holds only if every one of those program
    // points is safe.
    DemandDrivenNullnessAnalysis.Result result = DemandDrivenNullnessAnalysis.Result.SAFE;
    for (MatchedNode matched : selectedNodes) {
      if (DemandDrivenNullnessAnalysis.analyzeReference(
              cfg, matched.node, matched.node, assumptions)
          != DemandDrivenNullnessAnalysis.Result.SAFE) {
        result = DemandDrivenNullnessAnalysis.Result.UNKNOWN;
        break;
      }
    }

    Node selectedNode = selectedNodes.get(0).node;
    if (result == DemandDrivenNullnessAnalysis.Result.SAFE) {
      trees.printMessage(
          Diagnostic.Kind.NOTE,
          String.format(
              "[demand-driven-nullness] SAFE: '%s' is non-null on every path reaching %s",
              expressionText, position),
          selectedNode.getTree(),
          root);
    } else {
      trees.printMessage(
          Diagnostic.Kind.ERROR,
          String.format(
              "[demand-driven-nullness] UNKNOWN: could not prove that '%s' is non-null on every"
                  + " path reaching %s",
              expressionText, position),
          selectedNode.getTree(),
          root);
    }
  }

  /** Verifies that the method returns non-null whenever the selected parameter has its value. */
  private void verifyReturnsNonNullIf(
      CompilationUnitTree root,
      MethodTree methodTree,
      ExecutableElement methodElement,
      ControlFlowGraph cfg,
      Trees trees) {
    Boolean value = parameterValue;
    assert value != null : "@AssumeAssertion(nullness): the configuration was validated";

    List<? extends VariableTree> parameters = methodTree.getParameters();
    if (parameterIndex > parameters.size()) {
      error(
          methodElement,
          String.format(
              "'%s' has %d parameter(s), so #%d does not exist",
              targetMethod, parameters.size(), parameterIndex));
      return;
    }
    VariableTree parameter = parameters.get(parameterIndex - 1);
    VariableElement parameterElement = TreeUtils.elementFromDeclaration(parameter);
    if (parameterElement == null || parameterElement.asType().getKind() != TypeKind.BOOLEAN) {
      // A boxed Boolean is rejected too: it can be null, so "is true" is not the negation of
      // "is false" and the contract's two cases would not partition the parameter's values.
      error(
          methodElement,
          String.format(
              "#%d of '%s' is '%s', but a conditional return contract requires a boolean parameter",
              parameterIndex, targetMethod, parameter.getType()));
      return;
    }
    boolean returnsAReference =
        switch (methodElement.getReturnType().getKind()) {
          case DECLARED, ARRAY, TYPEVAR -> true;
          default -> false;
        };
    if (!returnsAReference) {
      // A constructor's element reports VOID as well, so this covers those too.
      error(
          methodElement,
          String.format(
              "'%s' returns '%s', which cannot be null, so it has no non-null contract to prove",
              targetMethod, methodElement.getReturnType()));
      return;
    }

    DemandDrivenNullnessAnalysis.Result result =
        DemandDrivenNullnessAnalysis.analyzeReturnsNonNullIf(
            cfg, parameterIndex, value, assumptions);
    if (result == DemandDrivenNullnessAnalysis.Result.SAFE) {
      trees.printMessage(
          Diagnostic.Kind.NOTE,
          String.format(
              "[demand-driven-nullness] SAFE: '%s' returns non-null on every normal return when"
                  + " #%d is %s",
              targetMethod, parameterIndex, value),
          methodTree,
          root);
    } else {
      trees.printMessage(
          Diagnostic.Kind.ERROR,
          String.format(
              "[demand-driven-nullness] UNKNOWN: could not prove that '%s' returns non-null on"
                  + " every normal return when #%d is %s",
              targetMethod, parameterIndex, value),
          methodTree,
          root);
    }
  }

  /** Returns the CFG nodes whose source tree starts at {@code offset}, shortest text first. */
  private static List<MatchedNode> nodesStartingAt(
      ControlFlowGraph cfg, CompilationUnitTree root, SourcePositions positions, long offset) {
    List<MatchedNode> result = new ArrayList<>();
    for (Node node : cfg.getAllNodes()) {
      Tree tree = node.getTree();
      if (tree == null || positions.getStartPosition(root, tree) != offset) {
        continue;
      }
      result.add(new MatchedNode(node, tree.toString()));
    }
    result.sort(Comparator.comparingInt(matched -> matched.text.length()));
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

  /** Parses a required {@code #i} parameter option, or returns -1 when it is invalid. */
  private int parameterOption(String name) {
    String value = processingEnv.getOptions().get(name);
    if (value != null) {
      String digits = value.trim().startsWith("#") ? value.trim().substring(1) : value.trim();
      try {
        int result = Integer.parseInt(digits);
        if (result > 0) {
          return result;
        }
      } catch (NumberFormatException ignored) {
        // Fall through to the shared error below.
      }
    }
    error(
        "option -A"
            + name
            + " must be a one-based parameter reference such as '#2', but was '"
            + value
            + "'");
    return -1;
  }

  /**
   * Reads an optional comma-separated list of {@code <owner>#<method>} entries. An absent or empty
   * option is the empty set; an entry that is not in that form is an error, because silently
   * ignoring it would answer the query under fewer assumptions than the caller asked for.
   */
  private Set<String> methodListOption(String name) {
    String value = processingEnv.getOptions().get(name);
    if (value == null || value.isBlank()) {
      return Set.of();
    }
    Set<String> methods = new LinkedHashSet<>();
    for (String entry : value.split(",", -1)) {
      String trimmed = entry.trim();
      if (trimmed.isEmpty()) {
        continue;
      }
      int separator = trimmed.indexOf('#');
      if (separator <= 0
          || separator != trimmed.lastIndexOf('#')
          || separator == trimmed.length() - 1) {
        error(
            "option -A"
                + name
                + " entries must be '<owner>#<method>' with the owner fully qualified, but was '"
                + trimmed
                + "'");
        continue;
      }
      methods.add(trimmed);
    }
    return methods;
  }

  /**
   * Reads an optional boolean flag. An absent option is false; the bare form {@code -Aname} and
   * {@code -Aname=true} are both true.
   */
  private boolean flagOption(String name) {
    Map<String, String> options = processingEnv.getOptions();
    if (!options.containsKey(name)) {
      return false;
    }
    String value = options.get(name);
    if (value == null || value.isEmpty() || "true".equals(value)) {
      return true;
    }
    if ("false".equals(value)) {
      return false;
    }
    error("option -A" + name + " must be 'true' or 'false', but was '" + value + "'");
    return false;
  }

  /** Parses a required {@code true}/{@code false} option, or returns null when it is invalid. */
  private @Nullable Boolean booleanOption(String name) {
    String value = processingEnv.getOptions().get(name);
    if ("true".equals(value)) {
      return Boolean.TRUE;
    }
    if ("false".equals(value)) {
      return Boolean.FALSE;
    }
    error("option -A" + name + " must be exactly 'true' or 'false', but was '" + value + "'");
    return null;
  }

  /**
   * Reports an option that does not belong to the selected query. Returns whether it was absent.
   */
  private boolean rejectOption(String name, String selectedBy) {
    if (!processingEnv.getOptions().containsKey(name)) {
      return true;
    }
    error("option -A" + name + " cannot be combined with -A" + selectedBy);
    return false;
  }

  /** Parses a required one-based {@code line:column} option, or returns null when it is invalid. */
  private @Nullable SourcePosition positionOption(String name) {
    String value = processingEnv.getOptions().get(name);
    if (value == null || value.isBlank()) {
      error("missing required option -A" + name + "=<line>:<column>");
      return null;
    }
    int separator = value.lastIndexOf(':');
    if (separator > 0) {
      try {
        int line = Integer.parseInt(value.substring(0, separator).trim());
        int column = Integer.parseInt(value.substring(separator + 1).trim());
        if (line > 0 && column > 0) {
          return new SourcePosition(line, column);
        }
      } catch (NumberFormatException ignored) {
        // Fall through to the shared error below.
      }
    }
    error(
        "option -A" + name + " must be a one-based <line>:<column> pair, but was '" + value + "'");
    return null;
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

  /** A CFG node and the source text of its underlying tree. */
  private static final class MatchedNode {
    final Node node;
    final String text;

    MatchedNode(Node node, String text) {
      this.node = node;
      this.text = text;
    }
  }

  /** A one-based line and column in the compiled source file. */
  private static final class SourcePosition {
    final int line;
    final int column;

    SourcePosition(int line, int column) {
      this.line = line;
      this.column = column;
    }

    /** Returns the character offset of this position, or -1 when it is out of range. */
    long offsetIn(LineMap lineMap) {
      try {
        return lineMap.getPosition(line, column);
      } catch (IndexOutOfBoundsException | IllegalArgumentException ignored) {
        return -1;
      }
    }

    @Override
    public String toString() {
      return line + ":" + column;
    }
  }
}
