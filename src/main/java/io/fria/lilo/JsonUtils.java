package io.fria.lilo;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;

final class JsonUtils {

  private static final ObjectMapper OBJECT_MAPPER = createMapper();

  @SuppressWarnings("checkstyle:WhitespaceAround")
  private JsonUtils() {}

  public static List<Map<String, Object>> getList(final Map<String, Object> map, final String key) {
    return (List<Map<String, Object>>) map.get(key);
  }

  public static Map<String, Object> getMap(final Map<String, Object> map, final String key) {
    return (Map<String, Object>) map.get(key);
  }

  public static String getName(final Map<String, Object> map) {
    return (String) map.get("name");
  }

  public static String getStr(final Map<String, Object> map, final String key) {
    return (String) map.get(key);
  }

  @SuppressWarnings("checkstyle:WhitespaceAround")
  public static Map<String, Object> toMap(final String text) {

    try {
      return OBJECT_MAPPER.readValue(text, new TypeReference<>() {});
    } catch (final JsonProcessingException e) {
      throw new IllegalArgumentException("Deserialization exception");
    }
  }

  public static <T> T toObj(final String text, final Class<T> clazz) {

    try {
      return OBJECT_MAPPER.readValue(text, clazz);
    } catch (final JsonProcessingException e) {
      throw new IllegalArgumentException("Deserialization exception");
    }
  }

  public static String toStr(final Object obj) {

    try {
      return OBJECT_MAPPER.writeValueAsString(obj);
    } catch (final JsonProcessingException e) {
      throw new IllegalArgumentException("Deserialization exception");
    }
  }

  private static ObjectMapper createMapper() {
    final ObjectMapper mapper = new ObjectMapper();
    mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    return mapper;
  }
}
