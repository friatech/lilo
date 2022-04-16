package io.fria.lilo.complex;

import com.fasterxml.jackson.databind.ObjectMapper;
import graphql.ExecutionInput;
import graphql.ExecutionResult;
import graphql.GraphQL;
import graphql.schema.GraphQLScalarType;
import graphql.schema.idl.RuntimeWiring;
import graphql.schema.idl.TypeRuntimeWiring;
import io.fria.lilo.DummyCoercing;
import io.fria.lilo.Lilo;
import io.fria.lilo.TestUtils;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import static graphql.schema.idl.TypeRuntimeWiring.newTypeWiring;
import static io.fria.lilo.TestUtils.createGraphQL;
import static io.fria.lilo.TestUtils.createSchemaSource;
import static io.fria.lilo.TestUtils.loadResource;

class ComplexQueryTest {

  private static final String SCHEMA1_NAME = "project1";
  private static final String SCHEMA2_NAME = "project2";
  private static final List<?> NULL_LIST;
  private static final Map<String, Object> RESULT_MAP =
      Map.of(
          "id",
          1,
          "name",
          "John",
          "age",
          34,
          "enabled",
          true,
          "role",
          "ADMIN",
          "username",
          "john",
          "__typename",
          "WebUser");

  static {
    NULL_LIST = new ArrayList<>();
    NULL_LIST.add(null);
  }

  private static TypeRuntimeWiring.Builder addServer1Fetchers(
      final TypeRuntimeWiring.Builder builder) {

    return builder
        .dataFetcher("server1queryStringA", env -> null)
        .dataFetcher("server1queryStringB", env -> "StringResult2")
        .dataFetcher("server1queryStringC", env -> null)
        .dataFetcher("server1queryStringD", env -> NULL_LIST)
        .dataFetcher("server1queryStringE", env -> List.of("StringResult5"))
        .dataFetcher("server1queryStringF", env -> "StringResult6")
        .dataFetcher("server1queryIntA", env -> null)
        .dataFetcher("server1queryIntB", env -> 2)
        .dataFetcher("server1queryIntC", env -> null)
        .dataFetcher("server1queryIntD", env -> NULL_LIST)
        .dataFetcher("server1queryIntE", env -> List.of(5))
        .dataFetcher("server1queryIntF", env -> 6)
        .dataFetcher("server1queryFloatA", env -> null)
        .dataFetcher("server1queryFloatB", env -> 2f)
        .dataFetcher("server1queryFloatC", env -> null)
        .dataFetcher("server1queryFloatD", env -> NULL_LIST)
        .dataFetcher("server1queryFloatE", env -> List.of(5f))
        .dataFetcher("server1queryFloatF", env -> 6f)
        .dataFetcher("server1queryBooleanA", env -> null)
        .dataFetcher("server1queryBooleanB", env -> true)
        .dataFetcher("server1queryBooleanC", env -> null)
        .dataFetcher("server1queryBooleanD", env -> NULL_LIST)
        .dataFetcher("server1queryBooleanE", env -> List.of(false))
        .dataFetcher("server1queryBooleanF", env -> true)
        .dataFetcher("server1queryIdA", env -> null)
        .dataFetcher("server1queryIdB", env -> "ID2")
        .dataFetcher("server1queryIdC", env -> null)
        .dataFetcher("server1queryIdD", env -> NULL_LIST)
        .dataFetcher("server1queryIdE", env -> List.of("ID5"))
        .dataFetcher("server1queryIdF", env -> "ID6")
        .dataFetcher("server1queryEnumA", env -> null)
        .dataFetcher("server1queryEnumB", env -> Enum1.ENUM_1_VALUE_A)
        .dataFetcher("server1queryEnumC", env -> null)
        .dataFetcher("server1queryEnumD", env -> NULL_LIST)
        .dataFetcher("server1queryEnumE", env -> List.of(Enum1.ENUM_1_VALUE_B))
        .dataFetcher("server1queryEnumF", env -> Enum1.ENUM_1_VALUE_C)
        .dataFetcher("server1queryEnumCommonA", env -> null)
        .dataFetcher("server1queryEnumCommonB", env -> EnumCommon.ENUM_COMMON_VALUE_A)
        .dataFetcher("server1queryEnumCommonC", env -> null)
        .dataFetcher("server1queryEnumCommonD", env -> NULL_LIST)
        .dataFetcher("server1queryEnumCommonE", env -> List.of(EnumCommon.ENUM_COMMON_VALUE_B))
        .dataFetcher("server1queryEnumCommonF", env -> EnumCommon.ENUM_COMMON_VALUE_C)
        .dataFetcher("server1queryScalarA", env -> null)
        .dataFetcher(
            "server1queryScalarB", env -> UUID.fromString("22222222-2222-2222-2222-222222222222"))
        .dataFetcher("server1queryScalarC", env -> null)
        .dataFetcher("server1queryScalarD", env -> NULL_LIST)
        .dataFetcher(
            "server1queryScalarE",
            env -> List.of(UUID.fromString("55555555-5555-5555-5555-555555555555")))
        .dataFetcher(
            "server1queryScalarF",
            env -> List.of(UUID.fromString("66666666-6666-6666-6666-666666666666")))
        .dataFetcher("server1queryScalarCommonA", env -> null)
        .dataFetcher(
            "server1queryScalarCommonB",
            env -> UUID.fromString("22222222-2222-2222-2222-222222222222"))
        .dataFetcher("server1queryScalarCommonC", env -> null)
        .dataFetcher("server1queryScalarCommonD", env -> NULL_LIST)
        .dataFetcher(
            "server1queryScalarCommonE",
            env -> List.of(UUID.fromString("55555555-5555-5555-5555-555555555555")))
        .dataFetcher(
            "server1queryScalarCommonF",
            env -> UUID.fromString("66666666-6666-6666-6666-666666666666"))
        .dataFetcher("server1queryTypeA", env -> null)
        .dataFetcher(
            "server1queryTypeB",
            env ->
                new ObjectMapper()
                    .readValue(new ObjectMapper().writeValueAsString(createType1()), Map.class))
        .dataFetcher("server1queryTypeC", env -> null)
        .dataFetcher("server1queryTypeD", env -> NULL_LIST)
        .dataFetcher("server1queryTypeE", env -> List.of("Type1_5"))
        .dataFetcher("server1queryTypeF", env -> "Type1_6")
        .dataFetcher("server1queryTypeCommonA", env -> null)
        .dataFetcher("server1queryTypeCommonB", env -> "XXX2")
        .dataFetcher("server1queryTypeCommonC", env -> null)
        .dataFetcher("server1queryTypeCommonD", env -> NULL_LIST)
        .dataFetcher("server1queryTypeCommonE", env -> List.of("XXX5"))
        .dataFetcher("server1queryTypeCommonF", env -> "XXX6")
        .dataFetcher("server1queryTypeImplementedA", env -> null)
        .dataFetcher("server1queryTypeImplementedB", env -> "XXX2")
        .dataFetcher("server1queryTypeImplementedC", env -> null)
        .dataFetcher("server1queryTypeImplementedD", env -> NULL_LIST)
        .dataFetcher("server1queryTypeImplementedE", env -> List.of("XXX5"))
        .dataFetcher("server1queryTypeImplementedF", env -> "XXX6")
        .dataFetcher("server1queryTypeImplementedCommonA", env -> null)
        .dataFetcher("server1queryTypeImplementedCommonB", env -> "XXX2")
        .dataFetcher("server1queryTypeImplementedCommonC", env -> null)
        .dataFetcher("server1queryTypeImplementedCommonD", env -> NULL_LIST)
        .dataFetcher("server1queryTypeImplementedCommonE", env -> List.of("XXX5"))
        .dataFetcher("server1queryTypeImplementedCommonF", env -> "XXX6")
        .dataFetcher("server1queryTypeExtendedA", env -> null)
        .dataFetcher("server1queryTypeExtendedB", env -> "XXX2")
        .dataFetcher("server1queryTypeExtendedC", env -> null)
        .dataFetcher("server1queryTypeExtendedD", env -> NULL_LIST)
        .dataFetcher("server1queryTypeExtendedE", env -> List.of("XXX5"))
        .dataFetcher("server1queryTypeExtendedF", env -> "XXX6")
        .dataFetcher("server1queryTypeExtendedCommonA", env -> null)
        .dataFetcher("server1queryTypeExtendedCommonB", env -> "XXX2")
        .dataFetcher("server1queryTypeExtendedCommonC", env -> null)
        .dataFetcher("server1queryTypeExtendedCommonD", env -> NULL_LIST)
        .dataFetcher("server1queryTypeExtendedCommonE", env -> List.of("XXX5"))
        .dataFetcher("server1queryTypeExtendedCommonF", env -> "XXX6")
        .dataFetcher("server1queryTypeUnionA", env -> null)
        .dataFetcher("server1queryTypeUnionB", env -> "XXX2")
        .dataFetcher("server1queryTypeUnionC", env -> null)
        .dataFetcher("server1queryTypeUnionD", env -> NULL_LIST)
        .dataFetcher("server1queryTypeUnionE", env -> List.of("XXX5"))
        .dataFetcher("server1queryTypeUnionF", env -> "XXX6")
        .dataFetcher("server1queryTypeUnionCommonA", env -> null)
        .dataFetcher("server1queryTypeUnionCommonB", env -> "XXX2")
        .dataFetcher("server1queryTypeUnionCommonC", env -> null)
        .dataFetcher("server1queryTypeUnionCommonD", env -> NULL_LIST)
        .dataFetcher("server1queryTypeUnionCommonE", env -> List.of("XXX5"))
        .dataFetcher("server1queryTypeUnionCommonF", env -> "XXX6")
        .dataFetcher(
            "server1queryInputA",
            env ->
                new ObjectMapper()
                    .readValue(new ObjectMapper().writeValueAsString(createType1()), Map.class))
        .dataFetcher("server1queryInputB", env -> "XXX2")
        .dataFetcher("server1queryInputC", env -> null)
        .dataFetcher("server1queryInputD", env -> NULL_LIST)
        .dataFetcher("server1queryInputE", env -> List.of("XXX5"))
        .dataFetcher("server1queryInputF", env -> "XXX6")
        .dataFetcher("server1queryInputCommonA", env -> null)
        .dataFetcher("server1queryInputCommonB", env -> "XXX2")
        .dataFetcher("server1queryInputCommonC", env -> null)
        .dataFetcher("server1queryInputCommonD", env -> NULL_LIST)
        .dataFetcher("server1queryInputCommonE", env -> List.of("XXX5"))
        .dataFetcher("server1queryInputCommonF", env -> "XXX6");
  }

