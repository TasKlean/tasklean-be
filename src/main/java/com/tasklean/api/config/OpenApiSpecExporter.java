package com.tasklean.api.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.web.server.context.WebServerApplicationContext;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

/**
 * Writes the generated OpenAPI spec to a file on every dev startup, so the committed contract stays
 * in step with the code without anyone remembering to export it. Dev profile only.
 */
@Slf4j
@Component
@Profile("dev")
public class OpenApiSpecExporter {

    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    private final String exportPath;

    public OpenApiSpecExporter(@Value("${tasklean.openapi.export-path:docs/openapi.json}") String exportPath) {
        this.exportPath = exportPath;
    }

    /**
     * Fetches the spec from this instance's own {@code /v3/api-docs} and writes it to the export
     * path when the content has changed. Failures are logged and swallowed — a dev convenience must
     * never stop the application from starting.
     *
     * @param event the ready event, used to discover the port this instance actually bound to
     */
    @EventListener(ApplicationReadyEvent.class)
    public void exportSpec(ApplicationReadyEvent event) {
        Path target = Path.of(exportPath);

        Path parent = target.toAbsolutePath().getParent();
        if (parent == null || !Files.isDirectory(parent)) {
            log.debug("Skipping OpenAPI export: {} is not an existing directory", parent);
            return;
        }

        try {
            String spec = fetchSpec(portOf(event.getApplicationContext()));
            if (spec == null) {
                return;
            }
            if (Files.exists(target) && Files.readString(target, StandardCharsets.UTF_8).equals(spec)) {
                log.debug("OpenAPI spec unchanged: {}", target);
                return;
            }
            Files.writeString(target, spec, StandardCharsets.UTF_8);
            // A changed contract is worth seeing in the log — it means a client may need regenerating.
            log.info("OpenAPI spec written: {}", target);
        } catch (Exception e) {
            // Includes InterruptedException from the HTTP call; restore the flag rather than lose it.
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            log.warn("Could not export the OpenAPI spec to {}: {}", target, e.getMessage());
        }
    }

    private String fetchSpec(int port) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/v3/api-docs"))
                .timeout(TIMEOUT)
                .GET()
                .build();
        try (HttpClient client = HttpClient.newBuilder().connectTimeout(TIMEOUT).build()) {
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                // Expected when springdoc is switched off; nothing to export.
                log.debug("Skipping OpenAPI export: /v3/api-docs returned {}", response.statusCode());
                return null;
            }
            return response.body().endsWith("\n") ? response.body() : response.body() + "\n";
        }
    }

    private static int portOf(ApplicationContext context) {
        if (context instanceof WebServerApplicationContext webContext && webContext.getWebServer() != null) {
            return webContext.getWebServer().getPort();
        }
        throw new IllegalStateException("No web server bound to this context");
    }
}
