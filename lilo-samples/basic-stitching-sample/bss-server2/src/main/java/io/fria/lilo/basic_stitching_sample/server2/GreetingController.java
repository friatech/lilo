package io.fria.lilo.basic_stitching_sample.server2;

import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Controller;

@Controller
public class GreetingController {

  @QueryMapping
  public @NonNull String greeting2() {
    return "Hello world";
  }
}
