package com.szu.rag.auth.service;

import com.szu.rag.auth.model.vo.UserVO;

public interface AuthService {

    UserVO login(String username, String password);

    UserVO register(String username, String password, String nickname);

    void logout();

    UserVO getCurrentUser();
}
