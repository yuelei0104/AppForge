package com.yuelei.appforge.mapper;

import org.apache.ibatis.annotations.Mapper;
import com.mybatisflex.core.BaseMapper;
import com.yuelei.appforge.model.entity.User;

/**
 *  映射层。
 *
 * @author yuelei
 */
@Mapper
public interface UserMapper extends BaseMapper<User> {

}