  private static TypeRuntimeWiring.Builder addServer2Fetchers(
      final TypeRuntimeWiring.Builder builder) {

    return builder
        .dataFetcher("server2queryStringA", env -> null)
        .dataFetcher("server2queryStringB", env -> "StringResult2")
        .dataFetcher("server2queryStringC", env -> null)
        .dataFetcher("server2queryStringD", env -> NULL_LIST)
        .dataFetcher("server2queryStringE", env -> List.of("StringResult5"))
        .dataFetcher("server2queryStringF", env -> "StringResult6")
        .dataFetcher("server2queryIntA", env -> null)
        .dataFetcher("server2queryIntB", env -> 2)
        .dataFetcher("server2queryIntC", env -> null)
        .dataFetcher("server2queryIntD", env -> NULL_LIST)
        .dataFetcher("server2queryIntE", env -> List.of(5))
        .dataFetcher("server2queryIntF", env -> 6)
        .dataFetcher("server2queryFloatA", env -> null)
        .dataFetcher("server2queryFloatB", env -> 2f)
        .dataFetcher("server2queryFloatC", env -> null)
        .dataFetcher("server2queryFloatD", env -> NULL_LIST)
        .dataFetcher("server2queryFloatE", env -> List.of(5f))
        .dataFetcher("server2queryFloatF", env -> 6f)
        .dataFetcher("server2queryBooleanA", env -> null)
        .dataFetcher("server2queryBooleanB", env -> true)
        .dataFetcher("server2queryBooleanC", env -> null)
        .dataFetcher("server2queryBooleanD", env -> NULL_LIST)
        .dataFetcher("server2queryBooleanE", env -> List.of(false))
        .dataFetcher("server2queryBooleanF", env -> true)
        .dataFetcher("server2queryIdA", env -> null)
        .dataFetcher("server2queryIdB", env -> "ID2")
        .dataFetcher("server2queryIdC", env -> null)
        .dataFetcher("server2queryIdD", env -> NULL_LIST)
        .dataFetcher("server2queryIdE", env -> List.of("ID5"))
        .dataFetcher("server2queryIdF", env -> "ID6")
        .dataFetcher("server2queryEnumA", env -> null)
        .dataFetcher("server2queryEnumB", env -> Enum2.ENUM_2_VALUE_A)
        .dataFetcher("server2queryEnumC", env -> null)
        .dataFetcher("server2queryEnumD", env -> NULL_LIST)
        .dataFetcher("server2queryEnumE", env -> List.of(Enum2.ENUM_2_VALUE_B))
        .dataFetcher("server2queryEnumF", env -> Enum2.ENUM_2_VALUE_C)
        .dataFetcher("server2queryEnumCommonA", env -> null)
        .dataFetcher("server2queryEnumCommonB", env -> EnumCommon.ENUM_COMMON_VALUE_A)
        .dataFetcher("server2queryEnumCommonC", env -> null)
        .dataFetcher("server2queryEnumCommonD", env -> NULL_LIST)
        .dataFetcher("server2queryEnumCommonE", env -> List.of(EnumCommon.ENUM_COMMON_VALUE_B))
        .dataFetcher("server2queryEnumCommonF", env -> EnumCommon.ENUM_COMMON_VALUE_C)
        .dataFetcher("server2queryScalarA", env -> null)
        .dataFetcher(
            "server2queryScalarB", env -> UUID.fromString("22222222-2222-2222-2222-222222222222"))
        .dataFetcher("server2queryScalarC", env -> null)
        .dataFetcher("server2queryScalarD", env -> NULL_LIST)
        .dataFetcher(
            "server2queryScalarE",
            env -> List.of(UUID.fromString("55555555-5555-5555-5555-555555555555")))
        .dataFetcher(
            "server2queryScalarF",
            env -> List.of(UUID.fromString("66666666-6666-6666-6666-666666666666")))
        .dataFetcher("server2queryScalarCommonA", env -> null)
        .dataFetcher(
            "server2queryScalarCommonB",
            env -> UUID.fromString("22222222-2222-2222-2222-222222222222"))
        .dataFetcher("server2queryScalarCommonC", env -> null)
        .dataFetcher("server2queryScalarCommonD", env -> NULL_LIST)
        .dataFetcher(
            "server2queryScalarCommonE",
            env -> List.of(UUID.fromString("55555555-5555-5555-5555-555555555555")))
        .dataFetcher(
            "server2queryScalarCommonF",
            env -> UUID.fromString("66666666-6666-6666-6666-666666666666"))
        .dataFetcher("server2queryTypeA", env -> null)
        .dataFetcher("server2queryTypeB", env -> createType2())
        .dataFetcher("server2queryTypeC", env -> null)
        .dataFetcher("server2queryTypeD", env -> NULL_LIST)
        .dataFetcher("server2queryTypeE", env -> List.of("Type1_5"))
        .dataFetcher("server2queryTypeF", env -> "Type1_6")
        .dataFetcher("server2queryTypeCommonA", env -> null)
        .dataFetcher("server2queryTypeCommonB", env -> "XXX2")
        .dataFetcher("server2queryTypeCommonC", env -> null)
        .dataFetcher("server2queryTypeCommonD", env -> NULL_LIST)
        .dataFetcher("server2queryTypeCommonE", env -> List.of("XXX5"))
        .dataFetcher("server2queryTypeCommonF", env -> "XXX6")
        .dataFetcher("server2queryTypeImplementedA", env -> null)
        .dataFetcher("server2queryTypeImplementedB", env -> "XXX2")
        .dataFetcher("server2queryTypeImplementedC", env -> null)
        .dataFetcher("server2queryTypeImplementedD", env -> NULL_LIST)
        .dataFetcher("server2queryTypeImplementedE", env -> List.of("XXX5"))
        .dataFetcher("server2queryTypeImplementedF", env -> "XXX6")
        .dataFetcher("server2queryTypeImplementedCommonA", env -> null)
        .dataFetcher("server2queryTypeImplementedCommonB", env -> "XXX2")
        .dataFetcher("server2queryTypeImplementedCommonC", env -> null)
        .dataFetcher("server2queryTypeImplementedCommonD", env -> NULL_LIST)
        .dataFetcher("server2queryTypeImplementedCommonE", env -> List.of("XXX5"))
        .dataFetcher("server2queryTypeImplementedCommonF", env -> "XXX6")
        .dataFetcher("server2queryTypeExtendedA", env -> null)
        .dataFetcher("server2queryTypeExtendedB", env -> "XXX2")
        .dataFetcher("server2queryTypeExtendedC", env -> null)
        .dataFetcher("server2queryTypeExtendedD", env -> NULL_LIST)
        .dataFetcher("server2queryTypeExtendedE", env -> List.of("XXX5"))
        .dataFetcher("server2queryTypeExtendedF", env -> "XXX6")
        .dataFetcher("server2queryTypeExtendedCommonA", env -> null)
        .dataFetcher("server2queryTypeExtendedCommonB", env -> "XXX2")
        .dataFetcher("server2queryTypeExtendedCommonC", env -> null)
        .dataFetcher("server2queryTypeExtendedCommonD", env -> NULL_LIST)
        .dataFetcher("server2queryTypeExtendedCommonE", env -> List.of("XXX5"))
        .dataFetcher("server2queryTypeExtendedCommonF", env -> "XXX6")
        .dataFetcher("server2queryTypeUnionA", env -> null)
        .dataFetcher("server2queryTypeUnionB", env -> "XXX2")
        .dataFetcher("server2queryTypeUnionC", env -> null)
        .dataFetcher("server2queryTypeUnionD", env -> NULL_LIST)
        .dataFetcher("server2queryTypeUnionE", env -> List.of("XXX5"))
        .dataFetcher("server2queryTypeUnionF", env -> "XXX6")
        .dataFetcher("server2queryTypeUnionCommonA", env -> null)
        .dataFetcher("server2queryTypeUnionCommonB", env -> "XXX2")
        .dataFetcher("server2queryTypeUnionCommonC", env -> null)
        .dataFetcher("server2queryTypeUnionCommonD", env -> NULL_LIST)
        .dataFetcher("server2queryTypeUnionCommonE", env -> List.of("XXX5"))
        .dataFetcher("server2queryTypeUnionCommonF", env -> "XXX6")
        .dataFetcher(
            "server2queryInputA",
            env ->
                new ObjectMapper()
                    .readValue(new ObjectMapper().writeValueAsString(createType1()), Map.class))
        .dataFetcher("server2queryInputB", env -> "XXX2")
        .dataFetcher("server2queryInputC", env -> null)
        .dataFetcher("server2queryInputD", env -> NULL_LIST)
        .dataFetcher("server2queryInputE", env -> List.of("XXX5"))
        .dataFetcher("server2queryInputF", env -> "XXX6")
        .dataFetcher("server2queryInputCommonA", env -> null)
        .dataFetcher("server2queryInputCommonB", env -> "XXX2")
        .dataFetcher("server2queryInputCommonC", env -> null)
        .dataFetcher("server2queryInputCommonD", env -> NULL_LIST)
        .dataFetcher("server2queryInputCommonE", env -> List.of("XXX5"))
        .dataFetcher("server2queryInputCommonF", env -> "XXX6");
  }

