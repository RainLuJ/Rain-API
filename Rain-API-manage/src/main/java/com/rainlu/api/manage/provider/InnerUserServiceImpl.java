package com.rainlu.api.manage.provider;


import com.rainlu.api.common.model.entity.User;
import com.rainlu.api.common.service.InnerUserService;
import com.rainlu.api.manage.service.UserService;
import org.apache.dubbo.config.annotation.DubboService;

import javax.annotation.Resource;

@DubboService
public class InnerUserServiceImpl implements InnerUserService {

    @Resource
    private UserService userService;

    @Override
    public User getUserById(Long userId) {
        return userService.getById(userId);
    }
}
