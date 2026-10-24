package com.telt;

import com.jayway.jsonpath.JsonPath;
import com.telt.support.TestcontainersConfiguration;
import com.telt.constants.AttemptStatus;
import com.telt.persistence.RlsContext;
import com.telt.repository.ExamAttemptRepository;
import com.telt.service.AttemptExpiryJob;
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
class ExamResultIntegrationTests {

    private static final String PASSWORD = "Str0ng!Pass";
    private static final String NEW_PASSWORD = "Str0ng!Pass2";
    private static final AtomicLong MOBILE = new AtomicLong(9900000000L);
    private static final String VERSION = "{\"content\":{\"text\":\"%s\","
            + "\"options\":[{\"id\":\"a\",\"text\":\"3\"},{\"id\":\"b\",\"text\":\"4\"}]},"
            + "\"answerKey\":{\"correctOptionIds\":[\"b\"]},\"defaultMarks\":2}";
    private static final String SETTINGS = "{\"durationMinutes\":%d,\"shuffleQuestions\":true,\"shuffleOptions\":true,"
            + "\"negativeMarking\":true,\"resultRelease\":\"MANUAL\"}";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private AttemptExpiryJob expiryJob;

    @Autowired
    private ExamAttemptRepository attemptRepository;

    private String root;
    private String admin;

    @BeforeEach
    void setUp() throws Exception {
        root = token(login("root@telt.test", "Root#Pass123"));
        admin = tenantAdmin();
        call(put("/tenant-settings"), admin, "{\"subAdminDefaultAccessMode\":\"DEPARTMENT_SCOPED\",\"separationOfDuties\":false}")
                .andExpect(status().isOk());
    }

