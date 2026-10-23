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

import java.nio.charset.StandardCharsets;
import java.util.Base64;
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
class ExamIntegrationTests {

    private static final String PASSWORD = "Str0ng!Pass";
    private static final String NEW_PASSWORD = "Str0ng!Pass2";
    private static final AtomicLong MOBILE = new AtomicLong(9300000000L);
    private static final String VERSION = "{\"content\":{\"text\":\"%s\","
            + "\"options\":[{\"id\":\"a\",\"text\":\"3\"},{\"id\":\"b\",\"text\":\"4\"}]},"
            + "\"answerKey\":{\"correctOptionIds\":[\"b\"]},\"explanation\":\"2+2=4\"}";
    private static final String SETTINGS = "{\"durationMinutes\":%d,\"shuffleQuestions\":true,\"shuffleOptions\":true,"
            + "\"negativeMarking\":false,\"resultRelease\":\"MANUAL\"}";

    @Autowired
    private MockMvc mvc;
    @Autowired
    private PostgreSQLContainer postgres;

    private String root;
    private String admin;
    private UUID tenant;

    @BeforeEach
    void setUp() throws Exception {
        root = token(login("root@telt.test", "Root#Pass123"));
        admin = tenantAdmin();
        tenant = UUID.fromString(claim(admin, "tid"));
        call(put("/tenant-settings"), admin, "{\"subAdminDefaultAccessMode\":\"DEPARTMENT_SCOPED\",\"separationOfDuties\":false}")
                .andExpect(status().isOk());
    }

