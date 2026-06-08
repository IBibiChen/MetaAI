package com.metax.chat.history;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * MetaChatHistoryMapper .
 *
 * <p>
 * 完整聊天历史 MyBatis Plus Mapper
 * 业务读写通过 MyBatis Plus 完成，不通过 Spring Data JPA Repository
 *
 * @author IBibiChen
 * @version v1.0
 * @since 2026/6/2
 */
@Mapper
public interface MetaChatHistoryMapper extends BaseMapper<MetaChatHistoryDO> {

}
