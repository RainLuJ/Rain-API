package com.rainlu.api.sdk;

import com.rainlu.api.sdk.client.DayApiClient;
import com.rainlu.api.sdk.client.NameApiClient;
import com.rainlu.api.sdk.client.RandomApiClient;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

/**
 * @description 自定义starter中的自动配置类，提供已经完成了自动配置的bean对象。
 *              这些bean对象，就是可供访问的接口。
 */
@Configuration
@ConfigurationProperties("rain.api")
// 必须要加上！因为在starter中没有主启动类，当前类的作用就是一个主配置类
@ComponentScan
@Data
public class HeartApiClientAutoConfiguration {

    private String accessKey;
    private String secretKey;


    @Bean
    public NameApiClient nameApiClient() {
        return new NameApiClient(accessKey, secretKey);
    }

    @Bean
    public RandomApiClient randomApiClient() {
        return new RandomApiClient(accessKey, secretKey);
    }

    @Bean
    public DayApiClient dayApiClient() {
        return new DayApiClient(accessKey, secretKey);
    }
}
