package com.rainlu.api.order.provider;


import com.rainlu.api.common.model.entity.Order;
import com.rainlu.api.common.service.InnerOrderService;
import com.rainlu.api.order.service.TOrderService;
import org.apache.dubbo.config.annotation.DubboService;

import javax.annotation.Resource;
import java.util.List;

/**
 * @description 提供给其它模块调用的接口服务
 */
@DubboService
public class InnerOrderServiceImpl implements InnerOrderService {

    @Resource
    TOrderService orderService;

    /**
     * @description
     */
    @Override
    public List<Order> listTopBuyInterfaceInfo(int limit) {
        return orderService.listTopBuyInterfaceInfo(limit);
    }

}
