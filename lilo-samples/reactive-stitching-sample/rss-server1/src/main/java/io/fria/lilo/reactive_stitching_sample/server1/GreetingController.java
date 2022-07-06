package io.fria.lilo.reactive_stitching_sample.server1;

import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Controller;

@Controller
public class GreetingController {

  @QueryMapping
  public @NonNull String greeting1() {
    return "Hello world";
  }
}
