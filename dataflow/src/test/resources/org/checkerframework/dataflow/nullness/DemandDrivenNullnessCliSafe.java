class DemandDrivenNullnessCliSafe {
  void target(String value) {
    boolean found = value != null;
    if (found) {
      value.toString();
    }
  }
}
