package com.telt;

import com.jayway.jsonpath.JsonPath;
import com.telt.support.CapturingPasswordResetNotifier;
import com.telt.support.TestcontainersConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "telt.bootstrap.super-admin.email=root@telt.test",
        "telt.bootstrap.super-admin.mobile-number=9000000000",
        "telt.bootstrap.super-admin.password=" + PlatformApiIntegrationTests.ADMIN_PASSWORD,
        "telt.account.max-failed-logins=3"
})
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class PlatformApiIntegrationTests {

    static final String ADMIN_PASSWORD = "Root#Pass123";
    private static final String PASSWORD = "Str0ng!Pass";
    private static final AtomicLong MOBILE = new AtomicLong(9100000000L);

    @Autowired
    private MockMvc mvc;

    @Autowired
    private CapturingPasswordResetNotifier notifier;

    private String superAdminToken;

    @BeforeEach
    void loginSuperAdmin() throws Exception {
        superAdminToken = accessToken(login("root@telt.test", ADMIN_PASSWORD).andExpect(status().isOk()));
    }

    @Test
    void unauthenticatedRequestsGetJsonErrorWithCorrelationId() throws Exception {
        mvc.perform(get("/user").header("X-Correlation-Id", "abc-123"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("X-Correlation-Id", "abc-123"))
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.correlationId").value("abc-123"));

        mvc.perform(get("/user").header("Authorization", "Bearer not-a-jwt"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_TOKEN"));
    }

    @Test
    void springDataRestRepositoriesAreNotExposed() throws Exception {
        mvc.perform(authed(get("/users"), superAdminToken)).andExpect(status().isNotFound());
    }

    @Test
    void tenantAdminIsConfinedToOwnTenant() throws Exception {
        String tenantA = createTenant();
        String tenantB = createTenant();
        String adminA = email("admin-a");
        String userB = email("user-b");
        createUser(superAdminToken, adminA, "TENANT_ADMIN", tenantA, PASSWORD);
        String userBId = createUser(superAdminToken, userB, "STUDENT", tenantB, PASSWORD);

        String adminToken = firstLoginAndChangePassword(adminA);

        mvc.perform(authed(get("/tenant"), adminToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        mvc.perform(authed(get("/user"), adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].memberships[0].tenantName", everyItem(is(tenantA))))
                .andExpect(jsonPath("$[*].email", not(hasItem(userB))))
                .andExpect(jsonPath("$[*].password").doesNotExist());

        mvc.perform(authed(get("/user/" + userBId), adminToken)).andExpect(status().isNotFound());
        mvc.perform(authed(post("/user/" + userBId + "/deactivate"), adminToken)).andExpect(status().isNotFound());

        mvc.perform(authed(post("/user/register"), adminToken).content(userJson(email("x"), "STUDENT", tenantB, PASSWORD)))
                .andExpect(status().isForbidden());
        mvc.perform(authed(post("/user/register"), adminToken).content(userJson(email("x"), "SUPER_ADMIN", null, PASSWORD)))
                .andExpect(status().isForbidden());

        MvcResult created = mvc.perform(authed(post("/user/register"), adminToken)
                        .content(userJson(email("own"), "STUDENT", null, PASSWORD)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.memberships[0].tenantName").value(tenantA))
                .andExpect(jsonPath("$.passwordChangeRequired").value(true))
                .andReturn();
        assertThat(created.getResponse().getContentAsString()).doesNotContain("\"password\"");
    }

    @Test
    void usersMustChangePasswordOnFirstLogin() throws Exception {
        String tenant = createTenant();
        String user = email("first");
        createUser(superAdminToken, user, "STUDENT", tenant, PASSWORD);

        ResultActions login = login(user, PASSWORD).andExpect(jsonPath("$.user.passwordChangeRequired").value(true));
        String token = accessToken(login);

        mvc.perform(authed(get("/roles"), token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PASSWORD_CHANGE_REQUIRED"));
        mvc.perform(authed(get("/me"), token)).andExpect(status().isOk()).andExpect(jsonPath("$.email").value(user));

        String newToken = changePassword(token, PASSWORD, "N3w!Password");
        mvc.perform(authed(put("/me"), newToken).content("""
                        {"personalInfo": {"firstName": "Asha", "lastName": "Rao"},
                         "currentAddress": {"addressLine1": "1 Main St", "city": "Chennai", "country": "IN", "zipCode": "600001"}}
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.personalInfo.firstName").value("Asha"))
                .andExpect(jsonPath("$.currentAddress.city").value("Chennai"))
                .andExpect(jsonPath("$.passwordChangeRequired").value(false));
    }

    @Test
    void refreshTokensRotateAndReuseRevokesTheSession() throws Exception {
        String refresh1 = refreshToken(login("root@telt.test", ADMIN_PASSWORD));

        String refresh2 = refreshToken(refresh(refresh1).andExpect(status().isOk()));
        assertThat(refresh2).isNotEqualTo(refresh1);

        refresh(refresh1).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("INVALID_TOKEN"));
        refresh(refresh2).andExpect(status().isUnauthorized());

        String refresh3 = refreshToken(login("root@telt.test", ADMIN_PASSWORD));
        mvc.perform(post("/auth/logout").contentType(MediaType.APPLICATION_JSON)
                .content("{\"refreshToken\":\"" + refresh3 + "\"}")).andExpect(status().isNoContent());
        refresh(refresh3).andExpect(status().isUnauthorized());
    }

    @Test
    void forgotAndResetPasswordIsSingleUseAndRevokesSessions() throws Exception {
        String tenant = createTenant();
        String user = email("reset");
        createUser(superAdminToken, user, "STUDENT", tenant, PASSWORD);
        String oldRefresh = refreshToken(login(user, PASSWORD));

        forgot("nobody@telt.test").andExpect(status().isOk());
        forgot(user).andExpect(status().isOk());
        String token = notifier.lastTokenFor(user);
        assertThat(token).isNotBlank();

        reset(token, "weak").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.details[0].field").value("newPassword"));
        reset(token, "Reset!Pass9").andExpect(status().isOk());
        reset(token, "Again!Pass9").andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_TOKEN"));

        refresh(oldRefresh).andExpect(status().isUnauthorized());
        login(user, PASSWORD).andExpect(status().isUnauthorized());
        login(user, "Reset!Pass9").andExpect(status().isOk())
                .andExpect(jsonPath("$.user.passwordChangeRequired").value(false));
    }

    @Test
    void changePasswordValidatesCurrentPassword() throws Exception {
        String tenant = createTenant();
        String user = email("change");
        createUser(superAdminToken, user, "STUDENT", tenant, PASSWORD);
        String token = accessToken(login(user, PASSWORD));

        mvc.perform(authed(post("/auth/change-password"), token)
                        .content("{\"oldPassword\":\"Wrong!Pass1\",\"newPassword\":\"N3w!Password\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(authed(post("/auth/change-password"), token)
                        .content("{\"oldPassword\":\"" + PASSWORD + "\",\"newPassword\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isBadRequest());
        changePassword(token, PASSWORD, "N3w!Password");
        login(user, "N3w!Password").andExpect(status().isOk());
    }

    @Test
    void repeatedFailedLoginsLockTheAccountUntilAdminUnlocks() throws Exception {
        String tenant = createTenant();
        String user = email("lock");
        String userId = createUser(superAdminToken, user, "STUDENT", tenant, PASSWORD);

        for (int i = 0; i < 3; i++) {
            login(user, "Wrong!Pass1").andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
        }
        login(user, PASSWORD).andExpect(status().isLocked()).andExpect(jsonPath("$.code").value("ACCOUNT_LOCKED"));

        mvc.perform(authed(post("/user/" + userId + "/unlock"), superAdminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.locked").value(false));
        login(user, PASSWORD).andExpect(status().isOk());
    }

    @Test
    void deactivatedUsersCannotLoginOrRefresh() throws Exception {
        String tenant = createTenant();
        String user = email("inactive");
        String userId = createUser(superAdminToken, user, "STUDENT", tenant, PASSWORD);
        String refresh = refreshToken(login(user, PASSWORD));

        mvc.perform(authed(post("/user/" + userId + "/deactivate"), superAdminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("INACTIVE"));
        login(user, PASSWORD).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("ACCOUNT_INACTIVE"));
        refresh(refresh).andExpect(status().isUnauthorized());

        mvc.perform(authed(post("/user/" + userId + "/activate"), superAdminToken)).andExpect(status().isOk());
        login(user, PASSWORD).andExpect(status().isOk());

        mvc.perform(authed(post("/user/" + userId + "/password-reset"), superAdminToken))
                .andExpect(status().isAccepted());
        assertThat(notifier.lastTokenFor(user)).isNotBlank();
    }

    @Test
    void usersCreatedWithoutPasswordReceiveSetupLink() throws Exception {
        String tenant = createTenant();
        String user = email("nopass");
        createUser(superAdminToken, user, "STUDENT", tenant, null);
        String token = notifier.lastTokenFor(user);
        assertThat(token).isNotBlank();
        reset(token, "Setup!Pass9").andExpect(status().isOk());
        login(user, "Setup!Pass9").andExpect(status().isOk());
    }

    @Test
    void tenantManagementRules() throws Exception {
        String name = createTenant();
        mvc.perform(authed(post("/tenant/create"), superAdminToken).content("{\"tenantName\":\"" + name + "\"}"))
                .andExpect(status().isConflict());
        mvc.perform(authed(delete("/tenant/" + UUID.randomUUID()), superAdminToken)).andExpect(status().isNotFound());
        mvc.perform(authed(get("/roles"), superAdminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].name", hasItems("SUPER_ADMIN", "TENANT_ADMIN", "STUDENT", "EXAM_OWNER", "QUESTION_REVIEWER")));
    }

    private String firstLoginAndChangePassword(String email) throws Exception {
        String token = accessToken(login(email, PASSWORD));
        return changePassword(token, PASSWORD, PASSWORD + "x");
    }

    private String changePassword(String token, String oldPassword, String newPassword) throws Exception {
        return accessToken(mvc.perform(authed(post("/auth/change-password"), token)
                        .content("{\"oldPassword\":\"" + oldPassword + "\",\"newPassword\":\"" + newPassword + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.passwordChangeRequired").value(false)));
    }

    private String createTenant() throws Exception {
        String name = "tenant-" + UUID.randomUUID();
        mvc.perform(authed(post("/tenant/create"), superAdminToken).content("{\"tenantName\":\"" + name + "\"}"))
                .andExpect(status().isCreated());
        return name;
    }

    private String createUser(String token, String email, String role, String tenant, String password) throws Exception {
        MvcResult result = mvc.perform(authed(post("/user/register"), token)
                        .content(userJson(email, role, tenant, password)))
                .andExpect(status().isCreated())
                .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.id");
    }

    private static String userJson(String email, String role, String tenant, String password) {
        return "{\"email\":\"" + email + "\",\"mobileNumber\":" + MOBILE.incrementAndGet() + ",\"role\":\"" + role + "\""
                + (tenant == null ? "" : ",\"tenantName\":\"" + tenant + "\"")
                + (password == null ? "" : ",\"password\":\"" + password + "\"")
                + ",\"personalInfo\":{\"firstName\":\"Test\"}"
                + ",\"permanentAddress\":{\"addressLine1\":\"" + UUID.randomUUID() + "\",\"city\":\"Coimbatore\"}}";
    }

    private ResultActions login(String userName, String password) throws Exception {
        return mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"userName\":\"" + userName + "\",\"password\":\"" + password + "\"}"));
    }

    private ResultActions refresh(String refreshToken) throws Exception {
        return mvc.perform(post("/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                .content("{\"refreshToken\":\"" + refreshToken + "\"}"));
    }

    private ResultActions forgot(String identifier) throws Exception {
        return mvc.perform(post("/auth/forgot-password").contentType(MediaType.APPLICATION_JSON)
                .content("{\"emailOrMobile\":\"" + identifier + "\"}"));
    }

    private ResultActions reset(String token, String newPassword) throws Exception {
        return mvc.perform(post("/auth/reset-password").contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"" + token + "\",\"newPassword\":\"" + newPassword + "\"}"));
    }

    private static MockHttpServletRequestBuilder authed(MockHttpServletRequestBuilder builder, String token) {
        return builder.header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON);
    }

    private static String accessToken(ResultActions result) throws Exception {
        return JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$.token");
    }

    private static String refreshToken(ResultActions result) throws Exception {
        return JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$.refreshToken");
    }

    private static String email(String prefix) {
        return prefix + "-" + UUID.randomUUID() + "@telt.test";
    }
}
