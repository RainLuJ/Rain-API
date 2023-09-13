package com.rainlu.api.common.service;


import com.rainlu.api.common.model.entity.User;

public interface InnerUserService {


    /**
     * 根据用户id获取用户信息
     * @param userId
     * @return
     */
    User getUserById(Long userId);
}
