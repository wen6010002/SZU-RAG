package com.szu.rag.chat.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.szu.rag.chat.model.entity.Conversation;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ConversationMapper extends BaseMapper<Conversation> {}
