package com.metax.history;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * MetaChatMapper .
 *
 * <p>
 * 聊天会话主表 MyBatis Plus Mapper
 *
 * @author IBibiChen
 * @version v1.0
 * @since 2026/6/4
 */
@Mapper
public interface MetaChatMapper extends BaseMapper<MetaChatDO> {
}
