package io.fria.lilo;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class JsonUtils {

  private static final ObjectMapper OBJECT_MAPPER = createMapper();

  private JsonUtils() {
    // Utility class
  }

  public static @NotNull Optional<Map<String, Object>> getMap(
      final @NotNull Map<String, Object> map, final @NotNull String key) {

    try {
      return Optional.ofNullable((Map<String, Object>) getValue(map, key));
    } catch (final ClassCastException e) {
      throw new IllegalArgumentException("Map item is not in map type");
    }
  }

  public static @NotNull Optional<List<Map<String, Object>>> getMapList(
      final @NotNull Map<String, Object> map, final @NotNull String key) {

    try {
      return Optional.ofNullable((List<Map<String, Object>>) getValue(map, key));
    } catch (final ClassCastException e) {
      throw new IllegalArgumentException("Map item is not in list type");
    }
  }

  public static @NotNull Optional<String> getName(final @NotNull Map<String, Object> map) {
    return getStr(map, "name");
  }

  public static @NotNull Optional<String> getStr(
      final @NotNull Map<String, Object> map, final @NotNull String key) {

    try {
      return Optional.ofNullable((String) getValue(map, key));
    } catch (final ClassCastException e) {
      throw new IllegalArgumentException("Map item is not in string type");
    }
  }

  @SuppressWarnings("checkstyle:WhitespaceAround")
  public static @NotNull Optional<Map<String, Object>> toMap(final @NotNull String jsonText) {

    try {
      return Optional.ofNullable(
          OBJECT_MAPPER.readValue(Objects.requireNonNull(jsonText), new TypeReference<>() {}));
    } catch (final JsonProcessingException e) {
      throw new IllegalArgumentException("Deserialization exception", e);
    }
  }

  public static <T> @NotNull Optional<T> toObj(
      final @NotNull String jsonText, final Class<T> clazz) {

    try {
      return Optional.ofNullable(OBJECT_MAPPER.readValue(Objects.requireNonNull(jsonText), clazz));
    } catch (final JsonProcessingException e) {
      throw new IllegalArgumentException("Deserialization exception", e);
    }
  }

  public static @NotNull String toStr(final @Nullable Object obj) {

    try {
      return OBJECT_MAPPER.writeValueAsString(obj);
    } catch (final JsonProcessingException e) {
      throw new IllegalArgumentException("Deserialization exception", e);
    }
  }

  private static @NotNull ObjectMapper createMapper() {
    final ObjectMapper mapper = new ObjectMapper();
    mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    return mapper;
  }

  private static @Nullable Object getValue(
      final @NotNull Map<String, Object> map, final @NotNull String key) {
    return Objects.requireNonNull(map).get(Objects.requireNonNull(key));
  }
}
