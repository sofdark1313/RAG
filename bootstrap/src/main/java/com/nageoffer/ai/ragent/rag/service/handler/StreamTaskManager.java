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

package com.nageoffer.ai.ragent.rag.service.handler;

import cn.hutool.core.util.StrUtil;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.nageoffer.ai.ragent.rag.enums.SSEEventType;
import com.nageoffer.ai.ragent.rag.dto.CompletionPayload;
import com.nageoffer.ai.ragent.framework.web.SseEmitterSender;
import com.nageoffer.ai.ragent.infra.chat.StreamCancellationHandle;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBucket;
import org.redisson.api.RTopic;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

@Slf4j
@Component
public class StreamTaskManager {

    private static final String CANCEL_TOPIC = "ragent:stream:cancel";
    private static final String CANCEL_KEY_PREFIX = "ragent:stream:cancel:";
    private static final String OWNER_KEY_PREFIX = "ragent:stream:owner:";
    private static final int MAX_ID_LENGTH = 20;
    private static final Duration CANCEL_TTL = Duration.ofMinutes(30);

    private final Cache<String, StreamTaskInfo> tasks = CacheBuilder.newBuilder()
            .expireAfterWrite(CANCEL_TTL)
            .maximumSize(10000)  // 限制最大数量，基本上不可能超出这个数量。如果觉得不稳妥，可以把值调大并在配置文件声明
            .build();

    private final RedissonClient redissonClient;
    private int listenerId = -1;

    public StreamTaskManager(RedissonClient redissonClient) {
        this.redissonClient = redissonClient;
    }

    @PostConstruct
    public void subscribe() {
        RTopic topic = redissonClient.getTopic(CANCEL_TOPIC);
        listenerId = topic.addListener(String.class, (channel, taskId) -> {
            String normalizedTaskId = normalizeOptionalId(taskId);
            if (normalizedTaskId == null) {
                return;
            }
            cancelLocal(normalizedTaskId);
        });
    }

    @PreDestroy
    public void unsubscribe() {
        if (listenerId == -1) {
            return;
        }
        redissonClient.getTopic(CANCEL_TOPIC).removeListener(listenerId);
    }

    public void register(String taskId, String userId, SseEmitterSender sender, Supplier<CompletionPayload> onCancelSupplier) {
        String normalizedTaskId = normalizeRequiredId(taskId, "taskId");
        String normalizedUserId = normalizeRequiredId(userId, "userId");
        StreamTaskInfo taskInfo = getOrCreate(normalizedTaskId);
        taskInfo.ownerUserId = normalizedUserId;
        taskInfo.sender = sender;
        taskInfo.onCancelSupplier = onCancelSupplier;
        redissonClient.getBucket(ownerKey(normalizedTaskId)).set(normalizedUserId, CANCEL_TTL);
        if (isTaskCancelledInRedis(normalizedTaskId, taskInfo)) {
            CompletionPayload payload = resolveCancelPayload(taskInfo);
            sendCancelAndDone(sender, payload);
            sender.complete();
        }
    }

    public void bindHandle(String taskId, StreamCancellationHandle handle) {
        String normalizedTaskId = normalizeOptionalId(taskId);
        if (normalizedTaskId == null) {
            return;
        }
        StreamTaskInfo taskInfo = getOrCreate(normalizedTaskId);
        taskInfo.handle = handle;
        if (taskInfo.cancelled.get() && handle != null) {
            handle.cancel();
        }
    }

    public boolean isCancelled(String taskId) {
        String normalizedTaskId = normalizeOptionalId(taskId);
        if (normalizedTaskId == null) {
            return false;
        }
        StreamTaskInfo info = tasks.getIfPresent(normalizedTaskId);
        return info != null && info.cancelled.get();
    }

    public boolean cancel(String taskId, String userId) {
        String normalizedTaskId = normalizeOptionalId(taskId);
        String normalizedUserId = normalizeOptionalId(userId);
        if (normalizedTaskId == null || normalizedUserId == null || !isOwner(normalizedTaskId, normalizedUserId)) {
            return false;
        }
        // 先设置 Redis 标记，再发布消息
        RBucket<Boolean> bucket = redissonClient.getBucket(cancelKey(normalizedTaskId));
        bucket.set(Boolean.TRUE, CANCEL_TTL);

        // 发布消息通知所有节点（包括本地）
        // 本地节点也通过监听器统一处理，避免重复调用 cancelLocal
        redissonClient.getTopic(CANCEL_TOPIC).publish(normalizedTaskId);
        return true;
    }

