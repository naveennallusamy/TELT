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
class ExamAttemptIntegrationTests {

    private static final String PASSWORD = "Str0ng!Pass";
    private static final String NEW_PASSWORD = "Str0ng!Pass2";
    private static final AtomicLong MOBILE = new AtomicLong(9400000000L);
    private static final String VERSION = "{\"content\":{\"text\":\"%s\","
            + "\"options\":[{\"id\":\"a\",\"text\":\"3\"},{\"id\":\"b\",\"text\":\"4\"}]},"
            + "\"answerKey\":{\"correctOptionIds\":[\"b\"]},\"defaultMarks\":2}";
    private static final String SETTINGS = "{\"durationMinutes\":%d,\"shuffleQuestions\":true,\"shuffleOptions\":true,"
            + "\"negativeMarking\":true,\"resultRelease\":\"MANUAL\"}";

    @Autowired
    private MockMvc mvc;

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
    void studentTakesSectionedExamAndEvaluatorGradesDescriptive() throws Exception {
        String bank = bank(admin);
        String q1 = approvedQuestion(admin, bank, "First");
        String q2 = approvedQuestion(admin, bank, "Second");
        String essay = descriptive(admin, bank);
        String examId = JsonPath.read(body(call(post("/exams"), admin, exam("ATT1", 30,
                "[{\"title\":\"A\",\"timeLimitMinutes\":10,\"items\":[{\"questionId\":\"" + q1 + "\"},"
                        + "{\"questionId\":\"" + q2 + "\",\"negativeMarks\":1}]},"
                        + "{\"title\":\"B\",\"items\":[{\"questionId\":\"" + essay + "\"}]}]"))
                .andExpect(status().isCreated())), "$.exam.examId");
        publish(admin, examId, 1);
        String scheduleId = openSchedule(examId, 86400);
        Student student = student(admin);
        Student other = student(admin);
        call(post("/exam-schedules/" + scheduleId + "/assignments"), admin, "{\"userIds\":[\"" + student.id() + "\"]}")
                .andExpect(status().isOk());

        call(post("/exam-schedules/" + scheduleId + "/attempts"), other.token(), null).andExpect(status().isNotFound());
        String started = body(call(post("/exam-schedules/" + scheduleId + "/attempts"), student.token(), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.currentSection").value(0))
                .andExpect(jsonPath("$.sections[0].state").value("CURRENT"))
                .andExpect(jsonPath("$.sections[0].questions", hasSize(2)))
                .andExpect(jsonPath("$.sections[0].questions[0].options", hasSize(2)))
                .andExpect(jsonPath("$.sections[0].questions[0].answerKey").doesNotExist())
                .andExpect(jsonPath("$.sections[1].state").value("UPCOMING"))
                .andExpect(jsonPath("$.sections[1].questionCount").value(1))
                .andExpect(jsonPath("$.sections[1].questions", hasSize(0))));
        String attemptId = JsonPath.read(started, "$.attemptId");
        String order = JsonPath.read(started, "$.sections[0].questions[*].snapshotId").toString();
        String options = JsonPath.read(started, "$.sections[0].questions[*].options[*].id").toString();
        String resumed = body(call(post("/exam-schedules/" + scheduleId + "/attempts"), student.token(), null)
                .andExpect(status().isOk()).andExpect(jsonPath("$.attemptId").value(attemptId)));
        assertThat(JsonPath.read(resumed, "$.sections[0].questions[*].snapshotId").toString()).isEqualTo(order);
        assertThat(JsonPath.read(resumed, "$.sections[0].questions[*].options[*].id").toString()).isEqualTo(options);

        String s1 = snapshot(started, "First");
        String s2 = snapshot(started, "Second");
        String base = "/me/attempts/" + attemptId;
        call(get(base), other.token(), null).andExpect(status().isNotFound());
        call(put(base + "/answers"), student.token(), answer(s1, "\"z\"")).andExpect(status().isBadRequest());
        call(put(base + "/answers"), student.token(), answer(s1, "\"a\",\"b\"")).andExpect(status().isBadRequest());
        call(put(base + "/answers"), student.token(), "{\"answers\":[{\"snapshotId\":\"" + s1
                + "\",\"response\":{\"selectedOptionIds\":[\"b\"]}},{\"snapshotId\":\"" + s2
                + "\",\"response\":{\"selectedOptionIds\":[\"a\"]}}]}").andExpect(status().isOk())
                .andExpect(jsonPath("$.sections[0].questions[?(@.snapshotId=='" + s1 + "')].response.selectedOptionIds[0]",
                        contains("b")));

        String moved = body(call(post(base + "/next-section"), student.token(), null).andExpect(status().isOk())
                .andExpect(jsonPath("$.sections[0].state").value("DONE"))
                .andExpect(jsonPath("$.sections[1].questions", hasSize(1))));
        String s3 = JsonPath.read(moved, "$.sections[1].questions[0].snapshotId");
        call(put(base + "/answers"), student.token(), answer(s1, "\"a\"")).andExpect(status().isConflict());
        call(post(base + "/next-section"), student.token(), null).andExpect(status().isBadRequest());
        call(put(base + "/answers"), student.token(), "{\"answers\":[{\"snapshotId\":\"" + s3
                + "\",\"response\":{\"text\":\"Because it is\"}}]}").andExpect(status().isOk());
        call(post(base + "/submit"), student.token(), null).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUBMITTED"))
                .andExpect(jsonPath("$.score").doesNotExist());
        call(put(base + "/answers"), student.token(), answer(s3, "\"a\"")).andExpect(status().isConflict());
        call(post("/exam-schedules/" + scheduleId + "/attempts"), student.token(), null).andExpect(status().isConflict());
        call(get("/me/attempts"), student.token(), null).andExpect(jsonPath("$", hasSize(1)));

        call(get("/exam-schedules/" + scheduleId + "/attempts"), admin, null).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].score").value(1.0))
                .andExpect(jsonPath("$[0].maxScore").value(6.0))
                .andExpect(jsonPath("$[0].answered").value(3))
                .andExpect(jsonPath("$[0].gradingStatus").value("PENDING_MANUAL"));
        call(get("/attempts/" + attemptId), admin, null).andExpect(status().isOk())
                .andExpect(jsonPath("$.answers", hasSize(3)))
                .andExpect(jsonPath("$.answers[?(@.snapshotId=='" + s2 + "')].marksAwarded", contains(-1.0)))
                .andExpect(jsonPath("$.answers[?(@.snapshotId=='" + s1 + "')].answerKey.correctOptionIds[0]",
                        contains("b")));
        String grade = "/attempts/" + attemptId + "/answers/";
        call(put(grade + s3 + "/grade"), admin, "{\"marks\":5}").andExpect(status().isBadRequest());
        call(put(grade + s1 + "/grade"), admin, "{\"marks\":1}").andExpect(status().isConflict());
        call(put(grade + s3 + "/grade"), admin, "{\"marks\":1.5,\"feedback\":\"Thin\"}").andExpect(status().isOk())
                .andExpect(jsonPath("$.attempt.score").value(2.5))
                .andExpect(jsonPath("$.attempt.gradingStatus").value("GRADED"));
        call(get("/attempts/" + attemptId), tenantAdmin(), null).andExpect(status().isNotFound());
    }

