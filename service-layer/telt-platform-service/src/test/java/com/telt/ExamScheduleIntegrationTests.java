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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

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
class ExamScheduleIntegrationTests {

    private static final String PASSWORD = "Str0ng!Pass";
    private static final String NEW_PASSWORD = "Str0ng!Pass2";
    private static final AtomicLong MOBILE = new AtomicLong(9200000000L);
    private static final String VERSION = "{\"content\":{\"text\":\"%s\","
            + "\"options\":[{\"id\":\"a\",\"text\":\"3\"},{\"id\":\"b\",\"text\":\"4\"}]},"
            + "\"answerKey\":{\"correctOptionIds\":[\"b\"]},\"defaultMarks\":2}";
    private static final String SETTINGS = "{\"durationMinutes\":%d,\"shuffleQuestions\":true,\"shuffleOptions\":true,"
            + "\"resultRelease\":\"MANUAL\"}";

    @Autowired
    private MockMvc mvc;

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
    void tenantSchedulesExamAndStudentsSeeOnlyAssignedOnes() throws Exception {
        String bank = bank(admin);
        String q1 = approvedQuestion(admin, bank, "First");
        String q2 = approvedQuestion(admin, bank, "Second");
        String examId = JsonPath.read(body(call(post("/exams"), admin, exam("SCH1", 30,
                "[{\"title\":\"A\",\"timeLimitMinutes\":10,\"items\":[{\"questionId\":\"" + q1 + "\"}]},"
                        + "{\"title\":\"B\",\"items\":[{\"questionId\":\"" + q2 + "\"}]}]"))
                .andExpect(status().isCreated())), "$.exam.examId");
        call(post("/exam-schedules"), admin, schedule(examId, 1, 2, null, null)).andExpect(status().isConflict());
        publish(admin, examId, 1);

        String parent = department(admin, "SCI");
        String child = JsonPath.read(body(call(post("/departments"), admin, "{\"code\":\"PHY\",\"name\":\"Physics\","
                + "\"parentDepartmentId\":\"" + parent + "\"}").andExpect(status().isCreated())), "$.departmentId");
        Student inChild = student(admin);
        call(patch("/members/" + inChild.id()), admin, "{\"departmentId\":\"" + child + "\"}").andExpect(status().isOk());
        Student unassigned = student(admin);
        Student direct = student(admin);

        call(post("/exam-schedules"), admin, schedule(examId, 2, 1, null, null)).andExpect(status().isBadRequest());
        call(post("/exam-schedules"), admin, schedule(examId, 1, 2, "{\"durationMinutes\":5}", null))
                .andExpect(status().isBadRequest());
        String scheduleId = JsonPath.read(body(call(post("/exam-schedules"), admin,
                schedule(examId, 1, 2, "{\"durationMinutes\":45}", 15)).andExpect(status().isCreated())
                .andExpect(jsonPath("$.versionNo").value(1))
                .andExpect(jsonPath("$.effectiveSettings.durationMinutes").value(45))
                .andExpect(jsonPath("$.effectiveSettings.resultRelease").value("MANUAL"))), "$.scheduleId");

        String otherAdmin = tenantAdmin();
        Student foreign = student(otherAdmin);
        call(post("/exam-schedules/" + scheduleId + "/assignments"), admin, "{\"userIds\":[\"" + foreign.id() + "\"]}")
                .andExpect(status().isBadRequest());
        call(post("/exam-schedules/" + scheduleId + "/assignments"), admin, "{\"userIds\":[\"" + direct.id()
                + "\"],\"departmentIds\":[\"" + parent + "\"]}").andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)));

        call(get("/me/exams"), inChild.token(), null).andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].scheduleId").value(scheduleId))
                .andExpect(jsonPath("$[0].durationMinutes").value(45))
                .andExpect(jsonPath("$[0].lateEntryMinutes").value(15))
                .andExpect(jsonPath("$[0].totalMarks").value(4.0))
                .andExpect(jsonPath("$[0].sections[0].timeLimitMinutes").value(10))
                .andExpect(jsonPath("$[0].sections[*].questionCount", contains(1, 1)));
        call(get("/me/exams"), direct.token(), null).andExpect(jsonPath("$", hasSize(1)));
        call(get("/me/exams"), unassigned.token(), null).andExpect(jsonPath("$", hasSize(0)));
        call(get("/exam-schedules/" + scheduleId), otherAdmin, null).andExpect(status().isNotFound());
        call(get("/exam-schedules"), otherAdmin, null).andExpect(jsonPath("$", hasSize(0)));

        call(put("/exam-schedules/" + scheduleId), admin, schedule(examId, 3, 4, null, null)).andExpect(status().isOk())
                .andExpect(jsonPath("$.effectiveSettings.durationMinutes").value(30));
        call(post("/exam-schedules/" + scheduleId + "/cancel"), admin, null).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
        call(get("/me/exams"), inChild.token(), null).andExpect(jsonPath("$", hasSize(0)));
        call(post("/exam-schedules/" + scheduleId + "/assignments"), admin, "{\"userIds\":[\"" + unassigned.id() + "\"]}")
                .andExpect(status().isConflict());
    }

    @Test
    void platformExamDistributedWithConfigurableOverridesAndSuperAdminViews() throws Exception {
        String platformBank = bank(root);
        String q = approvedQuestion(root, platformBank, "Platform question");
        String sections = "[{\"title\":\"All\",\"items\":[{\"questionId\":\"" + q + "\"}]}]";
        String examId = JsonPath.read(body(call(post("/exams"), root, exam("PLAT-" + UUID.randomUUID().toString()
                .substring(0, 8), 30, sections)).andExpect(status().isCreated())), "$.exam.examId");
        call(post("/platform/exams/" + examId + "/distributions"), root, "{\"tenantId\":\"" + tenant + "\"}")
                .andExpect(status().isConflict());
        publish(root, examId, 1);
        call(post("/platform/exams/" + examId + "/distributions"), admin, "{\"tenantId\":\"" + tenant + "\"}")
                .andExpect(status().isForbidden());
        String distribution = body(call(post("/platform/exams/" + examId + "/distributions"), root,
                "{\"tenantId\":\"" + tenant + "\",\"overridePolicy\":{\"duration\":true}}")
                .andExpect(status().isCreated()).andExpect(jsonPath("$.sourceVersionNo").value(1))
                .andExpect(jsonPath("$.overridePolicy.duration").value(true))
                .andExpect(jsonPath("$.overridePolicy.maxAttempts").value(false)));
        String distributionId = JsonPath.read(distribution, "$.distributionId");
        String copy = JsonPath.read(distribution, "$.examId");
        call(post("/platform/exams/" + examId + "/distributions"), root, "{\"tenantId\":\"" + tenant + "\"}")
                .andExpect(status().isConflict());

        call(get("/exams"), admin, null).andExpect(jsonPath("$[?(@.examId=='" + copy + "')].sourceExamId", contains(examId)));
        call(get("/exams/" + copy + "/versions/1/questions"), admin, null).andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1))).andExpect(jsonPath("$[0].answerKey").value(nullValue()));
        call(post("/exams/" + copy + "/versions"), admin, version(30, sections)).andExpect(status().isConflict());

        call(post("/exam-schedules"), admin, schedule(copy, 1, 2, "{\"maxAttempts\":2}", null))
                .andExpect(status().isForbidden());
        call(post("/exam-schedules"), admin, schedule(copy, 1, 2, null, 10)).andExpect(status().isForbidden());
        String scheduleId = JsonPath.read(body(call(post("/exam-schedules"), admin,
                schedule(copy, 1, 2, "{\"durationMinutes\":40}", null)).andExpect(status().isCreated())), "$.scheduleId");

        call(get("/platform/tenants/" + tenant + "/exam-schedules"), admin, null).andExpect(status().isForbidden());
        call(get("/platform/tenants/" + tenant + "/exam-schedules"), root, null).andExpect(status().isOk())
                .andExpect(jsonPath("$[*].scheduleId", contains(scheduleId)));
        call(get("/platform/tenants/" + tenant + "/exams"), root, null)
                .andExpect(jsonPath("$[*].examId", hasItem(copy)));
        call(get("/platform/exams/" + examId + "/distributions"), root, null)
                .andExpect(jsonPath("$[0].tenantId").value(tenant.toString()))
                .andExpect(jsonPath("$[0].activeSchedules").value(1));
        call(get("/audit-events"), admin, null).andExpect(jsonPath("$..action", hasItem("PLATFORM_TENANT_VIEWED")));

        call(put("/platform/distributions/" + distributionId), root, "{\"duration\":true,\"maxAttempts\":true}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.overridePolicy.maxAttempts").value(true));
        call(post("/exam-schedules"), admin, schedule(copy, 3, 4, "{\"maxAttempts\":2}", null))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.effectiveSettings.maxAttempts").value(2));

        call(post("/exams/" + examId + "/versions"), root, version(20, sections)).andExpect(status().isCreated());
        publish(root, examId, 2);
        call(post("/platform/exams/" + examId + "/distributions"), root, "{\"tenantId\":\"" + tenant + "\"}")
                .andExpect(status().isCreated()).andExpect(jsonPath("$.sourceVersionNo").value(2))
                .andExpect(jsonPath("$.overridePolicy.duration").value(false));
        call(get("/exams/" + copy), admin, null).andExpect(jsonPath("$.exam.publishedVersion").value(2))
                .andExpect(jsonPath("$.versions[0].status").value("RETIRED"));
        call(get("/exam-schedules/" + scheduleId), admin, null).andExpect(jsonPath("$.versionNo").value(1));

        call(post("/platform/distributions/" + distributionId + "/revoke"), root, null).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REVOKED"));
        call(get("/exams/" + copy), admin, null).andExpect(jsonPath("$.exam.status").value("INACTIVE"));
        call(get("/exam-schedules/" + scheduleId), admin, null).andExpect(jsonPath("$.status").value("CANCELLED"));
        call(post("/exam-schedules"), admin, schedule(copy, 1, 2, null, null)).andExpect(status().isConflict());
    }

    private record Student(String id, String token) {
    }

    private Student student(String adminToken) throws Exception {
        String email = "s-" + UUID.randomUUID() + "@telt.test";
        String id = JsonPath.read(body(call(post("/user/register"), adminToken, "{\"email\":\"" + email
                + "\",\"mobileNumber\":" + MOBILE.incrementAndGet() + ",\"role\":\"STUDENT\",\"password\":\""
                + PASSWORD + "\"}").andExpect(status().isCreated())), "$.id");
        String first = token(login(email, PASSWORD).andExpect(status().isOk()));
        return new Student(id, token(call(post("/auth/change-password"), first,
                "{\"oldPassword\":\"" + PASSWORD + "\",\"newPassword\":\"" + NEW_PASSWORD + "\"}")
                .andExpect(status().isOk())));
    }

    private String department(String token, String code) throws Exception {
        return JsonPath.read(body(call(post("/departments"), token, "{\"code\":\"" + code + "\",\"name\":\"" + code
                + "\"}").andExpect(status().isCreated())), "$.departmentId");
    }

    private void publish(String token, String examId, int versionNo) throws Exception {
        String base = "/exams/" + examId + "/versions/" + versionNo;
        call(post(base + "/submit"), token, null).andExpect(status().isOk());
        call(post(base + "/review"), token, "{\"decision\":\"APPROVE\"}").andExpect(status().isOk());
        call(post(base + "/publish"), token, null).andExpect(status().isOk());
    }

    /**
     * A window from {@code startDays} to {@code endDays} days from now.
     */
    private static String schedule(String examId, int startDays, int endDays, String overrides, Integer lateEntry) {
        Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        return "{\"examId\":\"" + examId + "\",\"title\":\"Sitting\",\"startsAt\":\"" + now.plus(startDays, ChronoUnit.DAYS)
                + "\",\"endsAt\":\"" + now.plus(endDays, ChronoUnit.DAYS) + "\",\"timeZone\":\"Asia/Kolkata\""
                + (lateEntry == null ? "" : ",\"lateEntryMinutes\":" + lateEntry)
                + (overrides == null ? "" : ",\"overrides\":" + overrides) + "}";
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
