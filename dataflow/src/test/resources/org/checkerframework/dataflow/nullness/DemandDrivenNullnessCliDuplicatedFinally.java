class DemandDrivenNullnessCliDuplicatedFinally {
  static void use(String value) {}

  // The finally body is duplicated once per exit path from the try block. The early return runs it
  // with a null value, so only some of the copies are safe.
  void target(String value) {
    boolean found = value != null;
    try {
      if (!found) {
        return;
      }
      use(value);
    } finally {
      value.toString();
    }
  }
}
