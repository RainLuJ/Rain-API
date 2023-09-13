package com.rainlu.api.manage.service.impl;


import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.rainlu.api.common.common.ErrorCode;
import com.rainlu.api.common.exception.BusinessException;
import com.rainlu.api.common.exception.ThrowUtils;
import com.rainlu.api.common.model.entity.InterfaceInfo;
import com.rainlu.api.manage.mapper.InterfaceInfoMapper;
import com.rainlu.api.manage.service.InterfaceInfoService;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

/**
 *
 */
@Service
public class InterfaceInfoServiceImpl extends ServiceImpl<InterfaceInfoMapper, InterfaceInfo>
    implements InterfaceInfoService {

    @Override
    public void validInterfaceInfo(InterfaceInfo interfaceInfo, boolean add) {


        if (interfaceInfo == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }


        String name = interfaceInfo.getName();

        // region add为true：当前正在添加数据

        // 如果是在添加数据，则参数列表不能为空！
        if (add) {
            ThrowUtils.throwIf(StringUtils.isAnyBlank(name), ErrorCode.PARAMS_ERROR);
        }

        // endregion

        // region add为false，当前不是在添加数据

        // 有参数则校验
        if (StringUtils.isBlank(name)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "接口名称不能为空");
        }
        if (name.length() > 50) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "接口名称过长");
        }

        // endregion
    }
}




