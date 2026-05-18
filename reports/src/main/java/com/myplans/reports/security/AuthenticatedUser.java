package com.myplans.reports.security;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
@AllArgsConstructor
public class AuthenticatedUser {

    private final Integer idUsuario;
    private final String email;
    private final List<String> roles;

    public boolean hasRole(String role) {
        if (roles == null) {
            return false;
        }
        String prefixed = role.startsWith("ROLE_") ? role : "ROLE_" + role;
        return roles.contains(prefixed);
    }
}
