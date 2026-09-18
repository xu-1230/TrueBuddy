package org.example.backend.module.infra.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.example.backend.module.infra.model.InfraFileDO;

@Mapper
public interface InfraFileMapper {

    @Insert("INSERT INTO infra_file (bucket, path, name, url, size, type) "
            + "VALUES (#{bucket}, #{path}, #{name}, #{url}, #{size}, #{type})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(InfraFileDO file);

    @Select("SELECT id, bucket, path, name, url, size, type, create_time, update_time, creator, updater, deleted "
            + "FROM infra_file WHERE id = #{id} AND deleted = FALSE")
    InfraFileDO selectById(@Param("id") Long id);

}
