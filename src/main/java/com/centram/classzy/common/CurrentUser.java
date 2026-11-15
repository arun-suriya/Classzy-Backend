package com.centram.classzy.common;

import com.centram.classzy.institution.InstitutionRepository;
import com.centram.classzy.security.AppPrincipal;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Who is calling, and which institution are they working in?
 * Institution Admins / Teachers are tied to their own institution (from the JWT).
 * A Super Admin has no institution, so the UI sends the one being managed in the X-Institution-Id header.
 */
@Component
public class CurrentUser {

    public static final String INSTITUTION_HEADER = "X-Institution-Id";

    private final InstitutionRepository institutions;

    public CurrentUser(InstitutionRepository institutions) {
        this.institutions = institutions;
    }

    public AppPrincipal principal() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof AppPrincipal p)) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "You are not signed in.");
        }
        return p;
    }

    public Long institutionId() {
        AppPrincipal p = principal();
        if (p.institutionId() != null) {
            return p.institutionId();
        }
        // Super Admin: institution comes from the request header
        var attrs = (ServletRequestAttributes) RequestContextHolder.currentRequestAttributes();
        String header = attrs.getRequest().getHeader(INSTITUTION_HEADER);
        if (header == null || header.isBlank()) {
            throw ApiException.badRequest("Select an institution first (" + INSTITUTION_HEADER + " header is required for Super Admin).");
        }
        long id;
        try {
            id = Long.parseLong(header.trim());
        } catch (NumberFormatException e) {
            throw ApiException.badRequest(INSTITUTION_HEADER + " must be a number.");
        }
        if (!institutions.existsById(id)) {
            throw ApiException.notFound("Institution");
        }
        return id;
    }
}
