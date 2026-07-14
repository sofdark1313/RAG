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
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.nageoffer.ai.ragent.framework.exception.ClientException;
import com.nageoffer.ai.ragent.rag.dao.entity.ConversationDO;
import com.nageoffer.ai.ragent.rag.dao.entity.ConversationMessageDO;
import com.nageoffer.ai.ragent.rag.dao.entity.ConversationSummaryDO;
import com.nageoffer.ai.ragent.rag.dao.mapper.ConversationMapper;
import com.nageoffer.ai.ragent.rag.dao.mapper.ConversationMessageMapper;
import com.nageoffer.ai.ragent.rag.dao.mapper.ConversationSummaryMapper;
import com.nageoffer.ai.ragent.rag.service.ConversationGroupService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ConversationGroupServiceImpl implements ConversationGroupService {

    private static final int MAX_QUERY_LIMIT = 500;
    private static final int MAX_ID_LENGTH = 20;

    private final ConversationMessageMapper messageMapper;
    private final ConversationSummaryMapper summaryMapper;
    private final ConversationMapper conversationMapper;

    @Override
    public List<ConversationMessageDO> listLatestUserOnlyMessages(String conversationId, String userId, int limit) {
        String normalizedConversationId = normalizeOptionalId(conversationId, "会话ID");
        String normalizedUserId = normalizeOptionalId(userId, "用户ID");
        if (StrUtil.isBlank(normalizedConversationId) || StrUtil.isBlank(normalizedUserId) || limit <= 0) {
            return List.of();
        }
        int safeLimit = Math.min(limit, MAX_QUERY_LIMIT);
        return messageMapper.selectList(
                Wrappers.lambdaQuery(ConversationMessageDO.class)
                        .eq(ConversationMessageDO::getConversationId, normalizedConversationId)
                        .eq(ConversationMessageDO::getUserId, normalizedUserId)
                        .eq(ConversationMessageDO::getRole, "user")
                        .eq(ConversationMessageDO::getDeleted, 0)
                        .orderByDesc(ConversationMessageDO::getCreateTime)
                        .last("limit " + safeLimit)
        );
    }

    @Override
    public List<ConversationMessageDO> listMessagesBetweenIds(String conversationId, String userId, String afterId, String beforeId) {
        String normalizedConversationId = normalizeOptionalId(conversationId, "会话ID");
        String normalizedUserId = normalizeOptionalId(userId, "用户ID");
        String normalizedAfterId = normalizeOptionalId(afterId, "起始消息ID");
        String normalizedBeforeId = normalizeOptionalId(beforeId, "结束消息ID");
        if (StrUtil.isBlank(normalizedConversationId) || StrUtil.isBlank(normalizedUserId)) {
            return List.of();
        }
        var query = Wrappers.lambdaQuery(ConversationMessageDO.class)
                .eq(ConversationMessageDO::getConversationId, normalizedConversationId)
                .eq(ConversationMessageDO::getUserId, normalizedUserId)
                .in(ConversationMessageDO::getRole, "user", "assistant")
                .eq(ConversationMessageDO::getDeleted, 0);
        if (normalizedAfterId != null) {
            query.gt(ConversationMessageDO::getId, normalizedAfterId);
        }
        if (normalizedBeforeId != null) {
            query.lt(ConversationMessageDO::getId, normalizedBeforeId);
        }
        return messageMapper.selectList(
                query.orderByAsc(ConversationMessageDO::getId)
        );
    }

    @Override
    public String findMaxMessageIdAtOrBefore(String conversationId, String userId, java.util.Date at) {
        String normalizedConversationId = normalizeOptionalId(conversationId, "会话ID");
        String normalizedUserId = normalizeOptionalId(userId, "用户ID");
        if (StrUtil.isBlank(normalizedConversationId) || StrUtil.isBlank(normalizedUserId) || at == null) {
            return null;
        }
        ConversationMessageDO record = messageMapper.selectOne(
                Wrappers.lambdaQuery(ConversationMessageDO.class)
                        .eq(ConversationMessageDO::getConversationId, normalizedConversationId)
                        .eq(ConversationMessageDO::getUserId, normalizedUserId)
                        .eq(ConversationMessageDO::getDeleted, 0)
                        .le(ConversationMessageDO::getCreateTime, at)
                        .orderByDesc(ConversationMessageDO::getId)
                        .last("limit 1")
        );
        return record == null ? null : record.getId();
    }

    @Override
    public long countUserMessages(String conversationId, String userId) {
        String normalizedConversationId = normalizeOptionalId(conversationId, "会话ID");
        String normalizedUserId = normalizeOptionalId(userId, "用户ID");
        if (StrUtil.isBlank(normalizedConversationId) || StrUtil.isBlank(normalizedUserId)) {
            return 0;
        }
        return messageMapper.selectCount(
                Wrappers.lambdaQuery(ConversationMessageDO.class)
                        .eq(ConversationMessageDO::getConversationId, normalizedConversationId)
                        .eq(ConversationMessageDO::getUserId, normalizedUserId)
                        .eq(ConversationMessageDO::getRole, "user")
                        .eq(ConversationMessageDO::getDeleted, 0)
        );
    }

    @Override
    public ConversationSummaryDO findLatestSummary(String conversationId, String userId) {
        String normalizedConversationId = normalizeOptionalId(conversationId, "会话ID");
        String normalizedUserId = normalizeOptionalId(userId, "用户ID");
        if (StrUtil.isBlank(normalizedConversationId) || StrUtil.isBlank(normalizedUserId)) {
            return null;
        }
        return summaryMapper.selectOne(
                Wrappers.lambdaQuery(ConversationSummaryDO.class)
                        .eq(ConversationSummaryDO::getConversationId, normalizedConversationId)
                        .eq(ConversationSummaryDO::getUserId, normalizedUserId)
                        .eq(ConversationSummaryDO::getDeleted, 0)
                        .orderByDesc(ConversationSummaryDO::getId)
                        .last("limit 1")
        );
    }

    @Override
    public ConversationDO findConversation(String conversationId, String userId) {
        String normalizedConversationId = normalizeOptionalId(conversationId, "会话ID");
        String normalizedUserId = normalizeOptionalId(userId, "用户ID");
        if (StrUtil.isBlank(normalizedConversationId) || StrUtil.isBlank(normalizedUserId)) {
            return null;
        }
        return conversationMapper.selectOne(
                Wrappers.lambdaQuery(ConversationDO.class)
                        .eq(ConversationDO::getConversationId, normalizedConversationId)
                        .eq(ConversationDO::getUserId, normalizedUserId)
                        .eq(ConversationDO::getDeleted, 0)
        );
    }

    private String normalizeOptionalId(String value, String fieldName) {
        String text = StrUtil.trimToNull(value);
        if (text == null) {
            return null;
        }
        if (text.length() > MAX_ID_LENGTH || !text.matches("\\d{1,20}")) {
            throw new ClientException(fieldName + "不合法");
        }
        return text;
    }
}
