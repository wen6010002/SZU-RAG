package com.szu.rag.framework.result;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Result<T> {
    private String code;
    private String message;
    private T data;
    private String requestId;

    public static <T> Result<T> success(T data) {
        return new Result<>("200", "success", data, null);
    }

    public static <T> Result<T> success() {
        return new Result<>("200", "success", null, null);
    }

    public static <T> Result<T> error(String code, String message) {
        return new Result<>(code, message, null, null);
    }
}
