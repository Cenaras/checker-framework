class DemandDrivenNullnessSafeCases {
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
  static Box staticBox;
  boolean doneField;

  static Box submit() {
    return new Box();
  }

  static void arbitraryCall() {}

  void instanceMethod() {}

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

  void castReceiver(Object value) {
    if (value != null) {
      ((Box) value).foo();
    }
  }

  void castAssignment(Object value) {
    if (value != null) {
      Box box = (Box) value;
      box.foo();
    }
  }

  void castNullLiteral(Box x) {
    if (x == (Box) null) {
      return;
    }
    x.foo();
  }

  void trueBooleanLiteral(Box x) {
    if (x == null && true) {
      return;
    }
    x.foo();
  }

  void falseBooleanLiteral(Box x) {
    if (x == null || false) {
      return;
    }
    x.foo();
  }

  void stringLiteralAssignment(String text) {
    text = "";
    text.length();
  }

  Box guardedFieldAccess(Holder holder) {
    if (holder == null) {
      return null;
    }
    return holder.value;
  }

  Box guardedArrayAccess(Box[] boxes) {
    if (boxes == null) {
      return null;
    }
    return boxes[0];
  }

  void arrayCreationAssignment(Box[] boxes) {
    boxes = new Box[1];
    boxes[0] = null;
  }

  void nestedFieldAlias(Holder holder) {
    if (holder.value != null) {
      Holder alias = holder;
      alias.value.foo();
    }
  }

  void nestedCastReceiver(Object value) {
    if (value != null) {
      ((Box) (Object) value).foo();
    }
  }

  static void staticFieldGuard() {
    if (staticBox != null) {
      staticBox.foo();
    }
  }

  void thisReceiver() {
    this.instanceMethod();
  }

  void emptyConditional(Box x, boolean condition) {
    if (condition) {}
    if (x == null) {
      return;
    }
    x.foo();
  }
}
