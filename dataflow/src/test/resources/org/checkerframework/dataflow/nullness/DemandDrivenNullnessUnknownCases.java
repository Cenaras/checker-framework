class DemandDrivenNullnessUnknownCases {
  static class Box {
    void foo() {
    }
  }

  static class Holder {
    Box value;
  }

  Box field;

  static Box nullableValue() {
    return null;
  }

  static void arbitraryCall() {
  }

  static boolean possiblyMutateFields() {
    return true;
  }

  void unguardedDereference(Box x) {
    x.foo();
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
      arbitraryCall();
    }
    x.foo();
  }

  void unrelatedCheck(Box x, Box other) {
    if (other != null) {
      x.foo();
    }
  }

  void reassignedToNull(Box x) {
    if (x != null) {
      x = null;
      x.foo();
    }
  }

  void staleBooleanAfterReassignment(Box x) {
    boolean present = x != null;
    x = null;
    if (present) {
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

  void unknownReassignment(Box x) {
    if (x != null) {
      x = nullableValue();
      x.foo();
    }
  }

  void loopIsUnsupported(Box x, boolean condition) {
    while (condition) {
      condition = false;
    }
    x.foo();
  }

  void fieldChangedByCall() {
    if (field != null) {
      arbitraryCall();
      field.foo();
    }
  }

  void fieldChangedInCondition() {
    if (field != null && possiblyMutateFields()) {
      field.foo();
    }
  }

  void fieldChangedInBooleanAssignment() {
    boolean shouldRun = field != null && possiblyMutateFields();
    if (shouldRun) {
      field.foo();
    }
  }

  void fieldChangedByConstructor() {
    if (field != null) {
      new DemandDrivenNullnessUnknownCases();
      field.foo();
    }
  }

  void possiblyAliasedFieldWrite(Holder first, Holder second) {
    if (second.value != null) {
      first.value = null;
      second.value.foo();
    }
  }

  void conjunctionFailure(Box x, boolean enabled) {
    if (x == null && enabled) {
      return;
    }
    x.foo();
  }

  void negatedConjunction(Box x, boolean maintenanceMode) {
    boolean blocked = x == null && maintenanceMode;

    if (!blocked) {
      x.foo();
    }
  }

  void splitConjunction(Box x, boolean strict) {
    if (strict) {
      if (x == null) {
        return;
      }
    }

    x.foo();
  }

  void assignmentOverwrite(Box x, Box replacement) {
    if (x == null) {
      return;
    }
    x = replacement;
    x.foo(); // target
  }

  void validationInOnlyOneBranch(Box x, boolean validate) {
    if (validate) {
      if (x == null) {
        return;
      }
    } else {
      // no validation
    }
    x.foo();
  }

  void unrelatedFinalCheck(Box x, boolean special, boolean cancelled) {
    if (x == null && special) {
      return;
    } else if (cancelled) {
      return;
    } else {
      x.foo();
    }
  }

}
