class DemandDrivenNullnessSafeCases {
  static class Box {
    void foo() {}

    boolean isDone() {
      return false;
    }
  }

  Box futureField;
  boolean doneField;

  static Box submit() {
    return new Box();
  }

  static void arbitraryCall() {}

  void directGuard(Box x) {
    if (x != null) {
      x.foo();
    }
  }

  void guardReturns(Box x) {
    if (x == null) {
      return;
    }
    x.foo();
  }

  void booleanFromNullCheck(String regionsStr) {
    boolean requested = null != regionsStr && !regionsStr.isEmpty();
    if (!requested) {
      arbitraryCall();
    } else {
      regionsStr.toLowerCase();
    }
  }

  void controlFlowState(Box future, boolean done) {
    if (future == null && !done) {
      future = submit();
    } else if (done) {
      arbitraryCall();
    } else {
      future.isDone();
    }
  }

  void assignmentOnNullPath(Box x) {
    if (x == null) {
      x = new Box();
    }
    x.foo();
  }

  void localAliasAssignment(Box x) {
    if (x != null) {
      Box alias = x;
      alias.foo();
    }
  }

  void localPreservedAcrossCall(Box x) {
    if (x != null) {
      arbitraryCall();
      x.foo();
    }
  }

  void nestedGuards(Box x, boolean enabled) {
    if (enabled) {
      if (x != null) {
        x.foo();
      }
    }
  }

  void directFieldGuard() {
    if (futureField != null) {
      futureField.foo();
    }
  }

  void fieldControlFlow() {
    if (futureField == null && !doneField) {
      futureField = submit();
    } else if (doneField) {
      arbitraryCall();
    } else {
      futureField.isDone();
    }
  }

  void negatedDisjunction(Box x, boolean disabled) {
    boolean skip = x == null || disabled;
    if (!skip) {
      x.foo();
    }
  }

  void combinedConditions(Box x, boolean enabled) {
    if (!enabled) {
      return;
    }
    if (x == null && enabled) {
      return;
    }
    x.foo();
  }

  void multiStepBooleanSubstitution(Box x, boolean ready) {
    boolean missing = x == null;
    boolean usable = !missing && ready;
    if (usable) {
      x.foo();
    }
  }

  void unconditionalSafe(Box x, boolean mode) {
    if (mode) {
      if (x == null) {
        return;
      }
    } else {
      if (x == null) {
        return;
      }
    }
    x.foo();
  }

  void doubleBoolean(Box x, boolean cancelled, boolean ready) {
    if (x == null || cancelled) {
      return;
    } else if (!ready) {
      return;
    } else {
      x.foo();
    }
  }

  void booleanAndControlFlow(Box x, boolean active) {
    boolean invalid = x == null && active;

    if (invalid) {
      return;
    }
    if (!active) {
      return;
    }
    x.foo();
  }

  void dereferenceUnreachableAfterReassignment(Box x, boolean clear) {
    if (x == null) {
      return;
    }
    if (clear) {
      x = null;
      return;
    }
    x.foo();
  }

}
