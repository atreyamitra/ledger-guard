package com.atreyamitra.ledgerguard;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.ConfigurableApplicationContext;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Two complete application contexts (separate Tomcat, separate Hikari pool) share one PostgreSQL
 * database. This is NOT two machines, but nothing in-process is shared between the instances, so
 * the only thing that can arbitrate duplicates is the database.
 */
class MultiInstanceTest extends PostgresIntegrationTest {
    @LocalServerPort int firstPort;

    @Test @Timeout(120)
    void duplicatesSplitAcrossTwoInstancesCreditExactlyOnce() throws Exception {
        try (ConfigurableApplicationContext second = new SpringApplicationBuilder(LedgerGuardApplication.class).run(
                "--server.port=0", "--spring.datasource.url=" + jdbcUrl(),
                "--spring.datasource.username=" + dbUser(), "--spring.datasource.password=" + dbPassword(),
                "--app.webhook.secret=" + SECRET, "--spring.main.banner-mode=off")) {
            int secondPort = Integer.parseInt(second.getEnvironment().getProperty("local.server.port"));
            assertThat(secondPort).isNotEqualTo(firstPort);
            var id = createAccount(); String key = key(); String body = body(id, 1000);
            String ts = now(); String signature = sign(ts, key, body);
            HttpClient client = HttpClient.newHttpClient();
            var responses = parallel(30, index -> post(client, index % 2 == 0 ? firstPort : secondPort, body, key, ts, signature));
            assertThat(responses).allSatisfy(r -> assertThat(r.statusCode()).isEqualTo(200));
            assertThat(responses.stream().map(HttpResponse::body).distinct().count()).isEqualTo(1);
            assertThat(count(id)).isEqualTo(1); assertThat(balance(id)).isEqualTo(1000); assertThat(claims(key)).isEqualTo(1);
        }
    }

    private HttpResponse<String> post(HttpClient client, int port, String body, String key, String ts, String signature) throws Exception {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        return client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/webhooks/payments"))
                .header("Content-Type", "application/json").header("Idempotency-Key", key)
                .header("X-Timestamp", ts).header("X-Signature", signature)
                .POST(HttpRequest.BodyPublishers.ofByteArray(bytes)).build(), HttpResponse.BodyHandlers.ofString());
    }
}
