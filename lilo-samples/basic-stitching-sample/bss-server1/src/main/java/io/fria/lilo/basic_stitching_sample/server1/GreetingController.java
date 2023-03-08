package io.fria.lilo.basic_stitching_sample.server1;

import org.jetbrains.annotations.NotNull;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.stereotype.Controller;

@Controller
public class GreetingController {

  @QueryMapping
  public @NotNull String greeting1() {
    return "Hello world";
  }
}
