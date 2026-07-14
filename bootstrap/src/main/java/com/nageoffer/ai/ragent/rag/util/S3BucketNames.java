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

package com.nageoffer.ai.ragent.rag.util;

import cn.hutool.core.util.StrUtil;
import com.nageoffer.ai.ragent.framework.exception.ClientException;

import java.util.List;
import java.util.regex.Pattern;

/**
 * S3/RustFS bucket 名称校验工具。
 * 统一约束对象存储边界，避免业务层把外部输入直接传给 S3 API。
 */
public final class S3BucketNames {

    private static final int MIN_BUCKET_NAME_LENGTH = 3;
    private static final int MAX_BUCKET_NAME_LENGTH = 63;
    private static final Pattern BUCKET_NAME_PATTERN = Pattern.compile("^[a-z0-9][a-z0-9-]{1,61}[a-z0-9]$");
    private static final List<String> RESERVED_PREFIXES = List.of("xn--", "sthree-", "amzn-s3-demo-");
    private static final List<String> RESERVED_SUFFIXES = List.of("-s3alias", "--ol-s3", "--x-s3", "--table-s3", ".mrap");

    private S3BucketNames() {
    }

    /**
     * 标准化并校验必填 bucket 名称。
     *
     * @param value     原始 bucket 名称
     * @param fieldName 业务字段名
     * @return 去除首尾空白后的 bucket 名称
     */
    public static String normalizeRequiredBucketName(String value, String fieldName) {
        String bucketName = StrUtil.trimToNull(value);
        if (StrUtil.isBlank(bucketName)) {
            throw new ClientException(fieldName + "不能为空");
        }
        if (bucketName.length() < MIN_BUCKET_NAME_LENGTH || bucketName.length() > MAX_BUCKET_NAME_LENGTH) {
            throw new ClientException(fieldName + "长度必须在3到63个字符之间");
        }
        if (!BUCKET_NAME_PATTERN.matcher(bucketName).matches()) {
            throw new ClientException(fieldName + "仅允许小写字母、数字和连字符，且必须以字母或数字开头和结尾");
        }
        if (hasReservedAffix(bucketName)) {
            throw new ClientException(fieldName + "使用了对象存储保留前缀或后缀");
        }
        return bucketName;
    }

    private static boolean hasReservedAffix(String bucketName) {
        for (String prefix : RESERVED_PREFIXES) {
            if (bucketName.startsWith(prefix)) {
                return true;
            }
        }
        for (String suffix : RESERVED_SUFFIXES) {
            if (bucketName.endsWith(suffix)) {
                return true;
            }
        }
        return false;
    }
}
