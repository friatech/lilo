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
package io.fria.lilo;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import org.jetbrains.annotations.NotNull;

public abstract class AbstractDefaultRemoteRetriever {

  protected String retrieve(final @NotNull String schemaUrl, final @NotNull String query) {

    final byte[] response;

    try {
      final URL url = new URL(schemaUrl);

      final HttpURLConnection conn = (HttpURLConnection) url.openConnection();
      conn.setRequestMethod("POST");
      conn.setRequestProperty("Content-Type", "application/json");
      conn.setDoOutput(true);

      final OutputStream remoteOutputStream = conn.getOutputStream();
      final byte[] input = query.getBytes(StandardCharsets.UTF_8);
      remoteOutputStream.write(input, 0, input.length);
      remoteOutputStream.flush();
      remoteOutputStream.close();

      final InputStream responseInputStream = conn.getInputStream();
      final ByteArrayOutputStream responseOutputStream = new ByteArrayOutputStream();
      final byte[] buffer = new byte[4096];

      int bytesRead;

      while ((bytesRead = responseInputStream.read(buffer)) != -1) {
        responseOutputStream.write(buffer, 0, bytesRead);
      }

      response = responseOutputStream.toByteArray();
      responseOutputStream.close();
      responseInputStream.close();
    } catch (final IOException e) {
      throw new RuntimeException(e);
    }

    return new String(response);
  }
}
