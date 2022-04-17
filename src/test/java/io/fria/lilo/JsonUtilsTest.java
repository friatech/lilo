package io.fria.lilo;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class JsonUtilsTest {

  @Test
  void getMapListWithDifferentType() {

    Assertions.assertThrows(
        IllegalArgumentException.class,
        () -> JsonUtils.getMapList(Map.of("someKey", 12), "someKey"));
  }

  @Test
  void getMapListWithNonNullValue() {

    final Map<String, Object> map = Map.of("someKey", List.of("Apple"));
    final var mapListOptional = JsonUtils.getMapList(map, "someKey");
    Assertions.assertTrue(mapListOptional.isPresent());
  }

  @Test
  void getMapListWithNullValue() {

    final Map<String, Object> map = new HashMap<>();
    final var mapListOptional = JsonUtils.getMapList(map, "someKey");
    Assertions.assertTrue(mapListOptional.isEmpty());
  }

  @Test
  void getMapWithDifferentType() {

    Assertions.assertThrows(
        IllegalArgumentException.class, () -> JsonUtils.getMap(Map.of("someKey", 12), "someKey"));
  }

  @Test
  void getMapWithNonNullValue() {

    final Map<String, Object> map = Map.of("someKey", Map.of("One", 1));
    final var mapOptional = JsonUtils.getMap(map, "someKey");
    Assertions.assertTrue(mapOptional.isPresent());
  }

  @Test
  void getMapWithNullValue() {

    final Map<String, Object> map = new HashMap<>();
    final var mapOptional = JsonUtils.getMapList(map, "someKey");
    Assertions.assertTrue(mapOptional.isEmpty());
  }

  @Test
  void getNameWithDifferentType() {

    Assertions.assertThrows(
        IllegalArgumentException.class, () -> JsonUtils.getName(Map.of("name", 12)));
  }

  @Test
  void getNameWithNonNullValue() {

    final Map<String, Object> map = Map.of("name", "someText");
    final var stringOptional = JsonUtils.getName(map);
    Assertions.assertTrue(stringOptional.isPresent());
  }

  @Test
  void getNameWithNullValue() {

    final Map<String, Object> map = new HashMap<>();
    final var stringOptional = JsonUtils.getName(map);
    Assertions.assertTrue(stringOptional.isEmpty());
  }

  @Test
  void toMapWithInvalidJson() {

    Assertions.assertThrows(IllegalArgumentException.class, () -> JsonUtils.toMap(""));
  }

  @Test
  void toMapWithInvalidType() {

    Assertions.assertThrows(IllegalArgumentException.class, () -> JsonUtils.toMap("[1]"));
  }

  @Test
  void toMapWithNullValue() {

    final var mapOptional = JsonUtils.toMap("null");
    Assertions.assertTrue(mapOptional.isEmpty());
  }

  @Test
  void toMapWithValidJson() {

    final var mapOptional = JsonUtils.toMap("{\"someKey\": \"someValue\"}");
    Assertions.assertTrue(mapOptional.isPresent());
  }

  @Test
  void toObjWithInvalidJson() {

    Assertions.assertThrows(
        IllegalArgumentException.class, () -> JsonUtils.toObj("", String.class));
  }

  @Test
  void toObjWithInvalidType() {

    Assertions.assertThrows(
        IllegalArgumentException.class, () -> JsonUtils.toObj("[1]", Integer.class));
  }

  @Test
  void toObjWithNullValue() {

    final var mapOptional = JsonUtils.toObj("null", String.class);
    Assertions.assertTrue(mapOptional.isEmpty());
  }

  @Test
  void toObjWithValidJson() {

    final var mapOptional = JsonUtils.toObj("{\"someKey\": \"someValue\"}", Map.class);
    Assertions.assertTrue(mapOptional.isPresent());
  }

  @Test
  void toStr() {

    final String result = JsonUtils.toStr(Map.of("One", 1));
    Assertions.assertNotNull(result);
  }

  @Test
  void toStrWithNullInput() {

    final String result = JsonUtils.toStr(null);
    Assertions.assertNotNull(result);
  }
}
