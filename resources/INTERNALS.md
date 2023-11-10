# Lilo nasil calisir?

Oncelikle bir GraphQL Schema kaynagi belirtmek gereklidir. GraphQL schema'si aslinda bir sistemde calistirilacak
read only queryler, ya da data manipulasyonu yapmamizi saglayan mutation'lar ya da surekli read only data
cekmemizi saglayan subscriptionlarin adlarini, metod imzalarini, kullandigi veri tiplerini belirten bir sablondur.

Schema Source yerelde tanimlanmis bir dosya olabilecegi gibi uzaktaki bir baska sunucu da olabilir.
Uzaktaki sunucudaki schema bilgilerini yine graphql'in kendisine sorarak elde edebiliriz. Bu database'deki tum
sutunlari ve tablolari veren information schema gibidir aslinda. Database hakkindaki bilgiyi nasil SQL ile aliyorsak.
GraphQL hakkindaki bilgiyi de yine graphql introspection vasitasi ile aliriz.

Lilo'da 2 temel tip SchemaSource bulunmaktadir. Birincisi `RemoteSchemaSource` digeri ise `DefinedSchemaSource`'dur.

Bir `DefinedSchemaSource` icin bir `.graphqls` dosyasina ihtiyacimiz vardir. Bir de wiring tanimina. `.graphqls`
dosyasinda tahmin edilebilecegi gibi graphql schema tanimi bulunur. Runtime wiring ise daha cok hangi query talep
edildiginde  hangi metod calisacak hangi veri tipi nasil serialize edilecek gibi bilgiler bulunur.

`RemoteSchemaSource` icin ihtiyacimiz olan ise uzaktaki sunucunun graphql endpoint URL'i, bir adet Introspection
retriever ve `QueryRetriever` tanimlaridir. Bu retriever'lar uzaktaki sunucudan nasil veri alacagimizi programatik
olarak tanimlamamiza yardimci olur. HTTP ya da baska bir protokol kullanarak veri okumak mumkundur. HTTP icin Lilo
`DefaultRemoteIntrospectionRetriever` ve `DefaultRemoteQueryRetriever` siniflari sunmaktadir. Bunlar sayesinde basit
ihtiyaclar icin ekstra retreiver tanimlamaya gerek kalmadan hizlica kullanilabilir.

Basit Lilo tanimlamasi su sekildedir.

```java
final Lilo lilo =
Lilo.builder()
    .addSource(
        DefinedSchemaSource.create(
            SCHEMA1_NAME, loadResource("/defined/greeting1.graphqls"), WIRING))
    .addSource(
        RemoteSchemaSource.create(SCHEMA2_NAME, introspection2Retriever, query2Retriever))
    .build();
```

Bu goruldugu gibi bir builder pattern ve sonucta Lilo sinifini olusturur.

Daha sonra gelen graphql query `lilo.stitch(executionInput);` komutu ile calistirilir. Ornegin gelen graphql query'de 2
farkli query talebi oldugunu varsayalim `greeting1` ve `greeting2`. Lilo once gelen query'yi ayristirir ve daha sonra
teker teker bu query'leri ilgili uzak sunuculara sorar. Ornegin `greeting1` query'si sunucu1'de `greeting2` query'si
sunucu 2'de olabilir. Daha sonra bu cevaplari birlestirerek tek bir cevapmis gibi geri doner. Bu isleme kisaca stitching
denir.

Stitching sistemini yaparken extra graphql parsing operasyonlari implemente edilmedi. `grahpql-java` kutuphanesi bu islemleri
cok iyi bir sekilde zaten yapmakta. Buyuk olcude bu kutuphane kullanildi. Yerel schema tanimlari icin `RuntimeWiring` yaptigimizdan
bahsetmistik. Bu tanimlamalar icinde `DataFetcher` adi altinda metod tanimlamalari mumkundur. Lilo su akis ile calisir.

1. Uzak ve Yerel schema kaynaklari belirtilir.
2. Lilo Uzak kaynaklar icin bir introspection query'si gonderip tum tanimlamalari ceker.
3. Lilo tum bu uzak tanimlari birlestirip tek bir buyuk tanim haline getirir.
3. Lilo daha sonra her query ve mutation icin bir datafetcher olusturur.
4. Daha sonra lilo'dan bir query calistirmasini isteriz.
5. Lilo graphql-java vasitasi ile graphql request icindeki tum alt query'ler icin tanimlanan Remote QueryRetriever'lari
   calistirarak sorgu yapar.
6. Cevaplar DataFetcher'lar vasitasi ile graphl-java kutuphanesine teslim edilir.
7. Cevaplari grahpql-java birlestirir ve ExecutionResult olarak doner.

