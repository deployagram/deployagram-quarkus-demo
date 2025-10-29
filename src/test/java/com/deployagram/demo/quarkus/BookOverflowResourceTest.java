package com.deployagram.demo.quarkus;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.deployagram.annotations.junit5.Deployagram;
import com.github.deployagram.annotations.junit5.DeployagramConfig;
import com.github.deployagram.annotations.junit5.DeployagramConfigEntry;
import com.github.tomakehurst.wiremock.client.WireMock;
import io.quarkiverse.wiremock.devservice.ConnectWireMock;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.kafka.InjectKafkaCompanion;
import io.quarkus.test.kafka.KafkaCompanionResource;
import io.restassured.http.ContentType;
import io.smallrye.reactive.messaging.kafka.companion.ConsumerTask;
import io.smallrye.reactive.messaging.kafka.companion.KafkaCompanion;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static io.restassured.RestAssured.given;
import static net.javacrumbs.jsonunit.fluent.JsonFluentAssert.assertThatJson;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@QuarkusTest
@QuarkusTestResource(KafkaCompanionResource.class)
@ConnectWireMock
@Deployagram(startEnvironment = true, shareHostPorts = {BookOverflowResourceTest.QUARKUS_APP_PORT, BookOverflowResourceTest.WIREMOCK_PORT}, proxyPort = BookOverflowResourceTest.PROXY_PORT)
@DeployagramConfig({
        @DeployagramConfigEntry(key = "proxy.namesOfSourceApps.BookOverflow/suggestion", value = "AppA"),
        @DeployagramConfigEntry(key = "proxy.proxiedAppNames.BookOverflow/suggestion", value = "BookOverflow"),
        @DeployagramConfigEntry(key = "proxy.proxiedApps.BookOverflow/suggestion", value = "http://host.testcontainers.internal:" + BookOverflowResourceTest.QUARKUS_APP_PORT + "/BookOverflow/suggestion"),
        @DeployagramConfigEntry(key = "proxy.namesOfSourceApps.ClassicBooks/random", value = "BookOverflow"),
        @DeployagramConfigEntry(key = "proxy.proxiedAppNames.ClassicBooks/random", value = "ClassicBooks"),
        @DeployagramConfigEntry(key = "proxy.proxiedApps.ClassicBooks/random", value = "http://host.testcontainers.internal:" + BookOverflowResourceTest.WIREMOCK_PORT + "/ClassicBooks/random"),
})
class BookOverflowResourceTest {

    protected static final int QUARKUS_APP_PORT = 8081;
    protected static final int WIREMOCK_PORT = 9050;
    protected static final int PROXY_PORT = 9080;

    private static final String EMAIL_ADDRESS = "test@deployagram.com";

    @InjectKafkaCompanion
    KafkaCompanion companion;

    WireMock wiremock;

    @Test
    void callCForBook() {
        String expectedRecommendation = """
                {
                  "name": "Refactoring",
                  "edition": "1st",
                  "format": "paperback",
                  "authors": [
                    "Fowler Martin",
                    "Beck Kent"
                  ]
                }
                """;
        wiremock.register(get(urlEqualTo("/ClassicBooks/random"))
                .withHeader("Accept", equalTo("application/json"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(expectedRecommendation)));

        String body = given()
                .port(PROXY_PORT)
                .queryParam("email", EMAIL_ADDRESS)
                .when().get("/BookOverflow/suggestion")
                .then()
                .statusCode(200)
                .assertThat()
                .contentType(ContentType.JSON)
                .extract().body().asString();

        assertThatJson(body).isEqualTo(expectedRecommendation);
        assertExpectedKafkaMessage();
    }

    private void assertExpectedKafkaMessage() {
        ConsumerTask<String, String> consumerTask = companion.consumeStrings().fromTopics("SuppliedSuggestionsTopic", 1);

        Awaitility.await()
                  .atMost(Duration.ofSeconds(5))
                  .pollInterval(Duration.ofMillis(100))
                  .untilAsserted(() -> {
                      List<ConsumerRecord<String, String>> records = consumerTask.awaitCompletion(Duration.ofSeconds(1)).getRecords();
                      assertEquals(1, records.size());

                      ConsumerRecord<String, String> record = records.get(0);
                      assertNotNull(record.value());

                      ObjectMapper mapper = new ObjectMapper();
                      DontForget dontForget = mapper.readValue(record.value(), DontForget.class);

                      assertEquals(EMAIL_ADDRESS, dontForget.email());
                      BookSuggestion suggestion = dontForget.suggestion();
                      assertEquals("Refactoring", suggestion.name());
                      assertEquals("1st", suggestion.edition());
                      assertEquals("paperback", suggestion.format());
                  });
    }
}