    @Test
    void manualResultsNeedSubmitApproveAndPublish() throws Exception {
        Sitting s = sitting("RES1", "MANUAL", 3);
        String attemptId = takeAndSubmit(s);
        String results = "/exam-schedules/" + s.scheduleId() + "/results";
        call(get("/me/results"), s.student().token(), null).andExpect(jsonPath("$", hasSize(0)));
        call(get(results), admin, null).andExpect(status().isOk())
                .andExpect(jsonPath("$.resultRelease").value("MANUAL"))
                .andExpect(jsonPath("$.status").doesNotExist())
                .andExpect(jsonPath("$.released").value(false))
                .andExpect(jsonPath("$.attempts").value(1));
        call(post(results + "/submit"), admin, null).andExpect(status().isConflict());
        Thread.sleep(3500);
        call(post(results + "/publish"), admin, null).andExpect(status().isConflict());
        call(post(results + "/submit"), admin, null).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING_APPROVAL"));
        call(post(results + "/submit"), admin, null).andExpect(status().isConflict());
        settings(true);
        call(post(results + "/decision"), admin, "{\"decision\":\"APPROVE\"}").andExpect(status().isForbidden());
        settings(false);
        call(post(results + "/decision"), admin, "{\"decision\":\"REJECT\",\"comment\":\"Recheck\"}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("REJECTED"));
        call(post(results + "/submit"), admin, null).andExpect(status().isOk());
        call(post(results + "/decision"), admin, "{\"decision\":\"APPROVE\"}").andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));
        call(get("/me/results"), s.student().token(), null).andExpect(jsonPath("$", hasSize(0)));
        call(post(results + "/publish"), admin, null).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PUBLISHED"))
                .andExpect(jsonPath("$.released").value(true));
        call(get("/me/results"), s.student().token(), null).andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].attemptId").value(attemptId))
                .andExpect(jsonPath("$[0].score").value(2.0))
                .andExpect(jsonPath("$[0].maxScore").value(2.0))
                .andExpect(jsonPath("$[0].answers[0].marksAwarded").value(2.0))
                .andExpect(jsonPath("$[0].answers[0].answerKey").doesNotExist());
        call(get(results), s.student().token(), null).andExpect(status().isForbidden());
    }

    @Test
    void immediateResultsAreVisibleOnSubmit() throws Exception {
        Sitting s = sitting("RES2", "IMMEDIATE", 86400);
        takeAndSubmit(s);
        call(get("/me/results"), s.student().token(), null).andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].score").value(2.0));
        call(post("/exam-schedules/" + s.scheduleId() + "/results/submit"), admin, null)
                .andExpect(status().isConflict());
    }

    @Test
    void extraTimeExtendsDeadlinesAndSweeperExpiresAbandonedAttempts() throws Exception {
        Sitting s = sitting("RES3", "AFTER_WINDOW", 3);
        Student slow = s.student();
        Student other = student(admin);
        call(post("/exam-schedules/" + s.scheduleId() + "/assignments"), admin, "{\"userIds\":[\"" + other.id() + "\"]}")
                .andExpect(status().isOk());
        String acc = "/exam-schedules/" + s.scheduleId() + "/accommodations/";
        call(put(acc + slow.id()), admin, "{\"extraMinutes\":0}").andExpect(status().isBadRequest());
        call(put(acc + claim(admin, "sub")), admin, "{\"extraMinutes\":5}").andExpect(status().isBadRequest());
        call(put(acc + slow.id()), slow.token(), "{\"extraMinutes\":5}").andExpect(status().isForbidden());
        call(put(acc + slow.id()), admin, "{\"extraMinutes\":5,\"reason\":\"Medical\"}").andExpect(status().isOk())
                .andExpect(jsonPath("$.extraMinutes").value(5));

        String slowAttempt = JsonPath.read(body(call(post("/exam-schedules/" + s.scheduleId() + "/attempts"), slow.token(),
                null).andExpect(status().isOk())), "$.attemptId");
        String started = body(call(post("/exam-schedules/" + s.scheduleId() + "/attempts"), other.token(), null)
                .andExpect(status().isOk()));
        String otherAttempt = JsonPath.read(started, "$.attemptId");
        Instant before = Instant.parse(JsonPath.read(started, "$.deadlineAt"));
        call(put(acc + other.id()), admin, "{\"extraMinutes\":1}").andExpect(status().isOk());
        Instant after = Instant.parse(JsonPath.read(body(call(get("/me/attempts/" + otherAttempt), other.token(), null)),
                "$.deadlineAt"));
        assertThat(after).isEqualTo(before.plusSeconds(60));
        call(get("/exam-schedules/" + s.scheduleId() + "/accommodations"), admin, null)
                .andExpect(jsonPath("$", hasSize(2)));

        Thread.sleep(3500);
        call(get("/me/attempts/" + slowAttempt), slow.token(), null).andExpect(jsonPath("$.status").value("IN_PROGRESS"));

        String third = schedule3s(s.examId());
        Student gone = student(admin);
        call(post("/exam-schedules/" + third + "/assignments"), admin, "{\"userIds\":[\"" + gone.id() + "\"]}")
                .andExpect(status().isOk());
        String goneAttempt = JsonPath.read(body(call(post("/exam-schedules/" + third + "/attempts"), gone.token(), null)
                .andExpect(status().isOk())), "$.attemptId");
        Thread.sleep(3500);
        expiryJob.sweep();
        assertThat(RlsContext.asSystem(() -> attemptRepository.findById(UUID.fromString(goneAttempt)).orElseThrow()
                .getStatus())).isEqualTo(AttemptStatus.EXPIRED);
    }

    @Test
    void superAdminSeesTenantAttempts() throws Exception {
        Sitting s = sitting("RES4", "IMMEDIATE", 86400);
        String attemptId = takeAndSubmit(s);
        String tenant = claim(admin, "tid");
        String path = "/platform/tenants/" + tenant + "/exam-schedules/" + s.scheduleId() + "/attempts";
        call(get(path), admin, null).andExpect(status().isForbidden());
        call(get(path), root, null).andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(1)));
        call(get("/platform/tenants/" + tenant + "/attempts/" + attemptId), root, null).andExpect(status().isOk())
                .andExpect(jsonPath("$.answers", hasSize(1)));
    }

    private record Sitting(String examId, String scheduleId, Student student) {
    }

    private Sitting sitting(String code, String release, int endsInSeconds) throws Exception {
        String q = approvedQuestion(admin, bank(admin), "Only");
        String examId = JsonPath.read(body(call(post("/exams"), admin, exam(code, 30,
                "[{\"title\":\"All\",\"items\":[{\"questionId\":\"" + q + "\"}]}]")
                .replace("\"MANUAL\"", "\"" + release + "\"")).andExpect(status().isCreated())), "$.exam.examId");
        publish(admin, examId, 1);
        String scheduleId = openSchedule(examId, endsInSeconds);
        Student student = student(admin);
        call(post("/exam-schedules/" + scheduleId + "/assignments"), admin, "{\"userIds\":[\"" + student.id() + "\"]}")
                .andExpect(status().isOk());
        return new Sitting(examId, scheduleId, student);
    }

    private String schedule3s(String examId) throws Exception {
        return openSchedule(examId, 3);
    }

    private String takeAndSubmit(Sitting s) throws Exception {
        String started = body(call(post("/exam-schedules/" + s.scheduleId() + "/attempts"), s.student().token(), null)
                .andExpect(status().isOk()));
        String attemptId = JsonPath.read(started, "$.attemptId");
        call(put("/me/attempts/" + attemptId + "/answers"), s.student().token(), answer(snapshot(started, "Only"), "\"b\""))
                .andExpect(status().isOk());
        call(post("/me/attempts/" + attemptId + "/submit"), s.student().token(), null).andExpect(status().isOk());
        return attemptId;
    }

    private void settings(boolean separation) throws Exception {
        call(put("/tenant-settings"), admin, "{\"subAdminDefaultAccessMode\":\"DEPARTMENT_SCOPED\",\"separationOfDuties\":"
                + separation + "}").andExpect(status().isOk());
    }

    private String openSchedule(String examId, int endsInSeconds) throws Exception {
        Instant now = Instant.now();
        return JsonPath.read(body(call(post("/exam-schedules"), admin, "{\"examId\":\"" + examId
                + "\",\"title\":\"Now\",\"startsAt\":\"" + now.minusSeconds(60) + "\",\"endsAt\":\""
                + now.plusSeconds(endsInSeconds) + "\",\"timeZone\":\"UTC\"}").andExpect(status().isCreated())),
                "$.scheduleId");
    }

    private static String snapshot(String attempt, String text) {
        java.util.List<String> ids = JsonPath.read(attempt, "$.sections[*].questions[?(@.text=='" + text + "')].snapshotId");
        return ids.getFirst();
    }

    private static String answer(String snapshotId, String options) {
        return "{\"answers\":[{\"snapshotId\":\"" + snapshotId + "\",\"response\":{\"selectedOptionIds\":["
                + options + "]}}]}";
    }

    private String descriptive(String token, String bank) throws Exception {
        String id = JsonPath.read(body(call(post("/question-banks/" + bank + "/questions"), token,
                "{\"type\":\"DESCRIPTIVE\",\"version\":{\"content\":{\"text\":\"Explain\"},"
                        + "\"answerKey\":{\"rubric\":\"Mention the reason\"},\"defaultMarks\":2}}")
                .andExpect(status().isCreated())), "$.questionId");
        call(post("/questions/" + id + "/versions/1/submit"), token, null).andExpect(status().isOk());
        call(post("/questions/" + id + "/versions/1/review"), token, "{\"decision\":\"APPROVE\"}").andExpect(status().isOk());
        return id;
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
