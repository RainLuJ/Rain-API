package com.rainlu.api.manage.mapper;


import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.rainlu.api.common.model.entity.UserInterfaceInfo;
import com.rainlu.api.common.model.vo.UserInterfaceInfoAnalysisVo;
import org.apache.ibatis.annotations.Param;

import java.util.List;


public interface UserInterfaceInfoMapper extends BaseMapper<UserInterfaceInfo> {


    List<UserInterfaceInfoAnalysisVo> listTopInterfaceInfo(@Param("size") int size);

    List<UserInterfaceInfo> listTopInvokeInterfaceInfo(int limit);
}




