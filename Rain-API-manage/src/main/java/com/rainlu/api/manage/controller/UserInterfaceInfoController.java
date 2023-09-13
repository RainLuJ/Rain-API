package com.rainlu.api.manage.controller;


import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.rainlu.api.common.annotation.AuthCheck;
import com.rainlu.api.common.common.BaseResponse;
import com.rainlu.api.common.common.DeleteRequest;
import com.rainlu.api.common.common.ErrorCode;
import com.rainlu.api.common.constant.CommonConstant;
import com.rainlu.api.common.exception.BusinessException;
import com.rainlu.api.common.model.dto.interfaceinfo.InterfaceInfoQueryRequest;
import com.rainlu.api.common.model.dto.userinterface.UpdateUserInterfaceInfoDTO;
import com.rainlu.api.common.model.dto.userinterface.UserInterfaceInfoAddRequest;
import com.rainlu.api.common.model.dto.userinterface.UserInterfaceInfoUpdateRequest;
import com.rainlu.api.common.model.entity.InterfaceCharging;
import com.rainlu.api.common.model.entity.User;
import com.rainlu.api.common.model.entity.UserInterfaceInfo;
import com.rainlu.api.common.model.vo.UserInterfaceInfoVO;
import com.rainlu.api.common.utils.ResultUtils;
import com.rainlu.api.manage.service.InterfaceChargingService;
import com.rainlu.api.manage.service.UserInterfaceInfoService;
import com.rainlu.api.manage.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.List;

/**
 * 用户接口关系controlelr
 */
@RestController
@RequestMapping("/userInterfaceInfo")
@Slf4j
public class UserInterfaceInfoController {

    @Resource
    private UserInterfaceInfoService userInterfaceInfoService;

    @Resource
    private UserService userService;

    @Resource
    private InterfaceChargingService interfaceChargingService;

    // region 增删改查

