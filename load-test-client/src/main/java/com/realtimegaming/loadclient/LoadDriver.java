package com.realtimegaming.loadclient;

import com.realtimegaming.loadclient.metrics.LoadMetrics;
import com.realtimegaming.loadclient.scenario.PlayerLoop;
import com.realtimegaming.loadclient.scenario.WsSubscriberLoop;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.search.MeterNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
public class LoadDriver implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(LoadDriver.class);

    private final ApiClient api;
    private final LoadConfig config;
    private final LoadMetrics metrics;
    private final MeterRegistry registry;
    private final ConfigurableApplicationContext context;

    public LoadDriver(ApiClient api, LoadConfig config, LoadMetrics metrics,
                      MeterRegistry registry, ConfigurableApplicationContext context) {
        this.api = api;
        this.config = config;
        this.metrics = metrics;
        this.registry = registry;
        this.context = context;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        log.info("=== load test starting ===");
        log.info("config: {}", config);

        log.info("phase 1/4 — registering {} players", config.numPlayers());
        List<TestPlayer> players = registerPlayers();
        log.info("registered {} players (target was {})", players.size(), config.numPlayers());
        if (players.isEmpty()) {
            log.error("no players registered — aborting");
            shutdown();
            return;
        }

        AtomicBoolean stop = new AtomicBoolean(false);
        ThreadPoolTaskScheduler wsScheduler = new ThreadPoolTaskScheduler();
        wsScheduler.setPoolSize(4);
        wsScheduler.setThreadNamePrefix("ws-sched-");
        wsScheduler.afterPropertiesSet();

        log.info("phase 2/4 — starting load: rampUp={}s duration={}s wsSubscribers={}",
                config.rampUpSeconds(), config.durationSeconds(), config.wsSubscribers());

        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            long rampStepNs = config.rampUpSeconds() <= 0
                    ? 0
                    : Duration.ofSeconds(config.rampUpSeconds()).toNanos() / Math.max(1, players.size());
            for (int i = 0; i < players.size(); i++) {
                executor.submit(new PlayerLoop(api, players.get(i), config, stop));
                if (rampStepNs > 0) {
                    Thread.sleep(Duration.ofNanos(rampStepNs));
                }
            }
            int wsSubs = Math.min(config.wsSubscribers(), players.size());
            for (int i = 0; i < wsSubs; i++) {
                executor.submit(new WsSubscriberLoop(players.get(i), config, metrics, wsScheduler, stop));
            }

            log.info("phase 3/4 — load running for {}s", config.durationSeconds());
            Thread.sleep(Duration.ofSeconds(config.durationSeconds()));

            log.info("phase 4/4 — stopping workers");
            stop.set(true);
        }
        wsScheduler.shutdown();

        printSummary();

        if (config.holdAfterSeconds() > 0) {
            log.info("holding {}s for prometheus to scrape final metrics", config.holdAfterSeconds());
            Thread.sleep(Duration.ofSeconds(config.holdAfterSeconds()));
        }

        if (config.exitAfterRun()) {
            shutdown();
        } else {
            log.info("exitAfterRun=false — process staying alive");
        }
    }

    private List<TestPlayer> registerPlayers() throws InterruptedException {
        List<TestPlayer> players = new ArrayList<>(config.numPlayers());
        try (ExecutorService executor = Executors.newFixedThreadPool(config.registerConcurrency())) {
            List<java.util.concurrent.Future<TestPlayer>> futures = new ArrayList<>(config.numPlayers());
            for (int i = 0; i < config.numPlayers(); i++) {
                final int idx = i;
                futures.add(executor.submit(() -> {
                    String username = "loadtest-" + idx;
                    String password = "p4ssword-" + idx;
                    try {
                        return api.registerOrLogin(username, password);
                    } catch (Exception e) {
                        log.debug("register failed for {}: {}", username, e.getMessage());
                        return null;
                    }
                }));
            }
            for (var f : futures) {
                try {
                    TestPlayer p = f.get();
                    if (p != null) players.add(p);
                } catch (Exception ignored) {}
            }
        }
        return players;
    }

    private void printSummary() {
        log.info("=== load test summary ===");
        report("loadtest.score.duration");
        report("loadtest.score.errors");
        report("loadtest.read.duration");
        report("loadtest.read.errors");
        report("loadtest.ws.frames");
        report("loadtest.ws.connections.active");
    }

    private void report(String name) {
        try {
            registry.find(name).meters().forEach(m -> log.info("  {} :: {} :: {}",
                    m.getId().getName(), m.getId().getTags(), m.measure()));
        } catch (MeterNotFoundException ignored) {}
    }

    private void shutdown() {
        new Thread(() -> {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException ignored) {}
            context.close();
        }, "loadtest-shutdown").start();
    }
}
