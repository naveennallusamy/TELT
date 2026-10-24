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
class ExamReportIntegrationTests {

    private static final String PASSWORD = "Str0ng!Pass";
    private static final String NEW_PASSWORD = "Str0ng!Pass2";
    private static final AtomicLong MOBILE = new AtomicLong(8900000000L);
    private static final String VERSION = "{\"content\":{\"text\":\"%s\","
            + "\"options\":[{\"id\":\"a\",\"text\":\"3\"},{\"id\":\"b\",\"text\":\"4\"}]},"
            + "\"answerKey\":{\"correctOptionIds\":[\"b\"]},\"defaultMarks\":2}";
    private static final String SETTINGS = "{\"durationMinutes\":%d,\"shuffleQuestions\":true,\"shuffleOptions\":true,"
            + "\"passMarks\":2,\"resultRelease\":\"IMMEDIATE\"}";

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
    void scheduleReportRanksStudentsAndAnalysesQuestions() throws Exception {
        String bank = bank(admin);
        String q1 = approvedQuestion(admin, bank, "Q1");
        String q2 = approvedQuestion(admin, bank, "Q2");
        String examId = JsonPath.read(body(call(post("/exams"), admin, exam("REP1", 30,
                "[{\"title\":\"All\",\"items\":[{\"questionId\":\"" + q1 + "\"},{\"questionId\":\"" + q2
                        + "\"}]}]")).andExpect(status().isCreated())), "$.exam.examId");
        publish(admin, examId, 1);
        Student top = student(admin);
        Student middle = student(admin);
        Student bottom = student(admin);
        Student absent = student(admin);
        Instant ends = Instant.now().plusSeconds(10);
        String scheduleId = JsonPath.read(body(call(post("/exam-schedules"), admin, "{\"examId\":\"" + examId
                + "\",\"title\":\"Now\",\"startsAt\":\"" + Instant.now().minusSeconds(60) + "\",\"endsAt\":\""
                + ends + "\",\"timeZone\":\"UTC\"}").andExpect(status().isCreated())), "$.scheduleId");
        call(post("/exam-schedules/" + scheduleId + "/assignments"), admin, "{\"userIds\":[\"" + top.id() + "\",\""
                + middle.id() + "\",\"" + bottom.id() + "\",\"" + absent.id() + "\"]}").andExpect(status().isOk());
        take(scheduleId, top, "b", "b");
        take(scheduleId, middle, "b", "a");
        take(scheduleId, bottom, "a", "a");

        call(get("/me/results"), top.token(), null).andExpect(jsonPath("$[0].score").value(4.0))
                .andExpect(jsonPath("$[0].rank").doesNotExist());

        String report = "/exam-schedules/" + scheduleId + "/report";
        call(get(report), top.token(), null).andExpect(status().isForbidden());
        call(get(report), admin, null).andExpect(status().isOk())
                .andExpect(jsonPath("$.maxScore").value(4.0))
                .andExpect(jsonPath("$.passMarks").value(2.0))
                .andExpect(jsonPath("$.counts.assigned").value(4))
                .andExpect(jsonPath("$.counts.attempted").value(3))
                .andExpect(jsonPath("$.counts.graded").value(3))
                .andExpect(jsonPath("$.counts.absent").value(1))
                .andExpect(jsonPath("$.counts.submitted").value(3))
                .andExpect(jsonPath("$.stats.students").value(3))
                .andExpect(jsonPath("$.stats.mean").value(2.0))
                .andExpect(jsonPath("$.stats.median").value(2.0))
                .andExpect(jsonPath("$.stats.min").value(0.0))
                .andExpect(jsonPath("$.stats.max").value(4.0))
                .andExpect(jsonPath("$.stats.standardDeviation").value(1.63))
                .andExpect(jsonPath("$.stats.passRate").value(66.67))
                .andExpect(jsonPath("$.distribution", hasSize(10)))
                .andExpect(jsonPath("$.distribution[0].students").value(1))
                .andExpect(jsonPath("$.distribution[5].students").value(1))
                .andExpect(jsonPath("$.distribution[9].students").value(1))
                .andExpect(jsonPath("$.students", hasSize(4)))
                .andExpect(jsonPath("$.students[0].userId").value(top.id()))
                .andExpect(jsonPath("$.students[0].rank").value(1))
                .andExpect(jsonPath("$.students[0].percentile").value(83.33))
                .andExpect(jsonPath("$.students[0].passed").value(true))
                .andExpect(jsonPath("$.students[1].percentile").value(50.0))
                .andExpect(jsonPath("$.students[2].rank").value(3))
                .andExpect(jsonPath("$.students[2].passed").value(false))
                .andExpect(jsonPath("$.students[3].userId").value(absent.id()))
                .andExpect(jsonPath("$.students[3].status").value("ABSENT"))
                .andExpect(jsonPath("$.questions", hasSize(2)))
                .andExpect(jsonPath("$.questions[0].text").value("Q1"))
                .andExpect(jsonPath("$.questions[0].responses").value(3))
                .andExpect(jsonPath("$.questions[0].fullMarksRate").value(66.67))
                .andExpect(jsonPath("$.questions[0].optionCounts.b").value(2))
                .andExpect(jsonPath("$.questions[0].optionCounts.a").value(1))
                .andExpect(jsonPath("$.questions[0].discrimination").value(100.0))
                .andExpect(jsonPath("$.questions[1].fullMarksRate").value(33.33))
                .andExpect(jsonPath("$.questions[1].answerKey").doesNotExist());

        String csv = body(call(get(report + ".csv"), admin, null).andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/csv"))
                .andExpect(header().string("Content-Disposition", containsString("attachment"))));
        assertThat(csv.lines().toList()).hasSize(5);
        assertThat(csv).startsWith("userId,email,departmentId,status,attempts,score,maxScore,percentage,passed,rank,percentile");
        assertThat(csv.lines().toList().get(1)).startsWith(top.id() + ",").contains(",GRADED,1,4.00,4.00,100.00,true,1,83.33");
        call(get(report + ".csv"), top.token(), null).andExpect(status().isForbidden());

        String tenant = claim(admin, "tid");
        String platform = "/platform/tenants/" + tenant + "/exam-schedules/" + scheduleId + "/report";
        call(get(platform), admin, null).andExpect(status().isForbidden());
        call(get(platform), root, null).andExpect(status().isOk()).andExpect(jsonPath("$.counts.graded").value(3));

        Thread.sleep(Math.max(0, ends.toEpochMilli() - System.currentTimeMillis()) + 500);
        call(get("/me/results"), top.token(), null).andExpect(jsonPath("$[0].rank").value(1))
                .andExpect(jsonPath("$[0].percentile").value(83.33))
                .andExpect(jsonPath("$[0].cohortSize").value(3));
        call(get("/me/results"), bottom.token(), null).andExpect(jsonPath("$[0].rank").value(3))
                .andExpect(jsonPath("$[0].percentile").value(16.67));
    }

