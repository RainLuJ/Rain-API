package com.rainlu.api.gateway.algo.config;

import com.rainlu.api.gateway.algo.TokenBucket;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TokenBucketConfig {

    @Bean
    public TokenBucket tokenBucket() {
        return new TokenBucket(20, 5);
    }

}
