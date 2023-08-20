package io.fria.lilo.subscription;

import java.io.IOException;
import org.jetbrains.annotations.NotNull;

public interface LiloSubscriptionHandler {

  @NotNull
  Object handleMessage(@NotNull String message) throws IOException;
}