    @Test
    void superAdminComparesPlatformExamAcrossTenants() throws Exception {
        String q = approvedQuestion(root, bank(root), "Q1");
        String examId = JsonPath.read(body(call(post("/exams"), root, exam("PREP-" + UUID.randomUUID().toString()
                .substring(0, 8), 30, "[{\"title\":\"All\",\"items\":[{\"questionId\":\"" + q + "\"}]}]"))
                .andExpect(status().isCreated())), "$.exam.examId");
        publish(root, examId, 1);
        String tenant = claim(admin, "tid");
        String copy = JsonPath.read(body(call(post("/platform/exams/" + examId + "/distributions"), root,
                "{\"tenantId\":\"" + tenant + "\"}").andExpect(status().isCreated())), "$.examId");
        String scheduleId = openSchedule(copy, 3600);
        Student student = student(admin);
        call(post("/exam-schedules/" + scheduleId + "/assignments"), admin, "{\"userIds\":[\"" + student.id() + "\"]}")
                .andExpect(status().isOk());
        String started = body(call(post("/exam-schedules/" + scheduleId + "/attempts"), student.token(), null)
                .andExpect(status().isOk()));
        String attemptId = JsonPath.read(started, "$.attemptId");
        call(put("/me/attempts/" + attemptId + "/answers"), student.token(),
                "{\"answers\":[" + one(snapshot(started, "Q1"), "b") + "]}").andExpect(status().isOk());
        call(post("/me/attempts/" + attemptId + "/submit"), student.token(), null).andExpect(status().isOk());

        String path = "/platform/exams/" + examId + "/report";
        call(get(path), admin, null).andExpect(status().isForbidden());
        call(get(path), root, null).andExpect(status().isOk())
                .andExpect(jsonPath("$.tenants").value(1))
                .andExpect(jsonPath("$.students").value(1))
                .andExpect(jsonPath("$.averagePercentage").value(100.0))
                .andExpect(jsonPath("$.passRate").value(100.0))
                .andExpect(jsonPath("$.perTenant[0].tenantId").value(tenant))
                .andExpect(jsonPath("$.perTenant[0].tenantExamId").value(copy))
                .andExpect(jsonPath("$.perTenant[0].schedules").value(1));
    }

    private void take(String scheduleId, Student s, String q1, String q2) throws Exception {
        String started = body(call(post("/exam-schedules/" + scheduleId + "/attempts"), s.token(), null)
                .andExpect(status().isOk()));
        String attemptId = JsonPath.read(started, "$.attemptId");
        call(put("/me/attempts/" + attemptId + "/answers"), s.token(), answers(started, q1, q2)).andExpect(status().isOk());
        call(post("/me/attempts/" + attemptId + "/submit"), s.token(), null).andExpect(status().isOk());
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

    private static String answers(String attempt, String q1, String q2) {
        return "{\"answers\":[" + one(snapshot(attempt, "Q1"), q1) + "," + one(snapshot(attempt, "Q2"), q2) + "]}";
    }

    private static String one(String snapshotId, String option) {
        return "{\"snapshotId\":\"" + snapshotId + "\",\"response\":{\"selectedOptionIds\":[\"" + option + "\"]}}";
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


    private void publish(String token, String examId, int versionNo) throws Exception {
        String base = "/exams/" + examId + "/versions/" + versionNo;
        call(post(base + "/submit"), token, null).andExpect(status().isOk());
        call(post(base + "/review"), token, "{\"decision\":\"APPROVE\"}").andExpect(status().isOk());
        call(post(base + "/publish"), token, null).andExpect(status().isOk());
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
