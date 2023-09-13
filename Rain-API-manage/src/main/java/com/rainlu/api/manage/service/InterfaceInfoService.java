package com.rainlu.api.manage.service;


import com.baomidou.mybatisplus.extension.service.IService;
import com.rainlu.api.common.model.entity.InterfaceInfo;

/**
 *
 */
public interface InterfaceInfoService extends IService<InterfaceInfo> {

    /**
     * @description 校验参数信息
     */
    void validInterfaceInfo(InterfaceInfo interfaceInfo, boolean add);
}
