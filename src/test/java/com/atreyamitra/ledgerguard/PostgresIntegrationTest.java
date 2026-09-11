package com.atreyamitra.ledgerguard;

import com.fasterxml.jackson.databind.*;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
abstract class PostgresIntegrationTest {
    // One container for the JVM: avoids stopping it between classes sharing a Spring context.
    // Deliberately fails when Docker is unavailable; no disabledWithoutDocker false greens.
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");
    static { POSTGRES.start(); }
    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("app.webhook.secret", () -> "test-secret-change-me");
    }
    @Autowired TestRestTemplate http;
    @Autowired ObjectMapper mapper;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void disableOutputStreaming() {
        // The JDK's HttpURLConnection cannot retry a streamed request body when the server
        // responds 401, and throws "cannot retry due to server authentication, in streaming
        // mode". WebhookHmacTest intentionally exercises 401 responses, so force the request
        // factory to buffer the body instead of streaming it. @PostConstruct is NOT invoked on
        // JUnit 5 test instances (Spring's DependencyInjectionTestExecutionListener only
        // autowires fields), so this must run from a JUnit lifecycle callback instead.
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setOutputStreaming(false);
        http.getRestTemplate().setRequestFactory(factory);
    }

    UUID createAccount() throws Exception {
        var response = http.postForEntity("/api/accounts", Map.of("ownerName", "Asha"), String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(json(response).get("ownerName").asText()).isEqualTo("Asha");
        assertThat(json(response).get("balance").asLong()).isZero();
        return UUID.fromString(json(response).get("id").asText());
    }
    String body(UUID account, long amount) {
        return "{\"accountId\":\"" + account + "\",\"amountMinor\":" + amount
                + ",\"currency\":\"INR\",\"eventId\":\"evt_123\"}";
    }
    String key() { return UUID.randomUUID().toString(); }
    // Independent signer: does not invoke the production crypto utility.
    String sign(String body) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec("test-secret-change-me".getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return HexFormat.of().formatHex(mac.doFinal(body.getBytes(StandardCharsets.UTF_8)));
    }
    ResponseEntity<String> send(String body, String key) throws Exception { return send(body, key, sign(body)); }
    ResponseEntity<String> send(String body, String key, String signature) {
        HttpHeaders headers = new HttpHeaders(); headers.setContentType(MediaType.APPLICATION_JSON);
        if (key != null) headers.set("Idempotency-Key", key);
        if (signature != null) headers.set("X-Signature", signature);
        return http.postForEntity("/api/webhooks/payments",
                new HttpEntity<>(body.getBytes(StandardCharsets.UTF_8), headers), String.class);
    }
    JsonNode json(ResponseEntity<String> response) throws Exception { return mapper.readTree(response.getBody()); }
    long count(UUID id) { return jdbc.queryForObject("SELECT count(*) FROM ledger_entries WHERE account_id = ?", Long.class, id); }
    long balance(UUID id) { return jdbc.queryForObject("SELECT balance FROM accounts WHERE id = ?", Long.class, id); }
    long claims(String key) { return jdbc.queryForObject("SELECT count(*) FROM processed_webhooks WHERE idempotency_key = ?", Long.class, key); }
}
