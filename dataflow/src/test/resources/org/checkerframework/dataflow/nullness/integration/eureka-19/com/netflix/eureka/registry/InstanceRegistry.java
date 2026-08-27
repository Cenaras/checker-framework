package com.netflix.eureka.registry;

import com.netflix.appinfo.InstanceInfo;
import com.netflix.discovery.shared.LookupService;
import com.netflix.eureka.lease.LeaseManager;

public interface InstanceRegistry extends LeaseManager<InstanceInfo>, LookupService<String> {}
