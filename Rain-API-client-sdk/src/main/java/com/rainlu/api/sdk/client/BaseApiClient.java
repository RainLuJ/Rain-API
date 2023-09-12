package com.rainlu.api.sdk.client;

import cn.hutool.core.util.RandomUtil;
import com.rainlu.api.sdk.utils.SignUtils;

import java.util.HashMap;
import java.util.Map;

/**
 * SDK提供的接口调用客户端中共有的一些属性方法（accessKey & secretKey & 封装请求头的方法）
 */
public class BaseApiClient {

    protected final String accessKey;
    protected final String secretKey;

    protected static final String GATEWAY_HOST = "http://localhost:8090";

    public BaseApiClient(String accessKey, String secretKey) {
        this.accessKey = accessKey;
        this.secretKey = secretKey;
    }

    /**
     * 负责将数字签名的相关参数填入请求头中
     *
     * @param body 用户参数
     * @param accessKey 访问码
     * @param secretKey 秘钥
     * @return 封装的请求头Map
     */
    protected static Map<String, String> getHeadMap(String body, String accessKey, String secretKey) {
        //六个参数
        Map<String, String> headMap = new HashMap<>();
        headMap.put("accessKey", accessKey);
        headMap.put("body", body);
        headMap.put("sign", SignUtils.generateSign(body, secretKey));
        // 随机令牌：防止接口重放
        headMap.put("nonce", RandomUtil.randomNumbers(5));
        //当下时间/1000，时间戳大概10位
        headMap.put("timestamp", String.valueOf(System.currentTimeMillis() / 1000));
        return headMap;
    }


}