  private static Object createType1() {

    return Type1.builder()
        .someString("someStringType1")
        .children(
            List.of(
                TypeChild1.builder()
                    .someString("someStringTypeChild1")
                    .grandChildren(
                        List.of(
                            TypeGrandChild1.builder()
                                .stringList(List.of("A", "B"))
                                .intList(List.of(1, 2))
                                .floatList(List.of(3f, 4f))
                                .booleanList(List.of(true, false))
                                .idList(List.of("id1", "id2"))
                                .enumList(List.of(Enum1.ENUM_1_VALUE_A, Enum1.ENUM_1_VALUE_B))
                                .enumCommonList(
                                    List.of(
                                        EnumCommon.ENUM_COMMON_VALUE_A,
                                        EnumCommon.ENUM_COMMON_VALUE_B))
                                .scalarList(
                                    List.of(
                                        UUID.fromString("22222222-2222-2222-2222-222222222222"),
                                        UUID.fromString("55555555-5555-5555-5555-555555555555")))
                                .scalarCommonList(
                                    List.of(
                                        UUID.fromString("22222222-2222-2222-2222-222222222222"),
                                        UUID.fromString("55555555-5555-5555-5555-555555555555")))
                                .typeCommonList(List.of(new TypeCommon("typeCommon1")))
                                .typeImplementedList(
                                    List.of(new TypeImplemented1("typeImplemented1")))
                                .typeImplementedCommonList(
                                    List.of(new TypeImplementedCommon("typeImplementedCommon1")))
                                .typeExtendedList(List.of(new TypeExtended1("typeExtended1")))
                                .typeExtendedCommonList(
                                    List.of(
                                        new TypeExtendedCommon(
                                            "typeExtendedCommon1", "otherTypeExtendedCommon1")))
                                .typeUnionList(
                                    List.of(
                                        Map.of("someInteger", 1001, "__typename", "TypeUnion1Int")))
                                .typeUnionCommonList(List.of(new TypeUnionCommonInt(3001)))
                                .build()))
                    .build()))
        .build();
  }

  private static Object createType2() {

    return Type2.builder()
        .someString("someStringType2")
        .children(
            List.of(
                TypeChild2.builder()
                    .someString("someStringTypeChild2")
                    .grandChildren(
                        List.of(
                            TypeGrandChild2.builder()
                                .stringList(List.of("A", "B"))
                                .intList(List.of(1, 2))
                                .floatList(List.of(3f, 4f))
                                .booleanList(List.of(true, false))
                                .idList(List.of("id1", "id2"))
                                .enumList(List.of(Enum2.ENUM_2_VALUE_A, Enum2.ENUM_2_VALUE_B))
                                .enumCommonList(
                                    List.of(
                                        EnumCommon.ENUM_COMMON_VALUE_A,
                                        EnumCommon.ENUM_COMMON_VALUE_B))
                                .scalarList(
                                    List.of(
                                        UUID.fromString("22222222-2222-2222-2222-222222222222"),
                                        UUID.fromString("55555555-5555-5555-5555-555555555555")))
                                .scalarCommonList(
                                    List.of(
                                        UUID.fromString("22222222-2222-2222-2222-222222222222"),
                                        UUID.fromString("55555555-5555-5555-5555-555555555555")))
                                .typeCommonList(List.of(new TypeCommon("typeCommon2")))
                                .typeImplementedList(
                                    List.of(new TypeImplemented2("typeImplemented2")))
                                .typeImplementedCommonList(
                                    List.of(new TypeImplementedCommon("typeImplementedCommon2")))
                                .typeExtendedList(List.of(new TypeExtended2("typeExtended2")))
                                .typeExtendedCommonList(
                                    List.of(
                                        new TypeExtendedCommon(
                                            "typeExtendedCommon2", "otherTypeExtendedCommon2")))
                                .typeUnionList(
                                    List.of(
                                        Map.of("someInteger", 2002, "__typename", "TypeUnion2Int")))
                                .typeUnionCommonList(List.of(new TypeUnionCommonInt(3001)))
                                .build()))
                    .build()))
        .build();
  }

  private static RuntimeWiring createWiring() {

    return RuntimeWiring.newRuntimeWiring()
        .type(addServer2Fetchers(addServer1Fetchers(newTypeWiring("Queries"))))
        .type(
            newTypeWiring("Mutations")
                .dataFetcher("create", env -> RESULT_MAP)
                .dataFetcher("delete", env -> null))
        .type(newTypeWiring("Interface1").typeResolver(env -> null))
        .type(newTypeWiring("Interface2").typeResolver(env -> null))
        .type(newTypeWiring("InterfaceCommon").typeResolver(env -> null))
        .type(
            newTypeWiring("TypeUnion1")
                .typeResolver(env -> env.getSchema().getObjectType("TypeUnion1Int")))
        .type(
            newTypeWiring("TypeUnion2")
                .typeResolver(env -> env.getSchema().getObjectType("TypeUnion2Int")))
        .type(
            newTypeWiring("TypeUnionCommon")
                .typeResolver(env -> env.getSchema().getObjectType("TypeUnionCommonInt")))
        .scalar(GraphQLScalarType.newScalar().name("Scalar1").coercing(new DummyCoercing()).build())
        .scalar(GraphQLScalarType.newScalar().name("Scalar2").coercing(new DummyCoercing()).build())
        .scalar(
            GraphQLScalarType.newScalar()
                .name("ScalarCommon")
                .coercing(new DummyCoercing())
                .build())
        .build();
  }

