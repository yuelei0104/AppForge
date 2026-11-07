package com.yuelei.appforge.mapper;

import com.yuelei.appforge.model.entity.ChatHistory;
import org.apache.ibatis.annotations.Mapper;
import com.mybatisflex.core.BaseMapper;

/**
 *  映射层。
 *
 * @author yuelei
 */
@Mapper
public interface ChatHistoryMapper extends BaseMapper<ChatHistory> {

}
