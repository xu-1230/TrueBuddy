package org.example.backend.module.lecture.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.example.backend.module.lecture.model.LectureMeetingDO;

import java.time.LocalDateTime;

@Mapper
public interface LectureMeetingMapper {

    /** 新增会议（自增主键回填 id） */
    @Insert("INSERT INTO lecture_meeting (meeting_no, title, host_token, status) "
            + "VALUES (#{meetingNo}, #{title}, #{hostToken}, #{status})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(LectureMeetingDO meeting);

    /** 按会议号查询（仅未删除） */
    @Select("SELECT * FROM lecture_meeting WHERE meeting_no = #{meetingNo} AND deleted = FALSE")
    LectureMeetingDO selectByMeetingNo(String meetingNo);

    /** 更新会议状态与结束时间 */
    @Update("UPDATE lecture_meeting SET status = #{status}, end_time = #{endTime}, update_time = now() "
            + "WHERE id = #{id} AND deleted = FALSE")
    int updateStatus(@Param("id") Long id,
                     @Param("status") Integer status,
                     @Param("endTime") LocalDateTime endTime);

}
