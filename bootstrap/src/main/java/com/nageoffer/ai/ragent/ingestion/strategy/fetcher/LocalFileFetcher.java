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

package com.nageoffer.ai.ragent.ingestion.strategy.fetcher;

import com.nageoffer.ai.ragent.framework.exception.ServiceException;
import com.nageoffer.ai.ragent.ingestion.domain.context.DocumentSource;
import com.nageoffer.ai.ragent.ingestion.domain.enums.SourceType;
import com.nageoffer.ai.ragent.ingestion.util.MimeTypeDetector;
import com.nageoffer.ai.ragent.rag.service.FileStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.InputStream;

/**
 * 本地文件抓取器
 * 已禁用服务器本地文件系统读取，仅保留对历史 FILE 来源中 s3:// 对象存储定位符的兼容。
 */
@Component
@RequiredArgsConstructor
@Deprecated
public class LocalFileFetcher implements DocumentFetcher {

    private final FileStorageService fileStorageService;

    @Override
    public SourceType supportedType() {
        return SourceType.FILE;
    }

    @Override
    public FetchResult fetch(DocumentSource source) {
        String location = source.getLocation();
        if (!StringUtils.hasText(location)) {
            throw new ServiceException("文件路径不能为空");
        }
        if (!location.startsWith("s3://")) {
            throw new ServiceException("FILE 来源不支持读取服务器本地路径，请通过上传接口或 s3:// 对象存储定位符传入文件");
        }
        try {
            byte[] bytes;
            String fileName = source.getFileName();
            try (InputStream is = fileStorageService.openStream(location)) {
                bytes = is.readAllBytes();
            }
            if (!StringUtils.hasText(fileName)) {
                fileName = extractFileName(location);
            }
            String mimeType = MimeTypeDetector.detect(bytes, fileName);
            return new FetchResult(bytes, mimeType, fileName);
        } catch (Exception e) {
            throw new ServiceException("读取文件失败: " + e.getMessage());
        }
    }

    private String extractFileName(String location) {
        int idx = location.lastIndexOf('/');
        return idx >= 0 ? location.substring(idx + 1) : location;
    }
}
