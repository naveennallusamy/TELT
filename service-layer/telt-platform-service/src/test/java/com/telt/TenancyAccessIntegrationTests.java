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
class TenancyAccessIntegrationTests {

    private static final String PASSWORD = "Str0ng!Pass";
    private static final String NEW_PASSWORD = "Str0ng!Pass2";
    private static final AtomicLong MOBILE = new AtomicLong(9500000000L);

    @Autowired
    private MockMvc mvc;

    private String root;
    private String tenantName;
    private String admin;

    @BeforeEach
    void setUp() throws Exception {
        root = token(login("root@telt.test", "Root#Pass123", null));
        tenantName = "t-" + UUID.randomUUID();
        call(post("/tenant/create"), root, "{\"tenantName\":\"" + tenantName + "\"}").andExpect(status().isCreated());
        String adminEmail = email();
        register(root, adminEmail, "TENANT_ADMIN", tenantName, null, null).andExpect(status().isCreated());
        admin = activate(adminEmail);
    }

    @Test
    void departmentScopedSubAdminOnlyManagesStudentsOfItsDepartmentTree() throws Exception {
        String science = department(admin, "SCI", null);
        String physics = department(admin, "PHY", science);
        String arts = department(admin, "ART", null);

        String subEmail = email();
        String subId = id(register(admin, subEmail, "SUB_ADMIN", null, null, science).andExpect(status().isCreated()));
        call(get("/members"), admin, null).andExpect(jsonPath("$[?(@.userId=='" + subId + "')].accessMode")
                .value(contains("DEPARTMENT_SCOPED")));

        call(post("/members/" + subId + "/role-assignments"), admin,
                "{\"role\":\"STUDENT_MANAGER\",\"scopeType\":\"TENANT\"}").andExpect(status().isBadRequest());
        call(post("/members/" + subId + "/role-assignments"), admin,
                "{\"role\":\"TENANT_ADMIN\",\"scopeType\":\"DEPARTMENT\",\"departmentId\":\"" + science + "\"}")
                .andExpect(status().isBadRequest());
        call(post("/members/" + subId + "/role-assignments"), admin,
                "{\"role\":\"STUDENT_MANAGER\",\"scopeType\":\"DEPARTMENT\",\"departmentId\":\"" + science + "\"}")
                .andExpect(status().isCreated());
        String sub = activate(subEmail);

        call(get("/me/authorization"), sub, null)
                .andExpect(jsonPath("$.permissions.STUDENT_ENROLL.tenantWide").value(false))
                .andExpect(jsonPath("$.permissions.STUDENT_ENROLL.departmentIds", hasItems(science, physics)))
                .andExpect(jsonPath("$.permissions.STUDENT_ENROLL.departmentIds", not(hasItem(arts))));

        register(sub, email(), "STUDENT", null, null, physics).andExpect(status().isCreated());
        register(sub, email(), "STUDENT", null, null, arts).andExpect(status().isForbidden());
        register(sub, email(), "STUDENT", null, null, null).andExpect(status().isForbidden());
        register(sub, email(), "SUB_ADMIN", null, null, physics).andExpect(status().isForbidden());
        String artsStudent = id(register(admin, email(), "STUDENT", null, null, arts));

        call(get("/members"), sub, null).andExpect(status().isOk())
                .andExpect(jsonPath("$[*].departmentId", everyItem(in(new String[]{science, physics}))));
        call(post("/user/" + artsStudent + "/deactivate"), sub, null).andExpect(status().isNotFound());
        call(get("/tenant-settings"), sub, null).andExpect(status().isForbidden());
        call(post("/departments"), sub, "{\"code\":\"X\",\"name\":\"X\"}").andExpect(status().isForbidden());
        call(post("/members/" + artsStudent + "/role-assignments"), sub,
                "{\"role\":\"EXAM_OWNER\",\"scopeType\":\"DEPARTMENT\",\"departmentId\":\"" + science + "\"}")
                .andExpect(status().isForbidden());
    }

    @Test
    void tenantAdminConfiguresSubAdminAccessMode() throws Exception {
        call(put("/tenant-settings"), admin, "{\"subAdminDefaultAccessMode\":\"TENANT_WIDE\",\"separationOfDuties\":true}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.subAdminDefaultAccessMode").value("TENANT_WIDE"));
        String subEmail = email();
        String subId = id(register(admin, subEmail, "SUB_ADMIN", null, null, null));
        call(post("/members/" + subId + "/role-assignments"), admin,
                "{\"role\":\"EXAM_OWNER\",\"scopeType\":\"TENANT\"}").andExpect(status().isCreated());
        call(post("/members/" + subId + "/role-assignments"), admin,
                "{\"role\":\"QUESTION_REVIEWER\",\"scopeType\":\"TENANT\"}").andExpect(status().isCreated());
        String sub = activate(subEmail);
        call(get("/me/authorization"), sub, null)
                .andExpect(jsonPath("$.permissions.EXAM_PUBLISH.tenantWide").value(true))
                .andExpect(jsonPath("$.permissions.QUESTION_REVIEW.tenantWide").value(true))
                .andExpect(jsonPath("$.permissions.USER_MANAGE").doesNotExist());

        call(patch("/members/" + subId), admin, "{\"accessMode\":\"DEPARTMENT_SCOPED\"}").andExpect(status().isConflict());
        call(patch("/members/" + subId), sub, "{\"accessMode\":\"DEPARTMENT_SCOPED\"}").andExpect(status().isForbidden());

