package com.deployagram.demo.quarkus;

import com.fasterxml.jackson.databind.ObjectMapper;
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
class BookOverflowResourceTest {

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
