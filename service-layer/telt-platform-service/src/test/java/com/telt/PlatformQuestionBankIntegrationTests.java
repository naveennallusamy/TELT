package com.telt;

import com.jayway.jsonpath.JsonPath;
import com.telt.support.TestcontainersConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "telt.bootstrap.super-admin.email=root@telt.test",
        "telt.bootstrap.super-admin.mobile-number=9000000000",
        "telt.bootstrap.super-admin.password=Root#Pass123"
})
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class PlatformQuestionBankIntegrationTests {

    private static final String PASSWORD = "Str0ng!Pass";
    private static final String NEW_PASSWORD = "Str0ng!Pass2";
    private static final AtomicLong MOBILE = new AtomicLong(9800000000L);
    private static final String VERSION = "{\"content\":{\"text\":\"%s\","
            + "\"options\":[{\"id\":\"a\",\"text\":\"3\"},{\"id\":\"b\",\"text\":\"4\"}]},"
            + "\"answerKey\":{\"correctOptionIds\":[\"b\"]},\"explanation\":\"2+2=4\",\"tags\":[\"platform\"]}";

    @Autowired
    private MockMvc mvc;
    @Autowired
    private PostgreSQLContainer postgres;

    private String root;
    private String adminA;
    private String adminB;
    private UUID tenantA;

    @BeforeEach
    void setUp() throws Exception {
        root = token(login("root@telt.test", "Root#Pass123"));
        adminA = tenantAdmin();
        adminB = tenantAdmin();
        tenantA = UUID.fromString(claim(adminA, "tid"));
    }

    @Test
    void grantedTenantReadsPlatformReleaseButCannotModifyIt() throws Exception {
        String bank = bank(root, "Platform " + UUID.randomUUID());
        String q1 = approvedQuestion(bank, "Platform two plus two");
        approvedQuestion(bank, "Platform second question");
        call(post("/question-banks/" + bank + "/questions"), root,
                "{\"type\":\"SINGLE_CHOICE\",\"version\":" + VERSION.formatted("Still a draft") + "}")
                .andExpect(status().isCreated());

        call(get("/question-banks"), adminA, null).andExpect(jsonPath("$[*].bankId", not(hasItem(bank))));
        call(get("/questions/" + q1), adminA, null).andExpect(status().isNotFound());
        call(get("/tenant"), root, null).andExpect(jsonPath("$[*].tenantName", not(hasItem("__platform__"))));

        String release1 = JsonPath.read(body(call(post("/platform/question-banks/" + bank + "/releases"), root,
                "{\"notes\":\"first\"}").andExpect(status().isCreated()).andExpect(jsonPath("$.releaseNo").value(1))
                .andExpect(jsonPath("$.itemCount").value(2))), "$.releaseId");

        call(post("/platform/grants"), root, grant(bank, tenantA.toString(), "LATEST", null, true, false))
                .andExpect(status().isBadRequest());
        call(post("/platform/grants"), adminA, grant(bank, tenantA.toString(), "LATEST", null, false, false))
                .andExpect(status().isForbidden());
        String grantId = JsonPath.read(body(call(post("/platform/grants"), root,
                grant(bank, tenantA.toString(), "LATEST", null, false, false)).andExpect(status().isCreated())), "$.grantId");
        call(post("/platform/grants"), root, grant(bank, tenantA.toString(), "LATEST", null, false, false))
                .andExpect(status().isConflict());

        call(get("/granted-banks"), adminA, null).andExpect(jsonPath("$[0].grantId").value(grantId))
                .andExpect(jsonPath("$[0].effectiveReleaseNo").value(1));
        call(get("/granted-banks/" + grantId + "/questions"), adminA, null).andExpect(jsonPath("$.total").value(2))
                .andExpect(jsonPath("$.items[0].version.answerKey").value(nullValue()));
        call(post("/granted-banks/" + grantId + "/questions/" + q1 + "/clone"), adminA,
                "{\"bankId\":\"" + bank(adminA, "Own " + UUID.randomUUID()) + "\"}").andExpect(status().isForbidden());
        call(get("/granted-banks"), adminB, null).andExpect(jsonPath("$", hasSize(0)));
        call(get("/granted-banks/" + grantId + "/questions"), adminB, null).andExpect(status().isNotFound());

        call(put("/questions/" + q1 + "/versions/1"), adminA, VERSION.formatted("hijack")).andExpect(status().isNotFound());
        call(post("/questions/" + q1 + "/retire"), adminA, null).andExpect(status().isNotFound());
        try (SingleConnectionDataSource ds = appConnection()) {
            JdbcTemplate app = new JdbcTemplate(ds);
            app.queryForList("select set_config('telt.tenant_id', ?, false)", tenantA.toString());
            assertThat(app.queryForObject("select count(*) from question_versions where question_id = ?", Long.class,
                    UUID.fromString(q1))).isEqualTo(1);
            assertThat(app.update("update questions set status = 'RETIRED' where question_id = ?",
                    UUID.fromString(q1))).isZero();
            assertThat(app.update("update question_banks set name = 'hijacked' where bank_id = ?",
                    UUID.fromString(bank))).isZero();
            app.queryForList("select set_config('telt.tenant_id', ?, false)", UUID.fromString(claim(adminB, "tid")).toString());
            assertThat(app.queryForObject("select count(*) from question_versions where question_id = ?", Long.class,
                    UUID.fromString(q1))).isZero();
        }

        // A second approved version only reaches the tenant once it is released.
        call(post("/questions/" + q1 + "/versions"), root, VERSION.formatted("Platform two plus two, revised"))
                .andExpect(status().isCreated());
        call(post("/questions/" + q1 + "/versions/2/submit"), root, null).andExpect(status().isOk());
        call(post("/questions/" + q1 + "/versions/2/review"), root, "{\"decision\":\"APPROVE\"}").andExpect(status().isOk());
        call(get("/granted-banks/" + grantId + "/questions/" + q1), adminA, null)
                .andExpect(jsonPath("$.version.versionNo").value(1));
        call(post("/platform/question-banks/" + bank + "/releases"), root, null)
                .andExpect(jsonPath("$.releaseNo").value(2));
        call(get("/granted-banks/" + grantId + "/questions/" + q1), adminA, null)
                .andExpect(jsonPath("$.version.versionNo").value(2));

        call(put("/platform/grants/" + grantId), root, settings("PINNED", release1, true, true))
                .andExpect(jsonPath("$.effectiveReleaseNo").value(1));
        call(get("/granted-banks/" + grantId + "/questions/" + q1), adminA, null)
                .andExpect(jsonPath("$.version.versionNo").value(1))
                .andExpect(jsonPath("$.version.answerKey.correctOptionIds[0]").value("b"));
        String ownBank = bank(adminA, "Own " + UUID.randomUUID());
        String cloneId = JsonPath.read(body(call(post("/granted-banks/" + grantId + "/questions/" + q1 + "/clone"), adminA,
                "{\"bankId\":\"" + ownBank + "\"}").andExpect(status().isCreated())
                .andExpect(jsonPath("$.sourceQuestionId").value(q1)).andExpect(jsonPath("$.sourceVersionNo").value(1))
                .andExpect(jsonPath("$.versions[0].status").value("DRAFT"))), "$.questionId");
        call(put("/questions/" + cloneId + "/versions/1"), adminA, VERSION.formatted("Tenant's own wording"))
                .andExpect(status().isOk());
        call(get("/questions/" + q1), root, null).andExpect(jsonPath("$.versions[0].content.text")
                .value("Platform two plus two"));

        call(post("/platform/grants/" + grantId + "/revoke"), root, null).andExpect(jsonPath("$.status").value("REVOKED"));
        call(get("/granted-banks"), adminA, null).andExpect(jsonPath("$", hasSize(0)));
        call(get("/granted-banks/" + grantId + "/questions/" + q1), adminA, null).andExpect(status().isNotFound());
        call(get("/questions/" + cloneId), adminA, null).andExpect(status().isOk());
        call(get("/platform/grants").param("tenantId", tenantA.toString()), root, null)
                .andExpect(jsonPath("$[0].status").value("REVOKED"));
    }

    @Test
    void pinnedGrantMustReferenceAReleaseOfTheSameBank() throws Exception {
        String bankA = bank(root, "Platform " + UUID.randomUUID());
        String bankB = bank(root, "Platform " + UUID.randomUUID());
        approvedQuestion(bankA, "Pinned A");
        call(post("/platform/question-banks/" + bankB + "/releases"), root, null).andExpect(status().isConflict());
        String releaseA = JsonPath.read(body(call(post("/platform/question-banks/" + bankA + "/releases"), root, null)
                .andExpect(status().isCreated())), "$.releaseId");
        call(post("/platform/grants"), root, grant(bankB, tenantA.toString(), "PINNED", releaseA, false, false))
                .andExpect(status().isNotFound());
        call(post("/platform/grants"), root, grant(bankA, tenantA.toString(), "PINNED", null, false, false))
                .andExpect(status().isBadRequest());
        call(post("/platform/grants"), root, grant(bankA, "00000000-0000-0000-0000-000000000001", "LATEST", null,
                false, false)).andExpect(status().isNotFound());
        call(post("/platform/grants"), root, grant(bankA, tenantA.toString(), "PINNED", releaseA, false, true))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.effectiveReleaseId").value(releaseA));
    }

    private String approvedQuestion(String bank, String text) throws Exception {
        String id = JsonPath.read(body(call(post("/question-banks/" + bank + "/questions"), root,
                "{\"type\":\"SINGLE_CHOICE\",\"version\":" + VERSION.formatted(text) + "}")
                .andExpect(status().isCreated())), "$.questionId");
        call(post("/questions/" + id + "/versions/1/submit"), root, null).andExpect(status().isOk());
        call(post("/questions/" + id + "/versions/1/review"), root, "{\"decision\":\"APPROVE\"}").andExpect(status().isOk());
        return id;
    }

    private static String grant(String bank, String tenant, String policy, String release, boolean clone, boolean keys) {
        return "{\"bankId\":\"" + bank + "\",\"tenantId\":\"" + tenant + "\",\"settings\":"
                + settings(policy, release, clone, keys) + "}";
    }

    private static String settings(String policy, String release, boolean clone, boolean keys) {
        return "{\"releasePolicy\":\"" + policy + "\"" + (release == null ? "" : ",\"releaseId\":\"" + release + "\"")
                + ",\"allowClone\":" + clone + ",\"answerKeysVisible\":" + keys + "}";
    }

    private SingleConnectionDataSource appConnection() {
        return new SingleConnectionDataSource(postgres.getJdbcUrl(), TestcontainersConfiguration.APP_ROLE,
                TestcontainersConfiguration.APP_ROLE, true);
    }

    private String tenantAdmin() throws Exception {
        String tenantName = "t-" + UUID.randomUUID();
        call(post("/tenant/create"), root, "{\"tenantName\":\"" + tenantName + "\"}").andExpect(status().isCreated());
        String email = "u-" + UUID.randomUUID() + "@telt.test";
        call(post("/user/register"), root, "{\"email\":\"" + email + "\",\"mobileNumber\":" + MOBILE.incrementAndGet()
                + ",\"role\":\"TENANT_ADMIN\",\"password\":\"" + PASSWORD + "\",\"tenantName\":\"" + tenantName + "\"}")
                .andExpect(status().isCreated());
        String first = token(login(email, PASSWORD).andExpect(status().isOk()));
        return token(call(post("/auth/change-password"), first,
                "{\"oldPassword\":\"" + PASSWORD + "\",\"newPassword\":\"" + NEW_PASSWORD + "\"}").andExpect(status().isOk()));
    }

    private String bank(String token, String name) throws Exception {
        return JsonPath.read(body(call(post("/question-banks"), token, "{\"name\":\"" + name + "\"}")
                .andExpect(status().isCreated())), "$.bankId");
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
}
