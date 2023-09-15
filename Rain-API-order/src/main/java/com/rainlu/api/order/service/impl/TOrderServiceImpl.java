package com.rainlu.api.order.service.impl;


import cn.hutool.core.date.DateField;
import cn.hutool.core.date.DateTime;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.google.gson.Gson;
import com.rainlu.api.common.common.ErrorCode;
import com.rainlu.api.common.exception.BusinessException;
import com.rainlu.api.common.model.dto.order.OrderAddRequest;
import com.rainlu.api.common.model.dto.order.OrderQueryRequest;
import com.rainlu.api.common.model.entity.InterfaceInfo;
import com.rainlu.api.common.model.entity.Order;
import com.rainlu.api.common.model.entity.User;
import com.rainlu.api.common.model.vo.OrderVO;
import com.rainlu.api.common.service.ApiBackendService;
import com.rainlu.api.common.service.InnerUserService;
import com.rainlu.api.common.utils.JwtUtils;
import com.rainlu.api.order.enums.OrderStatusEnum;
import com.rainlu.api.order.mapper.TOrderMapper;
import com.rainlu.api.order.service.TOrderService;
import com.rainlu.api.order.utils.OrderMQUtils;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.beans.BeanUtils;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

/**
 *
 */
@Service
public class TOrderServiceImpl extends ServiceImpl<TOrderMapper, Order>
        implements TOrderService {

    @DubboReference
    private InnerUserService innerUserService;

    @DubboReference
    private ApiBackendService apiBackendService;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private OrderMQUtils orderMqUtils;

    @Resource
    private Gson gson;

    @Resource
    private TOrderMapper orderMapper;


    public static final String USER_LOGIN_STATE = "user:login:";


    @Transactional
    @Override
    public OrderVO addOrder(OrderAddRequest orderAddRequest, HttpServletRequest request) {

        /* 1.订单服务校验参数，如用户是否存在，接口是否存在等校验 */
        if (orderAddRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        Long userId = orderAddRequest.getUserId();
        Long interfaceId = orderAddRequest.getInterfaceId();
        Double charging = orderAddRequest.getCharging();
        Integer count = orderAddRequest.getCount();
        BigDecimal totalAmount = orderAddRequest.getTotalAmount();

        if (userId == null || interfaceId == null || count == null || totalAmount == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }

        if (count <= 0 || totalAmount.compareTo(new BigDecimal(0)) < 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }

        // User user = innerUserService.getUserById(userId);
        User user = getLoginUser(request);
        if (user == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "用户不存在");
        }

        InterfaceInfo interfaceInfo = apiBackendService.getInterfaceById(interfaceId);
        if (interfaceInfo == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "接口不存在");
        }

        // 后端校验订单总价格。避免前端计算的订单总金额不符合预期！
        double temp = charging * count;
        BigDecimal bd = new BigDecimal(temp);
        // 精度设置：小数点后两位 & 四舍五入
        double finalPrice = bd.setScale(2, BigDecimal.ROUND_HALF_UP).doubleValue();
        if (finalPrice != totalAmount.doubleValue()) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "价格错误");
        }


        /* 2.判断接口的库存是否足够 */
        int interfaceStock = apiBackendService.getInterfaceStockById(interfaceId);
        if (interfaceStock <= 0 || interfaceStock - count <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "接口库存不足");
        }

        /* 3.扣减接口库存 远程调用实现 */
        boolean updateStockResult = apiBackendService.updateInterfaceStock(interfaceId, count);
        if (!updateStockResult) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "扣减库存失败");
        }

        /* 4.数据库保存订单数据 */
        Order order = new Order();
        //生成订单号
        String orderNum = generateOrderNum(userId);
        order.setOrderSn(orderNum);
        order.setTotalAmount(orderAddRequest.getTotalAmount().doubleValue());
        BeanUtils.copyProperties(orderAddRequest, order);

        this.save(order);

        /* 5.同时消息队列发送延时消息（超时未支付的订单会被取消） */
        orderMqUtils.sendOrderSnInfo(order);


        /* 6.构造订单详情并回显 */
        OrderVO orderVO = new OrderVO();
        orderVO.setInterfaceId(interfaceId);
        orderVO.setUserId(userId);
        orderVO.setOrderNumber(orderNum);

        orderVO.setTotal(Long.valueOf(count));
        orderVO.setCharging(charging);
        orderVO.setTotalAmount(totalAmount.doubleValue());
        orderVO.setStatus(order.getStatus());
        orderVO.setInterfaceDesc(interfaceInfo.getDescription());
        orderVO.setInterfaceName(interfaceInfo.getName());
        DateTime date = DateUtil.date();
        orderVO.setCreateTime(date);
        orderVO.setExpirationTime(DateUtil.offset(date, DateField.MINUTE, 30));

        return orderVO;
    }

    @Override
    public Page<OrderVO> listPageOrder(OrderQueryRequest orderQueryRequest, HttpServletRequest request) {
        Integer type = Integer.parseInt(orderQueryRequest.getType());
        if (!OrderStatusEnum.getValues().contains(type)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        long current = orderQueryRequest.getCurrent();
        long pageSize = orderQueryRequest.getPageSize();


        User userVO = getLoginUser(request);
        QueryWrapper<Order> queryWrapper = new QueryWrapper<>();
        // 分页获取当前用户指定类型的订单
        queryWrapper.eq("userId", userVO.getId()).eq("status", type);
        Page<Order> orderPage = this.page(new Page<>(current, pageSize), queryWrapper);

        // 根据Order的Page对象的基本信息构造OrderVO的Page对象
        Page<OrderVO> orderVOPage = new Page<>(orderPage.getCurrent(), orderPage.getSize(), orderPage.getTotal());

        // 根据 Order 列表封装 OrderVO 列表
        List<OrderVO> orderVOList = orderPage.getRecords().stream().map(order -> {
            Long interfaceId = order.getInterfaceId();
            // 获取接口详细信息
            InterfaceInfo interfaceInfo = apiBackendService.getInterfaceById(interfaceId);

            // 封装OrderVO
            OrderVO orderVO = new OrderVO();
            BeanUtils.copyProperties(order, orderVO);
            orderVO.setTotal(Long.valueOf(order.getCount()));
            orderVO.setTotalAmount(order.getTotalAmount());
            orderVO.setOrderNumber(order.getOrderSn());

            orderVO.setInterfaceName(interfaceInfo.getName());
            orderVO.setInterfaceDesc(interfaceInfo.getDescription());
            orderVO.setExpirationTime(DateUtil.offset(order.getCreateTime(), DateField.MINUTE, 30));
            return orderVO;
        }).collect(Collectors.toList());

        // 将OrderVO列表存入Page对象中
        orderVOPage.setRecords(orderVOList);
        return orderVOPage;
    }

    @Override
    public List<Order> listTopBuyInterfaceInfo(int limit) {
        return orderMapper.listTopBuyInterfaceInfo(limit);
    }

    /**
     * 生成订单号
     *
     * @return
     */
    private String generateOrderNum(Long userId) {
        String timeId = IdWorker.getTimeId();
        String substring = timeId.substring(0, timeId.length() - 15);
        return substring + RandomUtil.randomNumbers(5) + userId;
    }

    /**
     * 获取登录用户
     *
     * @param request
     * @return
     */
    public User getLoginUser(HttpServletRequest request) {
        // 先判断是否已登录
        Long userId = JwtUtils.getUserIdByToken(request);
        if (userId == null) {
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR);
        }

        String userJson = stringRedisTemplate.opsForValue().get(USER_LOGIN_STATE + userId);
        User user = gson.fromJson(userJson, User.class);
        if (user == null) {
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR);
        }

        return user;
    }
}