    @Test
    void attemptExpiresAtScheduleEndAndIsGraded() throws Exception {
        String bank = bank(admin);
        String q = approvedQuestion(admin, bank, "Only");
        String examId = JsonPath.read(body(call(post("/exams"), admin, exam("ATT2", 30,
                "[{\"title\":\"All\",\"items\":[{\"questionId\":\"" + q + "\"}]}]"))
                .andExpect(status().isCreated())), "$.exam.examId");
        publish(admin, examId, 1);
        String scheduleId = openSchedule(examId, 3);
        Student student = student(admin);
        call(post("/exam-schedules/" + scheduleId + "/assignments"), admin, "{\"userIds\":[\"" + student.id() + "\"]}")
                .andExpect(status().isOk());
        String started = body(call(post("/exam-schedules/" + scheduleId + "/attempts"), student.token(), null)
                .andExpect(status().isOk()));
        String attemptId = JsonPath.read(started, "$.attemptId");
        call(put("/me/attempts/" + attemptId + "/answers"), student.token(),
                answer(snapshot(started, "Only"), "\"b\"")).andExpect(status().isOk());
        Thread.sleep(3500);
        call(get("/me/attempts/" + attemptId), student.token(), null).andExpect(jsonPath("$.status").value("EXPIRED"));
        call(get("/exam-schedules/" + scheduleId + "/attempts"), admin, null)
                .andExpect(jsonPath("$[0].score").value(2.0)).andExpect(jsonPath("$[0].gradingStatus").value("GRADED"));
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