Detayli aciklamalar:

1. Uzak ve Yerel schema kaynaklari belirtilir

```graphql
scalar Text

type Query {
    greeting1: Text!
}
```

```java
final RuntimeWiring myWiring =
    RuntimeWiring.newRuntimeWiring()
        .type(
            newTypeWiring("Query")
                .dataFetcher("greeting1", env -> "Hello greeting1")
                .dataFetcher("greeting2", env -> "Hello greeting2"))
        .scalar(
            GraphQLScalarType.newScalar()
                .name("Text")
                .coercing(new GraphqlStringCoercing())
                .build())
        .build();

final Lilo lilo = Lilo.builder()
    .addSource(DefinedSchemaSource.create("MyLocalSchema", loadResource("local.graphqls"), myWiring))
    .addSource(RemoteSchemaSource.create("MyRemoteSchema", introspection2Retriever, query2Retriever))
    .build();
```

Tum eklenen schema source'lar `Lilo` sinifi icinde `schemaSources` HashMap'inde biriktirilir.

Lilo'nun schema eklemenin yaninda bir cok ozelligi daha vardir introspection ya da default exception handler ekleme gibi
ama Lilo tum bu bilgilerden bir LiloContext instance'i olusturur. Neden LiloContext adli bir sinifa ihtiyac duyuldu?
Sanirim Lilo bir builder pattern olarak kalmaliydi. LiloContext ise daha cok graphql bilgilerinin icerildigi bir Lilo
sinifi gibi kaldi.

Lilo ilk query'nin stitching islemi talep edilene kadar hic bir introspection talebinde bulunmaz. Bu zamana kadar
yapilanlar yalnizca sinif olusturmadan ibaretti. Hicbir network iletisimi olmadi.

2. Lilo Uzak kaynaklar icin bir introspection query'si gonderip tum tanimlamalari ceker.

Gelen her sorgu icin

```java
lilo.stitch(executionInput);
```

calismalidir. ExecutionInput aslinda string query'den baska bir sey degildir fakat `graphql-java` kutuphanesi ile
interoperability  icin `ExecutionInput` sinifi kullanilir.

TODO: Ayrica string query alinacak sekilde guncelleme de yapilabilir.

`stitch` metodu calismadan once tanimlanan introspection fetch etme metoduna bakar. Bazi sistemler dinamik graphql
schema'larina sahip olabilir. Calisma zamaninda yeni mutation ve query'ler ekleniyor olabilir. Boyle durumlarda her
stitching operasyonundan once internal introspection cache'inin invalidate edilmesi gereklidir.

```java
if (IntrospectionFetchingMode.FETCH_BEFORE_EVERY_REQUEST == this.context.getIntrospectionFetchingMode()) {
  this.context.invalidateAll();
}
```

`LiloContext` icindeki `invalidateAll` metodu ise basitce her schema'yi dolasarak invalidate eder ve `schemasAreNotLoaded`
flag'ini set eder.

```java
public void invalidateAll() {
  this.sourceMap.values().forEach(SchemaSource::invalidate);
  this.schemasAreNotLoaded = true;
}
```

Her `SchemaSource` sinifinin aslinda kendi icerisinde bir cache'i vardir. Bunlar da invalidate edilir.

```java
public void invalidate() {
  this.typeDefinitionRegistry = null;
}
```

TODO: SchemaSource interface'i Abstract class olabilir invalidate metodu ayni gibi. Bir cok ayni sey bulunuyor olabilir.

`stitch` metodu eger hic bir invalidation saglamayacak ise LiloContex sinifindan olusturulmus merged graphql schema
talep edilir.

```java
this.context.getGraphQL(executionInput)
```

TODO: graphQL'in ismi mergedGraphQL olarak degistirilebilir.

Eger schema yuklenmemis ise tekrar yukleme islemi gerceklestirilir. Temel introspection bu seviyede olur.

```java
@NotNull
CompletableFuture<GraphQL> getGraphQLAsync(final @Nullable ExecutionInput executionInput) {

  if (this.schemasAreNotLoaded()) {
    return this.reloadGraphQL(executionInput == null ? null : executionInput.getLocalContext());
  }

  return CompletableFuture.supplyAsync(() -> this.graphQL);
}
```

TODO: Neden bu kadar karmasik bir `schemasAreNotLoaded` metoduna ihtiyac duyuyoruz?