  @Test
  void stitchingQueryTest() throws IOException {

    // Combined result -----------------------------------------------------
    final Map<String, Object> variables = new HashMap<>();

    variables.put("s1stringParam01", "String01");
    variables.put("s1stringParam02", null);
    variables.put("s1stringParam03", "String03");
    variables.put("s1stringParam04", "String04");
    variables.put("s1stringParam05", null);
    variables.put("s1stringParam06", List.of("String06"));
    variables.put("s1stringParam07", NULL_LIST);
    variables.put("s1stringParam08", List.of("String08"));
    variables.put("s1stringParam09", List.of("String09"));
    variables.put("s1stringParam10", List.of("String10"));
    variables.put("s1intParam01", 1);
    variables.put("s1intParam02", null);
    variables.put("s1intParam03", 3);
    variables.put("s1intParam04", 4);
    variables.put("s1intParam05", null);
    variables.put("s1intParam06", List.of(6));
    variables.put("s1intParam07", NULL_LIST);
    variables.put("s1intParam08", List.of(8));
    variables.put("s1intParam09", List.of(9));
    variables.put("s1intParam10", List.of(10));
    variables.put("s1floatParam01", 1f);
    variables.put("s1floatParam02", null);
    variables.put("s1floatParam03", 3f);
    variables.put("s1floatParam04", 4f);
    variables.put("s1floatParam05", null);
    variables.put("s1floatParam06", List.of(6f));
    variables.put("s1floatParam07", NULL_LIST);
    variables.put("s1floatParam08", List.of(8f));
    variables.put("s1floatParam09", List.of(9f));
    variables.put("s1floatParam10", List.of(10f));
    variables.put("s1booleanParam01", true);
    variables.put("s1booleanParam02", null);
    variables.put("s1booleanParam03", false);
    variables.put("s1booleanParam04", true);
    variables.put("s1booleanParam05", null);
    variables.put("s1booleanParam06", List.of(false));
    variables.put("s1booleanParam07", NULL_LIST);
    variables.put("s1booleanParam08", List.of(true));
    variables.put("s1booleanParam09", List.of(false));
    variables.put("s1booleanParam10", List.of(true));
    variables.put("s1idParam01", "Id01");
    variables.put("s1idParam02", null);
    variables.put("s1idParam03", "Id03");
    variables.put("s1idParam04", "Id04");
    variables.put("s1idParam05", null);
    variables.put("s1idParam06", List.of("Id06"));
    variables.put("s1idParam07", NULL_LIST);
    variables.put("s1idParam08", List.of("Id08"));
    variables.put("s1idParam09", List.of("Id09"));
    variables.put("s1idParam10", List.of("Id10"));
    variables.put("s1enumParam01", Enum1.ENUM_1_VALUE_A);
    variables.put("s1enumParam02", null);
    variables.put("s1enumParam03", Enum1.ENUM_1_VALUE_B);
    variables.put("s1enumParam04", Enum1.ENUM_1_VALUE_C);
    variables.put("s1enumParam05", null);
    variables.put("s1enumParam06", List.of(Enum1.ENUM_1_VALUE_A));
    variables.put("s1enumParam07", NULL_LIST);
    variables.put("s1enumParam08", List.of(Enum1.ENUM_1_VALUE_B));
    variables.put("s1enumParam09", List.of(Enum1.ENUM_1_VALUE_C));
    variables.put("s1enumParam10", List.of(Enum1.ENUM_1_VALUE_A));
    variables.put("s1enumCommonParam01", EnumCommon.ENUM_COMMON_VALUE_A);
    variables.put("s1enumCommonParam02", null);
    variables.put("s1enumCommonParam03", EnumCommon.ENUM_COMMON_VALUE_B);
    variables.put("s1enumCommonParam04", EnumCommon.ENUM_COMMON_VALUE_C);
    variables.put("s1enumCommonParam05", null);
    variables.put("s1enumCommonParam06", List.of(EnumCommon.ENUM_COMMON_VALUE_A));
    variables.put("s1enumCommonParam07", NULL_LIST);
    variables.put("s1enumCommonParam08", List.of(EnumCommon.ENUM_COMMON_VALUE_B));
    variables.put("s1enumCommonParam09", List.of(EnumCommon.ENUM_COMMON_VALUE_C));
    variables.put("s1enumCommonParam10", List.of(EnumCommon.ENUM_COMMON_VALUE_A));
    variables.put("s1scalarParam01", UUID.fromString("11111111-1111-1111-1111-111111111111"));
    variables.put("s1scalarParam02", null);
    variables.put("s1scalarParam03", UUID.fromString("11111111-1111-1111-1111-111111111111"));
    variables.put("s1scalarParam04", UUID.fromString("11111111-1111-1111-1111-111111111111"));
    variables.put("s1scalarParam05", null);
    variables.put("s1scalarParam06", UUID.fromString("11111111-1111-1111-1111-111111111111"));
    variables.put("s1scalarParam07", NULL_LIST);
    variables.put(
        "s1scalarParam08", List.of(UUID.fromString("11111111-1111-1111-1111-111111111111")));
    variables.put(
        "s1scalarParam09", List.of(UUID.fromString("11111111-1111-1111-1111-111111111111")));
    variables.put(
        "s1scalarParam10", List.of(UUID.fromString("11111111-1111-1111-1111-111111111111")));
    variables.put("s1scalarCommonParam01", UUID.fromString("11111111-1111-1111-1111-111111111111"));
    variables.put("s1scalarCommonParam02", null);
    variables.put("s1scalarCommonParam03", UUID.fromString("11111111-1111-1111-1111-111111111111"));
    variables.put("s1scalarCommonParam04", UUID.fromString("11111111-1111-1111-1111-111111111111"));
    variables.put("s1scalarCommonParam05", null);
    variables.put("s1scalarCommonParam06", UUID.fromString("11111111-1111-1111-1111-111111111111"));
    variables.put("s1scalarCommonParam07", NULL_LIST);
    variables.put(
        "s1scalarCommonParam08", List.of(UUID.fromString("11111111-1111-1111-1111-111111111111")));
    variables.put(
        "s1scalarCommonParam09", List.of(UUID.fromString("11111111-1111-1111-1111-111111111111")));
    variables.put(
        "s1scalarCommonParam10", List.of(UUID.fromString("11111111-1111-1111-1111-111111111111")));
    variables.put("s1typeParam01", "Type1_01");
    variables.put("s1typeParam02", null);
    variables.put("s1typeParam03", "Type1_03");
    variables.put("s1typeParam04", "Type1_04");
    variables.put("s1inputParam01", null);
    variables.put(
        "s1inputParam02",
        Map.of(
            "someString",
            "inputString",
            "children",
            List.of(
                Map.of(
                    "someString",
                    "inputString",
                    "children",
                    List.of(
                        Map.of(
                            "stringList", List.of("string"),
                            "intList", List.of(1),
                            "floatList", List.of(1f),
                            "booleanList", List.of(true),
                            "idList",
                                List.of(
                                    UUID.fromString("11111111-1111-1111-1111-111111111111")
                                        .toString()),
                            "enumList", List.of(Enum1.ENUM_1_VALUE_A),
                            "enumCommonList", List.of(EnumCommon.ENUM_COMMON_VALUE_A),
                            "scalarList",
                                List.of(UUID.fromString("11111111-1111-1111-1111-111111111111")),
                            "scalarCommonList",
                                List.of(
                                    UUID.fromString("11111111-1111-1111-1111-111111111111"))))))));
    variables.put("s1inputParam03", null);
    variables.put("s1inputParam04", List.of(
      Map.of(
        "someString",
        "inputString",
        "children",
        List.of(
          Map.of(
            "stringList", List.of("string"),
            "intList", List.of(1),
            "floatList", List.of(1f),
            "booleanList", List.of(true),
            "idList",
            List.of(
              UUID.fromString("11111111-1111-1111-1111-111111111111")
                .toString()),
            "enumList", List.of(Enum1.ENUM_1_VALUE_A),
            "enumCommonList", List.of(EnumCommon.ENUM_COMMON_VALUE_A),
            "scalarList",
            List.of(UUID.fromString("11111111-1111-1111-1111-111111111111")),
            "scalarCommonList",
            List.of(
              UUID.fromString("11111111-1111-1111-1111-111111111111")))))));
    variables.put("s1inputParam05", "someString");
    variables.put("s1inputParam06", "someOtherString");
    variables.put("s1inputParam07", List.of(
      Map.of(
        "stringList", List.of("string"),
        "intList", List.of(1),
        "floatList", List.of(1f),
        "booleanList", List.of(true),
        "idList",
        List.of(
          UUID.fromString("11111111-1111-1111-1111-111111111111")
            .toString()),
        "enumList", List.of(Enum1.ENUM_1_VALUE_A),
        "enumCommonList", List.of(EnumCommon.ENUM_COMMON_VALUE_A),
        "scalarList",
        List.of(UUID.fromString("11111111-1111-1111-1111-111111111111")),
        "scalarCommonList",
        List.of(
          UUID.fromString("11111111-1111-1111-1111-111111111111")))));
    variables.put("s2stringParam01", "String01");
    variables.put("s2stringParam02", null);
    variables.put("s2stringParam03", "String03");
    variables.put("s2stringParam04", "String04");
    variables.put("s2stringParam05", null);
    variables.put("s2stringParam06", List.of("String06"));
    variables.put("s2stringParam07", NULL_LIST);
    variables.put("s2stringParam08", List.of("String08"));
    variables.put("s2stringParam09", List.of("String09"));
    variables.put("s2stringParam10", List.of("String10"));
    variables.put("s2intParam01", 1);
    variables.put("s2intParam02", null);
    variables.put("s2intParam03", 3);
    variables.put("s2intParam04", 4);
    variables.put("s2intParam05", null);
    variables.put("s2intParam06", List.of(6));
    variables.put("s2intParam07", NULL_LIST);
    variables.put("s2intParam08", List.of(8));
    variables.put("s2intParam09", List.of(9));
    variables.put("s2intParam10", List.of(10));
    variables.put("s2floatParam01", 1f);
    variables.put("s2floatParam02", null);
    variables.put("s2floatParam03", 3f);
    variables.put("s2floatParam04", 4f);
    variables.put("s2floatParam05", null);
    variables.put("s2floatParam06", List.of(6f));
    variables.put("s2floatParam07", NULL_LIST);
    variables.put("s2floatParam08", List.of(8f));
    variables.put("s2floatParam09", List.of(9f));
    variables.put("s2floatParam10", List.of(10f));
    variables.put("s2booleanParam01", true);
    variables.put("s2booleanParam02", null);
    variables.put("s2booleanParam03", false);
    variables.put("s2booleanParam04", true);
    variables.put("s2booleanParam05", null);
    variables.put("s2booleanParam06", List.of(false));
    variables.put("s2booleanParam07", NULL_LIST);
    variables.put("s2booleanParam08", List.of(true));
    variables.put("s2booleanParam09", List.of(false));
    variables.put("s2booleanParam10", List.of(true));
    variables.put("s2idParam01", "Id01");
    variables.put("s2idParam02", null);
    variables.put("s2idParam03", "Id03");
    variables.put("s2idParam04", "Id04");
    variables.put("s2idParam05", null);
    variables.put("s2idParam06", List.of("Id06"));
    variables.put("s2idParam07", NULL_LIST);
    variables.put("s2idParam08", List.of("Id08"));
    variables.put("s2idParam09", List.of("Id09"));
    variables.put("s2idParam10", List.of("Id10"));
    variables.put("s2enumParam01", Enum2.ENUM_2_VALUE_A);
    variables.put("s2enumParam02", null);
    variables.put("s2enumParam03", Enum2.ENUM_2_VALUE_B);
    variables.put("s2enumParam04", Enum2.ENUM_2_VALUE_C);
    variables.put("s2enumParam05", null);
    variables.put("s2enumParam06", List.of(Enum2.ENUM_2_VALUE_A));
    variables.put("s2enumParam07", NULL_LIST);
    variables.put("s2enumParam08", List.of(Enum2.ENUM_2_VALUE_B));
    variables.put("s2enumParam09", List.of(Enum2.ENUM_2_VALUE_C));
    variables.put("s2enumParam10", List.of(Enum2.ENUM_2_VALUE_A));
    variables.put("s2enumCommonParam01", EnumCommon.ENUM_COMMON_VALUE_A);
    variables.put("s2enumCommonParam02", null);
    variables.put("s2enumCommonParam03", EnumCommon.ENUM_COMMON_VALUE_B);
    variables.put("s2enumCommonParam04", EnumCommon.ENUM_COMMON_VALUE_C);
    variables.put("s2enumCommonParam05", null);
    variables.put("s2enumCommonParam06", List.of(EnumCommon.ENUM_COMMON_VALUE_A));
    variables.put("s2enumCommonParam07", NULL_LIST);
    variables.put("s2enumCommonParam08", List.of(EnumCommon.ENUM_COMMON_VALUE_B));
    variables.put("s2enumCommonParam09", List.of(EnumCommon.ENUM_COMMON_VALUE_C));
    variables.put("s2enumCommonParam10", List.of(EnumCommon.ENUM_COMMON_VALUE_A));
    variables.put("s2scalarParam01", UUID.fromString("11111111-1111-1111-1111-111111111111"));
    variables.put("s2scalarParam02", null);
    variables.put("s2scalarParam03", UUID.fromString("11111111-1111-1111-1111-111111111111"));
    variables.put("s2scalarParam04", UUID.fromString("11111111-1111-1111-1111-111111111111"));
    variables.put("s2scalarParam05", null);
    variables.put("s2scalarParam06", UUID.fromString("11111111-1111-1111-1111-111111111111"));
    variables.put("s2scalarParam07", NULL_LIST);
    variables.put(
        "s2scalarParam08", List.of(UUID.fromString("11111111-1111-1111-1111-111111111111")));
    variables.put(
        "s2scalarParam09", List.of(UUID.fromString("11111111-1111-1111-1111-111111111111")));
    variables.put(
        "s2scalarParam10", List.of(UUID.fromString("11111111-1111-1111-1111-111111111111")));
    variables.put("s2scalarCommonParam01", UUID.fromString("11111111-1111-1111-1111-111111111111"));
    variables.put("s2scalarCommonParam02", null);
    variables.put("s2scalarCommonParam03", UUID.fromString("11111111-1111-1111-1111-111111111111"));
    variables.put("s2scalarCommonParam04", UUID.fromString("11111111-1111-1111-1111-111111111111"));
    variables.put("s2scalarCommonParam05", null);
    variables.put("s2scalarCommonParam06", UUID.fromString("11111111-1111-1111-1111-111111111111"));
    variables.put("s2scalarCommonParam07", NULL_LIST);
    variables.put(
        "s2scalarCommonParam08", List.of(UUID.fromString("11111111-1111-1111-1111-111111111111")));
    variables.put(
        "s2scalarCommonParam09", List.of(UUID.fromString("11111111-1111-1111-1111-111111111111")));
    variables.put(
        "s2scalarCommonParam10", List.of(UUID.fromString("11111111-1111-1111-1111-111111111111")));
    variables.put("s2typeParam01", "Type2_01");
    variables.put("s2typeParam02", null);
    variables.put("s2typeParam03", "Type2_03");
    variables.put("s2typeParam04", "Type2_04");
    variables.put("s2typeParam05", null);
    variables.put("s2typeParam06", List.of("Type2_06"));
    variables.put("s2typeParam07", NULL_LIST);
    variables.put("s2typeParam08", List.of("Type2_08"));
    variables.put("s2typeParam09", List.of("Type2_09"));
    variables.put("s2typeParam10", List.of("Type2_10"));
    variables.put("s2inputParam01", null);
    variables.put(
        "s2inputParam02",
        Map.of(
            "someString",
            "inputString",
            "children",
            List.of(
                Map.of(
                    "someString",
                    "inputString",
                    "children",
                    List.of(
                        Map.of(
                            "stringList", List.of("string"),
                            "intList", List.of(1),
                            "floatList", List.of(1f),
                            "booleanList", List.of(true),
                            "idList",
                                List.of(
                                    UUID.fromString("11111111-1111-1111-1111-111111111111")
                                        .toString()),
                            "enumList", List.of(Enum2.ENUM_2_VALUE_A),
                            "enumCommonList", List.of(EnumCommon.ENUM_COMMON_VALUE_A),
                            "scalarList",
                                List.of(UUID.fromString("11111111-1111-1111-1111-111111111111")),
                            "scalarCommonList",
                                List.of(
                                    UUID.fromString("11111111-1111-1111-1111-111111111111"))))))));

    variables.put("s2inputParam03", null);
    variables.put("s2inputParam04", List.of(
      Map.of(
        "someString",
        "inputString",
        "children",
        List.of(
          Map.of(
            "stringList", List.of("string"),
            "intList", List.of(1),
            "floatList", List.of(1f),
            "booleanList", List.of(true),
            "idList",
            List.of(
              UUID.fromString("11111111-1111-1111-1111-111111111111")
                .toString()),
            "enumList", List.of(Enum2.ENUM_2_VALUE_A),
            "enumCommonList", List.of(EnumCommon.ENUM_COMMON_VALUE_A),
            "scalarList",
            List.of(UUID.fromString("11111111-1111-1111-1111-111111111111")),
            "scalarCommonList",
            List.of(
              UUID.fromString("11111111-1111-1111-1111-111111111111")))))));
    variables.put("s2inputParam05", "someString");
    variables.put("s2inputParam06", "someOtherString");
    variables.put("s2inputParam07", List.of(
      Map.of(
        "stringList", List.of("string"),
        "intList", List.of(1),
        "floatList", List.of(1f),
        "booleanList", List.of(true),
        "idList",
        List.of(
          UUID.fromString("11111111-1111-1111-1111-111111111111")
            .toString()),
        "enumList", List.of(Enum2.ENUM_2_VALUE_A),
        "enumCommonList", List.of(EnumCommon.ENUM_COMMON_VALUE_A),
        "scalarList",
        List.of(UUID.fromString("11111111-1111-1111-1111-111111111111")),
        "scalarCommonList",
        List.of(
          UUID.fromString("11111111-1111-1111-1111-111111111111")))));
    variables.put("includeGrandChildren1", true);
    variables.put("includeGrandChildren2", false);
    variables.put("includeCommonString", true);

    final ExecutionInput executionInput =
        ExecutionInput.newExecutionInput()
            .query(loadResource("/complex/query.graphql"))
            .variables(variables)
            .build();

    final GraphQL combinedGraphQL = createGraphQL("/complex/combined.graphqls", createWiring());
    final ExecutionResult result = combinedGraphQL.execute(executionInput);
    final Map<String, Object> expected = result.getData();
    Assertions.assertNotNull(expected);
    Assertions.assertEquals(0, result.getErrors().size());

    // Stitching result ----------------------------------------------------
    final var project1GraphQL = createGraphQL("/complex/project1.graphqls", createWiring());
    final var project2GraphQL = createGraphQL("/complex/project2.graphqls", createWiring());
    final var introspection1Retriever = new TestUtils.TestIntrospectionRetriever(project1GraphQL);
    final var introspection2Retriever = new TestUtils.TestIntrospectionRetriever(project2GraphQL);
    final var query1Retriever = new TestUtils.TestQueryRetriever(combinedGraphQL);
    final var query2Retriever = new TestUtils.TestQueryRetriever(project2GraphQL);

    final Lilo lilo =
        Lilo.builder()
            .addSource(createSchemaSource(SCHEMA1_NAME, introspection1Retriever, query1Retriever))
            .addSource(createSchemaSource(SCHEMA2_NAME, introspection2Retriever, query2Retriever))
            .build();

    final ExecutionResult stitchResult = lilo.stitch(executionInput);

    final ObjectMapper objectMapper = new ObjectMapper();
    Assertions.assertEquals(
        objectMapper.writeValueAsString(expected),
        objectMapper.writeValueAsString(stitchResult.getData()));
    Assertions.assertEquals(0, stitchResult.getErrors().size());
  }

