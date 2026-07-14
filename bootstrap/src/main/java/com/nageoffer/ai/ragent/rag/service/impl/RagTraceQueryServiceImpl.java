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

package com.nageoffer.ai.ragent.rag.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.nageoffer.ai.ragent.framework.exception.ClientException;
import com.nageoffer.ai.ragent.framework.web.PageRequests;
import com.nageoffer.ai.ragent.rag.controller.request.RagTraceRunPageRequest;
import com.nageoffer.ai.ragent.rag.controller.vo.RagTraceDetailVO;
import com.nageoffer.ai.ragent.rag.controller.vo.RagTraceNodeVO;
import com.nageoffer.ai.ragent.rag.controller.vo.RagTraceRunVO;
import com.nageoffer.ai.ragent.rag.dao.entity.RagTraceNodeDO;
import com.nageoffer.ai.ragent.rag.dao.entity.RagTraceRunDO;
import com.nageoffer.ai.ragent.rag.dao.mapper.RagTraceNodeMapper;
import com.nageoffer.ai.ragent.rag.dao.mapper.RagTraceRunMapper;
import com.nageoffer.ai.ragent.rag.service.RagTraceQueryService;
import com.nageoffer.ai.ragent.user.dao.entity.UserDO;
import com.nageoffer.ai.ragent.user.dao.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * RAG Trace 查询服务实现
 */
@Service
@RequiredArgsConstructor
public class RagTraceQueryServiceImpl implements RagTraceQueryService {

    private static final int MAX_TRACE_ID_LENGTH = 64;
    private static final int MAX_ID_LENGTH = 20;
    private static final int MAX_STATUS_LENGTH = 16;

    private final RagTraceRunMapper runMapper;
    private final RagTraceNodeMapper nodeMapper;
    private final UserMapper userMapper;

    @Override
    public IPage<RagTraceRunVO> pageRuns(RagTraceRunPageRequest request) {
        LambdaQueryWrapper<RagTraceRunDO> wrapper = Wrappers.lambdaQuery(RagTraceRunDO.class)
                .orderByDesc(RagTraceRunDO::getStartTime);

        String traceId = normalizeOptionalText(request == null ? null : request.getTraceId(), MAX_TRACE_ID_LENGTH, "TraceId");
        String normalizedConversationId = normalizeOptionalId(request == null ? null : request.getConversationId(), "会话ID");
        String normalizedTaskId = normalizeOptionalId(request == null ? null : request.getTaskId(), "任务ID");
        String status = normalizeStatusFilter(request == null ? null : request.getStatus());

        if (StrUtil.isNotBlank(traceId)) {
            wrapper.eq(RagTraceRunDO::getTraceId, traceId);
        }
        if (StrUtil.isNotBlank(normalizedConversationId)) {
            wrapper.eq(RagTraceRunDO::getConversationId, normalizedConversationId);
        }
        if (StrUtil.isNotBlank(normalizedTaskId)) {
            wrapper.eq(RagTraceRunDO::getTaskId, normalizedTaskId);
        }
        if (StrUtil.isNotBlank(status)) {
            wrapper.eq(RagTraceRunDO::getStatus, status);
        }

        Page<RagTraceRunDO> page = PageRequests.from(request);
        IPage<RagTraceRunDO> pageResult = runMapper.selectPage(page, wrapper);
        Map<String, String> usernameMap = loadUsernameMap(pageResult.getRecords());
        Map<String, Long> ttftMap = loadTtftMap(pageResult.getRecords());
        return pageResult.convert(run -> toRunVO(run, usernameMap, ttftMap));
    }

    @Override
    public RagTraceDetailVO detail(String traceId) {
        String normalizedTraceId = normalizeRequiredText(traceId, MAX_TRACE_ID_LENGTH, "TraceId");
        RagTraceRunDO run = runMapper.selectOne(Wrappers.lambdaQuery(RagTraceRunDO.class)
                .eq(RagTraceRunDO::getTraceId, normalizedTraceId)
                .last("limit 1"));
        if (run == null) {
            return null;
        }
        Map<String, String> usernameMap = loadUsernameMap(List.of(run));
        Map<String, Long> ttftMap = loadTtftMap(List.of(run));
        return RagTraceDetailVO.builder()
                .run(toRunVO(run, usernameMap, ttftMap))
                .nodes(listNodes(normalizedTraceId))
                .build();
    }

    @Override
    public List<RagTraceNodeVO> listNodes(String traceId) {
        String normalizedTraceId = normalizeRequiredText(traceId, MAX_TRACE_ID_LENGTH, "TraceId");
        List<RagTraceNodeDO> nodes = nodeMapper.selectList(Wrappers.lambdaQuery(RagTraceNodeDO.class)
                .eq(RagTraceNodeDO::getTraceId, normalizedTraceId)
                .orderByAsc(RagTraceNodeDO::getStartTime)
                .orderByAsc(RagTraceNodeDO::getId));
        return nodes.stream().map(this::toNodeVO).toList();
    }