        ResultActions list = call(get("/members/" + subId + "/role-assignments"), admin, null);
        for (Object assignmentId : JsonPath.<java.util.List<Object>>read(body(list), "$[*].assignmentId")) {
            call(delete("/members/" + subId + "/role-assignments/" + assignmentId), admin, null)
                    .andExpect(status().isNoContent());
        }
        call(patch("/members/" + subId), admin, "{\"accessMode\":\"DEPARTMENT_SCOPED\"}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.accessMode").value("DEPARTMENT_SCOPED"));
        call(get("/me/authorization"), sub, null).andExpect(jsonPath("$.permissions.EXAM_PUBLISH").doesNotExist());
        call(get("/audit-events"), admin, null).andExpect(status().isOk())
                .andExpect(jsonPath("$[*].action", hasItems("ROLE_ASSIGNED", "ROLE_REVOKED", "MEMBER_UPDATED",
                        "TENANT_SETTINGS_UPDATED")));
    }

    @Test
    void studentsBelongToOneTenantWhileStaffCanSwitchTenants() throws Exception {
        String otherTenant = "t-" + UUID.randomUUID();
        call(post("/tenant/create"), root, "{\"tenantName\":\"" + otherTenant + "\"}").andExpect(status().isCreated());
        String otherAdminEmail = email();
        register(root, otherAdminEmail, "TENANT_ADMIN", otherTenant, null, null).andExpect(status().isCreated());
        String otherAdmin = activate(otherAdminEmail);

        String studentEmail = email();
        register(admin, studentEmail, "STUDENT", null, null, null).andExpect(status().isCreated());
        call(post("/members"), otherAdmin, "{\"email\":\"" + studentEmail + "\",\"membershipType\":\"SUB_ADMIN\"}")
                .andExpect(status().isConflict());

        String staffEmail = email();
        register(admin, staffEmail, "SUB_ADMIN", null, null, null).andExpect(status().isCreated());
        call(post("/members"), otherAdmin, "{\"email\":\"" + staffEmail + "\",\"membershipType\":\"SUB_ADMIN\"}")
                .andExpect(status().isCreated());
        String staff = activate(staffEmail);
        ResultActions me = call(get("/me"), staff, null).andExpect(jsonPath("$.memberships", hasSize(2)));
        String otherTenantId = JsonPath.<java.util.List<String>>read(body(me),
                "$.memberships[?(@.tenantName=='" + otherTenant + "')].tenantId").getFirst();
        call(post("/auth/switch-tenant"), staff, "{\"tenantId\":\"" + otherTenantId + "\"}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.user.tenantId").value(otherTenantId));
        call(post("/auth/switch-tenant"), admin, "{\"tenantId\":\"" + otherTenantId + "\"}")
                .andExpect(status().isForbidden());
    }

    @Test
    void departmentsAndCatalog() throws Exception {
        String parent = department(admin, "ENG", null);
        department(admin, "CSE", parent);
        call(post("/departments"), admin, "{\"code\":\"eng\",\"name\":\"Dup\"}").andExpect(status().isConflict());
        call(delete("/departments/" + parent), admin, null).andExpect(status().isConflict());
        call(get("/departments"), admin, null).andExpect(jsonPath("$", hasSize(2)));
        call(get("/roles"), admin, null).andExpect(jsonPath("$[?(@.name=='EXAM_OWNER')].assignable").value(contains(true)));
        call(get("/permissions"), admin, null).andExpect(jsonPath("$", hasItem("RESULT_APPROVE")));
    }

    private String department(String token, String code, String parent) throws Exception {
        return JsonPath.read(body(call(post("/departments"), token, "{\"code\":\"" + code + "\",\"name\":\"" + code + "\""
                + (parent == null ? "" : ",\"parentDepartmentId\":\"" + parent + "\"") + "}")
                .andExpect(status().isCreated())), "$.departmentId");
    }

    private ResultActions register(String token, String email, String role, String tenant, String accessMode,
                                   String departmentId) throws Exception {
        return call(post("/user/register"), token, "{\"email\":\"" + email + "\",\"mobileNumber\":" + MOBILE.incrementAndGet()
                + ",\"role\":\"" + role + "\",\"password\":\"" + PASSWORD + "\""
                + (tenant == null ? "" : ",\"tenantName\":\"" + tenant + "\"")
                + (accessMode == null ? "" : ",\"accessMode\":\"" + accessMode + "\"")
                + (departmentId == null ? "" : ",\"departmentId\":\"" + departmentId + "\"") + "}");
    }

    /**
     * First login + mandatory password change; returns a usable access token.
     */
    private String activate(String email) throws Exception {
        String first = token(login(email, PASSWORD, null).andExpect(status().isOk()));
        return token(call(post("/auth/change-password"), first,
                "{\"oldPassword\":\"" + PASSWORD + "\",\"newPassword\":\"" + NEW_PASSWORD + "\"}").andExpect(status().isOk()));
    }

    private ResultActions login(String user, String password, String tenantId) throws Exception {
        return mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON).content("{\"userName\":\"" + user
                + "\",\"password\":\"" + password + "\"" + (tenantId == null ? "" : ",\"tenantId\":\"" + tenantId + "\"") + "}"));
    }

    private ResultActions call(MockHttpServletRequestBuilder builder, String token, String json) throws Exception {
        builder.header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON);
        return mvc.perform(json == null ? builder : builder.content(json));
    }

    private static String id(ResultActions result) throws Exception {
        return JsonPath.read(body(result), "$.id");
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
