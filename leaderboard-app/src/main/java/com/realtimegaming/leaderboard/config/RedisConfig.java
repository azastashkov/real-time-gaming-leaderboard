package com.realtimegaming.leaderboard.config;

import com.realtimegaming.leaderboard.ws.LeaderboardEventPublisher;
import com.realtimegaming.leaderboard.ws.LeaderboardEventSubscriber;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

@Configuration
public class RedisConfig {

    @Bean
    public RedisMessageListenerContainer redisMessageListenerContainer(
            RedisConnectionFactory connectionFactory,
            LeaderboardEventSubscriber subscriber) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.addMessageListener(subscriber, ChannelTopic.of(LeaderboardEventPublisher.CHANNEL));
        return container;
    }
}