  private enum EnumCommon {
    ENUM_COMMON_VALUE_A,
    ENUM_COMMON_VALUE_B,
    ENUM_COMMON_VALUE_C
  }

  private enum Enum1 {
    ENUM_1_VALUE_A,
    ENUM_1_VALUE_B,
    ENUM_1_VALUE_C
  }

  private enum Enum2 {
    ENUM_2_VALUE_A,
    ENUM_2_VALUE_B,
    ENUM_2_VALUE_C
  }

  private static final class Type1 {
    private final String someString;
    private final List<TypeChild1> children;

    private Type1(final String someString, final List<TypeChild1> children) {
      this.someString = someString;
      this.children = children;
    }

    private static Type1Builder builder() {
      return new Type1Builder();
    }

    public List<TypeChild1> getChildren() {
      return this.children;
    }

    public String getSomeString() {
      return this.someString;
    }

    private static final class Type1Builder {
      private String someString;
      private List<TypeChild1> children;

      private Type1 build() {
        return new Type1(this.someString, this.children);
      }

      private Type1Builder children(final List<TypeChild1> children) {
        this.children = children;
        return this;
      }

      private Type1Builder someString(final String someString) {
        this.someString = someString;
        return this;
      }
    }
  }

  private static final class Type2 {
    private final String someString;
    private final List<TypeChild2> children;

