package com.rainlu.api.order.service;


import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.rainlu.api.common.model.entity.Order;
import com.rainlu.api.common.model.vo.OrderVO;
import com.rainlu.api.common.model.dto.order.OrderAddRequest;
import com.rainlu.api.common.model.dto.order.OrderQueryRequest;

import javax.servlet.http.HttpServletRequest;
import java.util.List;

/**
 *
 */
public interface TOrderService extends IService<Order> {

    /**
     * 创建订单
     * @param orderAddRequest
     * @param request
     * @return
     */
    OrderVO addOrder(OrderAddRequest orderAddRequest, HttpServletRequest request);

    /**
     * 根据订单的类型获取我的订单
     * @param orderQueryRequest 订单的类型：待支付 & 已支付 & 已过期/失效
     * @param request
     * @return
     */
    Page<OrderVO> listPageOrder(OrderQueryRequest orderQueryRequest, HttpServletRequest request);

    /**
     * 获取接口购买数量排前limit位的接口信息，便于进行数据分析
     * @param limit
     * @return
     */
    List<Order> listTopBuyInterfaceInfo(int limit);
}
