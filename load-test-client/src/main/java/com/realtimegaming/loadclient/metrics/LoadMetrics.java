package com.realtimegaming.loadclient.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicInteger;

@Component
public class LoadMetrics {

    private final MeterRegistry registry;
    private final Timer scoreTimer;
    private final Counter scoreErrors;
    private final Counter wsFramesTop;
    private final Counter wsFramesRank;
    private final AtomicInteger wsConnections = new AtomicInteger();

    public LoadMetrics(MeterRegistry registry) {
        this.registry = registry;
        this.scoreTimer = Timer.builder("loadtest.score.duration").register(registry);
        this.scoreErrors = Counter.builder("loadtest.score.errors").register(registry);
        this.wsFramesTop = Counter.builder("loadtest.ws.frames").tag("destination", "top10").register(registry);
        this.wsFramesRank = Counter.builder("loadtest.ws.frames").tag("destination", "rank").register(registry);
        registry.gauge("loadtest.ws.connections.active", wsConnections);
    }

    public Timer.Sample startScoreSample() {
        return Timer.start(registry);
    }

    public void scoreCompleted(Timer.Sample sample) {
        sample.stop(scoreTimer);
    }

    public Timer readTimer(String endpoint) {
        return Timer.builder("loadtest.read.duration")
                .tags(Tags.of("endpoint", endpoint))
                .register(registry);
    }

    public Counter readErrors(String endpoint) {
        return Counter.builder("loadtest.read.errors")
                .tags(Tags.of("endpoint", endpoint))
                .register(registry);
    }

    public Counter scoreErrors() {
        return scoreErrors;
    }

    public Counter wsFramesTop() {
        return wsFramesTop;
    }

    public Counter wsFramesRank() {
        return wsFramesRank;
    }

    public AtomicInteger wsConnections() {
        return wsConnections;
    }
}
