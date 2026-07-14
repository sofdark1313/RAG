/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.nageoffer.ai.ragent.user.service.impl;

import cn.hutool.core.lang.Assert;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.nageoffer.ai.ragent.framework.context.LoginUser;
import com.nageoffer.ai.ragent.framework.context.UserContext;
import com.nageoffer.ai.ragent.framework.exception.ClientException;
import com.nageoffer.ai.ragent.framework.web.PageRequests;
import com.nageoffer.ai.ragent.user.controller.request.ChangePasswordRequest;
import com.nageoffer.ai.ragent.user.controller.request.UserCreateRequest;
import com.nageoffer.ai.ragent.user.controller.request.UserPageRequest;
import com.nageoffer.ai.ragent.user.controller.request.UserUpdateRequest;
import com.nageoffer.ai.ragent.user.controller.vo.UserVO;
import com.nageoffer.ai.ragent.user.dao.entity.UserDO;
import com.nageoffer.ai.ragent.user.dao.mapper.UserMapper;
import com.nageoffer.ai.ragent.user.enums.UserRole;
import com.nageoffer.ai.ragent.user.service.PasswordHashService;
import com.nageoffer.ai.ragent.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private static final String DEFAULT_ADMIN_USERNAME = "admin";
    private static final int MAX_USERNAME_LENGTH = 64;
    private static final int MIN_PASSWORD_LENGTH = 8;
    private static final int MAX_PASSWORD_LENGTH = 128;
    private static final int MAX_ROLE_LENGTH = 32;
    private static final int MAX_AVATAR_LENGTH = 128;
    private static final int MAX_ID_LENGTH = 20;

    private final UserMapper userMapper;
    private final PasswordHashService passwordHashService;

    @Override
    public IPage<UserVO> pageQuery(UserPageRequest requestParam) {
        String keyword = normalizeOptionalText(requestParam == null ? null : requestParam.getKeyword(),
                MAX_USERNAME_LENGTH, "关键词");
        Page<UserDO> page = PageRequests.from(requestParam);
        IPage<UserDO> result = userMapper.selectPage(
                page,
                Wrappers.lambdaQuery(UserDO.class)
                        .eq(UserDO::getDeleted, 0)
                        .and(StrUtil.isNotBlank(keyword), wrapper -> wrapper
                                .like(UserDO::getUsername, keyword)
                                .or()
                                .like(UserDO::getRole, keyword))
                        .orderByDesc(UserDO::getUpdateTime)
        );
        return result.convert(this::toVO);
    }

    @Override
    public String create(UserCreateRequest requestParam) {
        Assert.notNull(requestParam, () -> new ClientException("请求不能为空"));
        String username = normalizeRequiredText(requestParam.getUsername(), MAX_USERNAME_LENGTH, "用户名");
        String password = normalizePassword(requestParam.getPassword(), "密码");
        String role = normalizeRole(requestParam.getRole());

        if (DEFAULT_ADMIN_USERNAME.equalsIgnoreCase(username)) {
            throw new ClientException("默认管理员用户名不可用");
        }
        ensureUsernameAvailable(username, null);

        UserDO record = UserDO.builder()
                .username(username)
                .password(passwordHashService.hash(password))
                .role(role)
                .avatar(normalizeOptionalText(requestParam.getAvatar(), MAX_AVATAR_LENGTH, "头像地址"))
                .build();
        userMapper.insert(record);
        return String.valueOf(record.getId());
    }

    @Override
    public void update(String id, UserUpdateRequest requestParam) {
        Assert.notNull(requestParam, () -> new ClientException("请求不能为空"));
        UserDO record = loadById(id);
        ensureNotDefaultAdmin(record);

        if (requestParam.getUsername() != null) {
            String username = normalizeRequiredText(requestParam.getUsername(), MAX_USERNAME_LENGTH, "用户名");
            if (!username.equals(record.getUsername())) {
                if (DEFAULT_ADMIN_USERNAME.equalsIgnoreCase(username)) {
                    throw new ClientException("默认管理员用户名不可用");
                }
                ensureUsernameAvailable(username, record.getId());
            }
            record.setUsername(username);
        }

        if (requestParam.getRole() != null) {
            record.setRole(normalizeRole(requestParam.getRole()));
        }

        if (requestParam.getAvatar() != null) {
            record.setAvatar(normalizeOptionalText(requestParam.getAvatar(), MAX_AVATAR_LENGTH, "头像地址"));
        }

        if (requestParam.getPassword() != null) {
            String password = normalizePassword(requestParam.getPassword(), "新密码");
            record.setPassword(passwordHashService.hash(password));
        }

        userMapper.updateById(record);
    }

    @Override
    public void delete(String id) {
        UserDO record = loadById(id);
        ensureNotDefaultAdmin(record);
        userMapper.deleteById(record.getId());
    }

    @Override
    public void changePassword(ChangePasswordRequest requestParam) {
        Assert.notNull(requestParam, () -> new ClientException("请求不能为空"));
        String current = normalizeRequiredText(requestParam.getCurrentPassword(), MAX_PASSWORD_LENGTH, "当前密码");
        String next = normalizePassword(requestParam.getNewPassword(), "新密码");

        LoginUser loginUser = UserContext.requireUser();
        String normalizedUserId = normalizeRequiredId(loginUser.getUserId(), "用户ID");
        UserDO record = userMapper.selectOne(
                Wrappers.lambdaQuery(UserDO.class)
                        .eq(UserDO::getId, normalizedUserId)
                        .eq(UserDO::getDeleted, 0)
        );
        Assert.notNull(record, () -> new ClientException("用户不存在"));
        if (!passwordHashService.matches(current, record.getPassword())) {
            throw new ClientException("当前密码不正确");
        }
        record.setPassword(passwordHashService.hash(next));
        userMapper.updateById(record);
    }

    private UserDO loadById(String id) {
        String normalizedId = normalizeRequiredId(id, "用户ID");
        UserDO record = userMapper.selectOne(
                Wrappers.lambdaQuery(UserDO.class)
                        .eq(UserDO::getId, normalizedId)
                        .eq(UserDO::getDeleted, 0)
        );
        Assert.notNull(record, () -> new ClientException("用户不存在"));
        return record;
    }

    private void ensureNotDefaultAdmin(UserDO record) {
        if (record != null && DEFAULT_ADMIN_USERNAME.equalsIgnoreCase(record.getUsername())) {
            throw new ClientException("默认管理员不允许修改或删除");
        }
    }

    private void ensureUsernameAvailable(String username, String excludeId) {
        UserDO existing = userMapper.selectOne(
                Wrappers.lambdaQuery(UserDO.class)
                        .eq(UserDO::getUsername, username)
                        .eq(UserDO::getDeleted, 0)
                        .ne(excludeId != null, UserDO::getId, excludeId)
        );
        if (existing != null) {
            throw new ClientException("用户名已存在");
        }
    }

    private String normalizeRole(String role) {
        String value = normalizeOptionalText(role, MAX_ROLE_LENGTH, "角色类型");
        if (StrUtil.isBlank(value)) {
            return UserRole.USER.getCode();
        }
        if (UserRole.ADMIN.getCode().equalsIgnoreCase(value)) {
            return UserRole.ADMIN.getCode();
        }
        if (UserRole.USER.getCode().equalsIgnoreCase(value)) {
            return UserRole.USER.getCode();
        }
        throw new ClientException("角色类型不合法");
    }

    private String normalizePassword(String value, String fieldName) {
        String text = normalizeRequiredText(value, MAX_PASSWORD_LENGTH, fieldName);
        if (text.length() < MIN_PASSWORD_LENGTH) {
            throw new ClientException(fieldName + "长度不能少于" + MIN_PASSWORD_LENGTH + "个字符");
        }
        return text;
    }

    private String normalizeRequiredText(String value, int maxLength, String fieldName) {
        String text = StrUtil.trimToNull(value);
        Assert.notBlank(text, () -> new ClientException(fieldName + "不能为空"));
        if (text.length() > maxLength) {
            throw new ClientException(fieldName + "长度不能超过" + maxLength + "个字符");
        }
        return text;
    }

    private String normalizeOptionalText(String value, int maxLength, String fieldName) {
        String text = StrUtil.trimToNull(value);
        if (text != null && text.length() > maxLength) {
            throw new ClientException(fieldName + "长度不能超过" + maxLength + "个字符");
        }
        return text;
    }

    private String normalizeRequiredId(String value, String fieldName) {
        String text = normalizeRequiredText(value, MAX_ID_LENGTH, fieldName);
        if (!text.matches("\\d{1,20}")) {
            throw new ClientException(fieldName + "不合法");
        }
        return text;
    }

    private UserVO toVO(UserDO record) {
        return UserVO.builder()
                .id(String.valueOf(record.getId()))
                .username(record.getUsername())
                .role(record.getRole())
                .avatar(record.getAvatar())
                .createTime(record.getCreateTime())
                .updateTime(record.getUpdateTime())
                .build();
    }
}
