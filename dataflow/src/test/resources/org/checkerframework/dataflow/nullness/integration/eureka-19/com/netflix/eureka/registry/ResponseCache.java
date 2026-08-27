package com.netflix.eureka.registry;

import javax.annotation.Nullable;

public interface ResponseCache {

  @Nullable
  String get(Key key);

  @Nullable
  byte[] getGZIP(Key key);
}
