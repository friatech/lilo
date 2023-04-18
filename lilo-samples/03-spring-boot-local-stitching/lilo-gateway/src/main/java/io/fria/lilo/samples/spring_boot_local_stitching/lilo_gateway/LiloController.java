/*
 * Copyright 2023 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.fria.lilo.samples.spring_boot_local_stitching.lilo_gateway;

import io.fria.lilo.GraphQLRequest;
import io.fria.lilo.Lilo;
import io.fria.lilo.RemoteSchemaSource;
import java.util.Map;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
public class LiloController {

  private static final String SERVER1_NAME = "SERVER1";
  private static final String SERVER1_BASE_URL = "http://localhost:8081";
  private static final String SERVER2_NAME = "SERVER2";
  private static final String SERVER2_BASE_URL = "http://localhost:8082";

  private final Lilo lilo;

  public LiloController() {

    this.lilo =
        Lilo.builder()
            .addSource(
                RemoteSchemaSource.create(
                    SERVER1_NAME,
                    new IntrospectionRetrieverImpl(SERVER1_BASE_URL),
                    new QueryRetrieverImpl(SERVER1_BASE_URL)))
            .addSource(
                RemoteSchemaSource.create(
                    SERVER2_NAME,
                    new IntrospectionRetrieverImpl(SERVER2_BASE_URL),
                    new QueryRetrieverImpl(SERVER2_BASE_URL)))
            .build();
  }

  @ResponseBody
  @PostMapping("/graphql")
  public @NotNull Map<String, Object> stitch(@RequestBody final @NotNull GraphQLRequest request) {
    return this.lilo.stitch(request.toExecutionInput()).toSpecification();
  }
}
