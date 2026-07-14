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

package com.nageoffer.ai.ragent.knowledge.mq;

import cn.hutool.core.util.StrUtil;
import com.nageoffer.ai.ragent.framework.context.LoginUser;
import com.nageoffer.ai.ragent.framework.context.UserContext;
import com.nageoffer.ai.ragent.framework.mq.MessageWrapper;
import com.nageoffer.ai.ragent.knowledge.mq.event.KnowledgeDocumentChunkEvent;
import com.nageoffer.ai.ragent.knowledge.service.KnowledgeDocumentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;

/**
 * 文档分块任务 MQ 消费者
 * 负责异步执行耗时的文本提取、分块、向量嵌入及写库操作
 */
@Slf4j
@Component
@RequiredArgsConstructor
@RocketMQMessageListener(
        topic = "knowledge-document-chunk_topic${unique-name:}",
        consumerGroup = "knowledge-document-chunk_cg${unique-name:}"
)
public class KnowledgeDocumentChunkConsumer implements RocketMQListener<MessageWrapper<KnowledgeDocumentChunkEvent>> {

    private static final int MAX_ID_LENGTH = 20;

    private final KnowledgeDocumentService documentService;

    @Override
    public void onMessage(MessageWrapper<KnowledgeDocumentChunkEvent> message) {
        if (message == null || message.getBody() == null) {
            log.warn("[消费者] 文档分块消息体为空");
            return;
        }
        KnowledgeDocumentChunkEvent event = message.getBody();
        String docId;
        try {
            docId = normalizeOptionalId(event.getDocId(), "文档ID");
        } catch (IllegalArgumentException e) {
            log.warn("[消费者] 文档分块消息文档ID不合法, keys={}", message.getKeys());
            return;
        }
        if (StrUtil.isBlank(docId)) {
            log.warn("[消费者] 文档分块消息缺少文档ID, keys={}", message.getKeys());
            return;
        }

        log.info("[消费者] 开始消费文档分块任务，docId={}, keys={}", docId, message.getKeys());

        UserContext.set(LoginUser.builder().username(event.getOperator()).build());
        try {
            documentService.executeChunk(docId);
        } finally {
            UserContext.clear();
        }
    }

    private String normalizeOptionalId(String value, String fieldName) {
        String text = StrUtil.trimToNull(value);
        if (text == null) {
            return null;
        }
        if (text.length() > MAX_ID_LENGTH || !text.matches("\\d{1,20}")) {
            throw new IllegalArgumentException(fieldName + "不合法");
        }
        return text;
    }
}
