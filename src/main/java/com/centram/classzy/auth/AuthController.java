package com.centram.classzy.auth;

import com.centram.classzy.auth.AuthDto.LoginRequest;
import com.centram.classzy.auth.AuthDto.LoginResponse;
import com.centram.classzy.auth.AuthDto.UserInfo;
import com.centram.classzy.audit.AuditService;
import com.centram.classzy.common.ApiException;
import com.centram.classzy.common.CurrentUser;
import com.centram.classzy.institution.InstitutionEnums.Lifecycle;
import com.centram.classzy.institution.InstitutionRepository;
import com.centram.classzy.security.AppPrincipal;
import com.centram.classzy.security.JwtService;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final JwtService jwt;
    private final CurrentUser current;
    private final InstitutionRepository institutions;
    private final AuditService audit;

    public AuthController(UserRepository users, PasswordEncoder encoder, JwtService jwt, CurrentUser current,
                          InstitutionRepository institutions, AuditService audit) {
        this.audit = audit;
        this.institutions = institutions;
        this.users = users;
        this.encoder = encoder;
        this.jwt = jwt;
        this.current = current;
    }

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest req) {
        var invalid = new ApiException(HttpStatus.UNAUTHORIZED, "Invalid email or password.");
        User user = users.findByEmailIgnoreCase(req.email().trim()).filter(User::isActive).orElse(null);
        if (user == null || !encoder.matches(req.password(), user.getPasswordHash())) {
            audit.recordAs(req.email().trim(), null, "LOGIN_FAILED", "USER", user == null ? null : user.getId(),
                    user == null ? null : user.getInstitutionId(), "Failed sign-in attempt for " + req.email().trim());
            throw invalid;
        }
        if (user.getInstitutionId() != null && institutions.findById(user.getInstitutionId())
                .map(i -> i.getLifecycle() == Lifecycle.ARCHIVED).orElse(true)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "This institution has been archived. Please contact the platform administrator.");
        }
        if (user.getRole() != com.centram.classzy.security.Role.SUPER_ADMIN && user.getRole() != com.centram.classzy.security.Role.INSTITUTION_ADMIN
                && user.getRole() != com.centram.classzy.security.Role.TEACHER) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Your account type does not have portal access yet.");
        }
        if (!users.withinValidity(user.getId())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "This account is outside its validity period. Please contact your administrator.");
        }
        users.touchLastLogin(user.getId());
        var principal = new AppPrincipal(user.getId(), user.getEmail(), user.getRole(), user.getInstitutionId());
        audit.recordAs(user.getEmail(), user.getRole().name(), "LOGIN", "USER", user.getId(), user.getInstitutionId(),
                user.getFullName() + " signed in");
        return new LoginResponse(jwt.issue(principal), "Bearer", jwt.expirySeconds(), UserInfo.of(user));
    }

    @GetMapping("/me")
    public UserInfo me() {
        User user = users.findById(current.principal().userId()).orElseThrow(() -> ApiException.notFound("User"));
        return UserInfo.of(user);
    }
}
