package com.rainlu.api.gateway.algo;

/**
 * @description 手写简易的令牌桶算法
 *      - 不依赖于内部线程，而是在每次处理请求之前先实时计算出要填充的令牌数并填充，然后再从桶中获取|扣减令牌
 */
public class TokenBucket {

    // 令牌桶的容量（能容纳多少个令牌？）
    private final long capacity;

    // 生成令牌的速率(rate)（即每毫秒向桶中填充多少个令牌？）
    private final double refillTokenPerMillisecond;

    // 当前桶中可用的令牌数量
    private double availableTokens;

    // 上一次向桶中填充令牌时的时间戳
    private long lastRefillTimestamp;

    /**
     * @param capacity 令牌桶的容量
     * @param refillTokenPerSecond 每秒向令牌桶中填充多少个令牌
     *                             - 为了使粒度更细，在此类中将速率进一步换算为了`每毫秒生成多少个令牌`
     *                             Demo: TokenBucket limiter = new TokenBucket(100, 100);
     *                                   生成一个容量为100，每秒生成100个令牌的令牌桶
     * @description 构造一个令牌桶
     */
    public TokenBucket (long capacity, long refillTokenPerSecond) {
        this.capacity = capacity;
        // 计算出每毫秒生成令牌的速率
        this.refillTokenPerMillisecond = (double) refillTokenPerSecond / (double) 1000;
        this.availableTokens = capacity;
        this.lastRefillTimestamp = System.currentTimeMillis();
    }

    /**
     * @param accquireTokens 当前请求想获取的令牌数
     * @return boolean 是否成功获取到了令牌
     * @description 尝试获取令牌桶中的令牌
     */
    public synchronized boolean tryAcquire(int accquireTokens) {
        /* 1. 在每次请求到达获取令牌之前，实时计算出需要填充的令牌并进行填充 */
        refill();

        /* 2. 尝试从桶中获取令牌 */
        if (this.availableTokens > accquireTokens) {
            this.availableTokens -= accquireTokens;

            return true;
        }

        return false;
    }

    /**
     * @description 根据给定的速率填充令牌
     */
    private void refill() {
        long curTimestamp = System.currentTimeMillis();

        if (curTimestamp > this.lastRefillTimestamp) {
            long timeGap = curTimestamp - this.lastRefillTimestamp;
            double refillToken = this.refillTokenPerMillisecond * timeGap;
            this.availableTokens = Math.min(refillToken + this.availableTokens, this.capacity);
            this.lastRefillTimestamp = curTimestamp;
        }
    }
}