    private RagTraceRunVO toRunVO(RagTraceRunDO run, Map<String, String> usernameMap, Map<String, Long> ttftMap) {
        String username = resolveUsername(run.getUserId(), usernameMap);
        String question = parseQuestion(run.getExtraData());
        return RagTraceRunVO.builder()
                .traceId(run.getTraceId())
                .traceName(run.getTraceName())
                .entryMethod(run.getEntryMethod())
                .conversationId(run.getConversationId())
                .taskId(run.getTaskId())
                .userId(run.getUserId())
                .username(username)
                .status(run.getStatus())
                .errorMessage(run.getErrorMessage())
                .durationMs(run.getDurationMs())
                .ttftMs(ttftMap.get(run.getTraceId()))
                .question(question)
                .startTime(run.getStartTime())
                .endTime(run.getEndTime())
                .build();
    }

    private Map<String, String> loadUsernameMap(List<RagTraceRunDO> runs) {
        if (runs == null || runs.isEmpty()) {
            return Collections.emptyMap();
        }

        Set<String> userIds = runs.stream()
                .map(RagTraceRunDO::getUserId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (userIds.isEmpty()) {
            return Collections.emptyMap();
        }

        List<UserDO> users = userMapper.selectList(Wrappers.lambdaQuery(UserDO.class)
                .in(UserDO::getId, userIds)
                .select(UserDO::getId, UserDO::getUsername));
        if (users == null || users.isEmpty()) {
            return Collections.emptyMap();
        }

        return users.stream().collect(Collectors.toMap(
                user -> String.valueOf(user.getId()),
                UserDO::getUsername,
                (left, right) -> left
        ));
    }

    private Map<String, Long> loadTtftMap(List<RagTraceRunDO> runs) {
        if (runs == null || runs.isEmpty()) {
            return Collections.emptyMap();
        }
        List<String> traceIds = runs.stream()
                .map(RagTraceRunDO::getTraceId)
                .filter(Objects::nonNull)
                .toList();
        if (traceIds.isEmpty()) {
            return Collections.emptyMap();
        }
        List<RagTraceNodeDO> ttftNodes = nodeMapper.selectList(
                Wrappers.lambdaQuery(RagTraceNodeDO.class)
                        .in(RagTraceNodeDO::getTraceId, traceIds)
                        .eq(RagTraceNodeDO::getNodeType, "USER_TTFT")
                        .select(RagTraceNodeDO::getTraceId, RagTraceNodeDO::getDurationMs));
        if (ttftNodes == null || ttftNodes.isEmpty()) {
            return Collections.emptyMap();
        }
        return ttftNodes.stream()
                .filter(node -> node.getTraceId() != null && node.getDurationMs() != null)
                .collect(Collectors.toMap(
                        RagTraceNodeDO::getTraceId,
                        RagTraceNodeDO::getDurationMs,
                        (left, right) -> left));
    }

    private String parseQuestion(String extraData) {
        if (StrUtil.isBlank(extraData)) {
            return null;
        }
        try {
            JSONObject json = JSONUtil.parseObj(extraData);
            return json.getStr("question");
        } catch (Exception ignored) {
            return null;
        }
    }

    private String normalizeStatusFilter(String status) {
        String text = normalizeOptionalText(status, MAX_STATUS_LENGTH, "状态");
        if (StrUtil.isBlank(text)) {
            return null;
        }
        String normalized = text.toUpperCase(Locale.ROOT);
        if ("RUNNING".equals(normalized) || "SUCCESS".equals(normalized)
                || "ERROR".equals(normalized) || "CANCELLED".equals(normalized)) {
            return normalized;
        }
        throw new ClientException("Trace 状态不合法");
    }

    private String normalizeOptionalText(String value, int maxLength, String fieldName) {
        String text = StrUtil.trimToNull(value);
        if (text != null && text.length() > maxLength) {
            throw new ClientException(fieldName + "长度不能超过" + maxLength + "个字符");
        }
        return text;
    }

    private String normalizeRequiredText(String value, int maxLength, String fieldName) {
        String text = normalizeOptionalText(value, maxLength, fieldName);
        if (StrUtil.isBlank(text)) {
            throw new ClientException(fieldName + "不能为空");
        }
        return text;
    }

    private String normalizeOptionalId(String value, String fieldName) {
        String text = normalizeOptionalText(value, MAX_ID_LENGTH, fieldName);
        if (text != null && !text.matches("\\d{1,20}")) {
            throw new ClientException(fieldName + "不合法");
        }
        return text;
    }

    private String resolveUsername(String userId, Map<String, String> usernameMap) {
        if (StrUtil.isBlank(userId) || usernameMap == null || usernameMap.isEmpty()) {
            return null;
        }
        return usernameMap.get(userId);
    }

    private RagTraceNodeVO toNodeVO(RagTraceNodeDO node) {
        return RagTraceNodeVO.builder()
                .traceId(node.getTraceId())
                .nodeId(node.getNodeId())
                .parentNodeId(node.getParentNodeId())
                .depth(node.getDepth())
                .nodeType(node.getNodeType())
                .nodeName(node.getNodeName())
                .className(node.getClassName())
                .methodName(node.getMethodName())
                .status(node.getStatus())
                .errorMessage(node.getErrorMessage())
                .durationMs(node.getDurationMs())
                .startTime(node.getStartTime())
                .endTime(node.getEndTime())
                .build();
    }
}
