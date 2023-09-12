package com.rainlu.api.sdk.client;

import cn.hutool.http.HttpRequest;
import cn.hutool.json.JSONUtil;
import com.rainlu.api.sdk.model.User;


/**
 * NameController-NameApiClient
 */
public class NameApiClient extends BaseApiClient {


    public NameApiClient(String accessKey, String secretKey) {
        super(accessKey, secretKey);
    }


    /**
     * 获取用户名
     *
     * @param user
     * @return
     */
    public String getName(User user) {
        String json = JSONUtil.toJsonStr(user);
        return HttpRequest.post(GATEWAY_HOST + "/api/interface/name/user")
                .addHeaders(getHeadMap(json, accessKey, secretKey))
                .body(json)
                .execute().body();
    }
}
