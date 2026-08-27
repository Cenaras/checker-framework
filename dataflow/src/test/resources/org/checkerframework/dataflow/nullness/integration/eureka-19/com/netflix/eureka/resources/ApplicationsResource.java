package com.netflix.eureka.resources;

import com.netflix.appinfo.EurekaAccept;
import com.netflix.eureka.EurekaServerConfig;
import com.netflix.eureka.Version;
import com.netflix.eureka.registry.Key;
import com.netflix.eureka.registry.Key.KeyType;
import com.netflix.eureka.registry.PeerAwareInstanceRegistry;
import com.netflix.eureka.registry.ResponseCache;
import com.netflix.eureka.registry.ResponseCacheImpl;
import com.netflix.eureka.util.EurekaMonitors;
import java.util.Arrays;
import javax.annotation.Nullable;
import javax.ws.rs.GET;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.core.UriInfo;

@Path("/{version}/apps")
@Produces({"application/xml", "application/json"})
public class ApplicationsResource {

  private static final String HEADER_ACCEPT = "Accept";

  private static final String HEADER_ACCEPT_ENCODING = "Accept-Encoding";

  private static final String HEADER_CONTENT_ENCODING = null;

  private static final String HEADER_CONTENT_TYPE = null;

  private static final String HEADER_GZIP_VALUE = null;

  private static final String HEADER_JSON_VALUE = null;

  private final EurekaServerConfig serverConfig = null;

  private final PeerAwareInstanceRegistry registry = null;

  private final ResponseCache responseCache = null;

  @Path("delta")
  @GET
  public Response getContainerDifferential(
      @PathParam("version") String version,
      @HeaderParam(HEADER_ACCEPT) String acceptHeader,
      @HeaderParam(HEADER_ACCEPT_ENCODING) String acceptEncoding,
      @HeaderParam(EurekaAccept.HTTP_X_EUREKA_ACCEPT) String eurekaAccept,
      @Context UriInfo uriInfo,
      @Nullable @QueryParam("regions") String regionsStr) {
    boolean isRemoteRegionRequested = null != regionsStr && !regionsStr.isEmpty();
    if ((serverConfig.shouldDisableDelta())
        || (!registry.shouldAllowAccess(isRemoteRegionRequested))) {
      return Response.status(Status.FORBIDDEN).build();
    }
    String[] regions = null;
    if (!isRemoteRegionRequested) {
      EurekaMonitors.GET_ALL_DELTA.increment();
    } else {
      regions = regionsStr.toLowerCase().split(",");
      Arrays.sort(regions);
      EurekaMonitors.GET_ALL_DELTA_WITH_REMOTE_REGIONS.increment();
    }
    CurrentRequestVersion.set(Version.toEnum(version));
    KeyType keyType = Key.KeyType.JSON;
    String returnMediaType = MediaType.APPLICATION_JSON;
    if (acceptHeader == null || !acceptHeader.contains(HEADER_JSON_VALUE)) {
      keyType = Key.KeyType.XML;
      returnMediaType = MediaType.APPLICATION_XML;
    }
    Key cacheKey =
        new Key(
            Key.EntityType.Application,
            ResponseCacheImpl.ALL_APPS_DELTA,
            keyType,
            CurrentRequestVersion.get(),
            EurekaAccept.fromString(eurekaAccept),
            regions);
    final Response response;
    if (acceptEncoding != null && acceptEncoding.contains(HEADER_GZIP_VALUE)) {
      response =
          Response.ok(responseCache.getGZIP(cacheKey))
              .header(HEADER_CONTENT_ENCODING, HEADER_GZIP_VALUE)
              .header(HEADER_CONTENT_TYPE, returnMediaType)
              .build();
    } else {
      response = Response.ok(responseCache.get(cacheKey)).build();
    }
    CurrentRequestVersion.remove();
    return response;
  }
}
