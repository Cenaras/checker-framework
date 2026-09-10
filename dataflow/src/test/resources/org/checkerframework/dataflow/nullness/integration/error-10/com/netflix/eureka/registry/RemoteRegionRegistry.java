package com.netflix.eureka.registry;

import com.netflix.discovery.shared.Applications;
import com.netflix.discovery.shared.LookupService;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import javax.annotation.Nullable;
import org.slf4j.Logger;

public class RemoteRegionRegistry implements LookupService<String> {

  private static final Logger logger = null;

  private final AtomicLong fetchRegistryGeneration = null;

  private final AtomicReference<Applications> applications =
      new AtomicReference<>(new Applications());

  private final AtomicReference<Applications> applicationsDelta = null;

  public boolean storeFullRegistry() {
    long currentGeneration = fetchRegistryGeneration.get();
    Applications apps = fetchRemoteRegistry(false);
    if (apps == null) {
      logger.error("The application is null for some reason. Not storing this information");
    } else if (fetchRegistryGeneration.compareAndSet(currentGeneration, currentGeneration + 1)) {
      applications.set(apps);
      applicationsDelta.set(apps);
      logger.info("Successfully updated registry with the latest content");
      return true;
    } else {
      logger.warn("Not updating applications as another thread is updating it already");
    }
    return false;
  }

  @Nullable private Applications fetchRemoteRegistry(boolean delta) {
    throw new java.lang.Error();
  }

  private boolean reconcileAndLogDifference(Applications delta, String reconcileHashCode)
      throws Throwable {
    logger.warn(
        "The Reconcile hashcodes do not match, client : {}, server : {}. Getting the full registry",
        reconcileHashCode,
        delta.getAppsHashCode());
    long currentGeneration = fetchRegistryGeneration.get();
    Applications apps = this.fetchRemoteRegistry(false);
    if (apps == null) {
      logger.error("The application is null for some reason. Not storing this information");
      return false;
    }
    if (fetchRegistryGeneration.compareAndSet(currentGeneration, currentGeneration + 1)) {
      applications.set(apps);
      applicationsDelta.set(apps);
      logger.warn(
          "The Reconcile hashcodes after complete sync up, client : {}, server : {}.",
          getApplications().getReconcileHashCode(),
          delta.getAppsHashCode());
      return true;
    } else {
      logger.warn(
          "Not setting the applications map as another thread has advanced the update generation");
      return true;
    }
  }

  public Applications getApplications() {
    throw new java.lang.Error();
  }
}
