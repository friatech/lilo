package io.fria.lilo.samples.spring_boot_subscription.lilo_gateway;

import java.io.IOException;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.AbstractWebSocketHandler;

@Component
public class MyWebSocketHandler extends AbstractWebSocketHandler {

  @Override
  public void afterConnectionEstablished(final WebSocketSession session) throws Exception {
    super.afterConnectionEstablished(session);
  }

  @Override
  public void handleMessage(final WebSocketSession session, final WebSocketMessage<?> message) throws Exception {
    super.handleMessage(session, message);
  }

  @Override
  protected void handleTextMessage(WebSocketSession session, TextMessage message) throws IOException {
    System.out.println("New Text Message Received");
    session.sendMessage(message);
  }

  @Override
  protected void handleBinaryMessage(WebSocketSession session, BinaryMessage message) throws IOException {
    System.out.println("New Binary Message Received");
    session.sendMessage(message);
  }

  @Override
  public void handleTransportError(final WebSocketSession session, final Throwable exception) throws Exception {
    exception.printStackTrace();
    super.handleTransportError(session, exception);
  }


  //  @Override
//  public Mono<Void> handle(WebSocketSession session) {
//
//    final Flux<WebSocketMessage> receive = session.receive();
//
//    receive.log();
//
//    receive.map(msg -> {
//      System.out.println(msg);
//      return msg;
//    });
//
//    return session.send(
//      session.receive()
//        .map(msg -> {
//          System.out.println(msg);
//          return msg + "";
//        })
//        .map(session::textMessage));
//  }
}
