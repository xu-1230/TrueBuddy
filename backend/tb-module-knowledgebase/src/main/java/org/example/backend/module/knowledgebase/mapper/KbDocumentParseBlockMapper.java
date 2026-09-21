package org.example.backend.module.knowledgebase.mapper;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.example.backend.module.knowledgebase.model.KbDocumentParseBlockDO;

import java.util.List;

@Mapper
public interface KbDocumentParseBlockMapper {

    @Select("SELECT * FROM kb_document_parse_block WHERE document_id = #{documentId} ORDER BY block_index ASC")
    List<KbDocumentParseBlockDO> selectListByDocumentId(Long documentId);

    /** 按阅读顺序批量写入解析块（单次解析内的块一次性落库，避免逐条往返） */
    @Insert("<script>"
            + "INSERT INTO kb_document_parse_block (document_id, space_id, block_index, page_num, "
            + "page_block_seq, block_type, content, text, external_id, token_count) VALUES "
            + "<foreach collection='blocks' item='b' separator=','>"
            + "(#{b.documentId}, #{b.spaceId}, #{b.blockIndex}, #{b.pageNum}, "
            + "#{b.pageBlockSeq}, #{b.blockType}, #{b.content}, #{b.text}, #{b.externalId}, #{b.tokenCount})"
            + "</foreach>"
            + "</script>")
    int insertBatch(@Param("blocks") List<KbDocumentParseBlockDO> blocks);

    /** 从属表采用物理删除：重新解析时先清空旧块再重建 */
    @Delete("DELETE FROM kb_document_parse_block WHERE document_id = #{documentId}")
    int deleteByDocumentId(Long documentId);

}
