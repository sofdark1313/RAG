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

package com.nageoffer.ai.ragent.infra.http;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.nageoffer.ai.ragent.infra.config.AIModelProperties;
import com.nageoffer.ai.ragent.infra.model.ModelTarget;
import lombok.NoArgsConstructor;
import okhttp3.ResponseBody;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * HTTP 响应处理工具类
 * 集中管理 OkHttp 响应读取、JSON 解析以及模型目标校验等公共逻辑
 */
@NoArgsConstructor(access = lombok.AccessLevel.PRIVATE)
public final class HttpResponseHelper {

    private static final Gson GSON = new Gson();
    private static final int DEFAULT_BODY_PREVIEW_CHARS = 1000;
    private static final String TRUNCATED_SUFFIX = "...(truncated)";

    /**
     * 读取响应体原始字符串
     */
    public static String readBody(ResponseBody body) throws IOException {
        if (body == null) {
            return "";
        }
        return new String(body.bytes(), StandardCharsets.UTF_8);
    }

    /**
     * 读取响应体预览，避免失败日志和异常消息带出完整模型响应或第三方错误体。
     */
    public static String readBodyPreview(ResponseBody body) throws IOException {
        return readBodyPreview(body, DEFAULT_BODY_PREVIEW_CHARS);
    }

    /**
     * 读取响应体预览，最多保留指定字符数。
     */
    public static String readBodyPreview(ResponseBody body, int maxChars) throws IOException {
        if (body == null) {
            return "";
        }
        int safeMaxChars = Math.max(1, maxChars);
        int maxBytes = safeMaxChars * 4;
        ByteArrayOutputStream out = new ByteArrayOutputStream(Math.min(maxBytes, 4096));
        boolean truncated = false;
        try (InputStream input = body.byteStream()) {
            byte[] buffer = new byte[512];
            while (out.size() <= maxBytes) {
                int remaining = maxBytes + 1 - out.size();
                int read = input.read(buffer, 0, Math.min(buffer.length, remaining));
                if (read < 0) {
                    break;
                }
                out.write(buffer, 0, read);
                if (out.size() > maxBytes) {
                    truncated = true;
                    break;
                }
            }
        }
        String text = out.toString(StandardCharsets.UTF_8);
        if (text.length() > safeMaxChars) {
            return text.substring(0, safeMaxChars) + TRUNCATED_SUFFIX;
        }
        return truncated ? text + TRUNCATED_SUFFIX : text;
    }

    /**
     * 将响应体解析为 JsonObject
     *
     * @param body  OkHttp 响应体
     * @param label 提供商标签，用于异常消息
     * @return 解析后的 JsonObject
     */
    public static JsonObject parseJson(ResponseBody body, String label) throws IOException {
        if (body == null) {
            throw new ModelClientException(label + " 响应为空", ModelClientErrorType.INVALID_RESPONSE, null);
        }
        String content = body.string();
        return GSON.fromJson(content, JsonObject.class);
    }

    /**
     * 校验并返回提供商配置
     */
    public static AIModelProperties.ProviderConfig requireProvider(ModelTarget target, String label) {
        if (target == null || target.provider() == null) {
            throw new IllegalStateException(label + " 提供商配置缺失");
        }
        return target.provider();
    }

    /**
     * 校验提供商 API 密钥
     */
    public static void requireApiKey(AIModelProperties.ProviderConfig provider, String label) {
        if (provider.getApiKey() == null || provider.getApiKey().isBlank()) {
            throw new IllegalStateException(label + " API密钥缺失");
        }
    }

    /**
     * 校验并返回模型名称
     */
    public static String requireModel(ModelTarget target, String label) {
        if (target == null || target.candidate() == null || target.candidate().getModel() == null) {
            throw new IllegalStateException(label + " 模型名称缺失");
        }
        return target.candidate().getModel();
    }
}
