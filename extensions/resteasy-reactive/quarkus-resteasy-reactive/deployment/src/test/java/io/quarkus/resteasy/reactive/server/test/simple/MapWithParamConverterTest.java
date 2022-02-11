package io.quarkus.resteasy.reactive.server.test.simple;

import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.stream.Collectors;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.ext.ParamConverter;
import javax.ws.rs.ext.ParamConverterProvider;
import javax.ws.rs.ext.Provider;

import org.hamcrest.Matchers;
import org.jboss.resteasy.reactive.RestQuery;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.type.TypeFactory;

import io.quarkus.test.QuarkusUnitTest;
import io.restassured.RestAssured;

public class MapWithParamConverterTest {

    @RegisterExtension
    static QuarkusUnitTest test = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(HelloResource.class, JsonParamConverter.class, CustomParamConverterProvider.class));

    @Test
    public void noQueryParams() {
        RestAssured.get("/hello/map")
                .then().statusCode(200).body(Matchers.equalTo(""));
    }

    @Test
    public void jsonQueryParamMap() {
        RestAssured
                .with()
                .queryParam("param", "{\"a\":\"1\",\"b\":\"2\"}")
                .get("/hello/map")
                .then().statusCode(200).body(Matchers.equalTo("a:1-b:2"));
    }

    @Test
    public void jsonQueryParamSet() {
        RestAssured
                .with()
                .queryParam("param", "[3,4,5]")
                .get("/hello/set")
                .then().statusCode(200).body(Matchers.equalTo("3-4-5"));
    }

    @Test
    public void jsonQueryParamSortedSet() {
        RestAssured
                .with()
                .queryParam("param", "[7,8,9]")
                .get("/hello/sortedset")
                .then().statusCode(200).body(Matchers.equalTo("7-8-9"));
    }

    @Test
    public void jsonQueryParamPojoList() {
        RestAssured
                .with()
                .queryParam("param", "[{\"field\":2},{\"field\":4}]")
                .get("/hello/pojolist")
                .then().statusCode(200).body(Matchers.equalTo("2-4"));
    }

    @Test
    public void jsonQueryParamStringList() {
        RestAssured
                .with()
                .queryParam("param", "a")
                .queryParam("param", "b")
                .queryParam("param", "c")
                .get("/hello/stringlist")
                .then().statusCode(200).body(Matchers.equalTo("a-b-c"));
    }

    @Test
    public void jsonQueryParamNestedList() {
        RestAssured
                .with()
                .queryParam("param", "[[{\"field\":2},{\"field\":4}],[{\"field\":1},{\"field\":3}]]")
                .get("/hello/nestedlist")
                .then().statusCode(200).body(Matchers.equalTo("2-4-1-3"));
    }

    @Path("hello")
    public static class HelloResource {

        @GET
        @Produces("text/plain")
        @Path("map")
        public String map(@RestQuery("param") Map<String, Integer> mapParam) {
            return Optional.ofNullable(mapParam)
                    .orElse(Map.of())
                    .entrySet().stream().map(e -> e.getKey() + ":" + e.getValue())
                    .collect(Collectors.joining("-"));
        }

        @GET
        @Produces("text/plain")
        @Path("set")
        public String set(@RestQuery("param") Set<Integer> mapParam) {
            return Optional.ofNullable(mapParam)
                    .orElse(Set.of())
                    .stream()
                    .map(Object::toString)
                    .collect(Collectors.joining("-"));
        }

        @GET
        @Produces("text/plain")
        @Path("sortedset")
        public String sortedSet(@RestQuery("param") SortedSet<Integer> mapParam) {
            return Optional.ofNullable(mapParam)
                    .orElse(new TreeSet<>())
                    .stream()
                    .map(Object::toString)
                    .collect(Collectors.joining("-"));
        }

        @GET
        @Produces("text/plain")
        @Path("pojolist")
        public String pojoList(@RestQuery("param") List<Pojo> listParam) {
            return Optional.ofNullable(listParam)
                    .orElse(List.of())
                    .stream()
                    .map(pojo -> pojo.field.toString())
                    .collect(Collectors.joining("-"));
        }

        @GET
        @Produces("text/plain")
        @Path("stringlist")
        public String stringList(@RestQuery("param") List<String> listParam) {
            return Optional.ofNullable(listParam)
                    .orElse(List.of())
                    .stream().collect(Collectors.joining("-"));
        }

        //        @GET
        //        @Produces("text/plain")
        //        @Path("nestedlist")
        //        public String nestedList(@RestQuery("param") List<List<Pojo>> listParam) {
        //            return Optional.ofNullable(listParam).orElse(List.of())
        //                    .stream()
        //                    .flatMap(Collection::stream)
        //                    .map(pojo -> pojo.field.toString())
        //                    .collect(Collectors.joining("-"));
        //        }
    }

    @Provider
    static class CustomParamConverterProvider implements ParamConverterProvider {

        @Override
        public <T> ParamConverter<T> getConverter(Class<T> rawType, Type genericType, Annotation[] annotations) {
            if (rawType == Map.class || rawType == Set.class || rawType == SortedSet.class ||
                    (rawType == List.class && genericType != null
                            && genericType.getTypeName().equals("java.util.List<" + Pojo.class.getName() + ">")))
                return new JsonParamConverter<>(rawType, genericType);
            return null;
        }

    }

    static class Pojo {
        Integer field;
    }

    static class JsonParamConverter<T> implements ParamConverter<T> {

        Class<T> rawType;
        JavaType genericType;
        ObjectMapper objectMapper = new ObjectMapper();

        public JsonParamConverter(Class<T> rawType, Type genericType) {
            this.genericType = genericType != null ? TypeFactory.defaultInstance().constructType(genericType) : null;
            this.rawType = rawType;
        }

        @Override
        public T fromString(String value) {
            if (rawType.isAssignableFrom(String.class)) {
                //noinspection unchecked
                return (T) value;
            }
            try {
                return genericType != null ? objectMapper.readValue(value, genericType)
                        : objectMapper.readValue(value, rawType);
            } catch (JsonProcessingException e) {
                throw (new RuntimeException(e));
            }
        }

        @Override
        public String toString(T value) {
            if (rawType.isAssignableFrom(String.class)) {
                return (String) value;
            }
            try {
                return objectMapper.writeValueAsString(value);
            } catch (JsonProcessingException e) {
                throw (new RuntimeException(e));
            }
        }

    }
}
