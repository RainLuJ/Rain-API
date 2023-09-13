package com.rainlu.api.common.utils;

import cn.hutool.crypto.digest.DigestAlgorithm;
import cn.hutool.crypto.digest.Digester;

/**
 * 生成签名的工具类
 */
public class SignUtils {


    private static final Digester md5 = new Digester(DigestAlgorithm.SHA1);

    /**
     * 生成签名的算法
     *
     * @param body 用户传递的参数
     * @param secretKey 系统分配给用户的秘钥
     * @return 生成的签名
     */
    public static String generateSign(String body, String secretKey) {
        return md5.digestHex(body + secretKey);
    }
}
