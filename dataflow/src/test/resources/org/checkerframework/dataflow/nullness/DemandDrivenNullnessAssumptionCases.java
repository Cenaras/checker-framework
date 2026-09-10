class DemandDrivenNullnessAssumptionCases {
  static class Box {
    void foo() {}
  }

  Box field;

  static Box nullableValue() {
    return null;
  }

  static void arbitraryCall() {}

  static boolean arbitraryPredicate(Object argument) {
    return true;
  }

  // The call may mutate `field`, so only assuming that it does not makes this provable.
  void callBetweenGuardAndDereference() {
    if (field != null) {
      arbitraryCall();
      field.foo();
    }
  }

  // The receiver of the guarded path is written by a call in the same way.
  void callBetweenGuardAndNestedDereference(Box replacement) {
    if (field != null) {
      arbitraryPredicate(replacement);
      field.foo();
    }
  }

  // A local is not a field, so no call can reach it and the assumption changes nothing.
  void localIsUnaffectedByCalls(Box x) {
    if (x != null) {
      arbitraryCall();
      x.foo();
    }
  }

  // Assuming that calls have no side effects says nothing about what a call returns.
  void callResultStaysNullable() {
    Box x = nullableValue();
    x.foo();
  }

  // Nor does it make two calls to one method interchangeable: that would be determinism, which is
  // a separate claim and is not assumed.
  void repeatedCallIsNotStable() {
    Box first = nullableValue();
    if (first != null) {
      Box second = nullableValue();
      second.foo();
    }
  }

  // The write is in this method's own code, not in the callee, so it is applied regardless.
  void writeInsideArgumentsStillApplies(Box x) {
    if (x != null && arbitraryPredicate(x = null)) {
      x.foo();
    }
  }

  // A conditional return contract: the field is established, then a call intervenes.
  Box returnsFieldAfterCall(boolean create) {
    if (create) {
      field = new Box();
      arbitraryCall();
      return field;
    }
    return null;
  }

  /** Declared to return null, but the caller may claim otherwise for this method by name. */
  static Box trusted() {
    return null;
  }

  /** An overload of the trusted name: one entry covers every overload of that name. */
  static Box trusted(int unused) {
    return null;
  }

  static class Other {
    /** Same simple name, different owner, so an entry for the outer class does not reach it. */
    static Box trusted() {
      return null;
    }
  }

  // The assumed result is non-null rather than unconstrained.
  void assumedCallResultIsNonNull() {
    Box x = trusted();
    x.foo();
  }

  // The same claim used without an intervening local.
  void assumedCallDereferencedDirectly() {
    trusted().foo();
  }

  // The claim is per method, not per name: an unlisted method stays unconstrained.
  void unlistedCallStaysNullable() {
    Box x = nullableValue();
    x.foo();
  }

  // The owner is part of the entry, so a same-named method elsewhere is unaffected.
  void sameNameInAnotherOwnerIsUnaffected() {
    Box x = Other.trusted();
    x.foo();
  }

  // Claiming a return value says nothing about side effects: the call can still clear the field.
  void assumedReturnDoesNotImplyPurity() {
    if (field != null) {
      trusted();
      field.foo();
    }
  }

  // A conditional return contract discharged by the claim rather than by the body.
  Box returnsTrustedCall(boolean create) {
    if (create) {
      return trusted();
    }
    return null;
  }
}
