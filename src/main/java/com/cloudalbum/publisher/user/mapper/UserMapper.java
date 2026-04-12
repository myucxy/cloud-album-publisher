package com.cloudalbum.publisher.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cloudalbum.publisher.user.entity.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface UserMapper extends BaseMapper<User> {

    @Select("SELECT r.code FROM t_role r " +
            "INNER JOIN t_user_role ur ON r.id = ur.role_id " +
            "WHERE ur.user_id = #{userId}")
    List<String> selectRoleCodesByUserId(Long userId);

    @Select("INSERT INTO t_user_role(user_id, role_id) " +
            "SELECT #{userId}, id FROM t_role WHERE code = #{roleCode}")
    void insertUserRole(Long userId, String roleCode);

    @Select("SELECT COUNT(DISTINCT u.id) FROM t_user u " +
            "INNER JOIN t_user_role ur ON u.id = ur.user_id " +
            "INNER JOIN t_role r ON r.id = ur.role_id " +
            "WHERE u.deleted = 0 AND r.code = 'ROLE_ADMIN'")
    long countAdminUsers();
}