    private Type2(final String someString, final List<TypeChild2> children) {
      this.someString = someString;
      this.children = children;
    }

    private static Type2Builder builder() {
      return new Type2Builder();
    }

    public List<TypeChild2> getChildren() {
      return this.children;
    }

    public String getSomeString() {
      return this.someString;
    }

    private static final class Type2Builder {
      private String someString;
      private List<TypeChild2> children;

      private Type2 build() {
        return new Type2(this.someString, this.children);
      }

      private Type2Builder children(final List<TypeChild2> children) {
        this.children = children;
        return this;
      }

      private Type2Builder someString(final String someString) {
        this.someString = someString;
        return this;
      }
    }
  }

  private static final class TypeChild1 {
    private final String someString;
    private final List<TypeGrandChild1> grandChildren;

    private TypeChild1(final String someString, final List<TypeGrandChild1> grandChildren) {
      this.someString = someString;
      this.grandChildren = grandChildren;
    }

    private static TypeChild1Builder builder() {
      return new TypeChild1Builder();
    }

    public List<TypeGrandChild1> getGrandChildren() {
      return this.grandChildren;
    }

    public String getSomeString() {
      return this.someString;
    }

    private static final class TypeChild1Builder {
      private String someString;
      private List<TypeGrandChild1> grandChildren;

      private TypeChild1 build() {
        return new TypeChild1(this.someString, this.grandChildren);
      }

      private TypeChild1Builder grandChildren(final List<TypeGrandChild1> grandChildren) {
        this.grandChildren = grandChildren;
        return this;
      }

      private TypeChild1Builder someString(final String someString) {
        this.someString = someString;
        return this;
      }
    }
  }

  private static final class TypeChild2 {
    private final String someString;
    private final List<TypeGrandChild2> grandChildren;

    private TypeChild2(final String someString, final List<TypeGrandChild2> grandChildren) {
      this.someString = someString;
      this.grandChildren = grandChildren;
    }

    private static TypeChild2Builder builder() {
      return new TypeChild2Builder();
    }

    public List<TypeGrandChild2> getGrandChildren() {
      return this.grandChildren;
    }

    public String getSomeString() {
      return this.someString;
    }

    private static final class TypeChild2Builder {
      private String someString;
      private List<TypeGrandChild2> grandChildren;

      private TypeChild2 build() {
        return new TypeChild2(this.someString, this.grandChildren);
      }

      private TypeChild2Builder grandChildren(final List<TypeGrandChild2> grandChildren) {
        this.grandChildren = grandChildren;
        return this;
      }

      private TypeChild2Builder someString(final String someString) {
        this.someString = someString;
        return this;
      }
    }
  }

  private static final class TypeGrandChild1 {

