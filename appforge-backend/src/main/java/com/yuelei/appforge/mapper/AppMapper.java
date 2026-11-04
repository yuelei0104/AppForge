package com.yuelei.appforge.mapper;

import com.mybatisflex.core.BaseMapper;
import com.yuelei.appforge.model.entity.App;
import org.apache.ibatis.annotations.Mapper;

/**
 * 应用映射层。
 *
 * @author yuelei
 */
@Mapper
public interface AppMapper extends BaseMapper<App> {
}
