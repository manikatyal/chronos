package com.mihir.jobscheduler.service;

import com.mihir.jobscheduler.entity.JobEntity;
import com.mihir.jobscheduler.model.UserRole;
import com.mihir.jobscheduler.security.AuthenticatedUser;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Service
public class CurrentUserService {

    public AuthenticatedUser currentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof AuthenticatedUser user)) {
            throw new AccessDeniedException("Authenticated user context is missing");
        }
        return user;
    }

    public String currentUserId() {
        return currentUser().userId();
    }

    public boolean isAdmin() {
        return currentUser().role() == UserRole.ADMIN;
    }

    public void ensureCanAccess(JobEntity job) {
        if (!isAdmin() && !currentUserId().equals(job.getCreatedBy())) {
            throw new AccessDeniedException("You are not allowed to access this job");
        }
    }

    public void ensureAdmin() {
        if (!isAdmin()) {
            throw new AccessDeniedException("Only admins can access this resource");
        }
    }
}
