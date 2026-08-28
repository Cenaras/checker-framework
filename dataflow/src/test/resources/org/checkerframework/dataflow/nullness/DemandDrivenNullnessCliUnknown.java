class DemandDrivenNullnessCliUnknown {
  void target(String value, boolean found) {
    if (found) {
      value.toString();
    }
  }
}
