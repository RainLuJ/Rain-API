package com.rainlu.api.gateway.config;

import com.google.common.util.concurrent.RateLimiter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class GuavaRateLimiterConfig {


    @SuppressWarnings("UnstableApiUsage")
    @Bean
    public RateLimiter rateLimiter(){
        /* 请求速率限制器，设置速率为 20 个请求/秒 */
        return RateLimiter.create(20);
    }
}