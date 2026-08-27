package com.netflix.eureka.registry;

public interface PeerAwareInstanceRegistry extends InstanceRegistry {

  boolean shouldAllowAccess(boolean remoteRegionRequired);
}
