package com.realtimegaming.loadclient.scenario;

import com.realtimegaming.loadclient.LoadConfig;
import com.realtimegaming.loadclient.TestPlayer;
import com.realtimegaming.loadclient.metrics.LoadMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

import java.lang.reflect.Type;
import java.util.concurrent.atomic.AtomicBoolean;

public class WsSubscriberLoop implements Runnable {

    private static final Logger log = LoggerFactory.getLogger(WsSubscriberLoop.class);

    private final WebSocketStompClient client;
    private final TestPlayer player;
    private final LoadConfig config;
    private final LoadMetrics metrics;
    private final AtomicBoolean stop;

    public WsSubscriberLoop(TestPlayer player, LoadConfig config, LoadMetrics metrics,
                            ThreadPoolTaskScheduler scheduler, AtomicBoolean stop) {
        this.player = player;
        this.config = config;
        this.metrics = metrics;
        this.stop = stop;
        this.client = new WebSocketStompClient(new StandardWebSocketClient());
        client.setMessageConverter(new MappingJackson2MessageConverter());
        client.setTaskScheduler(scheduler);
    }

    @Override
    public void run() {
        StompHeaders connectHeaders = new StompHeaders();
        connectHeaders.add("Authorization", "Bearer " + player.token());
        try {
            StompSession session = client
                    .connectAsync(config.wsUrl(), new WebSocketHttpHeaders(), connectHeaders, new StompSessionHandlerAdapter() {})
                    .get(java.util.concurrent.TimeUnit.SECONDS.toNanos(15), java.util.concurrent.TimeUnit.NANOSECONDS);
            metrics.wsConnections().incrementAndGet();
            try {
                session.subscribe("/topic/leaderboard/top10", new CountingHandler(metrics, "top10"));
                session.subscribe("/user/queue/rank", new CountingHandler(metrics, "rank"));
                while (!stop.get()) {
                    Thread.sleep(500);
                }
            } finally {
                if (session.isConnected()) {
                    try { session.disconnect(); } catch (Exception ignored) {}
                }
                metrics.wsConnections().decrementAndGet();
            }
        } catch (Exception e) {
            log.warn("ws subscriber failed for player {}: {}", player.username(), e.getMessage());
        }
    }

    private static class CountingHandler implements StompFrameHandler {
        private final LoadMetrics metrics;
        private final String dest;

        CountingHandler(LoadMetrics metrics, String dest) {
            this.metrics = metrics;
            this.dest = dest;
        }

        @Override
        public Type getPayloadType(StompHeaders headers) {
            return Object.class;
        }

        @Override
        public void handleFrame(StompHeaders headers, Object payload) {
            if ("top10".equals(dest)) metrics.wsFramesTop().increment();
            else metrics.wsFramesRank().increment();
        }
    }
}
