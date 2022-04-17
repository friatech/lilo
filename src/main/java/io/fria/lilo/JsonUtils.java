package io.fria.lilo;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class JsonUtils {

  private static final ObjectMapper OBJECT_MAPPER = createMapper();

  @SuppressWarnings("checkstyle:WhitespaceAround")
  private JsonUtils() {}

  @Nullable
  public static List<Map<String, Object>> getList(
      @NotNull final Map<String, Object> map, @NotNull final String key) {
    return (List<Map<String, Object>>) Objects.requireNonNull(map).get(Objects.requireNonNull(key));
  }

  @Nullable
  public static Map<String, Object> getMap(
      @NotNull final Map<String, Object> map, @NotNull final String key) {
    return (Map<String, Object>) Objects.requireNonNull(map).get(Objects.requireNonNull(key));
  }

  @Nullable
  public static String getName(@NotNull final Map<String, Object> map) {
    return getStr(map, "name");
  }

  @Nullable
  public static String getStr(@NotNull final Map<String, Object> map, @NotNull final String key) {
    return (String) Objects.requireNonNull(map).get(Objects.requireNonNull(key));
  }

  @Nullable
  @SuppressWarnings("checkstyle:WhitespaceAround")
  public static Map<String, Object> toMap(@Nullable final String jsonText) {

    try {
      return OBJECT_MAPPER.readValue(jsonText, new TypeReference<>() {});
    } catch (final JsonProcessingException e) {
      throw new IllegalArgumentException("Deserialization exception", e);
    }
  }

  @Nullable
  public static <T> T toObj(@Nullable final String jsonText, final Class<T> clazz) {

    try {
      return OBJECT_MAPPER.readValue(jsonText, clazz);
    } catch (final JsonProcessingException e) {
      throw new IllegalArgumentException("Deserialization exception", e);
    }
  }

  @NotNull
  public static String toStr(@Nullable final Object obj) {

    try {
      return OBJECT_MAPPER.writeValueAsString(obj);
    } catch (final JsonProcessingException e) {
      throw new IllegalArgumentException("Deserialization exception", e);
    }
  }

  @NotNull
  private static ObjectMapper createMapper() {
    final ObjectMapper mapper = new ObjectMapper();
    mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    return mapper;
  }
}
