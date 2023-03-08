package io.fria.lilo.reactive_stitching_sample.server2;

import org.jetbrains.annotations.NotNull;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.stereotype.Controller;

@Controller
public class GreetingController {

  @QueryMapping
  public @NotNull String greeting2() {
    return "Hello world";
  }
}
