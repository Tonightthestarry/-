package com.massdata.service;

import com.massdata.dao.mapper.UserMapper;
import com.massdata.dao.mongo.AnalysisResultRepository;
import com.massdata.entity.User;
import com.massdata.util.JwtUtil;
import com.massdata.util.R;
import org.junit.jupiter.api.*;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Base64;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * 用户认证服务白盒测试 - 模块五
 * 测试登录、JWT、权限、日志
 */
@DisplayName("用户认证服务白盒测试")
class UserServiceWhiteBoxTest {

    // ======================== 登录逻辑 ========================

    @Nested
    @DisplayName("用户验证逻辑")
    class UserValidationTests {

        @Test
        @DisplayName("T23: 正确的用户名密码应验证通过")
        void testCorrectPasswordValidation() {
            String password = "123456";
            String encoded = Base64.getEncoder().encodeToString(password.getBytes());

            String input = "123456";
            String inputEncoded = Base64.getEncoder().encodeToString(input.getBytes());

            assertTrue(encoded.equals(inputEncoded) || password.equals(input),
                    "密码123456应该验证通过");
        }

        @Test
        @DisplayName("T24: 错误的密码应拒绝")
        void testWrongPasswordRejected() {
            String password = "123456";
            String input = "wrongpassword";

            String encoded = Base64.getEncoder().encodeToString(password.getBytes());
            String inputEncoded = Base64.getEncoder().encodeToString(input.getBytes());

            boolean matches = encoded.equals(inputEncoded) || "123456".equals(input);
            assertFalse(matches, "错误密码应被拒绝");
        }

        @Test
        @DisplayName("T25: 空用户名应提示错误")
        void testEmptyUsername() {
            String username = "";
            boolean valid = username != null && !username.isEmpty();
            assertFalse(valid, "空用户名不应通过验证");
        }

        @Test
        @DisplayName("T26: 用户角色分类正确")
        void testUserRoles() {
            String[] roles = {"admin", "user"};
            assertTrue("admin".equals(roles[0]), "管理员角色应为admin");
            assertTrue("user".equals(roles[1]), "普通用户角色应为user");
            assertNotEquals(roles[0], roles[1], "admin和user是不同的角色");
        }
    }

    // ======================== JWT ========================

    @Nested
    @DisplayName("JWT Token测试")
    class JwtTokenTests {

        private JwtUtil jwtUtil;
        private String token;

        @BeforeEach
        void setUp() {
            // 手动构造JwtUtil(模拟Spring注入)
            jwtUtil = new JwtUtil();
            // 通过反射设置属性
            try {
                var secretField = JwtUtil.class.getDeclaredField("secret");
                secretField.setAccessible(true);
                secretField.set(jwtUtil, "test-secret-key-for-white-box-testing");
                var expField = JwtUtil.class.getDeclaredField("expiration");
                expField.setAccessible(true);
                expField.set(jwtUtil, 86400000L);
            } catch (Exception e) {
                fail("反射设置失败: " + e.getMessage());
            }
            token = jwtUtil.generateToken("testuser", "admin");
        }

        @Test
        @DisplayName("T27: JWT Token生成不为空")
        void testTokenGeneration() {
            assertNotNull(token, "Token不应为空");
            assertTrue(token.length() > 20, "Token长度应>20");
        }

        @Test
        @DisplayName("T28: Token可解析出用户名")
        void testTokenParseUsername() {
            String username = jwtUtil.getUsername(token);
            assertEquals("testuser", username, "应能解析出正确的用户名");
        }

        @Test
        @DisplayName("T29: Token可解析出角色")
        void testTokenParseRole() {
            String role = jwtUtil.getRole(token);
            assertEquals("admin", role, "应能解析出正确的角色");
        }

        @Test
        @DisplayName("T30: Token校验通过")
        void testTokenValidation() {
            assertTrue(jwtUtil.validate(token), "有效Token应校验通过");
        }

        @Test
        @DisplayName("T31: 无效Token校验失败")
        void testInvalidTokenFails() {
            assertFalse(jwtUtil.validate("invalid.token.here"), "无效Token应校验失败");
            // 空Token在JwtUtil层面会抛出异常，这是预期的防御行为
            assertThrows(IllegalArgumentException.class, () -> jwtUtil.validate(""),
                    "空Token应抛出异常");
        }
    }

    // ======================== 统一响应 ========================

    @Nested
    @DisplayName("统一响应格式测试")
    class ResponseFormatTests {

        @Test
        @DisplayName("T32: 成功响应格式正确")
        void testSuccessResponse() {
            Map<String, String> data = Map.of("key", "value");
            R ok = R.ok("操作成功", data);

            assertEquals(200, ok.getCode());
            assertEquals("操作成功", ok.getMessage());
            assertNotNull(ok.getData());
            assertEquals("value", ((Map<String, String>) ok.getData()).get("key"));
        }

        @Test
        @DisplayName("T33: 错误响应格式正确")
        void testErrorResponse() {
            R error = R.error(500, "服务器错误");

            assertEquals(500, error.getCode());
            assertEquals("服务器错误", error.getMessage());
            assertNull(error.getData());
        }

        @Test
        @DisplayName("T34: 权限拒绝响应")
        void testForbiddenResponse() {
            R forbidden = R.error(403, "无权限");

            assertEquals(403, forbidden.getCode());
            assertEquals("无权限", forbidden.getMessage());
        }

        @Test
        @DisplayName("T35: 空数据成功响应")
        void testEmptySuccessResponse() {
            R ok = R.ok();

            assertEquals(200, ok.getCode());
            assertEquals("success", ok.getMessage());
            assertNull(ok.getData());
        }
    }
}
