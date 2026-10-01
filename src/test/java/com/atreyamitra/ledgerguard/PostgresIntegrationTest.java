package com.atreyamitra.ledgerguard;

import com.fasterxml.jackson.databind.*;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
abstract class PostgresIntegrationTest {
    // Default: one Testcontainers PostgreSQL per JVM, shared by every test class. Docker being
    // unavailable is a hard failure, never a skip (no false greens).
    // Opt-in escape hatch for machines without Docker: set TEST_DB_URL (plus TEST_DB_USER /
    // TEST_DB_PASSWORD) to run the same suite against an existing, disposable PostgreSQL database.
    static final String EXTERNAL_URL = System.getenv("TEST_DB_URL");
    static final PostgreSQLContainer<?> POSTGRES = EXTERNAL_URL == null ? new PostgreSQLContainer<>("postgres:16-alpine") : null;
    static {
        if (POSTGRES != null) POSTGRES.start();
    }
    static String jdbcUrl() { return POSTGRES != null ? POSTGRES.getJdbcUrl() : EXTERNAL_URL; }
    static String dbUser() { return POSTGRES != null ? POSTGRES.getUsername() : System.getenv().getOrDefault("TEST_DB_USER", "postgres"); }
    static String dbPassword() { return POSTGRES != null ? POSTGRES.getPassword() : System.getenv().getOrDefault("TEST_DB_PASSWORD", ""); }
    static final String SECRET = "test-secret-change-me";
    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", PostgresIntegrationTest::jdbcUrl);
        registry.add("spring.datasource.username", PostgresIntegrationTest::dbUser);
        registry.add("spring.datasource.password", PostgresIntegrationTest::dbPassword);
        registry.add("app.webhook.secret", () -> SECRET);
    }
    @Autowired TestRestTemplate http;
    @Autowired ObjectMapper mapper;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void useApacheHttpClient() {
        // The JDK's default SimpleClientHttpRequestFactory (HttpURLConnection) throws
        // "cannot retry due to server authentication, in streaming mode" on a POST that
        // gets a 401 response, even with outputStreaming disabled -- Spring's buffering
        // request still calls setFixedLengthStreamingMode internally. WebhookHmacTest
        // intentionally exercises 401 responses, so use Apache HttpClient5 instead, which
        // does not have this JDK-specific bug.
        http.getRestTemplate().setRequestFactory(new HttpComponentsClientHttpRequestFactory(HttpClients.createDefault()));
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
    static String now() { return Long.toString(java.time.Instant.now().getEpochSecond()); }
    // Independent signer: does not invoke the production crypto utility. Signs "v1\n" + ts + "\n" + key + "\n" + body.
    String sign(String timestamp, String key, String body) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        mac.update(("v1\n" + timestamp + "\n" + key + "\n").getBytes(StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(mac.doFinal(body.getBytes(StandardCharsets.UTF_8)));
    }
    /** A correctly signed request with a fresh timestamp. */
    ResponseEntity<String> send(String body, String key) throws Exception {
        String ts = now();
        return sendSigned(body, key, ts, sign(ts, key == null ? "" : key, body));
    }
    /** Full control over every header; a null header is omitted. */
    ResponseEntity<String> sendSigned(String body, String key, String timestamp, String signature) {
        return http.postForEntity("/api/webhooks/payments",
                new HttpEntity<>(body.getBytes(StandardCharsets.UTF_8), headers(key, timestamp, signature)), String.class);
    }
    static HttpHeaders headers(String key, String timestamp, String signature) {
        HttpHeaders headers = new HttpHeaders(); headers.setContentType(MediaType.APPLICATION_JSON);
        if (key != null) headers.set("Idempotency-Key", key);
        if (timestamp != null) headers.set("X-Timestamp", timestamp);
        if (signature != null) headers.set("X-Signature", signature);
        return headers;
    }
    ResponseEntity<String> reconcile() { return http.postForEntity("/api/admin/reconcile", null, String.class); }
    JsonNode json(ResponseEntity<String> response) throws Exception { return mapper.readTree(response.getBody()); }
    long count(UUID id) { return jdbc.queryForObject("SELECT count(*) FROM ledger_entries WHERE account_id = ?", Long.class, id); }
    long balance(UUID id) { return jdbc.queryForObject("SELECT balance FROM accounts WHERE id = ?", Long.class, id); }
    long claims(String key) { return jdbc.queryForObject("SELECT count(*) FROM processed_webhooks WHERE idempotency_key = ?", Long.class, key); }

    @FunctionalInterface interface Request<T> { T run(int index) throws Exception; }
    /** Releases {@code count} threads at the same instant and collects every result (or fails on timeout). */
    <T> List<T> parallel(int count, Request<T> request) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(count);
        CountDownLatch ready = new CountDownLatch(count); CountDownLatch start = new CountDownLatch(1);
        List<Future<T>> futures = new ArrayList<>();
        try {
            for (int i = 0; i < count; i++) {
                final int index = i;
                futures.add(pool.submit(() -> {
                    ready.countDown();
                    if (!start.await(10, TimeUnit.SECONDS)) throw new AssertionError("Start barrier timed out");
                    return request.run(index);
                }));
            }
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(60);
            List<T> results = new ArrayList<>();
            for (var future : futures) {
                results.add(future.get(Math.max(1, deadline - System.nanoTime()), TimeUnit.NANOSECONDS));
            }
            return results;
        } finally {
            start.countDown(); pool.shutdownNow();
            assertThat(pool.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
    }
}