    private final List<String> stringList;
    private final List<Integer> intList;
    private final List<Float> floatList;
    private final List<Boolean> booleanList;
    private final List<String> idList;
    private final List<Enum1> enumList;
    private final List<EnumCommon> enumCommonList;
    private final List<UUID> scalarList;
    private final List<UUID> scalarCommonList;
    private final List<TypeCommon> typeCommonList;
    private final List<TypeImplemented1> typeImplementedList;
    private final List<TypeImplementedCommon> typeImplementedCommonList;
    private final List<TypeExtended1> typeExtendedList;
    private final List<TypeExtendedCommon> typeExtendedCommonList;
    private final List<Object> typeUnionList;
    private final List<Object> typeUnionCommonList;

    private TypeGrandChild1(
        final List<String> stringList,
        final List<Integer> intList,
        final List<Float> floatList,
        final List<Boolean> booleanList,
        final List<String> idList,
        final List<Enum1> enumList,
        final List<EnumCommon> enumCommonList,
        final List<UUID> scalarList,
        final List<UUID> scalarCommonList,
        final List<TypeCommon> typeCommonList,
        final List<TypeImplemented1> typeImplementedList,
        final List<TypeImplementedCommon> typeImplementedCommonList,
        final List<TypeExtended1> typeExtendedList,
        final List<TypeExtendedCommon> typeExtendedCommonList,
        final List<Object> typeUnionList,
        final List<Object> typeUnionCommonList) {
      this.stringList = stringList;
      this.intList = intList;
      this.floatList = floatList;
      this.booleanList = booleanList;
      this.idList = idList;
      this.enumList = enumList;
      this.enumCommonList = enumCommonList;
      this.scalarList = scalarList;
      this.scalarCommonList = scalarCommonList;
      this.typeCommonList = typeCommonList;
      this.typeImplementedList = typeImplementedList;
      this.typeImplementedCommonList = typeImplementedCommonList;
      this.typeExtendedList = typeExtendedList;
      this.typeExtendedCommonList = typeExtendedCommonList;
      this.typeUnionList = typeUnionList;
      this.typeUnionCommonList = typeUnionCommonList;
    }

    private static TypeGrandChild1Builder builder() {
      return new TypeGrandChild1Builder();
    }

    public List<Boolean> getBooleanList() {
      return this.booleanList;
    }

    public List<EnumCommon> getEnumCommonList() {
      return this.enumCommonList;
    }

    public List<Enum1> getEnumList() {
      return this.enumList;
    }

    public List<Float> getFloatList() {
      return this.floatList;
    }

    public List<String> getIdList() {
      return this.idList;
    }

    public List<Integer> getIntList() {
      return this.intList;
    }

    public List<UUID> getScalarCommonList() {
      return this.scalarCommonList;
    }

    public List<UUID> getScalarList() {
      return this.scalarList;
    }

    public List<String> getStringList() {
      return this.stringList;
    }

    public List<TypeCommon> getTypeCommonList() {
      return this.typeCommonList;
    }

    public List<TypeExtendedCommon> getTypeExtendedCommonList() {
      return this.typeExtendedCommonList;
    }

    public List<TypeExtended1> getTypeExtendedList() {
      return this.typeExtendedList;
    }

    public List<TypeImplementedCommon> getTypeImplementedCommonList() {
      return this.typeImplementedCommonList;
    }

    public List<TypeImplemented1> getTypeImplementedList() {
      return this.typeImplementedList;
    }

    public List<Object> getTypeUnionCommonList() {
      return this.typeUnionCommonList;
    }

    public List<Object> getTypeUnionList() {
      return this.typeUnionList;
    }

    private static final class TypeGrandChild1Builder {
      private List<String> stringList;
      private List<Integer> intList;
      private List<Float> floatList;
      private List<Boolean> booleanList;
      private List<String> idList;
      private List<Enum1> enumList;
      private List<EnumCommon> enumCommonList;
      private List<UUID> scalarList;
      private List<UUID> scalarCommonList;
      private List<TypeCommon> typeCommonList;
      private List<TypeImplemented1> typeImplementedList;
      private List<TypeImplementedCommon> typeImplementedCommonList;
      private List<TypeExtended1> typeExtendedList;
      private List<TypeExtendedCommon> typeExtendedCommonList;
      private List<Object> typeUnionList;
      private List<Object> typeUnionCommonList;

      private TypeGrandChild1Builder booleanList(final List<Boolean> booleanList) {
        this.booleanList = booleanList;
        return this;
      }

      private TypeGrandChild1 build() {
        return new TypeGrandChild1(
            this.stringList,
            this.intList,
            this.floatList,
            this.booleanList,
            this.idList,
            this.enumList,
            this.enumCommonList,
            this.scalarList,
            this.scalarCommonList,
            this.typeCommonList,
            this.typeImplementedList,
            this.typeImplementedCommonList,
            this.typeExtendedList,
            this.typeExtendedCommonList,
            this.typeUnionList,
            this.typeUnionCommonList);
      }

      private TypeGrandChild1Builder enumCommonList(final List<EnumCommon> enumCommonList) {
        this.enumCommonList = enumCommonList;
        return this;
      }

      private TypeGrandChild1Builder enumList(final List<Enum1> enumList) {
        this.enumList = enumList;
        return this;
      }

      private TypeGrandChild1Builder floatList(final List<Float> floatList) {
        this.floatList = floatList;
        return this;
      }

      private TypeGrandChild1Builder idList(final List<String> idList) {
        this.idList = idList;
        return this;
      }

      private TypeGrandChild1Builder intList(final List<Integer> intList) {
        this.intList = intList;
        return this;
      }

      private TypeGrandChild1Builder scalarCommonList(final List<UUID> scalarCommonList) {
        this.scalarCommonList = scalarCommonList;
        return this;
      }

      private TypeGrandChild1Builder scalarList(final List<UUID> scalarList) {
        this.scalarList = scalarList;
        return this;
      }

      private TypeGrandChild1Builder stringList(final List<String> stringList) {
        this.stringList = stringList;
        return this;
      }

      private TypeGrandChild1Builder typeCommonList(final List<TypeCommon> typeCommonList) {
        this.typeCommonList = typeCommonList;
        return this;
      }

      private TypeGrandChild1Builder typeExtendedCommonList(
          final List<TypeExtendedCommon> typeExtendedCommonList) {
        this.typeExtendedCommonList = typeExtendedCommonList;
        return this;
      }

      private TypeGrandChild1Builder typeExtendedList(final List<TypeExtended1> typeExtendedList) {
        this.typeExtendedList = typeExtendedList;
        return this;
      }

      private TypeGrandChild1Builder typeImplementedCommonList(
          final List<TypeImplementedCommon> typeImplementedCommonList) {
        this.typeImplementedCommonList = typeImplementedCommonList;
        return this;
      }

      private TypeGrandChild1Builder typeImplementedList(
          final List<TypeImplemented1> typeImplementedList) {
        this.typeImplementedList = typeImplementedList;
        return this;
      }

      private TypeGrandChild1Builder typeUnionCommonList(final List<Object> typeUnionCommonList) {
        this.typeUnionCommonList = typeUnionCommonList;
        return this;
      }

      private TypeGrandChild1Builder typeUnionList(final List<Object> typeUnionList) {
        this.typeUnionList = typeUnionList;
        return this;
      }
    }
  }

  private static final class TypeGrandChild2 {

    private final List<String> stringList;
    private final List<Integer> intList;
    private final List<Float> floatList;
    private final List<Boolean> booleanList;
    private final List<String> idList;
    private final List<Enum2> enumList;
    private final List<EnumCommon> enumCommonList;
    private final List<UUID> scalarList;
    private final List<UUID> scalarCommonList;
    private final List<TypeCommon> typeCommonList;
    private final List<TypeImplemented2> typeImplementedList;
    private final List<TypeImplementedCommon> typeImplementedCommonList;
    private final List<TypeExtended2> typeExtendedList;
    private final List<TypeExtendedCommon> typeExtendedCommonList;
    private final List<Object> typeUnionList;
    private final List<Object> typeUnionCommonList;

