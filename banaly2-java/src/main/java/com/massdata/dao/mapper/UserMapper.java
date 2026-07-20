package com.massdata.dao.mapper;

import com.massdata.entity.User;
import com.massdata.entity.SysLog;
import org.apache.ibatis.annotations.*;

import java.util.List;

/**
 * 用户Mapper - MyBatis操作H2 sys_user表
 */
@Mapper
public interface UserMapper {

    @Select("SELECT * FROM sys_user WHERE username = #{username}")
    User findByUsername(String username);

    @Select("SELECT * FROM sys_user WHERE id = #{id}")
    User findById(Long id);

    @Insert("INSERT INTO sys_user(username, password, role, nickname) VALUES(#{username}, #{password}, #{role}, #{nickname})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(User user);

    @Select("SELECT * FROM sys_user")
    List<User> findAll();

    /** 操作日志 */
    @Insert("INSERT INTO sys_log(username, operation, target, status, detail) VALUES(#{username}, #{operation}, #{target}, #{status}, #{detail})")
    int insertLog(SysLog log);

    @Select("SELECT * FROM sys_log ORDER BY create_time DESC LIMIT #{limit}")
    List<SysLog> findRecentLogs(int limit);

    @Select("SELECT COUNT(*) FROM sys_log WHERE create_time >= #{date}")
    long countTodayLogs(String date);

    @Update("UPDATE sys_user SET password = #{password} WHERE id = #{id}")
    int updatePassword(@Param("id") Long id, @Param("password") String password);

    @Delete("DELETE FROM sys_user WHERE id = #{id}")
    int deleteById(Long id);

    @Update("UPDATE sys_user SET role = #{role} WHERE id = #{id}")
    int updateRole(@Param("id") Long id, @Param("role") String role);
}
