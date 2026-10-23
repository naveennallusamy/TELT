package com.telt.service.impl;

import com.telt.access.AuthorizationService;
import com.telt.access.AuthorizationSnapshot;
import com.telt.constants.AccountType;
import com.telt.constants.Permission;
import com.telt.constants.RecordStatus;
import com.telt.entity.tenant.TenantMembership;
import com.telt.service.MembershipService;
import com.telt.constants.UserStatus;
import com.telt.dto.common.AddressDto;
import com.telt.dto.user.PersonalInfoDto;
import com.telt.dto.user.RegisterUserRequest;
import com.telt.dto.user.UpdateProfileRequest;
import com.telt.dto.user.UserResponse;
import com.telt.entity.address.AddressInfo;
import com.telt.entity.tenant.Tenant;
import com.telt.entity.user.User;
import com.telt.entity.user.UserAddressAssoc;
import com.telt.entity.user.UserAddressId;
import com.telt.entity.user.UserPersonalInfo;
import com.telt.error.ApiException;
import com.telt.repository.UserRepository;
import com.telt.security.SecureTokens;
import com.telt.security.TokenService;
import com.telt.service.AddressService;
import com.telt.service.AuthService;
import com.telt.service.TenantService;
import com.telt.service.UserService;
import com.telt.util.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private static final String CURRENT = "current";
    private static final String PERMANENT = "permanent";

    private final UserRepository userRepository;
    private final MembershipService membershipService;
    private final AuthorizationService authorizationService;
    private final TenantService tenantService;
    private final AddressService addressService;
    private final AuthService authService;
    private final TokenService tokenService;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public UserResponse register(RegisterUserRequest request) {
        AccountType type = request.role();
        Tenant tenant = null;
        if (type == AccountType.SUPER_ADMIN) {
            if (!TenantContext.isSuperAdmin()) {
                throw ApiException.forbidden("Only super admins can create super admins");
            }
            if (request.tenantName() != null && !request.tenantName().isBlank()) {
                throw ApiException.badRequest("Super admins do not belong to a tenant");
            }
        } else {
            tenant = tenantService.resolveTargetTenant(request.tenantName(), true);
            AuthorizationSnapshot snapshot = authorizationService.current();
            boolean allowed = type == AccountType.STUDENT
                    ? snapshot.allowsDepartment(Permission.STUDENT_ENROLL, request.departmentId())
                    : snapshot.hasTenantWide(Permission.USER_MANAGE);
            authorizationService.require(() -> allowed);
        }
        if (userRepository.existsByEmailIgnoreCase(request.email())) {
            throw ApiException.conflict("E-mail is already registered");
        }
        if (userRepository.existsByMobileNumber(request.mobileNumber())) {
            throw ApiException.conflict("Mobile number is already registered");
        }
        if (request.username() != null && userRepository.existsByUsername(request.username())) {
            throw ApiException.conflict("Username is already taken");
        }

        User user = new User();
        user.setUsername(request.username());
        user.setEmail(request.email());
        user.setMobileNumber(request.mobileNumber());
        user.setCountryCode(request.countryCode());
        user.setPlatformRole(type == AccountType.SUPER_ADMIN ? User.PLATFORM_SUPER_ADMIN : null);
        boolean generatedPassword = request.password() == null;
        user.setPassword(passwordEncoder.encode(generatedPassword ? SecureTokens.newToken() : request.password()));
        user.setPasswordChangeRequired(true);
        applyPersonalInfo(user, request.personalInfo());
        applyAddress(user, CURRENT, request.currentAddress());
        applyAddress(user, PERMANENT, request.permanentAddress());

        User saved = userRepository.save(user);
        if (tenant != null) {
            membershipService.createMembership(saved, tenant, type.toMembershipType(), request.accessMode(),
                    request.departmentId());
        }
        if (generatedPassword) {
            authService.sendPasswordResetLink(saved);
        }
        return response(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public List<UserResponse> findAll() {
        if (TenantContext.isSuperAdmin()) {
            return userRepository.findAll().stream().map(this::response).toList();
        }
        return membershipService.visibleMemberships(Permission.USER_VIEW).stream()
                .map(TenantMembership::getUser).map(this::response).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public UserResponse findById(UUID userId) {
        return response(loadManaged(userId, Permission.USER_VIEW));
    }

    @Override
    @Transactional(readOnly = true)
    public UserResponse currentUser() {
        return response(load(TenantContext.requireCurrentUserId()));
    }

    @Override
    @Transactional
    public UserResponse updateCurrentUser(UpdateProfileRequest request) {
        User user = load(TenantContext.requireCurrentUserId());
        if (request.username() != null && !request.username().equals(user.getUsername())) {
            if (userRepository.existsByUsername(request.username())) {
                throw ApiException.conflict("Username is already taken");
            }
            user.setUsername(request.username());
        }
        if (request.countryCode() != null) {
            user.setCountryCode(request.countryCode());
        }
        applyPersonalInfo(user, request.personalInfo());
        applyAddress(user, CURRENT, request.currentAddress());
        applyAddress(user, PERMANENT, request.permanentAddress());
        return response(user);
    }

    @Override
    @Transactional
    public UserResponse setStatus(UUID userId, UserStatus status) {
        if (userId.equals(TenantContext.requireCurrentUserId())) {
            throw ApiException.badRequest("You cannot change the status of your own account");
        }
        if (TenantContext.isSuperAdmin()) {
            load(userId).setStatus(status);
        } else {
            membershipService.setStatus(userId, status == UserStatus.ACTIVE ? RecordStatus.ACTIVE : RecordStatus.INACTIVE);
        }
        if (status == UserStatus.INACTIVE) {
            tokenService.revokeAll(userId);
        }
        return response(load(userId));
    }

    @Override
    @Transactional
    public UserResponse unlock(UUID userId) {
        User user = loadManaged(userId, Permission.USER_MANAGE);
        user.setFailedLoginAttempts(0);
        user.setLockedUntil(null);
        return response(user);
    }

    @Override
    @Transactional
    public void sendPasswordReset(UUID userId) {
        User user = loadManaged(userId, Permission.USER_MANAGE);
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw ApiException.badRequest("Cannot send a reset link to an inactive account");
        }
        authService.sendPasswordResetLink(user);
    }

    /**
     * Super admins reach any user; tenant staff only members of their tenant within their permission scope.
     */
    private User loadManaged(UUID userId, Permission permission) {
        if (TenantContext.isSuperAdmin()) {
            return load(userId);
        }
        membershipService.requireAccessible(userId, permission);
        return load(userId);
    }

    private UserResponse response(User user) {
        return UserResponse.from(user, membershipService.summariesFor(user.getId()));
    }

    private User load(UUID userId) {
        return userRepository.findById(userId).orElseThrow(() -> ApiException.notFound("User"));
    }

    private void applyPersonalInfo(User user, PersonalInfoDto dto) {
        if (dto == null) {
            return;
        }
        UserPersonalInfo info = Optional.ofNullable(user.getPersonalInfo()).orElseGet(() -> {
            UserPersonalInfo created = new UserPersonalInfo();
            created.setUser(user);
            user.setPersonalInfo(created);
            return created;
        });
        dto.applyTo(info);
    }

    private void applyAddress(User user, String type, AddressDto dto) {
        if (dto == null) {
            return;
        }
        AddressInfo address = addressService.findOrCreateAddress(dto.toEntity());
        Optional<UserAddressAssoc> existing = user.getUserAddressDetails().stream()
                .filter(a -> type.equals(a.getUserAddressId().getAddressType()))
                .findFirst();
        if (existing.isPresent()
                && Objects.equals(existing.get().getAddress().getAddressId(), address.getAddressId())) {
            return;
        }
        existing.ifPresent(user.getUserAddressDetails()::remove);
        UserAddressId id = new UserAddressId();
        id.setAddressType(type);
        UserAddressAssoc mapping = new UserAddressAssoc();
        mapping.setUserAddressId(id);
        mapping.setUser(user);
        mapping.setAddress(address);
        user.getUserAddressDetails().add(mapping);
    }
}
