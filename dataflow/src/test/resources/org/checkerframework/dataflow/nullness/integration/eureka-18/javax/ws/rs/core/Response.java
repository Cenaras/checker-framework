package javax.ws.rs.core;

public class Response {
  public static class Status {
    public static final Status FORBIDDEN = new Status();
  }

  public static Response status(Status status) { return new Response(); }
  public static Response ok(Object entity) { return new Response(); }
  public Response header(String name, Object value) { return this; }
  public Response build() { return this; }
}
