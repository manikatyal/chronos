package com.mihir.jobworker.security;

public record TokenUser(String userId, String role) {

    public boolean isAdmin() {
        return "ADMIN".equals(role);
    }
}
