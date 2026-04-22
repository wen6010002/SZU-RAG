package com.szu.rag.auth.model.vo;

import lombok.Data;

@Data
public class UserVO {
    private Long id;
    private String username;
    private String nickname;
    private String role;
    private Integer status;
}
