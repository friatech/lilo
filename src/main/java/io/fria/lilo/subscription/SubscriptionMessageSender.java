package io.fria.lilo.subscription;

import java.io.IOException;

public interface SubscriptionMessageSender {
  void send(String message) throws IOException;
}
