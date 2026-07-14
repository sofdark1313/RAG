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

package com.nageoffer.ai.ragent.rag.core.vector;

import cn.hutool.core.util.StrUtil;
import com.nageoffer.ai.ragent.framework.exception.ClientException;

import java.util.regex.Pattern;

/**
 * 向量空间名称校验工具。
 * 统一约束 Milvus collection 等向量索引命名，避免外部输入直达向量引擎。
 */
public final class VectorSpaceNames {

    private static final int MAX_LOGICAL_NAME_LENGTH = 64;
    private static final Pattern LOGICAL_NAME_PATTERN = Pattern.compile("^[A-Za-z_][A-Za-z0-9_]{0,63}$");

    private VectorSpaceNames() {
    }

    /**
     * 标准化并校验必填向量空间逻辑名。
     *
     * @param value     原始逻辑名
     * @param fieldName 业务字段名
     * @return 去除首尾空白后的逻辑名
     */
    public static String normalizeRequiredLogicalName(String value, String fieldName) {
        String logicalName = normalizeOptionalLogicalName(value, fieldName);
        if (StrUtil.isBlank(logicalName)) {
            throw new ClientException(fieldName + "不能为空");
        }
        return logicalName;
    }

    /**
     * 标准化并校验可选向量空间逻辑名。
     *
     * @param value     原始逻辑名
     * @param fieldName 业务字段名
     * @return 空值返回 null，非空返回去除首尾空白后的逻辑名
     */
    public static String normalizeOptionalLogicalName(String value, String fieldName) {
        String logicalName = StrUtil.trimToNull(value);
        if (logicalName == null) {
            return null;
        }
        if (logicalName.length() > MAX_LOGICAL_NAME_LENGTH) {
            throw new ClientException(fieldName + "长度不能超过" + MAX_LOGICAL_NAME_LENGTH + "个字符");
        }
        if (!LOGICAL_NAME_PATTERN.matcher(logicalName).matches()) {
            throw new ClientException(fieldName + "仅允许字母、数字和下划线，且不能以数字开头");
        }
        return logicalName;
    }
}
