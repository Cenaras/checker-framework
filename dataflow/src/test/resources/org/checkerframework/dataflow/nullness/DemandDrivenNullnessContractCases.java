class DemandDrivenNullnessContractCases {
  static class Box {
    void foo() {}
  }

  static Box nullableValue() {
    return null;
  }

  // The motivating shape: the `required` branch supplies a value, the other may return null.
  Box findOrCreate(String key, boolean required) {
    Box found = nullableValue();
    if (found != null) {
      return found;
    }
    if (required) {
      return new Box();
    }
    return null;
  }

  // Throwing is not a return, so the true case has no returning path that yields null.
  Box findOrThrow(boolean required) {
    Box found = nullableValue();
    if (found == null && required) {
      throw new IllegalStateException();
    }
    return found;
  }

  // Non-null on every return regardless of the parameter, so either value proves the contract.
  Box alwaysNonNull(boolean flag) {
    if (flag) {
      return new Box();
    }
    return new Box();
  }

  // A boolean local derived from the parameter still connects the guard to the precondition.
  Box viaBooleanLocal(boolean required) {
    boolean create = required;
    if (create) {
      return new Box();
    }
    return null;
  }

  // The contract holds for `false`, not for `true`.
  Box invertedGuard(boolean lenient) {
    if (lenient) {
      return nullableValue();
    }
    return new Box();
  }

  // Reassigning the parameter must not let the entry precondition discharge the null return.
  Box reassignsParameter(boolean required) {
    required = false;
    if (required) {
      return new Box();
    }
    return null;
  }

  // The returned value comes from a call, which stays opaque.
  Box returnsCallResult(boolean required) {
    if (required) {
      return nullableValue();
    }
    return new Box();
  }

  // A boxed Boolean cannot partition into "true" and "false", so the query is unsupported.
  Box boxedParameter(Boolean required) {
    if (Boolean.TRUE.equals(required)) {
      return new Box();
    }
    return null;
  }

  // Nothing to guarantee for a void method. Note that CFGBuilder emits no ReturnNode for a bare
  // `return;`, so this must be rejected by its return type rather than by the absence of returns.
  void noReturnValue(boolean required) {
    if (required) {
      return;
    }
  }

  // A primitive return cannot be null either.
  int primitiveReturn(boolean required) {
    return required ? 1 : 2;
  }

  // The failure the catch records is what makes the null return unreachable when creating.
  Box createOrRecordFailure(boolean create) {
    Throwable failure = null;
    Box made = null;
    if (create) {
      try {
        made = new Box();
      } catch (Throwable t) {
        failure = t;
      }
    }
    if (failure == null && create) {
      return made;
    }
    return new Box();
  }
}
