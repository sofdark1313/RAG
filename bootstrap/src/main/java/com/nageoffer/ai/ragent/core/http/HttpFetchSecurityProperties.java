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

package com.nageoffer.ai.ragent.core.http;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 远程 HTTP 文件拉取安全配置。
 */
@Data
@Component
@ConfigurationProperties(prefix = "rag.http-fetch.security")
public class HttpFetchSecurityProperties {

    /**
     * 是否启用远程 URL 安全校验。
     */
    private boolean enabled = true;

    /**
     * 允许访问的域名白名单。为空时不限制域名，支持精确域名和 *.example.com 通配。
     */
    private Set<String> allowedHosts = new LinkedHashSet<>();

    /**
     * 允许访问的端口。为空时不限制端口，默认只允许 HTTP/HTTPS 标准端口。
     */
    private Set<Integer> allowedPorts = new LinkedHashSet<>(List.of(80, 443));

    /**
     * 是否阻断本机、内网、链路本地、多播等非公网地址。
     */
    private boolean blockPrivateAddresses = true;
}
