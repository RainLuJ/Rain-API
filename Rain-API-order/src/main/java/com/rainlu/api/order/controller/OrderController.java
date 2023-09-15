package com.rainlu.api.order.controller;


import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.rainlu.api.common.common.BaseResponse;
import com.rainlu.api.common.model.vo.OrderVO;
import com.rainlu.api.common.utils.ResultUtils;
import com.rainlu.api.common.model.dto.order.OrderAddRequest;
import com.rainlu.api.common.model.dto.order.OrderQueryRequest;
import com.rainlu.api.order.service.TOrderService;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/")
public class OrderController {

    @Resource
    private TOrderService orderService;

    /**
     * @description 前端：用户点击“购买”，调用此接口，生成订单信息
     * @author Jun Lu
     * @date 2023-09-13 15:18:49
     */
    @PostMapping("/addOrder")
    public BaseResponse<OrderVO> interfaceTOrder(@RequestBody OrderAddRequest orderAddRequest, HttpServletRequest request){
        OrderVO order = orderService.addOrder(orderAddRequest,request);
        return ResultUtils.success(order);
    }

    @GetMapping("/list")
    public BaseResponse<Page<OrderVO>> listPageOrder(OrderQueryRequest orderQueryRequest, HttpServletRequest request){
        Page<OrderVO> orderPage = orderService.listPageOrder(orderQueryRequest, request);
        return ResultUtils.success(orderPage);
    }


}