    private TypeGrandChild2(
        final List<String> stringList,
        final List<Integer> intList,
        final List<Float> floatList,
        final List<Boolean> booleanList,
        final List<String> idList,
        final List<Enum2> enumList,
        final List<EnumCommon> enumCommonList,
        final List<UUID> scalarList,
        final List<UUID> scalarCommonList,
        final List<TypeCommon> typeCommonList,
        final List<TypeImplemented2> typeImplementedList,
        final List<TypeImplementedCommon> typeImplementedCommonList,
        final List<TypeExtended2> typeExtendedList,
        final List<TypeExtendedCommon> typeExtendedCommonList,
        final List<Object> typeUnionList,
        final List<Object> typeUnionCommonList) {
      this.stringList = stringList;
      this.intList = intList;
      this.floatList = floatList;
      this.booleanList = booleanList;
      this.idList = idList;
      this.enumList = enumList;
      this.enumCommonList = enumCommonList;
      this.scalarList = scalarList;
      this.scalarCommonList = scalarCommonList;
      this.typeCommonList = typeCommonList;
      this.typeImplementedList = typeImplementedList;
      this.typeImplementedCommonList = typeImplementedCommonList;
      this.typeExtendedList = typeExtendedList;
      this.typeExtendedCommonList = typeExtendedCommonList;
      this.typeUnionList = typeUnionList;
      this.typeUnionCommonList = typeUnionCommonList;
    }

    private static TypeGrandChild2Builder builder() {
      return new TypeGrandChild2Builder();
    }

    public List<Boolean> getBooleanList() {
      return this.booleanList;
    }

    public List<EnumCommon> getEnumCommonList() {
      return this.enumCommonList;
    }

    public List<Enum2> getEnumList() {
      return this.enumList;
    }

    public List<Float> getFloatList() {
      return this.floatList;
    }

    public List<String> getIdList() {
      return this.idList;
    }

    public List<Integer> getIntList() {
      return this.intList;
    }

    public List<UUID> getScalarCommonList() {
      return this.scalarCommonList;
    }

    public List<UUID> getScalarList() {
      return this.scalarList;
    }

    public List<String> getStringList() {
      return this.stringList;
    }

    public List<TypeCommon> getTypeCommonList() {
      return this.typeCommonList;
    }

    public List<TypeExtendedCommon> getTypeExtendedCommonList() {
      return this.typeExtendedCommonList;
    }

    public List<TypeExtended2> getTypeExtendedList() {
      return this.typeExtendedList;
    }

    public List<TypeImplementedCommon> getTypeImplementedCommonList() {
      return this.typeImplementedCommonList;
    }

    public List<TypeImplemented2> getTypeImplementedList() {
      return this.typeImplementedList;
    }

    public List<Object> getTypeUnionCommonList() {
      return this.typeUnionCommonList;
    }

    public List<Object> getTypeUnionList() {
      return this.typeUnionList;
    }

    private static final class TypeGrandChild2Builder {
      private List<String> stringList;
      private List<Integer> intList;
      private List<Float> floatList;
      private List<Boolean> booleanList;
      private List<String> idList;
      private List<Enum2> enumList;
      private List<EnumCommon> enumCommonList;
      private List<UUID> scalarList;
      private List<UUID> scalarCommonList;
      private List<TypeCommon> typeCommonList;
      private List<TypeImplemented2> typeImplementedList;
      private List<TypeImplementedCommon> typeImplementedCommonList;
      private List<TypeExtended2> typeExtendedList;
      private List<TypeExtendedCommon> typeExtendedCommonList;
      private List<Object> typeUnionList;
      private List<Object> typeUnionCommonList;

      private TypeGrandChild2Builder booleanList(final List<Boolean> booleanList) {
        this.booleanList = booleanList;
        return this;
      }

      private TypeGrandChild2 build() {
        return new TypeGrandChild2(
            this.stringList,
            this.intList,
            this.floatList,
            this.booleanList,
            this.idList,
            this.enumList,
            this.enumCommonList,
            this.scalarList,
            this.scalarCommonList,
            this.typeCommonList,
            this.typeImplementedList,
            this.typeImplementedCommonList,
            this.typeExtendedList,
            this.typeExtendedCommonList,
            this.typeUnionList,
            this.typeUnionCommonList);
      }

      private TypeGrandChild2Builder enumCommonList(final List<EnumCommon> enumCommonList) {
        this.enumCommonList = enumCommonList;
        return this;
      }

      private TypeGrandChild2Builder enumList(final List<Enum2> enumList) {
        this.enumList = enumList;
        return this;
      }

      private TypeGrandChild2Builder floatList(final List<Float> floatList) {
        this.floatList = floatList;
        return this;
      }

      private TypeGrandChild2Builder idList(final List<String> idList) {
        this.idList = idList;
        return this;
      }

      private TypeGrandChild2Builder intList(final List<Integer> intList) {
        this.intList = intList;
        return this;
      }

      private TypeGrandChild2Builder scalarCommonList(final List<UUID> scalarCommonList) {
        this.scalarCommonList = scalarCommonList;
        return this;
      }

      private TypeGrandChild2Builder scalarList(final List<UUID> scalarList) {
        this.scalarList = scalarList;
        return this;
      }

      private TypeGrandChild2Builder stringList(final List<String> stringList) {
        this.stringList = stringList;
        return this;
      }

      private TypeGrandChild2Builder typeCommonList(final List<TypeCommon> typeCommonList) {
        this.typeCommonList = typeCommonList;
        return this;
      }

      private TypeGrandChild2Builder typeExtendedCommonList(
          final List<TypeExtendedCommon> typeExtendedCommonList) {
        this.typeExtendedCommonList = typeExtendedCommonList;
        return this;
      }

      private TypeGrandChild2Builder typeExtendedList(final List<TypeExtended2> typeExtendedList) {
        this.typeExtendedList = typeExtendedList;
        return this;
      }

      private TypeGrandChild2Builder typeImplementedCommonList(
          final List<TypeImplementedCommon> typeImplementedCommonList) {
        this.typeImplementedCommonList = typeImplementedCommonList;
        return this;
      }

      private TypeGrandChild2Builder typeImplementedList(
          final List<TypeImplemented2> typeImplementedList) {
        this.typeImplementedList = typeImplementedList;
        return this;
      }

      private TypeGrandChild2Builder typeUnionCommonList(final List<Object> typeUnionCommonList) {
        this.typeUnionCommonList = typeUnionCommonList;
        return this;
      }

      private TypeGrandChild2Builder typeUnionList(final List<Object> typeUnionList) {
        this.typeUnionList = typeUnionList;
        return this;
      }
    }
  }

  private static final class TypeImplemented1 {

    private final String someString;

    private TypeImplemented1(final String someString) {
      this.someString = someString;
    }

    public String getSomeString() {
      return this.someString;
    }
  }

  private static final class TypeExtended1 {

    private final String someString;

    private TypeExtended1(final String someString) {
      this.someString = someString;
    }

    public String getSomeString() {
      return this.someString;
    }
  }

  private static final class TypeImplemented2 {

    private final String someString;

    private TypeImplemented2(final String someString) {
      this.someString = someString;
    }

    public String getSomeString() {
      return this.someString;
    }
  }

  private static final class TypeExtended2 {

    private final String someString;

    private TypeExtended2(final String someString) {
      this.someString = someString;
    }

    public String getSomeString() {
      return this.someString;
    }
  }

  private static final class TypeCommon {

    private final String someString;

    private TypeCommon(final String someString) {
      this.someString = someString;
    }

    public String getSomeString() {
      return this.someString;
    }
  }

  private static final class TypeImplementedCommon {

    private final String someString;

    private TypeImplementedCommon(final String someString) {
      this.someString = someString;
    }

    public String getSomeString() {
      return this.someString;
    }
  }

  private static final class TypeExtendedCommon {

    private final String someString;
    private final String someOtherString;

    private TypeExtendedCommon(final String someString, final String someOtherString) {
      this.someString = someString;
      this.someOtherString = someOtherString;
    }

    public String getSomeOtherString() {
      return this.someOtherString;
    }

    public String getSomeString() {
      return this.someString;
    }
  }

  private static final class TypeUnionCommonInt {

    private final Integer someInteger;

    public TypeUnionCommonInt(final Integer someInteger) {
      this.someInteger = someInteger;
    }

    public String get__typename() {
      return "TypeUnionCommonInt";
    }

    public Integer someInteger() {
      return this.someInteger;
    }
  }
}
