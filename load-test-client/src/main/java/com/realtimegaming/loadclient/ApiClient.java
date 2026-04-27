package com.realtimegaming.loadclient;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.realtimegaming.loadclient.metrics.LoadMetrics;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

@Component
public class ApiClient {

    private static final Logger log = LoggerFactory.getLogger(ApiClient.class);

    private final HttpClient http;
    private final ObjectMapper mapper;
    private final LoadConfig config;
    private final LoadMetrics metrics;

    public ApiClient(LoadConfig config, ObjectMapper mapper, LoadMetrics metrics) {
        this.http = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(5))
                .build();
        this.mapper = mapper;
        this.config = config;
        this.metrics = metrics;
    }

    public TestPlayer registerOrLogin(String username, String password) throws Exception {
        Map<String, Object> body = Map.of("username", username, "password", password);
        HttpResponse<String> resp = jsonRequest("POST", "/api/auth/register", body, null);
        if (resp.statusCode() == 201 || resp.statusCode() == 200) {
            return parseLogin(resp.body());
        }
        if (resp.statusCode() == 409) {
            HttpResponse<String> login = jsonRequest("POST", "/api/auth/login", body, null);
            if (login.statusCode() == 200) {
                return parseLogin(login.body());
            }
            throw new RuntimeException("login failed: status=" + login.statusCode() + " body=" + login.body());
        }
        throw new RuntimeException("register failed: status=" + resp.statusCode() + " body=" + resp.body());
    }

    private TestPlayer parseLogin(String body) throws Exception {
        Map<String, Object> json = mapper.readValue(body, new TypeReference<>() {});
        long id = ((Number) json.get("playerId")).longValue();
        String username = (String) json.get("username");
        String token = (String) json.get("token");
        return new TestPlayer(id, username, token);
    }

    public void submitScore(TestPlayer player, long score) throws Exception {
        Timer.Sample sample = metrics.startScoreSample();
        try {
            HttpResponse<String> resp = jsonRequest("POST", "/api/scores",
                    Map.of("score", score), player.token());
            if (resp.statusCode() / 100 != 2) {
                metrics.scoreErrors().increment();
            }
        } catch (Exception e) {
            metrics.scoreErrors().increment();
            throw e;
        } finally {
            metrics.scoreCompleted(sample);
        }
    }

    public void fetch(String path, TestPlayer player, String endpointTag) throws Exception {
        Timer timer = metrics.readTimer(endpointTag);
        long start = System.nanoTime();
        try {
            HttpResponse<String> resp = jsonRequest("GET", path, null, player == null ? null : player.token());
            if (resp.statusCode() / 100 != 2) {
                metrics.readErrors(endpointTag).increment();
            }
        } catch (Exception e) {
            metrics.readErrors(endpointTag).increment();
            throw e;
        } finally {
            timer.record(Duration.ofNanos(System.nanoTime() - start));
        }
    }

    private HttpResponse<String> jsonRequest(String method, String path, Map<String, Object> body, String token)
            throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder()
                .uri(URI.create(config.baseUrl() + path))
                .timeout(Duration.ofSeconds(10))
                .header("Accept", "application/json");
        if (body != null) {
            b.header("Content-Type", "application/json");
            b.method(method, HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)));
        } else {
            b.method(method, HttpRequest.BodyPublishers.noBody());
        }
        if (token != null) {
            b.header("Authorization", "Bearer " + token);
        }
        return http.send(b.build(), HttpResponse.BodyHandlers.ofString());
    }
}