    @Test
    void tenantExamWithTimedSegmentsSnapshotsOwnAndGrantedQuestionsOnPublish() throws Exception {
        String own = approvedQuestion(admin, bank(admin), "Own question");
        String draft = JsonPath.read(body(call(post("/question-banks/" + bank(admin) + "/questions"), admin,
                "{\"type\":\"SINGLE_CHOICE\",\"version\":" + VERSION.formatted("Draft only") + "}")
                .andExpect(status().isCreated())), "$.questionId");
        String platformBank = bank(root);
        String platform = approvedQuestion(root, platformBank, "Platform question");
        call(post("/platform/question-banks/" + platformBank + "/releases"), root, null).andExpect(status().isCreated());
        String grant = JsonPath.read(body(call(post("/platform/grants"), root, "{\"bankId\":\"" + platformBank
                + "\",\"tenantId\":\"" + tenant + "\",\"settings\":{\"releasePolicy\":\"LATEST\",\"allowClone\":false,\"answerKeysVisible\":false}}")
                .andExpect(status().isCreated())), "$.grantId");

        String sections = "[{\"title\":\"Part A\",\"timeLimitMinutes\":%d,\"items\":[{\"questionId\":\"" + own + "\"}]},"
                + "{\"title\":\"Part B\",\"items\":[{\"questionId\":\"" + platform + "\",\"grantId\":\"" + grant
                + "\",\"marks\":2}]}]";
        call(post("/exams"), admin, exam("E1", 30, sections.formatted(10).replace(own, draft)))
                .andExpect(status().isBadRequest());
        call(post("/exams"), admin, exam("E1", 30, sections.formatted(40))).andExpect(status().isBadRequest());
        call(post("/exams"), admin, exam("E1", 30, sections.formatted(10).replace("\"grantId\":\"" + grant + "\",", "")))
                .andExpect(status().isBadRequest());
        call(post("/exams"), admin, exam("E1", 30, "[{\"title\":\"Pool\",\"pickCount\":3,\"items\":[{\"questionId\":\""
                + own + "\"}]}]")).andExpect(status().isBadRequest());

        String examId = JsonPath.read(body(call(post("/exams"), admin, exam("E1", 30, sections.formatted(10)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.versions[0].status").value("DRAFT"))
                .andExpect(jsonPath("$.versions[0].totalMarks").value(3.0))
                .andExpect(jsonPath("$.versions[0].settings.maxAttempts").value(1))
                .andExpect(jsonPath("$.versions[0].sections[0].timeLimitMinutes").value(10))
                .andExpect(jsonPath("$.versions[0].sections[1].items[0].grantId").value(grant))), "$.exam.examId");
        call(post("/exams"), admin, exam("e1", 30, sections.formatted(10))).andExpect(status().isConflict());
        call(post("/exams/" + examId + "/versions/1/publish"), admin, null).andExpect(status().isConflict());
        call(post("/exams/" + examId + "/versions/1/submit"), admin, null).andExpect(jsonPath("$.status").value("IN_REVIEW"));
        call(put("/exams/" + examId + "/versions/1"), admin, version(30, sections.formatted(5)))
                .andExpect(status().isConflict());
        call(post("/exams/" + examId + "/versions/1/review"), admin, "{\"decision\":\"REJECT\"}")
                .andExpect(jsonPath("$.status").value("DRAFT"));
        call(put("/exams/" + examId + "/versions/1"), admin, version(30, sections.formatted(5)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.sections[0].timeLimitMinutes").value(5));
        call(post("/exams/" + examId + "/versions/1/submit"), admin, null).andExpect(status().isOk());
        call(post("/exams/" + examId + "/versions/1/review"), admin, "{\"decision\":\"APPROVE\"}")
                .andExpect(jsonPath("$.status").value("APPROVED"));
        call(post("/exams/" + examId + "/versions/1/publish"), admin, null)
                .andExpect(jsonPath("$.status").value("PUBLISHED"));

        call(post("/platform/grants/" + grant + "/revoke"), root, null).andExpect(status().isOk());
        call(get("/exams/" + examId + "/versions/1/questions"), admin, null).andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[1].content.text").value("Platform question"))
                .andExpect(jsonPath("$[1].marks").value(2.0))
                .andExpect(jsonPath("$[0].answerKey.correctOptionIds[0]").value("b"))
                .andExpect(jsonPath("$[1].answerKey").value(nullValue()));
        call(get("/exams/" + examId), admin, null).andExpect(jsonPath("$.exam.publishedVersion").value(1));

        JdbcTemplate owner = new JdbcTemplate(new DriverManagerDataSource(postgres.getJdbcUrl(), postgres.getUsername(),
                postgres.getPassword()));
        assertThatThrownBy(() -> owner.update("update exam_question_snapshots set marks = 99 where exam_id = ?",
                UUID.fromString(examId))).rootCause().hasMessageContaining("immutable");
        assertThatThrownBy(() -> owner.update("update exam_sections set title = 'x' where exam_id = ?",
                UUID.fromString(examId))).rootCause().hasMessageContaining("cannot be modified");
        assertThatThrownBy(() -> owner.update("update exam_versions set settings = '{}' where exam_id = ?",
                UUID.fromString(examId))).rootCause().hasMessageContaining("cannot be modified");

        String other = tenantAdmin();
        call(get("/exams/" + examId), other, null).andExpect(status().isNotFound());
        call(get("/exams"), other, null).andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void superAdminAuthorsPlatformExamsFromPlatformQuestionsOnly() throws Exception {
        String platform = approvedQuestion(root, bank(root), "Platform only");
        String own = approvedQuestion(admin, bank(admin), "Tenant only");
        call(post("/exams"), root, exam("P-" + UUID.randomUUID(), 60, "[{\"title\":\"All\",\"items\":[{\"questionId\":\""
                + own + "\"}]}]")).andExpect(status().isBadRequest());
        String examId = JsonPath.read(body(call(post("/exams"), root, exam("P-" + UUID.randomUUID(), 60,
                "[{\"title\":\"All\",\"items\":[{\"questionId\":\"" + platform + "\"}]}]"))
                .andExpect(status().isCreated())), "$.exam.examId");
        call(post("/exams/" + examId + "/versions/1/submit"), root, null).andExpect(status().isOk());
        call(post("/exams/" + examId + "/versions/1/review"), root, "{\"decision\":\"APPROVE\"}").andExpect(status().isOk());
        call(post("/exams/" + examId + "/versions/1/publish"), root, null).andExpect(jsonPath("$.status").value("PUBLISHED"));
        call(get("/exams"), root, null).andExpect(jsonPath("$[*].examId", hasItem(examId)));
        call(get("/exams/" + examId), admin, null).andExpect(status().isNotFound());
        call(get("/exams"), admin, null).andExpect(jsonPath("$[*].examId", not(hasItem(examId))));
    }

    @Test
    void separationOfDutiesBlocksSelfReviewOfExams() throws Exception {
        call(put("/tenant-settings"), admin, "{\"subAdminDefaultAccessMode\":\"DEPARTMENT_SCOPED\",\"separationOfDuties\":true}")
                .andExpect(status().isOk());
        String bankId = bank(admin);
        call(put("/tenant-settings"), admin, "{\"subAdminDefaultAccessMode\":\"DEPARTMENT_SCOPED\",\"separationOfDuties\":false}")
                .andExpect(status().isOk());
        String q = approvedQuestion(admin, bankId, "Separation");
        call(put("/tenant-settings"), admin, "{\"subAdminDefaultAccessMode\":\"DEPARTMENT_SCOPED\",\"separationOfDuties\":true}")
                .andExpect(status().isOk());
        String examId = JsonPath.read(body(call(post("/exams"), admin, exam("S1", 20,
                "[{\"title\":\"All\",\"items\":[{\"questionId\":\"" + q + "\"}]}]")).andExpect(status().isCreated())),
                "$.exam.examId");
        call(post("/exams/" + examId + "/versions/1/submit"), admin, null).andExpect(status().isOk());
        call(post("/exams/" + examId + "/versions/1/review"), admin, "{\"decision\":\"APPROVE\"}")
                .andExpect(status().isForbidden());
    }

    private static String exam(String code, int duration, String sections) {
        return "{\"code\":\"" + code + "\",\"title\":\"Exam " + code + "\",\"version\":" + version(duration, sections) + "}";
    }

    private static String version(int duration, String sections) {
        return "{\"settings\":" + SETTINGS.formatted(duration) + ",\"sections\":" + sections + "}";
    }

    private String approvedQuestion(String token, String bank, String text) throws Exception {
        String id = JsonPath.read(body(call(post("/question-banks/" + bank + "/questions"), token,
                "{\"type\":\"SINGLE_CHOICE\",\"version\":" + VERSION.formatted(text) + "}")
                .andExpect(status().isCreated())), "$.questionId");
        call(post("/questions/" + id + "/versions/1/submit"), token, null).andExpect(status().isOk());
        call(post("/questions/" + id + "/versions/1/review"), token, "{\"decision\":\"APPROVE\"}").andExpect(status().isOk());
        return id;
    }

    private String bank(String token) throws Exception {
        return JsonPath.read(body(call(post("/question-banks"), token, "{\"name\":\"Bank " + UUID.randomUUID() + "\"}")
                .andExpect(status().isCreated())), "$.bankId");
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
