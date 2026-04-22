package com.szu.rag.auth.service;

import com.szu.rag.auth.model.vo.UserVO;
import com.szu.rag.framework.result.PageResult;

public interface UserService {

    PageResult<UserVO> listUsers(int page, int size, String keyword);

    UserVO createUser(String username, String password, String nickname, String role);

    UserVO updateUser(Long id, String nickname, String role, Integer status);

    void deleteUser(Long id);
}
