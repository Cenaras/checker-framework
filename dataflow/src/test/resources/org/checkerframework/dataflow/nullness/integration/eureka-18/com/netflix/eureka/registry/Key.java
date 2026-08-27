package com.netflix.eureka.registry;

import com.netflix.appinfo.EurekaAccept;
import com.netflix.eureka.Version;
import javax.annotation.Nullable;

public class Key {

  public enum KeyType {
    JSON,
    XML
  }

  public enum EntityType {
    Application
  }

  public Key(
      EntityType entityType,
      String entityName,
      KeyType type,
      Version v,
      EurekaAccept eurekaAccept,
      @Nullable String[] regions) {
    throw new java.lang.Error();
  }
}
