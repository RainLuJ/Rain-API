package com.rainlu.api.gateway.filter;

import cn.hutool.core.text.AntPathMatcher;
import com.rainlu.api.common.common.ErrorCode;
import com.rainlu.api.common.exception.BusinessException;
import com.rainlu.api.common.utils.JwtUtils;
import com.rainlu.api.gateway.algo.TokenBucket;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import javax.annotation.Resource;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @description 全局登录过滤器
 *                1. 限流过滤：对系统接口的访问如果超过限定的速率，则触发限流策略：抛出异常（友好提示）
 *                2. 白名单放行：对不需要登录的接口的请求直接放行
 *                3. 校验请求：请求中是否携带Token？Token是否合法？
 */
@Slf4j
@Component
public class LoginGlobalFilter implements GlobalFilter, Ordered {


    /*@Resource
    private RateLimiter rateLimiter;*/

    @Resource
    private TokenBucket tokenBucket;


    // 白名单放行
    public static final List<String> NOT_LOGIN_PATH = Arrays.asList(
            "/api/user/login", "/api/user/loginBySms", "/api/user/register","/api/user/email/register", "/api/user/smsCaptcha",
            "/api/user/getCaptcha", "/api/interface/**","/api/third/alipay/**","/api/interfaceInfo/sdk");


    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {


        ServerHttpRequest request = exchange.getRequest();
        ServerHttpResponse response = exchange.getResponse();
        HttpHeaders headers = request.getHeaders();

        /* 1. 限流过滤：如果可以立即毫不拖延地获得许可，则从该RateLimit获得许可。 */
        if (!tokenBucket.tryAcquire(1)) {
            log.error("请求太频繁了，被限流了!!!");
            throw new BusinessException(ErrorCode.TOO_MANY_REQUESTS);
        }

        /* 2. 白名单放行 */
        //登录过滤
        String path = request.getPath().toString();
        //判断请求路径是否需要登录
        List<Boolean> collect = NOT_LOGIN_PATH.stream().map(notLoginPath -> {
            AntPathMatcher antPathMatcher = new AntPathMatcher();
            return antPathMatcher.match(notLoginPath, path);
        }).collect(Collectors.toList());

        if (collect.contains(true)){
            return chain.filter(exchange);
        }

        /* 3. 没有Cookie或者Cookie没有携带Token == 未登录 */
        String cookie = headers.getFirst("Cookie");
        if (StringUtils.isBlank(cookie)){
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR);
        }
        if (!getLoginUserByCookie(cookie)) {
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR);
        }

        return chain.filter(exchange);
    }

    /**
     * @description 校验Cookie中的Token
     */
    private Boolean getLoginUserByCookie(String cookie) {
        // Cookie的存储格式："key1=val1; key2=val2; key3=val3"
        String[] split = cookie.split(";");
        for (String cookeKey : split) {
            String[] split1 = cookeKey.split("=");
            String cookieName = split1[0];
            if (cookieName.trim().equals("token")){
                String cookieValue = split1[1];
                return JwtUtils.checkToken(cookieValue);
            }
        }

        return false;
    }

    @Override
    public int getOrder() {
        return -1;
    }
}