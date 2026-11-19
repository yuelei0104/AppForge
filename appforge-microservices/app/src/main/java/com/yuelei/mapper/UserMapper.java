package com.yuelei.mapper;

import com.mybatisflex.core.BaseMapper;
import com.yuelei.entity.User;
import org.apache.ibatis.annotations.Mapper;

/**
 *  映射层。
 *
 * @author yuelei
 */
@Mapper
public interface UserMapper extends BaseMapper<User> {

}
