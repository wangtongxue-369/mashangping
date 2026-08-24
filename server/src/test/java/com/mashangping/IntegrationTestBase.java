package com.mashangping;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * 集成测试基类：手动单例 MySQL 容器，整个 JVM 只启动一次。
 * 本机没有 Docker 时相关测试按"跳过"处理。
 *
 * 说明：@Testcontainers(disabledWithoutDocker) 仅作"无 Docker 则禁用本类"的门闩，
 * 不配合 @Container 使用（避免容器随子类反复重启）；容器由下方静态块手动启动单例。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public abstract class IntegrationTestBase {

    /** 手动单例容器：避免注解模式下容器随 Spring 上下文缓存被反复重启 */
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("msp")
            .withUsername("test")
            .withPassword("test");

    static {
        if (DockerClientFactory.instance().isDockerAvailable()) {
            MYSQL.start();
        }
    }

    @BeforeAll
    static void requireDocker() {
        Assumptions.assumeTrue(MYSQL.isRunning(), "本机无 Docker，跳过数据库集成测试");
    }

    @DynamicPropertySource
    static void datasourceProps(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
    }

    @Autowired
    protected MockMvc mockMvc;

    /** 用指定用户信息生成真实可用的 Bearer 请求头值 */
    protected String bearer(com.mashangping.security.JwtService jwtService,
                            long uid, String username, String role) {
        return "Bearer " + jwtService.generate(
                new com.mashangping.security.TokenPayload(uid, username, role));
    }
}
