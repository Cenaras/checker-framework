class DemandDrivenNullnessCliSafeFinally {
  static void use(String value) {}

  // The finally body is duplicated here too, but the guard precedes the try block, so every copy
  // is safe.
  void target(String value) {
    boolean found = value != null;
    if (!found) {
      return;
    }
    try {
      use(value);
    } finally {
      value.toString();
    }
  }
}
