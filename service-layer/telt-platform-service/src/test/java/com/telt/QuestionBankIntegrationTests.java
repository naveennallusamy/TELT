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
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
class QuestionBankIntegrationTests {

    private static final String PASSWORD = "Str0ng!Pass";
    private static final String NEW_PASSWORD = "Str0ng!Pass2";
    private static final AtomicLong MOBILE = new AtomicLong(9700000000L);
    private static final String SINGLE_CHOICE = "{\"type\":\"SINGLE_CHOICE\",\"version\":{\"content\":{\"text\":\"%s\","
            + "\"options\":[{\"id\":\"a\",\"text\":\"3\"},{\"id\":\"b\",\"text\":\"4\"}]},"
            + "\"answerKey\":{\"correctOptionIds\":[\"%s\"]},\"explanation\":\"2+2=4\",\"difficulty\":\"EASY\","
            + "\"tags\":[\"arithmetic\"]}}";

    @Autowired
    private MockMvc mvc;
    @Autowired
    private PostgreSQLContainer postgres;

    private String root;
    private String admin;

    @BeforeEach
    void setUp() throws Exception {
        root = token(login("root@telt.test", "Root#Pass123"));
        admin = tenantAdmin();
    }

    @Test
    void departmentAuthorWritesAndReviewerApprovesWithinTheirDepartment() throws Exception {
        String science = department(admin, "SCI");
        String arts = department(admin, "ART");
        String scienceBank = bank(admin, "Science " + UUID.randomUUID(), science);
        String artsBank = bank(admin, "Arts " + UUID.randomUUID(), arts);
        String author = subAdmin("QUESTION_AUTHOR", science);
        String reviewer = subAdmin("QUESTION_REVIEWER", science);

        call(post("/question-banks"), author, "{\"name\":\"x" + UUID.randomUUID() + "\",\"departmentId\":\"" + arts + "\"}")
                .andExpect(status().isForbidden());
        call(post("/question-banks/" + artsBank + "/questions"), author, SINGLE_CHOICE.formatted("2+2?", "b"))
                .andExpect(status().isForbidden());
        call(post("/question-banks/" + scienceBank + "/questions"), author, SINGLE_CHOICE.formatted("2+2?", "z"))
                .andExpect(status().isBadRequest());
        String questionId = JsonPath.read(body(call(post("/question-banks/" + scienceBank + "/questions"), author,
                SINGLE_CHOICE.formatted("What is two plus two", "b")).andExpect(status().isCreated())
                .andExpect(jsonPath("$.versions[0].status").value("DRAFT"))), "$.questionId");
        call(get("/question-banks"), author, null).andExpect(jsonPath("$[*].bankId", hasItem(scienceBank)))
                .andExpect(jsonPath("$[*].bankId", not(hasItem(artsBank))));

        call(post("/questions/" + questionId + "/versions/1/submit"), author, null).andExpect(status().isOk());
        call(put("/questions/" + questionId + "/versions/1"), author,
                "{\"content\":{\"text\":\"changed\",\"options\":[{\"id\":\"a\",\"text\":\"1\"},{\"id\":\"b\",\"text\":\"2\"}]},"
                        + "\"answerKey\":{\"correctOptionIds\":[\"a\"]}}").andExpect(status().isConflict());
        call(post("/questions/" + questionId + "/versions/1/review"), author, "{\"decision\":\"APPROVE\"}")
                .andExpect(status().isForbidden());
        call(post("/questions/" + questionId + "/versions/1/review"), reviewer, "{\"decision\":\"APPROVE\"}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("APPROVED"));
        call(get("/questions/" + questionId), reviewer, null).andExpect(jsonPath("$.approvedVersion").value(1))
                .andExpect(jsonPath("$.versions[0].answerKey.correctOptionIds[0]").value("b"));

        call(post("/questions/" + questionId + "/versions"), author, SINGLE_CHOICE.formatted("What is two plus two now", "b")
                .replaceFirst("\\{\"type\":\"SINGLE_CHOICE\",\"version\":", "").replaceFirst("}$", ""))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.versionNo").value(2));
        call(get("/questions").param("q", "plus").param("status", "DRAFT"), author, null)
                .andExpect(jsonPath("$.total").value(1)).andExpect(jsonPath("$.items[0].latestVersion").value(2))
                .andExpect(jsonPath("$.items[0].approvedVersion").value(1));
        call(get("/questions").param("tag", "arithmetic").param("bankId", artsBank), author, null)
                .andExpect(jsonPath("$.total").value(0));
    }

    @Test
    void separationOfDutiesBlocksSelfApprovalUntilDisabled() throws Exception {
        String bankId = bank(admin, "Bank " + UUID.randomUUID(), null);
        String questionId = JsonPath.read(body(call(post("/question-banks/" + bankId + "/questions"), admin,
                SINGLE_CHOICE.formatted("Self review", "a")).andExpect(status().isCreated())), "$.questionId");
        call(post("/questions/" + questionId + "/versions/1/submit"), admin, null).andExpect(status().isOk());
        call(post("/questions/" + questionId + "/versions/1/review"), admin, "{\"decision\":\"APPROVE\"}")
                .andExpect(status().isForbidden());
        call(put("/tenant-settings"), admin, "{\"subAdminDefaultAccessMode\":\"DEPARTMENT_SCOPED\",\"separationOfDuties\":false}")
                .andExpect(status().isOk());
        call(post("/questions/" + questionId + "/versions/1/review"), admin, "{\"decision\":\"APPROVE\"}")
                .andExpect(status().isOk());
        call(post("/questions/" + questionId + "/retire"), admin, null).andExpect(jsonPath("$.status").value("RETIRED"))
                .andExpect(jsonPath("$.versions[0].status").value("RETIRED"));
    }

    @Test
    void submittedVersionsAreImmutableInTheDatabaseAndInvisibleToOtherTenants() throws Exception {
        String bankId = bank(admin, "Bank " + UUID.randomUUID(), null);
        String questionId = JsonPath.read(body(call(post("/question-banks/" + bankId + "/questions"), admin,
                SINGLE_CHOICE.formatted("Frozen", "a")).andExpect(status().isCreated())), "$.questionId");
        call(post("/questions/" + questionId + "/versions/1/submit"), admin, null).andExpect(status().isOk());

        JdbcTemplate owner = new JdbcTemplate(new DriverManagerDataSource(postgres.getJdbcUrl(), postgres.getUsername(),
                postgres.getPassword()));
        assertThatThrownBy(() -> owner.update("update question_versions set content = '{\"text\":\"tampered\"}' "
                + "where question_id = ?", UUID.fromString(questionId))).rootCause().hasMessageContaining("cannot be modified");

        String otherAdmin = tenantAdmin();
        call(get("/questions/" + questionId), otherAdmin, null).andExpect(status().isNotFound());
        call(get("/questions").param("q", "Frozen"), otherAdmin, null).andExpect(jsonPath("$.total").value(0));
    }

    private String subAdmin(String role, String departmentId) throws Exception {
        String email = email();
        String id = JsonPath.read(body(call(post("/user/register"), admin, "{\"email\":\"" + email + "\",\"mobileNumber\":"
                + MOBILE.incrementAndGet() + ",\"role\":\"SUB_ADMIN\",\"password\":\"" + PASSWORD + "\",\"departmentId\":\""
                + departmentId + "\"}").andExpect(status().isCreated())), "$.id");
        call(post("/members/" + id + "/role-assignments"), admin, "{\"role\":\"" + role
                + "\",\"scopeType\":\"DEPARTMENT\",\"departmentId\":\"" + departmentId + "\"}").andExpect(status().isCreated());
        return activate(email);
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

    private String department(String token, String code) throws Exception {
        return JsonPath.read(body(call(post("/departments"), token, "{\"code\":\"" + code + "\",\"name\":\"" + code + "\"}")
                .andExpect(status().isCreated())), "$.departmentId");
    }

    private String bank(String token, String name, String departmentId) throws Exception {
        return JsonPath.read(body(call(post("/question-banks"), token, "{\"name\":\"" + name + "\""
                + (departmentId == null ? "" : ",\"departmentId\":\"" + departmentId + "\"") + "}")
                .andExpect(status().isCreated())), "$.bankId");
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
