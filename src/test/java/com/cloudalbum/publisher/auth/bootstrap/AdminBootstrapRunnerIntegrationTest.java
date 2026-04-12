package com.cloudalbum.publisher.auth.bootstrap;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cloudalbum.publisher.CloudAlbumPublisherApplication;
import com.cloudalbum.publisher.user.entity.User;
import com.cloudalbum.publisher.user.mapper.UserMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(classes = CloudAlbumPublisherApplication.class)
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "bootstrap.admin.enabled=true",
        "bootstrap.admin.username=bootstrap-admin",
        "bootstrap.admin.email=bootstrap-admin@cloud-album.local",
        "bootstrap.admin.nickname=Bootstrap Admin"
})
class AdminBootstrapRunnerIntegrationTest {

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private AdminBootstrapRunner adminBootstrapRunner;

    @Test
    void createsDefaultAdminWhenNoAdminExists() {
        User admin = findBootstrapAdmin();

        assertNotNull(admin, "default admin should be created on startup");
        assertEquals("bootstrap-admin@cloud-album.local", admin.getEmail());
        assertEquals("Bootstrap Admin", admin.getNickname());
        assertEquals(1, admin.getStatus());
        assertTrue(admin.getPassword() != null && !admin.getPassword().isBlank());
        assertTrue(admin.getPassword().startsWith("$2"), "password should be stored as a BCrypt hash");

        List<String> roles = userMapper.selectRoleCodesByUserId(admin.getId());
        assertEquals(List.of("ROLE_ADMIN"), roles);
        assertEquals(1L, userMapper.countAdminUsers());
    }

    @Test
    void doesNotCreateAnotherAdminWhenRunnerExecutesAgain() throws Exception {
        Long beforeAdminCount = userMapper.countAdminUsers();
        Long beforeUserCount = userMapper.selectCount(null);

        adminBootstrapRunner.run(new DefaultApplicationArguments(new String[0]));

        assertEquals(beforeAdminCount, userMapper.countAdminUsers());
        assertEquals(beforeUserCount, userMapper.selectCount(null));
        assertNotNull(findBootstrapAdmin());
    }

    private User findBootstrapAdmin() {
        return userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getUsername, "bootstrap-admin")
                .last("LIMIT 1"));
    }
}
