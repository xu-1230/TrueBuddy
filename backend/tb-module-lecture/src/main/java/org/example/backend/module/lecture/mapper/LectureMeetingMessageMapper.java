package org.example.backend.module.lecture.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.example.backend.module.lecture.model.LectureMeetingMessageDO;

import java.util.List;

@Mapper
public interface LectureMeetingMessageMapper {

    /** 新增消息（自增主键回填 id） */
    @Insert("INSERT INTO lecture_meeting_message (meeting_id, attendee_id, nickname, role, content, kind) "
            + "VALUES (#{meetingId}, #{attendeeId}, #{nickname}, #{role}, #{content}, #{kind})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(LectureMeetingMessageDO message);

    /** 按主键查询 */
    @Select("SELECT * FROM lecture_meeting_message WHERE id = #{id}")
    LectureMeetingMessageDO selectById(Long id);

    /**
     * 拉取某会议最近 limit 条消息，按时间正序返回（先取最近 N 条，再翻正序）。
     */
    @Select("SELECT * FROM (SELECT * FROM lecture_meeting_message WHERE meeting_id = #{meetingId} "
            + "AND deleted = FALSE ORDER BY id DESC LIMIT #{limit}) t ORDER BY t.id ASC")
    List<LectureMeetingMessageDO> selectRecentByMeetingId(@Param("meetingId") Long meetingId,
                                                          @Param("limit") int limit);

}
