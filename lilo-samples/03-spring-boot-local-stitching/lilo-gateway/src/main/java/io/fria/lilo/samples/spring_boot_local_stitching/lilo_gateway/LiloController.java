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

import static graphql.schema.idl.TypeRuntimeWiring.newTypeWiring;

import graphql.schema.idl.RuntimeWiring;
import io.fria.lilo.DefinedSchemaSource;
import io.fria.lilo.GraphQLRequest;
import io.fria.lilo.Lilo;
import io.fria.lilo.RemoteSchemaSource;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.Map;
import java.util.Objects;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
public class LiloController {

  private static final String SOURCE1_NAME = "SERVER1";
  private static final String SOURCE1_BASE_URL = "http://localhost:8081";
  private static final String SOURCE2_NAME = "LOCAL_SCHEMA";

  private final Lilo lilo;

  public LiloController() throws IOException {

    try (final InputStream resourceAsStream =
        LiloController.class.getResourceAsStream("/graphql/source2.graphqls")) {
      final String schemaDefinition =
          new String(
              Objects.requireNonNull(resourceAsStream).readAllBytes(), Charset.defaultCharset());

      final RuntimeWiring wiring =
          RuntimeWiring.newRuntimeWiring()
              .type(
                  newTypeWiring("Query").dataFetcher("greeting2", env -> "Hello from local schema"))
              .build();

      this.lilo =
          Lilo.builder()
              .addSource(
                  RemoteSchemaSource.create(
                      SOURCE1_NAME,
                      new IntrospectionRetrieverImpl(SOURCE1_BASE_URL),
                      new QueryRetrieverImpl(SOURCE1_BASE_URL)))
              .addSource(DefinedSchemaSource.create(SOURCE2_NAME, schemaDefinition, wiring))
              .build();
    }
  }

  @ResponseBody
  @PostMapping("/graphql")
  public @NotNull Map<String, Object> stitch(@RequestBody final @NotNull GraphQLRequest request) {
    return this.lilo.stitch(request.toExecutionInput()).toSpecification();
  }
}
