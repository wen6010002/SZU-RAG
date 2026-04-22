package com.szu.rag.auth.model.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum RoleEnum {

    USER("USER", "普通用户"),
    ADMIN("ADMIN", "管理员");

    private final String value;
    private final String label;

    public static boolean isValid(String role) {
        for (RoleEnum r : values()) {
            if (r.value.equals(role)) return true;
        }
        return false;
    }
}