    /**
     * 检查任务是否在 Redis 中被标记为已取消
     * 如果是，会同步状态到本地缓存
     */
    private boolean isTaskCancelledInRedis(String taskId, StreamTaskInfo taskInfo) {
        if (taskInfo.cancelled.get()) {
            return true;
        }

        RBucket<Boolean> bucket = redissonClient.getBucket(cancelKey(taskId));
        Boolean cancelled = bucket.get();
        if (Boolean.TRUE.equals(cancelled)) {
            taskInfo.cancelled.set(true);
            return true;
        }
        return false;
    }

    private void cancelLocal(String taskId) {
        String normalizedTaskId = normalizeOptionalId(taskId);
        if (normalizedTaskId == null) {
            return;
        }
        StreamTaskInfo taskInfo = tasks.getIfPresent(normalizedTaskId);
        if (taskInfo == null) {
            return;
        }

        // 使用 CAS 确保只执行一次
        if (!taskInfo.cancelled.compareAndSet(false, true)) {
            return;
        }

        if (taskInfo.handle != null) {
            taskInfo.handle.cancel();
        }

        // 在取消时执行回调，保存已累积的内容
        if (taskInfo.sender != null) {
            CompletionPayload payload = resolveCancelPayload(taskInfo);
            sendCancelAndDone(taskInfo.sender, payload);
            taskInfo.sender.complete();
        }
    }

    public void unregister(String taskId) {
        String normalizedTaskId = normalizeOptionalId(taskId);
        if (normalizedTaskId == null) {
            return;
        }
        // 清理本地缓存
        tasks.invalidate(normalizedTaskId);

        // 清理Redis
        redissonClient.getBucket(cancelKey(normalizedTaskId)).deleteAsync();
        redissonClient.getBucket(ownerKey(normalizedTaskId)).deleteAsync();
    }

    private String cancelKey(String taskId) {
        return CANCEL_KEY_PREFIX + taskId;
    }

    private String ownerKey(String taskId) {
        return OWNER_KEY_PREFIX + taskId;
    }

    private boolean isOwner(String taskId, String userId) {
        String normalizedTaskId = normalizeOptionalId(taskId);
        String normalizedUserId = normalizeOptionalId(userId);
        if (normalizedTaskId == null || normalizedUserId == null) {
            return false;
        }
        RBucket<String> ownerBucket = redissonClient.getBucket(ownerKey(normalizedTaskId));
        String ownerUserId = ownerBucket.get();
        if (StrUtil.isNotBlank(ownerUserId)) {
            return ownerUserId.equals(normalizedUserId);
        }
        StreamTaskInfo taskInfo = tasks.getIfPresent(normalizedTaskId);
        return taskInfo != null && normalizedUserId.equals(taskInfo.ownerUserId);
    }

    private void sendCancelAndDone(SseEmitterSender sender, CompletionPayload payload) {
        CompletionPayload actualPayload = payload == null ? new CompletionPayload(null, null) : payload;
        sender.sendEvent(SSEEventType.CANCEL.value(), actualPayload);
        sender.sendEvent(SSEEventType.DONE.value(), "[DONE]");
    }

    @SneakyThrows
    private StreamTaskInfo getOrCreate(String taskId) {
        return tasks.get(taskId, StreamTaskInfo::new);
    }

    private CompletionPayload resolveCancelPayload(StreamTaskInfo taskInfo) {
        Supplier<CompletionPayload> supplier = taskInfo == null ? null : taskInfo.onCancelSupplier;
        return supplier == null ? null : supplier.get();
    }

    private String normalizeRequiredId(String value, String fieldName) {
        String normalized = normalizeOptionalId(value);
        if (normalized == null) {
            throw new IllegalArgumentException(fieldName + " is invalid");
        }
        return normalized;
    }

    private String normalizeOptionalId(String value) {
        String normalized = StrUtil.trimToNull(value);
        if (normalized == null || normalized.length() > MAX_ID_LENGTH || !normalized.matches("\\d{1,20}")) {
            return null;
        }
        return normalized;
    }

    private static final class StreamTaskInfo {
        private final AtomicBoolean cancelled = new AtomicBoolean(false);
        private volatile String ownerUserId;
        private volatile StreamCancellationHandle handle;
        private volatile SseEmitterSender sender;
        private volatile Supplier<CompletionPayload> onCancelSupplier;
    }
}
