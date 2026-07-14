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

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import com.nageoffer.ai.ragent.framework.context.UserContext;
import com.nageoffer.ai.ragent.framework.exception.ClientException;
import com.nageoffer.ai.ragent.infra.chat.StreamCallback;
import com.nageoffer.ai.ragent.rag.service.ratelimit.ChatQueueLimiter;
import com.nageoffer.ai.ragent.rag.service.RAGChatService;
import com.nageoffer.ai.ragent.rag.service.handler.StreamCallbackFactory;
import com.nageoffer.ai.ragent.rag.service.handler.StreamTaskManager;
import com.nageoffer.ai.ragent.rag.service.pipeline.StreamChatContext;
import com.nageoffer.ai.ragent.rag.service.pipeline.StreamChatPipeline;
import com.nageoffer.ai.ragent.rag.trace.StreamChatTraceRunner;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * RAG 对话服务默认实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RAGChatServiceImpl implements RAGChatService {

    private static final int MAX_QUESTION_LENGTH = 4000;
    private static final int MAX_CONVERSATION_ID_LENGTH = 20;
    private static final int MAX_ID_LENGTH = 20;

    private final StreamChatPipeline chatPipeline;
    private final ChatQueueLimiter chatQueueLimiter;
    private final StreamCallbackFactory callbackFactory;
    private final StreamChatTraceRunner traceRunner;
    private final StreamTaskManager taskManager;

    @Override
    public void streamChat(String question, String conversationId, Boolean deepThinking, SseEmitter emitter) {
        String userId = normalizeRequiredId(UserContext.getUserId(), "用户ID");
        String actualQuestion = normalizeQuestion(question);
        String actualConversationId = normalizeConversationId(conversationId);
        String taskId = IdUtil.getSnowflakeNextIdStr();
        StreamCallback callback = callbackFactory.createChatEventHandler(emitter, actualConversationId, taskId);

        chatQueueLimiter.enqueue(actualQuestion, actualConversationId, emitter,
                () -> traceRunner.run(actualQuestion, actualConversationId, taskId, callback, traceAware -> {
                    StreamChatContext ctx = StreamChatContext.builder()
                            .question(actualQuestion)
                            .conversationId(actualConversationId)
                            .taskId(taskId)
                            .deepThinking(Boolean.TRUE.equals(deepThinking))
                            .userId(userId)
                            .callback(traceAware)
                            .build();
                    chatPipeline.execute(ctx);
                }));
    }

    @Override
    public void stopTask(String taskId) {
        String normalizedTaskId = normalizeRequiredId(taskId, "任务ID");
        String userId = normalizeRequiredId(UserContext.getUserId(), "用户ID");
        taskManager.cancel(normalizedTaskId, userId);
    }

    /**
     * 标准化用户问题，避免空问题和超长问题进入存储、检索与模型链路。
     */
    private String normalizeQuestion(String question) {
        String actualQuestion = StrUtil.trimToNull(question);
        if (StrUtil.isBlank(actualQuestion)) {
            throw new ClientException("问题不能为空");
        }
        if (actualQuestion.length() > MAX_QUESTION_LENGTH) {
            throw new ClientException("问题长度不能超过" + MAX_QUESTION_LENGTH + "个字符");
        }
        return actualQuestion;
    }

    /**
     * 标准化会话 ID；前端只应回传服务端生成的雪花 ID。
     */
    private String normalizeConversationId(String conversationId) {
        String actualConversationId = StrUtil.trimToNull(conversationId);
        if (actualConversationId == null) {
            return IdUtil.getSnowflakeNextIdStr();
        }
        if (actualConversationId.length() > MAX_CONVERSATION_ID_LENGTH || !actualConversationId.matches("\\d{1,20}")) {
            throw new ClientException("会话ID格式不正确");
        }
        return actualConversationId;
    }

    private String normalizeRequiredId(String value, String fieldName) {
        String normalized = StrUtil.trimToNull(value);
        if (StrUtil.isBlank(normalized)) {
            throw new ClientException(fieldName + "不能为空");
        }
        if (normalized.length() > MAX_ID_LENGTH || !normalized.matches("\\d{1,20}")) {
            throw new ClientException(fieldName + "不合法");
        }
        return normalized;
    }
}
