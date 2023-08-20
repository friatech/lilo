package io.fria.lilo.samples.spring_boot_subscription.lilo_gateway;

import io.fria.lilo.Lilo;
import java.lang.reflect.Method;
import java.util.Map;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.web.socket.server.support.DefaultHandshakeHandler;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

  private final Lilo lilo;

  public WebSocketConfig(final Lilo lilo) {
    this.lilo = lilo;
  }

  @Override
  public void registerWebSocketHandlers(final WebSocketHandlerRegistry registry) {

    try {
      final Method setOrderMethod = registry.getClass().getDeclaredMethod("setOrder", int.class);
      setOrderMethod.invoke(registry, Integer.MIN_VALUE);
    } catch (final Exception e) {
      throw new RuntimeException(e);
    }

    registry.addHandler(new MyWebSocketHandler(this.lilo), "/graphql")
      .addInterceptors(new HandshakeInterceptor() {
        @Override
        public boolean beforeHandshake(final ServerHttpRequest request, final ServerHttpResponse response, final WebSocketHandler wsHandler, final Map<String, Object> attributes) {
          response.getHeaders().add("Sec-WebSocket-Protocol", "graphql-transport-ws");
          return true;
        }

        @Override
        public void afterHandshake(final ServerHttpRequest request, final ServerHttpResponse response, final WebSocketHandler wsHandler, final Exception exception) {

        }
      })
      .setHandshakeHandler(new DefaultHandshakeHandler())
      .setAllowedOrigins("*");
  }
}
