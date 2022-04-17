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
  void getName() {}

  @Test
  void getStr() {}

  @Test
  void toMap() {}

  @Test
  void toObj() {}

  @Test
  void toStr() {}
}