```java
public @NotNull CompletableFuture<GraphQL> reloadGraphQL(final @Nullable Object localContext) {

  return this.loadSources(localContext)
      .thenApply(
          sourceMapClone -> {
            LiloContext.this.graphQL = LiloContext.this.createGraphQL(sourceMapClone);
            LiloContext.this.sourceMap = toSourceMap(sourceMapClone.stream());

            return LiloContext.this.graphQL;
          });
}
```

Bu metoda localContext gonderiyoruz. Cunku bazen sunuculardaki Authentication farkli olabilir. Gelen query'den
bir token pass etmek gerekebilir bu nedenle localContext IntrospectionRetriever'a kadar ulasmali.

Bu metod temel olarak `loadSources` metodunu cagiriyor bu metod ise asenkron bir metod tum source'larin
introspection'inin tamamlanmasi bekleniliyor.

```java
  private @NotNull CompletableFuture<List<SchemaSource>> loadSources(
      final @Nullable Object localContext) {

    CompletableFuture<List<SchemaSource>> combined = CompletableFuture.supplyAsync(ArrayList::new);

    final List<CompletableFuture<SchemaSource>> futures =
        this.sourceMap.values().stream()
            .map(
                ss -> {
                  if (ss.isSchemaNotLoaded()) {
                    return ss.loadSchema(LiloContext.this, localContext);
                  } else {
                    return CompletableFuture.supplyAsync(() -> ss);
                  }
                })
            .collect(Collectors.toList());

    for (final CompletableFuture<SchemaSource> future : futures) {
      combined =
          combined.thenCombine(
              future,
              (combinedSchemaSources, baseSchemaSource) -> {
                combinedSchemaSources.add(baseSchemaSource);
                return combinedSchemaSources;
              });
    }

    return combined;
  }
```

TODO: This method basically combines a list of completable futures in one big completable feature can be easier.

TODO: Neden loadSchema SchemaSource donuyor. Gereksiz bence

`loadSchema` metodu introspectionRetriever'i cagiriyor daha sonra `fetchIntrospection` metodunu cagiriyor ki
bu isim yanlis olabilir. Temel olan burada introspection'in fetch edilmesi degil aslinda typewiring'lerin olusturulmasi

TODO: Bu metodlar sinifin karmasikligini arttiriyor ayri bir utility class'a konulabilir ayrica bazi metodlar da yanlis
Utility class'da `SchemaMerger.getOperationTypeNames` gibi

TODO: `GraphQLResult` external bir class olabilir.

Tum remoteSchemeSource'lardaki loadSchema metodlari cagrildiktan sonra

```java
LiloContext.this.graphQL = LiloContext.this.createGraphQL(sourceMapClone);
```

merged graphQL olusturulur.

TODO: createGraphQL static metod olabilir ayrica baska bir utility class'da da olabilir.


## Subscription

```
> HTTP GET /graphql
< HTTP 1.1 101 Switching protocols
> {"type":"connection_init","payload":{}}
< {"id":null,"type":"connection_ack","payload":{}}
> {"id":"ad3cc738-116a-4228-8337-d4b985378890","type":"subscribe","payload":{"query":"subscription {\n  greeting1Subscription\n}","variables":{}}}
< {"id":"ad3cc738-116a-4228-8337-d4b985378890","type":"next","payload":{"data":{"greeting1Subscription":"Hi!"}}}
< {"id":"ad3cc738-116a-4228-8337-d4b985378890","type":"next","payload":{"data":{"greeting1Subscription":"Bonjour!"}}}
< {"id":"ad3cc738-116a-4228-8337-d4b985378890","type":"next","payload":{"data":{"greeting1Subscription":"Hola!"}}}
> WebSocket Connection Close
```

Oncelikle introspection varolan sekilde calisir. Eger bir subscription kullanacaksak Lilo taniminda
belirtmemiz gerekir.

```java
Lilo.builder()
  .addSource(
    RemoteSchemaSource.create(
      SOURCE1_NAME,
      new IntrospectionRetrieverImpl(SOURCE1_GRAPHQL_URL),
      new QueryRetrieverImpl(SOURCE1_GRAPHQL_URL),
      new SubscriptionRetrieverImpl(SOURCE1_GRAPHQL_WS_URL)
    )
  )
);
```

datafetcher taniminda subscription var ise data fetch edilmez onun yerine subscribtionRetriever.sendQuery metodu cagirilir.
subscriptionRetriever zaten bir connection yoksa otomatik olarak baslatir.



------------------------

subscription test case'leri:

- Tek bir altair connection baslat. Ardarda kapatip tekrar baslat
- Altair connection baslat. Connection'i kapattiginda server'da "Data generation ended" logunu gormek gerek bu graceful shutdown manasina gelir.
- Ayni anda 2 farkli server'a deneme yap


