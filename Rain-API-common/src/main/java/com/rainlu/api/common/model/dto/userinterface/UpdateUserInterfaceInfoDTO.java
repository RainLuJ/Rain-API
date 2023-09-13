package com.rainlu.api.common.model.dto.userinterface;

import lombok.Data;

import java.io.Serializable;

/**
 *
 */
@Data
public class UpdateUserInterfaceInfoDTO implements Serializable {

    private static final long serialVersionUID = 1472097902521779075L;

    private Long userId;

    private Long interfaceId;

    /**
     * 分配的接口调用次数
     */
    private Long lockNum;
}
