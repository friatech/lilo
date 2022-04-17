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

  @SuppressWarnings("checkstyle:WhitespaceAround")
  private JsonUtils() {}

  public static @NotNull Optional<Map<String, Object>> getMap(
      @NotNull final Map<String, Object> map, @NotNull final String key) {

    try {
      return Optional.ofNullable((Map<String, Object>) getValue(map, key));
    } catch (final ClassCastException e) {
      throw new IllegalArgumentException("Map item is not in map type");
    }
  }

  public static @NotNull Optional<List<Map<String, Object>>> getMapList(
      @NotNull final Map<String, Object> map, @NotNull final String key) {

    try {
      return Optional.ofNullable((List<Map<String, Object>>) getValue(map, key));
    } catch (final ClassCastException e) {
      throw new IllegalArgumentException("Map item is not in list type");
    }
  }

  @Nullable
  public static String getName(@NotNull final Map<String, Object> map) {
    return getStr(map, "name");
  }

  @Nullable
  public static String getStr(@NotNull final Map<String, Object> map, @NotNull final String key) {
    return (String) getValue(map, key);
  }

  @SuppressWarnings("checkstyle:WhitespaceAround")
  public static @NotNull Optional<Map<String, Object>> toMap(@Nullable final String jsonText) {

    try {
      return Optional.ofNullable(OBJECT_MAPPER.readValue(jsonText, new TypeReference<>() {}));
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

  private static @NotNull ObjectMapper createMapper() {
    final ObjectMapper mapper = new ObjectMapper();
    mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    return mapper;
  }

  private static @Nullable Object getValue(
      @NotNull final Map<String, Object> map, @NotNull final String key) {
    return Objects.requireNonNull(map).get(Objects.requireNonNull(key));
  }
}
