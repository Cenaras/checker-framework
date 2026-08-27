class DemandDrivenCases {
  static class Box {
    void foo() {}

    boolean isDone() {
      return false;
    }
  }

  static class Holder {
    Box value;
  }

  Box futureField;
  boolean doneField;

  static Box submit() {
    return new Box();
  }

  static void finish() {}

  static boolean possiblyMutateFields() {
    return true;
  }

  void booleanFromNullCheck(String regionsStr) {
    boolean isRemoteRegionRequested = null != regionsStr && !regionsStr.isEmpty();
    if (!isRemoteRegionRequested) {
      finish();
    } else {
      regionsStr.toLowerCase();
    }
  }

  void controlFlowState(Box future, boolean done) {
    if (future == null && !done) {
      future = submit();
    } else if (done) {
      finish();
    } else {
      future.isDone();
    }
  }

  void disjunction(Box x, boolean enabled) {
    if (x != null || enabled) {
      x.foo();
    }
  }

  void booleanDisjunction(Box x, boolean force) {
    boolean shouldRun = x != null || force;
    if (shouldRun) {
      x.foo();
    }
  }

  void wrongBranch(Box x) {
    boolean present = x != null;
    if (!present) {
      x.foo();
    }
  }

  void nonDominatingCheck(Box x) {
    if (x != null) {
      finish();
    }
    x.foo();
  }

  void reassignedToNull(Box x) {
    if (x != null) {
      x = null;
      x.foo();
    }
  }

  void onlyOnePathInitializes(Box x, boolean condition) {
    x = null;
    if (condition) {
      x = new Box();
    }
    x.foo();
  }

  void assignmentOnBothPaths(Box x) {
    if (x == null) {
      x = new Box();
    }
    x.foo();
  }

  void unknownReassignment(Box x) {
    if (x != null) {
      x = submit();
      x.foo();
    }
  }

  void fieldControlFlow() {
    if (futureField == null && !doneField) {
      futureField = submit();
    } else if (doneField) {
      finish();
    } else {
      futureField.isDone();
    }
  }

  void fieldChangedByCall() {
    if (futureField != null) {
      finish();
      futureField.foo();
    }
  }

  void fieldChangedInCondition() {
    if (futureField != null && possiblyMutateFields()) {
      futureField.foo();
    }
  }

  void fieldChangedInBooleanAssignment() {
    boolean shouldRun = futureField != null && possiblyMutateFields();
    if (shouldRun) {
      futureField.foo();
    }
  }

  void fieldChangedByConstructor() {
    if (futureField != null) {
      new DemandDrivenCases();
      futureField.foo();
    }
  }

  void possiblyAliasedFieldWrite(Holder first, Holder second) {
    if (second.value != null) {
      first.value = null;
      second.value.foo();
    }
  }
}
