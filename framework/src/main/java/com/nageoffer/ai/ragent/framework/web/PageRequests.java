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

package com.nageoffer.ai.ragent.framework.web;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

/**
 * 分页请求边界收敛工具。
 */
public final class PageRequests {

    private static final long DEFAULT_CURRENT = 1L;
    private static final long DEFAULT_SIZE = 10L;
    private static final long MAX_SIZE = 100L;

    private PageRequests() {
    }

    /**
     * 根据外部传入分页参数构建受限分页对象。
     *
     * @param current 外部页码
     * @param size 外部页大小
     * @param <T> 分页记录类型
     * @return 已夹紧页码和页大小的 MyBatis-Plus 分页对象
     */
    public static <T> Page<T> of(long current, long size) {
        return new Page<>(normalizeCurrent(current), normalizeSize(size));
    }

    /**
     * 根据已有分页对象构建受限分页对象。
     *
     * @param page 外部分页对象
     * @param <T> 分页记录类型
     * @return 已夹紧页码和页大小的 MyBatis-Plus 分页对象
     */
    public static <T> Page<T> from(Page<?> page) {
        if (page == null) {
            return of(DEFAULT_CURRENT, DEFAULT_SIZE);
        }
        return of(page.getCurrent(), page.getSize());
    }

    private static long normalizeCurrent(long current) {
        return current < DEFAULT_CURRENT ? DEFAULT_CURRENT : current;
    }

    private static long normalizeSize(long size) {
        if (size < 1) {
            return DEFAULT_SIZE;
        }
        return Math.min(size, MAX_SIZE);
    }
}
