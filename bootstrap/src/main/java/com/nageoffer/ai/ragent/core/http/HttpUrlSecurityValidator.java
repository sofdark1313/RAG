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

import com.nageoffer.ai.ragent.framework.exception.ClientException;
import lombok.RequiredArgsConstructor;
import okhttp3.Dns;
import okhttp3.HttpUrl;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.net.IDN;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 服务端主动访问远程 URL 时的 SSRF 防护校验器。
 */
@Component
@RequiredArgsConstructor
public class HttpUrlSecurityValidator {

    private static final Set<String> ALLOWED_SCHEMES = Set.of("http", "https");
    private static final int IPV4_CGNAT_PREFIX = 100;
    private static final int IPV4_CGNAT_SECOND_OCTET_MIN = 64;
    private static final int IPV4_CGNAT_SECOND_OCTET_MAX = 127;

    private final HttpFetchSecurityProperties properties;

    /**
     * 解析并校验远程 URL，确保协议、域名和端口满足当前安全策略。
     */
    public HttpUrl validate(String rawUrl) {
        if (!StringUtils.hasText(rawUrl)) {
            throw new ClientException("远程 URL 不能为空");
        }
        HttpUrl parsed = HttpUrl.parse(rawUrl.trim());
        if (parsed == null) {
            throw new ClientException("远程 URL 格式不合法");
        }
        if (!properties.isEnabled()) {
            return parsed;
        }
        validateScheme(parsed.scheme());
        validateNoUserInfo(parsed);
        validatePort(parsed.port());
        validateHost(parsed.host());
        return parsed;
    }

    /**
     * 创建带地址校验的 DNS 解析器，防止重定向或 DNS 变化绕过初始 URL 校验。
     */
    public Dns secureDns() {
        return hostname -> {
            if (!properties.isEnabled()) {
                return Dns.SYSTEM.lookup(hostname);
            }
            validateHost(hostname);
            List<InetAddress> addresses = Dns.SYSTEM.lookup(hostname);
            validateResolvedAddresses(hostname, addresses);
            return addresses;
        };
    }

    private void validateScheme(String scheme) {
        if (!ALLOWED_SCHEMES.contains(scheme)) {
            throw new ClientException("远程 URL 只允许使用 HTTP 或 HTTPS 协议");
        }
    }

    private void validateNoUserInfo(HttpUrl url) {
        if (StringUtils.hasText(url.encodedUsername()) || StringUtils.hasText(url.encodedPassword())) {
            throw new ClientException("远程 URL 不允许携带用户名或密码");
        }
    }

    private void validatePort(int port) {
        Set<Integer> allowedPorts = properties.getAllowedPorts();
        if (allowedPorts != null && !allowedPorts.isEmpty() && !allowedPorts.contains(port)) {
            throw new ClientException("远程 URL 端口不在允许范围内");
        }
    }

    private void validateHost(String host) {
        String normalizedHost = normalizeHost(host);
        Set<String> allowedHosts = properties.getAllowedHosts();
        if (allowedHosts == null || allowedHosts.isEmpty()) {
            return;
        }
        boolean matched = allowedHosts.stream()
                .filter(StringUtils::hasText)
                .map(this::normalizeAllowedHost)
                .anyMatch(allowedHost -> matchesAllowedHost(normalizedHost, allowedHost));
        if (!matched) {
            throw new ClientException("远程 URL 域名不在允许范围内");
        }
    }

    private void validateResolvedAddresses(String hostname, List<InetAddress> addresses) throws UnknownHostException {
        if (!properties.isBlockPrivateAddresses()) {
            return;
        }
        for (InetAddress address : addresses) {
            if (isUnsafeAddress(address)) {
                throw new UnknownHostException("远程 URL 域名解析到不安全地址: " + hostname);
            }
        }
    }

    private boolean matchesAllowedHost(String host, String allowedHost) {
        if (allowedHost.startsWith("*.")) {
            String suffix = allowedHost.substring(2);
            return host.endsWith("." + suffix);
        }
        return host.equals(allowedHost);
    }

    private boolean isUnsafeAddress(InetAddress address) {
        return address.isAnyLocalAddress()
                || address.isLoopbackAddress()
                || address.isLinkLocalAddress()
                || address.isSiteLocalAddress()
                || address.isMulticastAddress()
                || isIpv4CarrierGradeNat(address)
                || isIpv6UniqueLocal(address);
    }

    private boolean isIpv4CarrierGradeNat(InetAddress address) {
        if (!(address instanceof Inet4Address)) {
            return false;
        }
        byte[] bytes = address.getAddress();
        int first = bytes[0] & 0xff;
        int second = bytes[1] & 0xff;
        return first == IPV4_CGNAT_PREFIX
                && second >= IPV4_CGNAT_SECOND_OCTET_MIN
                && second <= IPV4_CGNAT_SECOND_OCTET_MAX;
    }

    private boolean isIpv6UniqueLocal(InetAddress address) {
        if (!(address instanceof Inet6Address)) {
            return false;
        }
        byte[] bytes = address.getAddress();
        int first = bytes[0] & 0xff;
        return (first & 0xfe) == 0xfc;
    }

    private String normalizeAllowedHost(String host) {
        String normalized = host.trim().toLowerCase(Locale.ROOT);
        if (normalized.startsWith("*.")) {
            return "*." + normalizeHost(normalized.substring(2));
        }
        return normalizeHost(normalized);
    }

    private String normalizeHost(String host) {
        if (!StringUtils.hasText(host)) {
            throw new ClientException("远程 URL 域名不能为空");
        }
        String normalized = host.trim().toLowerCase(Locale.ROOT);
        if (normalized.endsWith(".")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        if (normalized.contains(":")) {
            return normalized;
        }
        try {
            return IDN.toASCII(normalized);
        } catch (IllegalArgumentException e) {
            throw new ClientException("远程 URL 域名格式不合法");
        }
    }
}
