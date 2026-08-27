package com.netflix.eureka.util;

public enum EurekaMonitors {
  GET_ALL("getAllCounter", "Number of total registry queries seen since startup"),
  GET_ALL_WITH_REMOTE_REGIONS(
      "getAllWithRemoteRegionCounter",
      "Number of total registry queries with remote regions, seen since startup");

  private EurekaMonitors(String name, String description) {
    throw new java.lang.Error();
  }

  public void increment() {
    throw new java.lang.Error();
  }
}
