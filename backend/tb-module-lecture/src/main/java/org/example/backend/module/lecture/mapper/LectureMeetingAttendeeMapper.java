package org.example.backend.module.lecture.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.example.backend.module.lecture.model.LectureMeetingAttendeeDO;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface LectureMeetingAttendeeMapper {

    /** 新增参会人（自增主键回填 id） */
    @Insert("INSERT INTO lecture_meeting_attendee (meeting_id, nickname, attendee_token, role) "
            + "VALUES (#{meetingId}, #{nickname}, #{attendeeToken}, #{role})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(LectureMeetingAttendeeDO attendee);

    /** 按会议查询全部参会人（按入会顺序） */
    @Select("SELECT * FROM lecture_meeting_attendee WHERE meeting_id = #{meetingId} AND deleted = FALSE ORDER BY id ASC")
    List<LectureMeetingAttendeeDO> selectListByMeetingId(Long meetingId);

    /** 按会议 + 昵称查询（用于重名检测） */
    @Select("SELECT * FROM lecture_meeting_attendee WHERE meeting_id = #{meetingId} AND nickname = #{nickname} "
            + "AND deleted = FALSE LIMIT 1")
    LectureMeetingAttendeeDO selectByMeetingIdAndNickname(@Param("meetingId") Long meetingId,
                                                          @Param("nickname") String nickname);

    /** 按参会凭据查询（学生侧状态接口与消息落库的发送者校验用） */
    @Select("SELECT * FROM lecture_meeting_attendee WHERE attendee_token = #{attendeeToken} AND deleted = FALSE")
    LectureMeetingAttendeeDO selectByAttendeeToken(String attendeeToken);

    /** 累计入会人数（台账行数，不是在线人数） */
    @Select("SELECT COUNT(1) FROM lecture_meeting_attendee WHERE meeting_id = #{meetingId} AND deleted = FALSE")
    int countByMeetingId(Long meetingId);

    /**
     * 单个参会人离会：只更新尚未记过离会时间的行。
     *
     * <p>{@code leave_time IS NULL} 条件保证重复调用幂等——第二次不会把首次离会时间刷成当下。
     */
    @Update("UPDATE lecture_meeting_attendee SET leave_time = now(), update_time = now() "
            + "WHERE id = #{id} AND leave_time IS NULL AND deleted = FALSE")
    int markLeft(Long id);

    /** 会议结束时给全部「尚未显式离开」的台账行补记离会时间 */
    @Update("UPDATE lecture_meeting_attendee SET leave_time = now(), update_time = now() "
            + "WHERE meeting_id = #{meetingId} AND leave_time IS NULL AND deleted = FALSE")
    int markAllLeft(Long meetingId);

}
