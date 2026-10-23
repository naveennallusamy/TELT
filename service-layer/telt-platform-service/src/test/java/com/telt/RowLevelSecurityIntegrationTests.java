package com.telt;

import com.jayway.jsonpath.JsonPath;
import com.telt.support.TestcontainersConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.fail;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "telt.bootstrap.super-admin.email=root@telt.test",
        "telt.bootstrap.super-admin.mobile-number=9000000000",
        "telt.bootstrap.super-admin.password=Root#Pass123"
})
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class RowLevelSecurityIntegrationTests {

    private static final String PASSWORD = "Str0ng!Pass";
    private static final String NEW_PASSWORD = "Str0ng!Pass2";
    private static final AtomicLong MOBILE = new AtomicLong(9600000000L);

    @Autowired
    private MockMvc mvc;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private PlatformTransactionManager transactionManager;
    @Autowired
    private PostgreSQLContainer postgres;

    private String root;
    private String adminA;
    private UUID tenantA;
    private UUID deptA;
    private UUID deptB;

    @BeforeEach
    void setUp() throws Exception {
        root = token(login("root@telt.test", "Root#Pass123"));
        adminA = tenantAdmin();
        String adminB = tenantAdmin();
        tenantA = UUID.fromString(claim(adminA, "tid"));
        deptA = UUID.fromString(department(adminA));
        deptB = UUID.fromString(department(adminB));
    }

    @Test
    void applicationRoleSeesNoTenantRowsWithoutTenantContext() {
        assertThat(count("select count(*) from departments where department_id in (?, ?)", deptA, deptB)).isZero();
        assertThat(count("select count(*) from tenant_memberships")).isZero();
    }

    @Test
    void tenantContextLimitsReadsAndWritesToThatTenant() {
        tx().executeWithoutResult(s -> {
            jdbc.queryForList("select set_config('telt.tenant_id', ?, true)", tenantA.toString());
            assertThat(jdbc.queryForList("select department_id from departments", UUID.class))
                    .contains(deptA).doesNotContain(deptB);
            assertThat(jdbc.update("update departments set name = 'hijacked' where department_id = ?", deptB)).isZero();
            s.setRollbackOnly();
            UUID tenantB = jdbc.queryForObject("select tenant_id from tenant t where exists (select 1 from tenant x "
                    + "where x.tenant_id = t.tenant_id) and t.tenant_id <> ? limit 1", UUID.class, tenantA);
            assertThatThrownBy(() -> jdbc.update("insert into departments (department_id, tenant_id, code, name, path) "
                    + "values (?, ?, 'X', 'X', '/x/')", UUID.randomUUID(), tenantB))
                    .isInstanceOf(DataAccessException.class).rootCause().hasMessageContaining("row-level security");
        });
    }

    @Test
    void auditLogIsAppendOnlyForApplicationRole() {
        assertThatThrownBy(() -> tx().execute(s -> jdbc.update("delete from audit_events")))
                .isInstanceOf(DataAccessException.class).rootCause().hasMessageContaining("permission denied");
    }

    @Test
    void roleChangesMadeOutsideThisInstanceInvalidateCachedPermissions() throws Exception {
        String subEmail = email();
        String subId = JsonPath.read(body(register(adminA, subEmail, "SUB_ADMIN", deptA.toString())
                .andExpect(status().isCreated())), "$.id");
        String sub = activate(subEmail);
        call(get("/me/authorization"), sub, null).andExpect(status().isOk())
                .andExpect(jsonPath("$.permissions.STUDENT_ENROLL").doesNotExist());

        JdbcTemplate otherInstance = new JdbcTemplate(new DriverManagerDataSource(postgres.getJdbcUrl(),
                postgres.getUsername(), postgres.getPassword()));
        otherInstance.update("insert into role_assignments (assignment_id, tenant_id, user_id, role_id, scope_type, "
                        + "department_id, granted_by, granted_at) select ?, ?, ?, role_id, 'DEPARTMENT', ?, 'test', now() "
                        + "from roles where name = 'STUDENT_MANAGER'",
                UUID.randomUUID(), tenantA, UUID.fromString(subId), deptA);

        long deadline = System.currentTimeMillis() + 10_000;
        while (!body(call(get("/me/authorization"), sub, null)).contains("STUDENT_ENROLL")) {
            if (System.currentTimeMillis() > deadline) {
                fail("Cached authorization was not invalidated by the database notification");
            }
            Thread.sleep(100);
        }
    }

    private long count(String sql, Object... args) {
        Long count = tx().execute(s -> jdbc.queryForObject(sql, Long.class, args));
        return count == null ? 0 : count;
    }

    private TransactionTemplate tx() {
        return new TransactionTemplate(transactionManager);
    }

    private String tenantAdmin() throws Exception {
        String tenantName = "t-" + UUID.randomUUID();
        call(post("/tenant/create"), root, "{\"tenantName\":\"" + tenantName + "\"}").andExpect(status().isCreated());
        String email = email();
        call(post("/user/register"), root, "{\"email\":\"" + email + "\",\"mobileNumber\":" + MOBILE.incrementAndGet()
                + ",\"role\":\"TENANT_ADMIN\",\"password\":\"" + PASSWORD + "\",\"tenantName\":\"" + tenantName + "\"}")
                .andExpect(status().isCreated());
        return activate(email);
    }

    private String department(String token) throws Exception {
        String code = "D" + MOBILE.incrementAndGet();
        return JsonPath.read(body(call(post("/departments"), token, "{\"code\":\"" + code + "\",\"name\":\"" + code
                + "\"}").andExpect(status().isCreated())), "$.departmentId");
    }

    private ResultActions register(String token, String email, String role, String departmentId) throws Exception {
        return call(post("/user/register"), token, "{\"email\":\"" + email + "\",\"mobileNumber\":" + MOBILE.incrementAndGet()
                + ",\"role\":\"" + role + "\",\"password\":\"" + PASSWORD + "\",\"departmentId\":\"" + departmentId + "\"}");
    }

    private String activate(String email) throws Exception {
        String first = token(login(email, PASSWORD).andExpect(status().isOk()));
        return token(call(post("/auth/change-password"), first,
                "{\"oldPassword\":\"" + PASSWORD + "\",\"newPassword\":\"" + NEW_PASSWORD + "\"}").andExpect(status().isOk()));
    }

    private ResultActions login(String user, String password) throws Exception {
        return mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"userName\":\"" + user + "\",\"password\":\"" + password + "\"}"));
    }

    private ResultActions call(MockHttpServletRequestBuilder builder, String token, String json) throws Exception {
        builder.header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON);
        return mvc.perform(json == null ? builder : builder.content(json));
    }

    private static String claim(String jwt, String name) {
        String payload = new String(Base64.getUrlDecoder().decode(jwt.split("\\.")[1]), StandardCharsets.UTF_8);
        return JsonPath.read(payload, "$." + name);
    }

    private static String token(ResultActions result) throws Exception {
        return JsonPath.read(body(result), "$.token");
    }

    private static String body(ResultActions result) throws Exception {
        return result.andReturn().getResponse().getContentAsString();
    }

    private static String email() {
        return "u-" + UUID.randomUUID() + "@telt.test";
    }
}
