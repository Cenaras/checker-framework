package com.netflix.appinfo;

public enum EurekaAccept {
  VALUE;

  public static final String HTTP_X_EUREKA_ACCEPT = "X-Eureka-Accept";

  public static EurekaAccept fromString(String value) {
    return value == null ? null : VALUE;
  }
}
