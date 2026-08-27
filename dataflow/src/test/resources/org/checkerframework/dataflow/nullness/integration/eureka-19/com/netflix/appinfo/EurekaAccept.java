package com.netflix.appinfo;
public enum EurekaAccept {
    VALUE;

    public static final String HTTP_X_EUREKA_ACCEPT = "X-Eureka-Accept";

    public static com.netflix.appinfo.EurekaAccept fromString(java.lang.String parameter0) {
        return parameter0 == null ? null : VALUE;
    }
}
