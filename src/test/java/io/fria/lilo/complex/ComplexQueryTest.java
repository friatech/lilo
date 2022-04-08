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
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import static graphql.schema.idl.TypeRuntimeWiring.newTypeWiring;
import static io.fria.lilo.TestUtils.createGraphQL;
import static io.fria.lilo.TestUtils.createSchemaSource;
import static io.fria.lilo.TestUtils.loadResource;

class ComplexQueryTest {

    private static final String              SCHEMA1_NAME = "project1";
    private static final String              SCHEMA2_NAME = "project2";
    private static final List                NULL_LIST;
    private static final Map<String, Object> RESULT_MAP   = Map.of("id", 1, "name", "John", "age", 34, "enabled", true, "role", "ADMIN", "username", "john", "__typename", "WebUser");

    static {
        NULL_LIST = new ArrayList<>();
        NULL_LIST.add(null);
    }

    private static TypeRuntimeWiring.Builder addServer1Fetchers(final TypeRuntimeWiring.Builder builder) {

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
            .dataFetcher("server1queryScalarB", env -> UUID.fromString("22222222-2222-2222-2222-222222222222"))
            .dataFetcher("server1queryScalarC", env -> null)
            .dataFetcher("server1queryScalarD", env -> NULL_LIST)
            .dataFetcher("server1queryScalarE", env -> List.of(UUID.fromString("55555555-5555-5555-5555-555555555555")))
            .dataFetcher("server1queryScalarF", env -> List.of(UUID.fromString("66666666-6666-6666-6666-666666666666")))
            .dataFetcher("server1queryScalarCommonA", env -> null)
            .dataFetcher("server1queryScalarCommonB", env -> UUID.fromString("22222222-2222-2222-2222-222222222222"))
            .dataFetcher("server1queryScalarCommonC", env -> null)
            .dataFetcher("server1queryScalarCommonD", env -> NULL_LIST)
            .dataFetcher("server1queryScalarCommonE", env -> List.of(UUID.fromString("55555555-5555-5555-5555-555555555555")))
            .dataFetcher("server1queryScalarCommonF", env -> UUID.fromString("66666666-6666-6666-6666-666666666666"))
            .dataFetcher("server1queryTypeA", env -> null)
            .dataFetcher("server1queryTypeB", env -> new ObjectMapper().readValue(new ObjectMapper().writeValueAsString(createType1()), Map.class))
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
            .dataFetcher("server1queryInputA", env -> null)
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

    private static TypeRuntimeWiring.Builder addServer2Fetchers(final TypeRuntimeWiring.Builder builder) {

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
            .dataFetcher("server2queryScalarB", env -> UUID.fromString("22222222-2222-2222-2222-222222222222"))
            .dataFetcher("server2queryScalarC", env -> null)
            .dataFetcher("server2queryScalarD", env -> NULL_LIST)
            .dataFetcher("server2queryScalarE", env -> List.of(UUID.fromString("55555555-5555-5555-5555-555555555555")))
            .dataFetcher("server2queryScalarF", env -> List.of(UUID.fromString("66666666-6666-6666-6666-666666666666")))
            .dataFetcher("server2queryScalarCommonA", env -> null)
            .dataFetcher("server2queryScalarCommonB", env -> UUID.fromString("22222222-2222-2222-2222-222222222222"))
            .dataFetcher("server2queryScalarCommonC", env -> null)
            .dataFetcher("server2queryScalarCommonD", env -> NULL_LIST)
            .dataFetcher("server2queryScalarCommonE", env -> List.of(UUID.fromString("55555555-5555-5555-5555-555555555555")))
            .dataFetcher("server2queryScalarCommonF", env -> UUID.fromString("66666666-6666-6666-6666-666666666666"))
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
            .dataFetcher("server2queryInputA", env -> null)
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

    private static RuntimeWiring createWiring() {

        return RuntimeWiring.newRuntimeWiring()
            .type(addServer2Fetchers(addServer1Fetchers(newTypeWiring("Queries"))))
            .type(
                newTypeWiring("Mutations")
                    .dataFetcher("create", env -> RESULT_MAP)
                    .dataFetcher("delete", env -> null)
            )
            .type(newTypeWiring("Interface1").typeResolver(env -> null))
            .type(newTypeWiring("Interface2").typeResolver(env -> null))
            .type(newTypeWiring("InterfaceCommon").typeResolver(env -> null))
            .type(newTypeWiring("TypeUnion1").typeResolver(env -> env.getSchema().getObjectType("TypeUnion1Int")))
            .type(newTypeWiring("TypeUnion2").typeResolver(env -> env.getSchema().getObjectType("TypeUnion2Int")))
            .type(newTypeWiring("TypeUnionCommon").typeResolver(env -> env.getSchema().getObjectType("TypeUnionCommonInt")))
            .scalar(GraphQLScalarType.newScalar().name("Scalar1").coercing(new DummyCoercing()).build())
            .scalar(GraphQLScalarType.newScalar().name("Scalar2").coercing(new DummyCoercing()).build())
            .scalar(GraphQLScalarType.newScalar().name("ScalarCommon").coercing(new DummyCoercing()).build())
            .build();
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
                                    .enumCommonList(List.of(EnumCommon.ENUM_COMMON_VALUE_A, EnumCommon.ENUM_COMMON_VALUE_B))
                                    .scalarList(List.of(UUID.fromString("22222222-2222-2222-2222-222222222222"), UUID.fromString("55555555-5555-5555-5555-555555555555")))
                                    .scalarCommonList(List.of(UUID.fromString("22222222-2222-2222-2222-222222222222"), UUID.fromString("55555555-5555-5555-5555-555555555555")))
                                    .typeCommonList(List.of(TypeCommon.builder().someString("typeCommon1").build()))
                                    .typeImplementedList(List.of(TypeImplemented1.builder().someString("typeImplemented1").build()))
                                    .typeImplementedCommonList(List.of(TypeCommon.builder().someString("typeImplementedCommon1").build()))
                                    .typeExtendedList(List.of(TypeExtended1.builder().someString("typeExtended1").build()))
                                    .typeExtendedCommonList(List.of(TypeExtendedCommon.builder().someString("typeExtendedCommon1").build()))
                                    .typeUnionList(List.of(Map.of("someInteger", 1001, "__typename", "TypeUnion1Int")))
                                    .typeUnionCommonList(List.of(TypeUnionCommonInt.builder().someInteger(3001).build()))
                                    .build()
                            )
                        )
                        .build()
                )
            )
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
                                    .enumCommonList(List.of(EnumCommon.ENUM_COMMON_VALUE_A, EnumCommon.ENUM_COMMON_VALUE_B))
                                    .scalarList(List.of(UUID.fromString("22222222-2222-2222-2222-222222222222"), UUID.fromString("55555555-5555-5555-5555-555555555555")))
                                    .scalarCommonList(List.of(UUID.fromString("22222222-2222-2222-2222-222222222222"), UUID.fromString("55555555-5555-5555-5555-555555555555")))
                                    .typeCommonList(List.of(TypeCommon.builder().someString("typeCommon2").build()))
                                    .typeImplementedList(List.of(TypeImplemented2.builder().someString("typeImplemented2").build()))
                                    .typeImplementedCommonList(List.of(TypeCommon.builder().someString("typeImplementedCommon2").build()))
                                    .typeExtendedList(List.of(TypeExtended2.builder().someString("typeExtended2").build()))
                                    .typeExtendedCommonList(List.of(TypeExtendedCommon.builder().someString("typeExtendedCommon2").build()))
                                    .typeUnionList(List.of(Map.of("someInteger", 2002, "__typename", "TypeUnion2Int")))
                                    .typeUnionCommonList(List.of(TypeUnionCommonInt.builder().someInteger(3001).build()))
                                    .build()
                            )
                        )
                        .build()
                )
            )
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
        variables.put("s1scalarParam08", List.of(UUID.fromString("11111111-1111-1111-1111-111111111111")));
        variables.put("s1scalarParam09", List.of(UUID.fromString("11111111-1111-1111-1111-111111111111")));
        variables.put("s1scalarParam10", List.of(UUID.fromString("11111111-1111-1111-1111-111111111111")));
        variables.put("s1scalarCommonParam01", UUID.fromString("11111111-1111-1111-1111-111111111111"));
        variables.put("s1scalarCommonParam02", null);
        variables.put("s1scalarCommonParam03", UUID.fromString("11111111-1111-1111-1111-111111111111"));
        variables.put("s1scalarCommonParam04", UUID.fromString("11111111-1111-1111-1111-111111111111"));
        variables.put("s1scalarCommonParam05", null);
        variables.put("s1scalarCommonParam06", UUID.fromString("11111111-1111-1111-1111-111111111111"));
        variables.put("s1scalarCommonParam07", NULL_LIST);
        variables.put("s1scalarCommonParam08", List.of(UUID.fromString("11111111-1111-1111-1111-111111111111")));
        variables.put("s1scalarCommonParam09", List.of(UUID.fromString("11111111-1111-1111-1111-111111111111")));
        variables.put("s1scalarCommonParam10", List.of(UUID.fromString("11111111-1111-1111-1111-111111111111")));
        variables.put("s1typeParam01", "Type1_01");
        variables.put("s1typeParam02", null);
        variables.put("s1typeParam03", "Type1_03");
        variables.put("s1typeParam04", "Type1_04");
        variables.put("s1typeParam05", null);
        variables.put("s1typeParam06", List.of("Type1_06"));
        variables.put("s1typeParam07", NULL_LIST);
        variables.put("s1typeParam08", List.of("Type1_08"));
        variables.put("s1typeParam09", List.of("Type1_09"));
        variables.put("s1typeParam10", List.of("Type1_10"));
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
        variables.put("s2scalarParam08", List.of(UUID.fromString("11111111-1111-1111-1111-111111111111")));
        variables.put("s2scalarParam09", List.of(UUID.fromString("11111111-1111-1111-1111-111111111111")));
        variables.put("s2scalarParam10", List.of(UUID.fromString("11111111-1111-1111-1111-111111111111")));
        variables.put("s2scalarCommonParam01", UUID.fromString("11111111-1111-1111-1111-111111111111"));
        variables.put("s2scalarCommonParam02", null);
        variables.put("s2scalarCommonParam03", UUID.fromString("11111111-1111-1111-1111-111111111111"));
        variables.put("s2scalarCommonParam04", UUID.fromString("11111111-1111-1111-1111-111111111111"));
        variables.put("s2scalarCommonParam05", null);
        variables.put("s2scalarCommonParam06", UUID.fromString("11111111-1111-1111-1111-111111111111"));
        variables.put("s2scalarCommonParam07", NULL_LIST);
        variables.put("s2scalarCommonParam08", List.of(UUID.fromString("11111111-1111-1111-1111-111111111111")));
        variables.put("s2scalarCommonParam09", List.of(UUID.fromString("11111111-1111-1111-1111-111111111111")));
        variables.put("s2scalarCommonParam10", List.of(UUID.fromString("11111111-1111-1111-1111-111111111111")));
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
        variables.put("includeGrandChildren1", true);
        variables.put("includeGrandChildren2", false);

        final ExecutionInput executionInput = ExecutionInput.newExecutionInput()
            .query(loadResource("/complex/query.graphql"))
            .variables(variables)
            .build();

        final GraphQL             combinedGraphQL = createGraphQL("/complex/combined.graphqls", createWiring());
        final ExecutionResult     result          = combinedGraphQL.execute(executionInput);
        final Map<String, Object> expected        = result.getData();
        Assertions.assertNotNull(expected);

        // Stitching result ----------------------------------------------------
        final var project1GraphQL         = createGraphQL("/complex/project1.graphqls", createWiring());
        final var project2GraphQL         = createGraphQL("/complex/project2.graphqls", createWiring());
        final var introspection1Retriever = new TestUtils.TestIntrospectionRetriever(project1GraphQL);
        final var introspection2Retriever = new TestUtils.TestIntrospectionRetriever(project2GraphQL);
        final var query1Retriever         = new TestUtils.TestQueryRetriever(combinedGraphQL);
        final var query2Retriever         = new TestUtils.TestQueryRetriever(project2GraphQL);

        final Lilo lilo = Lilo.builder()
            .addSource(createSchemaSource(SCHEMA1_NAME, introspection1Retriever, query1Retriever))
            .addSource(createSchemaSource(SCHEMA2_NAME, introspection2Retriever, query2Retriever))
            .build();

        final ExecutionResult stitchResult = lilo.stitch(executionInput);

        final ObjectMapper objectMapper = new ObjectMapper();
        Assertions.assertEquals(objectMapper.writeValueAsString(expected), objectMapper.writeValueAsString(stitchResult.getData()));
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

    @Builder
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    private static class Type1 {
        String           someString;
        List<TypeChild1> children;
    }

    @Builder
    private static class Type2 {
        String           someString;
        List<TypeChild2> children;
    }

    @Builder
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    private static class TypeChild1 {
        String                someString;
        List<TypeGrandChild1> grandChildren;
    }

    @Builder
    private static class TypeChild2 {
        String                someString;
        List<TypeGrandChild2> grandChildren;
    }

    @Builder
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    private static class TypeGrandChild1 {
        List<String>             stringList;
        List<Integer>            intList;
        List<Float>              floatList;
        List<Boolean>            booleanList;
        List<String>             idList;
        List<Enum1>              enumList;
        List<EnumCommon>         enumCommonList;
        List<UUID>               scalarList;
        List<UUID>               scalarCommonList;
        List<TypeCommon>         typeCommonList;
        List<TypeImplemented1>   typeImplementedList;
        List<TypeCommon>         typeImplementedCommonList;
        List<TypeExtended1>      typeExtendedList;
        List<TypeExtendedCommon> typeExtendedCommonList;
        List<Object>             typeUnionList;
        List<Object>             typeUnionCommonList;
    }

    @Builder
    private static class TypeGrandChild2 {
        List<String>             stringList;
        List<Integer>            intList;
        List<Float>              floatList;
        List<Boolean>            booleanList;
        List<String>             idList;
        List<Enum2>              enumList;
        List<EnumCommon>         enumCommonList;
        List<UUID>               scalarList;
        List<UUID>               scalarCommonList;
        List<TypeCommon>         typeCommonList;
        List<TypeImplemented2>   typeImplementedList;
        List<TypeCommon>         typeImplementedCommonList;
        List<TypeExtended2>      typeExtendedList;
        List<TypeExtendedCommon> typeExtendedCommonList;
        List<Object>             typeUnionList;
        List<Object>             typeUnionCommonList;
    }

    @Builder
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    private static class TypeImplemented1 {
        String someString;
    }

    @Builder
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    private static class TypeExtended1 {
        String someString;
    }

    @Builder
    private static class TypeImplemented2 {
        String someString;
    }

    @Builder
    private static class TypeExtended2 {
        String someString;
    }

    @Builder
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    private static class TypeCommon {
        String someString;
    }

    @Builder
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    private static class TypeExtendedCommon {
        String someString;
        String someOtherString;
    }

    @Builder
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    private static class TypeUnionCommonInt {
        String  __typename = "TypeUnionCommonInt";
        Integer someInteger;
    }
}