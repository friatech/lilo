package io.fria.lilo.subscription;

public class GraphQLSubscriptionMessage {

  private String id;
  private String type;
  private Object payload;

  public String getId() {
    return this.id;
  }

  public void setId(final String id) {
    this.id = id;
  }

  public String getType() {
    return this.type;
  }

  public void setType(final String type) {
    this.type = type;
  }

  public Object getPayload() {
    return this.payload;
  }

  public void setPayload(final Object payload) {
    this.payload = payload;
  }
}
