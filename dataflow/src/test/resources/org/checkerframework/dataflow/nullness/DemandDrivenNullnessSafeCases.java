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
}
