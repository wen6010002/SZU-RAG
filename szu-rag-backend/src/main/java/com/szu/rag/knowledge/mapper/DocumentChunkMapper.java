package com.szu.rag.knowledge.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.szu.rag.knowledge.model.entity.DocumentChunk;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface DocumentChunkMapper extends BaseMapper<DocumentChunk> {

    @Select("<script>" +
            "SELECT * FROM t_document_chunk " +
            "WHERE MATCH(chunk_text) AGAINST(#{query} IN NATURAL LANGUAGE MODE) " +
            "<if test='kbId != null'> AND knowledge_base_id = #{kbId} </if>" +
            "ORDER BY MATCH(chunk_text) AGAINST(#{query} IN NATURAL LANGUAGE MODE) DESC " +
            "LIMIT #{topK}" +
            "</script>")
    List<DocumentChunk> fullTextSearch(@Param("query") String query, @Param("kbId") Long kbId, @Param("topK") int topK);
}
