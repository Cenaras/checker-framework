package com.netflix.eureka.util;

public enum EurekaMonitors {
  GET_ALL_DELTA("getAllDeltaCounter", "Number of total deltas since startup"),
  GET_ALL_DELTA_WITH_REMOTE_REGIONS(
      "getAllDeltaWithRemoteRegionCounter",
      "Number of total deltas with remote regions since startup");

  private EurekaMonitors(String name, String description) {
    throw new java.lang.Error();
  }

  public void increment() {
    throw new java.lang.Error();
  }
}
