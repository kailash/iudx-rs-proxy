package iudx.rs.proxy.common;

import static iudx.rs.proxy.apiserver.util.ApiServerConstants.*;
public class Api {
  
  private final String dxApiBasePath;
  
  public Api(String dxApiBasePath) {
    this.dxApiBasePath=dxApiBasePath;
    buildEndpoints();
  }
  
  private StringBuilder entitiesEndpoint;
  private StringBuilder temporalEndpoint;
  private StringBuilder postEntitiesEndpoint;
  private StringBuilder postTemporalEndpoint;
  private StringBuilder consumerAuditEndpoint;
  private StringBuilder providerAuditEndpoint;
  
  private void buildEndpoints() {
    entitiesEndpoint=new StringBuilder(dxApiBasePath).append(ENTITIES_URL);
    temporalEndpoint=new StringBuilder(dxApiBasePath).append(TEMPORAL_URL);
    postEntitiesEndpoint=new StringBuilder(dxApiBasePath).append(POST_ENTITIES_URL);
    postTemporalEndpoint=new StringBuilder(dxApiBasePath).append(POST_TEMPORAL_URL);
    consumerAuditEndpoint=new StringBuilder(dxApiBasePath).append(CONSUMER_AUDIT_URL);
    providerAuditEndpoint=new StringBuilder(dxApiBasePath).append(PROVIDER_AUDIT_URL);
  }
  
  public String getEntitiesEndpoint() {
    return entitiesEndpoint.toString();
  }

  public String getTemporalEndpoint() {
    return temporalEndpoint.toString();
  }

  public String getPostEntitiesEndpoint() {
    return postEntitiesEndpoint.toString();
  }

  public String getPostTemporalEndpoint() {
    return postTemporalEndpoint.toString();
  }
  
  public String getConsumerAuditEndpoint() {
    return consumerAuditEndpoint.toString();
  }

  public String getProviderAuditEndpoint() {
    return providerAuditEndpoint.toString();
  }

  

}