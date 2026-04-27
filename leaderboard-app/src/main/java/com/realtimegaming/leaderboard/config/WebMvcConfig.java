package com.realtimegaming.leaderboard.config;

import com.realtimegaming.leaderboard.security.AuthenticatedPlayerArgumentResolver;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final AuthenticatedPlayerArgumentResolver authenticatedPlayerArgumentResolver;

    public WebMvcConfig(AuthenticatedPlayerArgumentResolver authenticatedPlayerArgumentResolver) {
        this.authenticatedPlayerArgumentResolver = authenticatedPlayerArgumentResolver;
    }

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(authenticatedPlayerArgumentResolver);
    }
}