    /**
     * 创建
     *
     * @param userInterfaceInfoAddRequest
     * @param request
     * @return
     */
    @PostMapping("/add")
    @AuthCheck(mustRole = "admin")
    public BaseResponse<Long> addUserInterfaceInfo(@RequestBody UserInterfaceInfoAddRequest userInterfaceInfoAddRequest, HttpServletRequest request) {
        if (userInterfaceInfoAddRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        UserInterfaceInfo userInterfaceInfo = new UserInterfaceInfo();
        BeanUtils.copyProperties(userInterfaceInfoAddRequest, userInterfaceInfo);
        // 校验
        userInterfaceInfoService.validUserInterfaceInfo(userInterfaceInfo, true);
        User loginUser = userService.getLoginUser(request);
        userInterfaceInfo.setUserId(loginUser.getId());
        boolean result = userInterfaceInfoService.save(userInterfaceInfo);
        if (!result) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR);
        }
        long newInterfaceInfoId = userInterfaceInfo.getId();
        return ResultUtils.success(newInterfaceInfoId);
    }

    /**
     * 删除
     *
     * @param deleteRequest
     * @param request
     * @return
     */
    @PostMapping("/delete")
    @AuthCheck(mustRole = "admin")
    public BaseResponse<Boolean> deleteUserInterfaceInfo(@RequestBody DeleteRequest deleteRequest, HttpServletRequest request) {
        if (deleteRequest == null || deleteRequest.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        User user = userService.getLoginUser(request);
        long id = deleteRequest.getId();
        // 判断是否存在
        UserInterfaceInfo oldUserInterfaceInfo = userInterfaceInfoService.getById(id);
        if (oldUserInterfaceInfo == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR);
        }
        // 仅本人或管理员可删除
        if (!oldUserInterfaceInfo.getUserId().equals(user.getId()) && !userService.isAdmin(request)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
        }
        boolean b = userInterfaceInfoService.removeById(id);
        return ResultUtils.success(b);
    }

    /**
     * 更新
     *
     * @param userInterfaceInfoUpdateRequest
     * @param request
     * @return
     */
    @PostMapping("/update")
    @AuthCheck(mustRole = "admin")
    public BaseResponse<Boolean> updateUserInterfaceInfo(@RequestBody UserInterfaceInfoUpdateRequest userInterfaceInfoUpdateRequest,
                                                         HttpServletRequest request) {
        if (userInterfaceInfoUpdateRequest == null || userInterfaceInfoUpdateRequest.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        UserInterfaceInfo userInterfaceInfo = new UserInterfaceInfo();
        BeanUtils.copyProperties(userInterfaceInfoUpdateRequest, userInterfaceInfo);
        // 参数校验
        userInterfaceInfoService.validUserInterfaceInfo(userInterfaceInfo, false);
        User user = userService.getLoginUser(request);
        long id = userInterfaceInfoUpdateRequest.getId();
        // 判断是否存在
        UserInterfaceInfo oldUserInterfaceInfo = userInterfaceInfoService.getById(id);
        if (oldUserInterfaceInfo == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR);
        }
        // 仅本人或管理员可修改
        if (!oldUserInterfaceInfo.getUserId().equals(user.getId()) && !userService.isAdmin(request)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
        }
        boolean result = userInterfaceInfoService.updateById(userInterfaceInfo);
        return ResultUtils.success(result);
    }

    /**
     * 根据 id 获取
     *
     * @param id
     * @return
     */
    @GetMapping("/get")
    @AuthCheck(mustRole = "admin")
    public BaseResponse<UserInterfaceInfo> getInterfaceInfoById(long id) {
        if (id <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        UserInterfaceInfo userInterfaceInfo = userInterfaceInfoService.getById(id);
        return ResultUtils.success(userInterfaceInfo);
    }

    /**
     * 获取列表（仅管理员可使用）
     *
     * @param userInterfaceInfoQueryRequest
     * @return
     */
    @AuthCheck(mustRole = "admin")
    @GetMapping("/list")
    public BaseResponse<List<UserInterfaceInfo>> listInterfaceInfo(InterfaceInfoQueryRequest userInterfaceInfoQueryRequest) {
        UserInterfaceInfo userInterfaceInfoQuery = new UserInterfaceInfo();
        if (userInterfaceInfoQueryRequest != null) {
            BeanUtils.copyProperties(userInterfaceInfoQueryRequest, userInterfaceInfoQuery);
        }
        QueryWrapper<UserInterfaceInfo> queryWrapper = new QueryWrapper<>(userInterfaceInfoQuery);
        List<UserInterfaceInfo> userInterfaceInfoList = userInterfaceInfoService.list(queryWrapper);
        return ResultUtils.success(userInterfaceInfoList);
    }

    /**
     * 分页获取列表
     *
     * @param userInterfaceInfoQueryRequest
     * @param request
     * @return
     */
    @GetMapping("/list/page")
    @AuthCheck(mustRole = "admin")
    public BaseResponse<Page<UserInterfaceInfo>> listInterfaceInfoByPage(InterfaceInfoQueryRequest userInterfaceInfoQueryRequest, HttpServletRequest request) {
        if (userInterfaceInfoQueryRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        UserInterfaceInfo userInterfaceInfoQuery = new UserInterfaceInfo();
        BeanUtils.copyProperties(userInterfaceInfoQueryRequest, userInterfaceInfoQuery);
        long current = userInterfaceInfoQueryRequest.getCurrent();
        long size = userInterfaceInfoQueryRequest.getPageSize();
        String sortField = userInterfaceInfoQueryRequest.getSortField();
        String sortOrder = userInterfaceInfoQueryRequest.getSortOrder();
        // 限制爬虫
        if (size > 50) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        QueryWrapper<UserInterfaceInfo> queryWrapper = new QueryWrapper<>(userInterfaceInfoQuery);
        queryWrapper.orderBy(StringUtils.isNotBlank(sortField),
                sortOrder.equals(CommonConstant.SORT_ORDER_ASC), sortField);
        Page<UserInterfaceInfo> userInterfaceInfoPage = userInterfaceInfoService.page(new Page<>(current, size), queryWrapper);
        return ResultUtils.success(userInterfaceInfoPage);
    }

    @GetMapping("/list/userId")
    public BaseResponse<List<UserInterfaceInfoVO>> getInterfaceInfoByUserId(@RequestParam Long userId, HttpServletRequest request) {
        List<UserInterfaceInfoVO> userInterfaceInfoVOList = userInterfaceInfoService.getInterfaceInfoByUserId(userId, request);
        return ResultUtils.success(userInterfaceInfoVOList);
    }

    /**
     * 给用户分配免费的调用次数
     *
     * @param updateUserInterfaceInfoDTO
     * @param request
     * @return
     */
    @PostMapping("/get/free")
    public BaseResponse<Boolean> getFreeInterfaceCount(@RequestBody UpdateUserInterfaceInfoDTO updateUserInterfaceInfoDTO, HttpServletRequest request) {
        Long interfaceId = updateUserInterfaceInfoDTO.getInterfaceId();
        Long userId = updateUserInterfaceInfoDTO.getUserId();
        Long lockNum = updateUserInterfaceInfoDTO.getLockNum();
        if (interfaceId == null || userId == null || lockNum == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        if (lockNum > 100) { // 防止恶意刷次数
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "您一次性获取的次数太多了");
        }
        synchronized (userId) {
            User loginUser = userService.getLoginUser(request);
            if (!userId.equals(loginUser.getId())) {
                throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
            }
            /**
             * 如何判断接口是收费的还是免费的？
             *     收费的接口被记录在 `接口费用表` 中（引用 `接口信息表` 中的主键），根据当前获取次数的接口id查询 `接口收费表`，如果有记录，则说明当前接口是收费的接口
             */
            long interfaceCharging = interfaceChargingService
                    .count(
                            new QueryWrapper<InterfaceCharging>()
                                    .eq("interfaceId", interfaceId)
                    );
            if (interfaceCharging > 0) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "这个是付费接口噢!");
            }
            UserInterfaceInfo one = userInterfaceInfoService
                    .getOne(
                            new QueryWrapper<UserInterfaceInfo>()
                                    .eq("userId", userId)
                                    .eq("interfaceInfoId", interfaceId)
                    );

            // 为防止恶意刷接口次数，当获取的接口调用次数过多时，进行限制
            if (one != null && one.getLeftNum() >= 250) {
                throw new BusinessException(ErrorCode.OPERATION_ERROR, "您当前获取的接口调次数大于250！");
            }

            boolean b = userInterfaceInfoService.updateUserInterfaceInfo(updateUserInterfaceInfoDTO);
            return ResultUtils.success(b);
        }
    }
}
