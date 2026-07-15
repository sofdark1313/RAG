<template>
  <div class="admin-page knowledge-documents-page">
    <header class="admin-page-header">
      <div>
        <h1 class="admin-page-title">文档管理</h1>
        <p class="admin-page-subtitle">{{ kb ? `${kb.name}（${kb.collectionName}）` : kbId }}</p>
      </div>
      <div class="admin-page-actions">
        <button class="ui-button action-button" data-variant="outline" type="button" @click="router.push('/admin/knowledge')">
          返回知识库
        </button>
        <button class="ui-button admin-primary-gradient action-button" type="button" @click="openUploadDialog">
          <FileUp class="h-4 w-4" />
          上传文档
        </button>
      </div>
    </header>

    <section class="ui-card">
      <div class="ui-card-header document-card-header">
        <div>
          <h2 class="ui-card-title">文档列表</h2>
          <p class="ui-card-description">支持筛选与分块管理</p>
        </div>
        <div class="document-toolbar">
          <input v-model="searchInput" class="ui-input toolbar-input" placeholder="搜索文档名称" @keyup.enter="handleSearch" />
          <button class="ui-button toolbar-button" data-variant="outline" type="button" @click="handleSearch">搜索</button>
          <select v-model="statusFilter" class="ui-input toolbar-select" @change="handleFilterChange">
            <option value="">全部状态</option>
            <option v-for="option in statusOptions" :key="option" :value="option">{{ option }}</option>
          </select>
          <button class="ui-button toolbar-button" data-variant="outline" type="button" @click="handleRefresh">
            <RefreshCw class="h-4 w-4" />
            刷新
          </button>
        </div>
      </div>
      <div class="ui-card-content px-6 pt-5">
        <div v-if="loading" class="empty-state">加载中...</div>
        <div v-else-if="documents.length === 0" class="empty-state">暂无文档</div>
        <div v-else class="ui-table-wrap">
          <table class="ui-table document-table w-full">
            <thead class="ui-table-header">
              <tr class="ui-table-row">
                <th class="ui-table-head w-[44px] text-left">
                  <input type="checkbox" :checked="allSelected" aria-label="全选" @change="toggleSelectAll" />
                </th>
                <th class="ui-table-head w-[280px] text-left">文档</th>
                <th class="ui-table-head w-[105px] text-left">状态</th>
                <th class="ui-table-head w-[70px] text-left">启用</th>
                <th class="ui-table-head w-[80px] text-left">分块数</th>
                <th class="ui-table-head w-[120px] text-left">处理模式</th>
                <th class="ui-table-head w-[155px] text-left">更新时间</th>
                <th class="ui-table-head min-w-[360px] text-left">操作</th>
              </tr>
            </thead>
            <tbody class="ui-table-body">
              <tr v-for="doc in documents" :key="doc.id" class="ui-table-row">
                <td class="ui-table-cell">
                  <input type="checkbox" :checked="selectedIds.has(String(doc.id))" :aria-label="`选择 ${doc.docName}`" @change="toggleSelect(String(doc.id))" />
                </td>
                <td class="ui-table-cell">
                  <div class="document-name-cell">
                    <component :is="fileIcon(doc)" class="h-4 w-4 shrink-0" :class="fileIconColor(doc)" />
                    <div class="min-w-0 flex-1">
                      <div class="flex min-w-0 items-center gap-2">
                        <button class="document-link" type="button" :title="doc.docName || ''" @click="openChunks(doc.id)">
                          {{ doc.docName || "-" }}
                        </button>
                        <span v-if="doc.chunksEdited" class="edited-badge" title="该文档存在被手工编辑过的分块，重新分块会丢失">已编辑</span>
                      </div>
                      <div class="document-meta">
                        {{ [doc.fileType, doc.fileSize != null ? formatSize(doc.fileSize) : null, formatSourceLabel(doc.sourceType)].filter(Boolean).join(" · ") }}
                      </div>
                    </div>
                  </div>
                </td>
                <td class="ui-table-cell">
                  <span class="status-cell"><i :class="statusDotClass(doc.status)"></i>{{ doc.status || "-" }}</span>
                </td>
                <td class="ui-table-cell">
                  <button
                    class="toggle-switch"
                    :class="{ 'is-active': Boolean(doc.enabled) }"
                    type="button"
                    role="switch"
                    :aria-checked="Boolean(doc.enabled)"
                    :title="doc.enabled ? '禁用文档' : '启用文档'"
                    @click="handleToggleEnabled(doc)"
                  ><span></span></button>
                </td>
                <td class="ui-table-cell tabular-nums">{{ doc.chunkCount && doc.chunkCount > 0 ? doc.chunkCount : "-" }}</td>
                <td class="ui-table-cell" :title="processModeTitle(doc)">{{ processModeLabel(doc) }}</td>
                <td class="ui-table-cell text-slate-500"><AdminRelativeTime :value="doc.updateTime" /></td>
                <td class="ui-table-cell">
                  <div class="row-actions">
                    <button v-if="canPreview(doc)" class="ui-button row-button" data-variant="outline" type="button" @click="handlePreview(doc)">
                      <Eye class="h-4 w-4" />预览
                    </button>
                    <button class="ui-button row-button" data-variant="outline" type="button" @click="openEditDialog(doc)">
                      <Pencil class="h-4 w-4" />编辑
                    </button>
                    <button class="ui-button row-button" data-variant="outline" type="button" @click="chunkTarget = doc">
                      <PlayCircle class="h-4 w-4" />分块
                    </button>
                    <button class="icon-button" type="button" title="下载文件" @click="handleDownload(doc)"><Download class="h-4 w-4" /></button>
                    <button class="icon-button" type="button" title="分块详情" @click="openChunkLogs(doc)"><FileBarChart class="h-4 w-4" /></button>
                    <button class="icon-button danger-button" type="button" title="删除" @click="deleteTarget = doc"><Trash2 class="h-4 w-4" /></button>
                  </div>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
        <AdminPagination
          v-if="pageData"
          :current="pageData.current"
          :pages="pageData.pages"
          :total="pageData.total"
          @change="changePage"
        />
      </div>
    </section>

    <AdminModal :open="uploadOpen" title="上传文档" description="支持本地文件或远程URL，并配置分块策略" width="620px" @close="uploadOpen = false">
      <div class="form-stack">
        <label class="form-field">
          <span>来源类型</span>
          <select v-model="uploadForm.sourceType" class="ui-input form-control">
            <option value="file">Local File</option>
            <option value="url">Remote URL</option>
          </select>
        </label>

        <label v-if="uploadForm.sourceType === 'url'" class="form-field">
          <span>来源地址</span>
          <input v-model="uploadForm.sourceLocation" class="ui-input form-control" placeholder="https://example.com/document.pdf" />
          <small>填写远程文档 URL</small>
        </label>
        <div
          v-else
          class="file-drop"
          :class="{ 'is-dragging': isDragging, 'has-file': uploadFile }"
          @click="fileInput?.click()"
          @dragover.prevent="isDragging = true"
          @dragleave.prevent="isDragging = false"
          @drop.prevent="handleFileDrop"
        >
          <input ref="fileInput" class="hidden" type="file" accept=".pdf,.md,.markdown,.doc,.docx,.txt,.xlsx,.xls,.csv,.png,.jpg,.jpeg,.svg" @change="handleFileSelect" />
          <FileUp class="h-7 w-7 text-indigo-500" />
          <strong>{{ uploadFile?.name || "拖拽文件到此处，或点击选择" }}</strong>
          <span>{{ uploadFile ? formatSize(uploadFile.size) : "支持 PDF、Markdown、Word、Excel、TXT、图片(PNG/JPG)等格式" }}</span>
          <button v-if="uploadFile" class="text-button" type="button" @click.stop="clearUploadFile"><X class="h-3 w-3" />重新选择</button>
        </div>

        <div v-if="uploadForm.sourceType === 'url'" class="form-panel">
          <label class="inline-field">
            <div><strong>开启定时拉取</strong><small>开启后按频率自动更新文档</small></div>
            <input v-model="uploadForm.scheduleEnabled" type="checkbox" />
          </label>
          <label v-if="uploadForm.scheduleEnabled" class="form-field">
            <span>拉取频率</span>
            <input v-model="uploadForm.scheduleCron" class="ui-input form-control" placeholder="例如：0 0 0 * * ?" />
            <small>支持 cron 表达式，例如每天凌晨</small>
          </label>
        </div>

        <div class="form-panel">
          <label class="form-field">
            <span>处理模式</span>
            <select v-model="uploadForm.processMode" class="ui-input form-control">
              <option value="chunk">直接分块</option>
              <option value="pipeline">数据通道</option>
            </select>
          </label>

          <label v-if="uploadForm.processMode === 'pipeline'" class="form-field">
            <span>选择通道</span>
            <select v-model="uploadForm.pipelineId" class="ui-input form-control">
              <option value="" disabled>请选择</option>
              <option v-for="pipeline in pipelines" :key="pipeline.id" :value="pipeline.id">{{ pipeline.name }}</option>
            </select>
            <small>通过ETL处理提升文件数据质量，增强向量搜索效果</small>
          </label>

          <template v-else>
            <div v-if="uploadIsTable" class="form-stack compact-stack">
              <p class="form-hint">表格按行切分，每块自动重复表头并以「列名: 值」嵌入；按下方预算控制每块大小</p>
              <label v-if="uploadFileExt !== 'csv'" class="form-field">
                <span>Excel 解析方式</span>
                <select v-model="uploadForm.excelParser" class="ui-input form-control">
                  <option value="poi">简单 KeyVal（规整单表，快）</option>
                  <option value="mineru">复杂版面（MinerU，合并/多表/多行表头，较慢）</option>
                </select>
              </label>
              <div class="form-grid">
                <label class="form-field"><span>块大小预算</span><input v-model="uploadForm.chunkSize" class="ui-input form-control" type="number" /><small>每块嵌入文本的字符预算上限</small></label>
                <label class="form-field"><span>每块最大行数</span><input v-model="uploadForm.rowsPerChunk" class="ui-input form-control" type="number" /><small>行数硬上限，与预算共同决定切分粒度</small></label>
              </div>
            </div>
            <div v-else class="form-stack compact-stack">
              <label class="form-field">
                <span>切分方式</span>
                <select v-model="uploadForm.chunkStrategy" class="ui-input form-control" @change="applyUploadStrategyDefaults">
                  <option v-for="strategy in chunkStrategies" :key="strategy.value" :value="strategy.value">{{ strategy.label }}</option>
                </select>
              </label>
              <div v-if="uploadForm.chunkStrategy === 'fixed_size'" class="form-grid three-cols">
                <label class="form-field"><span>块大小</span><input v-model="uploadForm.chunkSize" class="ui-input form-control" type="number" @input="uploadNoChunk = uploadForm.chunkSize === '-1'" /></label>
                <label class="form-field"><span>重叠大小</span><input v-model="uploadForm.overlapSize" class="ui-input form-control" type="number" /></label>
                <label class="form-field"><span>不分块</span><button class="toggle-switch field-switch" :class="{ 'is-active': uploadNoChunk }" type="button" role="switch" :aria-checked="uploadNoChunk" @click="toggleUploadNoChunk"><span></span></button><small>开启后块大小为-1</small></label>
              </div>
              <div v-else class="form-grid">
                <label class="form-field"><span>理想块大小</span><input v-model="uploadForm.targetChars" class="ui-input form-control" type="number" /></label>
                <label class="form-field"><span>块上限</span><input v-model="uploadForm.maxChars" class="ui-input form-control" type="number" /></label>
                <label class="form-field"><span>块下限</span><input v-model="uploadForm.minChars" class="ui-input form-control" type="number" /></label>
                <label class="form-field"><span>重叠大小</span><input v-model="uploadForm.overlapChars" class="ui-input form-control" type="number" /></label>
              </div>
            </div>
          </template>
        </div>
      </div>
      <template #footer>
        <button class="ui-button modal-button" data-variant="outline" type="button" :disabled="uploadSaving" @click="uploadOpen = false">取消</button>
        <button class="ui-button modal-button" data-variant="default" type="button" :disabled="uploadSaving" @click="handleUpload">{{ uploadSaving ? "上传中..." : "上传" }}</button>
      </template>
    </AdminModal>

    <AdminModal :open="Boolean(detailTarget)" title="编辑文档" description="修改文档配置，保存后需重新分块才会生效" width="620px" @close="detailTarget = null">
      <div v-if="detailTarget" class="form-stack">
        <label class="form-field"><span>来源类型</span><input class="ui-input form-control muted-control" :value="formatSourceLabel(detailTarget.sourceType)" disabled /></label>
        <label class="form-field"><span>{{ detailTarget.sourceType?.toLowerCase() === 'url' ? '文档名称' : '本地文件' }}</span><input v-model="detailForm.docName" class="ui-input form-control" /></label>
        <template v-if="detailTarget.sourceType?.toLowerCase() === 'url'">
          <label class="form-field"><span>来源地址</span><input v-model="detailForm.sourceLocation" class="ui-input form-control" /></label>
          <div class="form-panel">
            <label class="inline-field"><div><strong>开启定时拉取</strong><small>开启后按频率自动更新文档</small></div><input v-model="detailForm.scheduleEnabled" type="checkbox" /></label>
            <label v-if="detailForm.scheduleEnabled" class="form-field"><span>拉取频率（Cron表达式）</span><input v-model="detailForm.scheduleCron" class="ui-input form-control" placeholder="0 0 * * *" /></label>
          </div>
        </template>
        <label class="form-field"><span>处理模式</span><select v-model="detailForm.processMode" class="ui-input form-control"><option value="chunk">分块策略</option><option value="pipeline">数据通道</option></select><small>分块策略：直接分块；数据通道：使用Pipeline清洗</small></label>
        <label v-if="detailForm.processMode === 'pipeline'" class="form-field"><span>数据通道</span><select v-model="detailForm.pipelineId" class="ui-input form-control"><option value="" disabled>选择数据通道</option><option v-for="pipeline in pipelines" :key="pipeline.id" :value="pipeline.id">{{ pipeline.name }}</option></select></label>
        <div v-else class="form-panel">
          <template v-if="detailIsTable">
            <p class="form-hint">表格按行切分，每块自动重复表头并以「列名: 值」嵌入；按下方预算控制每块大小</p>
            <label v-if="detailFileExt !== 'csv'" class="form-field"><span>Excel 解析方式</span><select v-model="detailConfig.excelParser" class="ui-input form-control"><option value="poi">简单 KeyVal（规整单表，快）</option><option value="mineru">复杂版面（MinerU，合并/多表/多行表头，较慢）</option></select></label>
            <div class="form-grid"><label class="form-field"><span>块大小预算</span><input v-model="detailConfig.chunkSize" class="ui-input form-control" type="number" /></label><label class="form-field"><span>每块最大行数</span><input v-model="detailConfig.rowsPerChunk" class="ui-input form-control" type="number" /></label></div>
          </template>
          <template v-else>
            <label class="form-field"><span>分块策略</span><select v-model="detailForm.chunkStrategy" class="ui-input form-control" @change="applyDetailStrategyDefaults"><option v-for="strategy in chunkStrategies" :key="strategy.value" :value="strategy.value">{{ strategy.label }}</option></select></label>
            <div v-if="detailForm.chunkStrategy === 'fixed_size'" class="form-grid three-cols">
              <label class="form-field"><span>块大小</span><input v-model="detailConfig.chunkSize" class="ui-input form-control" type="number" @input="detailNoChunk = detailConfig.chunkSize === '-1'" /></label>
              <label class="form-field"><span>重叠大小</span><input v-model="detailConfig.overlapSize" class="ui-input form-control" type="number" /></label>
              <label class="form-field"><span>不分块</span><button class="toggle-switch field-switch" :class="{ 'is-active': detailNoChunk }" type="button" role="switch" :aria-checked="detailNoChunk" @click="toggleDetailNoChunk"><span></span></button><small>开启后块大小为-1</small></label>
            </div>
            <div v-else class="form-grid"><label class="form-field"><span>理想块大小</span><input v-model="detailConfig.targetChars" class="ui-input form-control" type="number" /></label><label class="form-field"><span>块上限</span><input v-model="detailConfig.maxChars" class="ui-input form-control" type="number" /></label><label class="form-field"><span>块下限</span><input v-model="detailConfig.minChars" class="ui-input form-control" type="number" /></label><label class="form-field"><span>重叠大小</span><input v-model="detailConfig.overlapChars" class="ui-input form-control" type="number" /></label></div>
          </template>
        </div>
      </div>
      <template #footer><button class="ui-button modal-button" data-variant="outline" type="button" :disabled="detailSaving" @click="detailTarget = null">关闭</button><button class="ui-button modal-button" data-variant="default" type="button" :disabled="detailSaving" @click="handleDetailSave">{{ detailSaving ? "保存中..." : "保存" }}</button></template>
    </AdminModal>

    <AdminModal :open="Boolean(previewTarget)" :title="previewTarget?.docName || '预览'" width="1100px" max-height="92vh" @close="closePreview">
      <div class="preview-area" :class="{ 'is-file-preview': previewUsesSourceFile }">
        <div v-if="previewLoading" class="empty-state">加载中...</div>
        <iframe v-else-if="previewTarget?.fileType?.toLowerCase() === 'pdf'" class="source-preview" :src="previewFileUrl" :title="previewTarget.docName || ''"></iframe>
        <SpreadsheetPreview v-else-if="previewTarget && isSpreadsheetType(previewTarget.fileType)" :doc-id="String(previewTarget.id)" />
        <div v-else-if="previewTarget && isImageType(previewTarget.fileType)" class="image-preview"><img :src="previewFileUrl" :alt="previewTarget.docName || ''" /></div>
        <div v-else-if="previewContent" class="markdown-preview">
          <pre v-if="previewFrontMatter.head" class="front-matter">{{ previewFrontMatter.head }}</pre>
          <MarkdownContent :content="previewFrontMatter.body" />
        </div>
      </div>
    </AdminModal>

    <AdminModal :open="Boolean(logTarget)" title="分块详情" :description="`文档 [${logTarget?.docName || ''}] 的分块执行日志`" width="800px" @close="logTarget = null">
      <div v-if="logLoading" class="empty-state">加载中...</div>
      <div v-else-if="latestLog" class="log-detail">
        <div class="log-summary"><div class="flex items-center gap-3"><span class="ui-badge log-badge" :class="logStatusClass(latestLog.status)">{{ formatLogStatus(latestLog.status) }}</span><span class="text-sm text-slate-500">{{ latestLog.processMode === 'pipeline' ? '数据通道' : '直接分块' }}{{ logModeDetail(latestLog) }}</span></div><strong>{{ latestLog.chunkCount ?? 0 }} <small>块</small></strong></div>
        <div class="log-metrics"><div v-if="latestLog.processMode !== 'pipeline'" class="metric"><span>文本提取</span><strong>{{ formatDuration(latestLog.extractDuration) }}</strong></div><div class="metric"><span>{{ latestLog.processMode === 'pipeline' ? '数据通道耗时' : '分块耗时' }}</span><strong>{{ formatDuration(latestLog.chunkDuration) }}</strong></div><div v-if="latestLog.processMode !== 'pipeline'" class="metric"><span>向量化</span><strong>{{ formatDuration(latestLog.embedDuration) }}</strong></div><div class="metric"><span>持久化</span><strong>{{ formatDuration(latestLog.persistDuration) }}</strong></div><div class="metric"><span>其他</span><strong>{{ formatDuration(latestLog.otherDuration) }}</strong></div><div class="metric total"><span>总耗时</span><strong>{{ formatDuration(latestLog.totalDuration) }}</strong></div></div>
        <p class="text-sm text-slate-500">执行时间 {{ formatDateTime(latestLog.startTime) }} ~ {{ latestLog.endTime ? formatDateTime(latestLog.endTime) : "进行中" }}</p>
        <div v-if="latestLog.errorMessage" class="error-box"><strong>错误信息</strong><p>{{ latestLog.errorMessage }}</p></div>
      </div>
      <div v-else class="empty-state">暂无分块日志</div>
    </AdminModal>

    <AdminModal :open="Boolean(deleteTarget)" title="确认删除文档？" width="460px" @close="deleteTarget = null"><p class="text-sm leading-6 text-slate-600">文档 [{{ deleteTarget?.docName }}] 将被删除，且向量数据会清理。</p><template #footer><button class="ui-button modal-button" data-variant="outline" type="button" @click="deleteTarget = null">取消</button><button class="ui-button modal-button destructive-button" type="button" @click="handleDelete">删除</button></template></AdminModal>
    <AdminModal :open="batchDeleteOpen" title="确认批量删除？" width="460px" @close="batchDeleteOpen = false"><p class="text-sm leading-6 text-slate-600">将删除选中的 {{ selectedIds.size }} 个文档，且向量数据会清理。</p><template #footer><button class="ui-button modal-button" data-variant="outline" type="button" @click="batchDeleteOpen = false">取消</button><button class="ui-button modal-button destructive-button" type="button" @click="handleBatchDelete">删除 {{ selectedIds.size }} 个文档</button></template></AdminModal>
    <AdminModal :open="Boolean(chunkTarget)" :title="chunkTarget?.chunkCount ? '重新分块？' : '开始分块？'" width="520px" @close="chunkTarget = null"><div class="space-y-3 text-sm leading-6 text-slate-600"><p v-if="chunkTarget?.chunkCount">文档 [{{ chunkTarget.docName }}] 已有 {{ chunkTarget.chunkCount }} 个分块记录。</p><p v-else>文档 [{{ chunkTarget?.docName }}] 将开始分块并写入向量库。</p><p v-if="chunkTarget?.chunkCount" class="font-medium text-amber-600">重新分块会清空原有 Chunk 记录及向量数据。</p><p v-if="chunkTarget?.chunksEdited" class="warning-box"><strong>注意：</strong>该文档存在被手工编辑过的分块，重新分块会从源文件重新生成，所有手动修改将丢失且无法恢复。</p></div><template #footer><button class="ui-button modal-button" data-variant="outline" type="button" @click="chunkTarget = null">取消</button><button class="ui-button modal-button" :class="{ 'destructive-button': chunkTarget?.chunksEdited }" data-variant="default" type="button" @click="handleChunk">{{ chunkTarget?.chunkCount ? "确认" : "开始" }}</button></template></AdminModal>

    <div v-if="selectedIds.size > 0" class="batch-bar"><Check class="h-4 w-4 text-emerald-400" /><strong>已选 {{ selectedIds.size }} 项</strong><i></i><button type="button" :disabled="batchOperating" @click="handleBatchChunk"><PlayCircle class="h-4 w-4" />批量分块</button><button class="danger" type="button" :disabled="batchOperating" @click="batchDeleteOpen = true"><Trash2 class="h-4 w-4" />删除</button><i></i><button class="close" type="button" title="取消选择" @click="selectedIds.clear()"><X class="h-4 w-4" /></button></div>
  </div>
</template>

<script setup lang="ts">
import { Check, Download, Eye, FileBarChart, FileImage, FileSpreadsheet, FileText, FileUp, Link as LinkIcon, Pencil, PlayCircle, RefreshCw, Trash2, X } from "lucide-vue-next";
import { computed, onMounted, reactive, ref, watch } from "vue";
import { useRoute, useRouter } from "vue-router";

import AdminModal from "@/components/admin/AdminModal.vue";
import AdminPagination from "@/components/admin/AdminPagination.vue";
import AdminRelativeTime from "@/components/admin/AdminRelativeTime.vue";
import SpreadsheetPreview from "@/components/admin/SpreadsheetPreview.vue";
import MarkdownContent from "@/components/chat/MarkdownContent.vue";
import { csvToMarkdown } from "@/lib/csvToMarkdown";
import { getIngestionPipelines, type IngestionPipeline } from "@/services/ingestionService";
import {
  deleteDocument,
  enableDocument,
  fetchDocumentFile,
  getChunkLogsPage,
  getChunkStrategies,
  getDocument,
  getDocumentsPage,
  getKnowledgeBase,
  previewDocument,
  startDocumentChunk,
  updateDocument,
  uploadDocument,
  type ChunkStrategyOption,
  type KnowledgeBase,
  type KnowledgeDocument,
  type KnowledgeDocumentChunkLog,
  type PageResult
} from "@/services/knowledgeService";
import { getSystemSettings } from "@/services/settingsService";
import { getErrorMessage } from "@/utils/error";
import { toast } from "@/utils/toast";

const PAGE_SIZE = 10;
const NO_CHUNK_VALUE = -1;
const TABLE_FILE_EXTS = ["xlsx", "xls", "csv"];
const statusOptions = ["pending", "running", "failed", "success"];
const route = useRoute();
const router = useRouter();
const kbId = computed(() => String(route.params.kbId || ""));

const kb = ref<KnowledgeBase | null>(null);
const pageData = ref<PageResult<KnowledgeDocument> | null>(null);
const pageNo = ref(Math.max(1, Number(history.state?.page) || 1));
const loading = ref(false);
const statusFilter = ref("");
const keyword = ref("");
const searchInput = ref("");
const selectedIds = ref(new Set<string>());
const batchOperating = ref(false);
const batchDeleteOpen = ref(false);
const deleteTarget = ref<KnowledgeDocument | null>(null);
const chunkTarget = ref<KnowledgeDocument | null>(null);
const pipelines = ref<IngestionPipeline[]>([]);
const pipelineMap = ref(new Map<string, string>());
const chunkStrategies = ref<ChunkStrategyOption[]>([]);
const maxFileSize = ref(50 * 1024 * 1024);

const uploadOpen = ref(false);
const uploadSaving = ref(false);
const uploadFile = ref<File | null>(null);
const fileInput = ref<HTMLInputElement | null>(null);
const isDragging = ref(false);
const uploadNoChunk = ref(false);
const uploadOriginalChunkSize = ref("512");
const uploadForm = reactive({ sourceType: "file" as "file" | "url", sourceLocation: "", scheduleEnabled: false, scheduleCron: "", processMode: "chunk" as "chunk" | "pipeline", chunkStrategy: "fixed_size", pipelineId: "", chunkSize: "512", overlapSize: "128", targetChars: "1400", maxChars: "1800", minChars: "600", overlapChars: "0", rowsPerChunk: "50", excelParser: "poi" });

const detailTarget = ref<KnowledgeDocument | null>(null);
const detailSaving = ref(false);
const detailNoChunk = ref(false);
const detailOriginalChunkSize = ref("512");
const detailForm = reactive({ docName: "", sourceLocation: "", scheduleEnabled: false, scheduleCron: "", processMode: "chunk", chunkStrategy: "structure_aware", pipelineId: "" });
const detailConfig = reactive<Record<string, string>>({});

const previewTarget = ref<KnowledgeDocument | null>(null);
const previewContent = ref("");
const previewLoading = ref(false);
const logTarget = ref<KnowledgeDocument | null>(null);
const logLoading = ref(false);
const logData = ref<PageResult<KnowledgeDocumentChunkLog> | null>(null);

const documents = computed(() => pageData.value?.records || []);
const allSelected = computed(() => documents.value.length > 0 && documents.value.every((doc) => selectedIds.value.has(String(doc.id))));
const uploadFileExt = computed(() => extOf(uploadFile.value?.name));
const uploadIsTable = computed(() => isTableExt(uploadFileExt.value));
const detailFileExt = computed(() => detailTarget.value?.fileType?.toLowerCase() || "");
const detailIsTable = computed(() => isTableExt(detailFileExt.value));
const latestLog = computed(() => logData.value?.records?.[0] || null);
const previewFileUrl = computed(() => `${import.meta.env.VITE_API_BASE_URL || ""}/knowledge-base/docs/${previewTarget.value?.id}/file`);
const previewUsesSourceFile = computed(() => Boolean(previewTarget.value && (previewTarget.value.fileType?.toLowerCase() === "pdf" || isSpreadsheetType(previewTarget.value.fileType) || isImageType(previewTarget.value.fileType))));
const previewFrontMatter = computed(() => parseFrontMatter(previewContent.value));

async function loadKnowledgeBase() {
  if (!kbId.value) return;
  try { kb.value = await getKnowledgeBase(kbId.value); } catch (error) { toast.error(getErrorMessage(error, "加载知识库失败")); }
}

async function loadDocuments(current = pageNo.value) {
  if (!kbId.value) return;
  loading.value = true;
  try { pageData.value = await getDocumentsPage(kbId.value, { current, size: PAGE_SIZE, status: statusFilter.value || undefined, keyword: keyword.value || undefined }); }
  catch (error) { toast.error(getErrorMessage(error, "加载文档失败")); }
  finally { loading.value = false; }
}

async function loadOptions() {
  const [pipelineResult, strategies] = await Promise.allSettled([getIngestionPipelines(1, 200), getChunkStrategies()]);
  if (pipelineResult.status === "fulfilled") {
    pipelines.value = pipelineResult.value.records || [];
    pipelineMap.value = new Map(pipelines.value.map((item) => [String(item.id), item.name]));
  }
  if (strategies.status === "fulfilled") chunkStrategies.value = strategies.value;
  try { maxFileSize.value = (await getSystemSettings()).upload.maxFileSize; } catch { /* Keep backend-compatible default. */ }
}

function changePage(next: number) { pageNo.value = next; }
function handleSearch() { pageNo.value = 1; keyword.value = searchInput.value.trim(); }
function handleFilterChange() { pageNo.value = 1; void loadDocuments(1); }
function handleRefresh() { pageNo.value = 1; void loadDocuments(1); }
function openChunks(docId: string) { void router.push(`/admin/knowledge/${kbId.value}/docs/${docId}`); }

function toggleSelect(id: string) { const next = new Set(selectedIds.value); next.has(id) ? next.delete(id) : next.add(id); selectedIds.value = next; }
function toggleSelectAll() { selectedIds.value = allSelected.value ? new Set() : new Set(documents.value.map((doc) => String(doc.id))); }

async function handleBatchChunk() {
  if (!selectedIds.value.size) return;
  batchOperating.value = true;
  let done = 0;
  try { for (const id of selectedIds.value) { await startDocumentChunk(id); done += 1; } toast.success(`已触发 ${done} 个文档分块`); selectedIds.value = new Set(); await loadDocuments(); }
  catch (error) { toast.error(getErrorMessage(error, `已处理 ${done}/${selectedIds.value.size}，操作中断`)); }
  finally { batchOperating.value = false; }
}

async function handleBatchDelete() {
  if (!selectedIds.value.size) return;
  batchOperating.value = true; batchDeleteOpen.value = false; let done = 0;
  try { for (const id of selectedIds.value) { await deleteDocument(id); done += 1; } toast.success(`已删除 ${done} 个文档`); selectedIds.value = new Set(); pageNo.value = 1; await loadDocuments(1); }
  catch (error) { toast.error(getErrorMessage(error, `已处理 ${done}/${selectedIds.value.size}，操作中断`)); }
  finally { batchOperating.value = false; }
}

async function handleDelete() { if (!deleteTarget.value) return; try { await deleteDocument(String(deleteTarget.value.id)); toast.success("删除成功"); deleteTarget.value = null; pageNo.value = 1; await loadDocuments(1); } catch (error) { toast.error(getErrorMessage(error, "删除失败")); } }
async function handleChunk() { if (!chunkTarget.value) return; try { await startDocumentChunk(String(chunkTarget.value.id)); toast.success("已开始分块"); chunkTarget.value = null; await loadDocuments(); } catch (error) { toast.error(getErrorMessage(error, "分块失败")); } }
async function handleToggleEnabled(doc: KnowledgeDocument) { try { await enableDocument(String(doc.id), !Boolean(doc.enabled)); toast.success(doc.enabled ? "已禁用" : "已启用"); await loadDocuments(); } catch (error) { toast.error(getErrorMessage(error, "操作失败")); } }

function resetUploadForm() { Object.assign(uploadForm, { sourceType: "file", sourceLocation: "", scheduleEnabled: false, scheduleCron: "", processMode: "chunk", chunkStrategy: "fixed_size", pipelineId: "", chunkSize: "512", overlapSize: "128", targetChars: "1400", maxChars: "1800", minChars: "600", overlapChars: "0", rowsPerChunk: "50", excelParser: "poi" }); uploadFile.value = null; uploadNoChunk.value = false; uploadOriginalChunkSize.value = "512"; }
function openUploadDialog() { resetUploadForm(); uploadOpen.value = true; }
function handleFileSelect(event: Event) { uploadFile.value = (event.target as HTMLInputElement).files?.[0] || null; }
function handleFileDrop(event: DragEvent) { isDragging.value = false; uploadFile.value = event.dataTransfer?.files?.[0] || null; }
function clearUploadFile() { uploadFile.value = null; if (fileInput.value) fileInput.value.value = ""; }
function applyUploadStrategyDefaults() { const strategy = chunkStrategies.value.find((item) => item.value === uploadForm.chunkStrategy); if (!strategy) return; for (const [key, value] of Object.entries(strategy.defaultConfig)) if (key in uploadForm) (uploadForm as unknown as Record<string, string>)[key] = String(value); uploadNoChunk.value = false; uploadOriginalChunkSize.value = uploadForm.chunkSize; }
function toggleUploadNoChunk() { if (uploadNoChunk.value) { uploadForm.chunkSize = uploadOriginalChunkSize.value; uploadNoChunk.value = false; } else { uploadOriginalChunkSize.value = uploadForm.chunkSize || "512"; uploadForm.chunkSize = String(NO_CHUNK_VALUE); uploadNoChunk.value = true; } }

async function handleUpload() {
  if (uploadForm.sourceType === "file" && !uploadFile.value) return toast.error("请选择文件");
  if (uploadFile.value && uploadFile.value.size > maxFileSize.value) return toast.error(`上传文件大小超过限制，最大允许 ${Math.floor(maxFileSize.value / 1024 / 1024)}MB`);
  if (uploadForm.sourceType === "url" && !uploadForm.sourceLocation.trim()) return toast.error("请输入来源地址");
  if (uploadForm.sourceType === "url" && uploadForm.scheduleEnabled && !uploadForm.scheduleCron.trim()) return toast.error("请输入定时频率");
  if (uploadForm.processMode === "pipeline" && !uploadForm.pipelineId) return toast.error("请选择数据通道");
  uploadSaving.value = true;
  try {
    let chunkConfig: string | null = null;
    let chunkStrategy: string | undefined;
    if (uploadForm.processMode === "chunk") {
      chunkStrategy = uploadIsTable.value ? "fixed_size" : uploadForm.chunkStrategy;
      if (uploadIsTable.value) { const config: Record<string, number | string> = { chunkSize: numberOr(uploadForm.chunkSize, 512), overlapSize: 0, rowsPerChunk: numberOr(uploadForm.rowsPerChunk, 50) }; if (uploadFileExt.value !== "csv") config.excelParser = uploadForm.excelParser; chunkConfig = JSON.stringify(config); }
      else { const strategy = chunkStrategies.value.find((item) => item.value === uploadForm.chunkStrategy); const values = uploadForm as unknown as Record<string, string>; const config: Record<string, number> = {}; for (const key of Object.keys(strategy?.defaultConfig || {})) config[key] = numberOr(values[key], strategy?.defaultConfig[key] || 0); chunkConfig = JSON.stringify(config); }
    }
    await uploadDocument(kbId.value, { sourceType: uploadForm.sourceType, file: uploadForm.sourceType === "file" ? uploadFile.value : null, sourceLocation: uploadForm.sourceType === "url" ? uploadForm.sourceLocation.trim() : null, scheduleEnabled: uploadForm.sourceType === "url" ? uploadForm.scheduleEnabled : false, scheduleCron: uploadForm.sourceType === "url" && uploadForm.scheduleEnabled ? uploadForm.scheduleCron.trim() : null, processMode: uploadForm.processMode, chunkStrategy, chunkConfig, pipelineId: uploadForm.processMode === "pipeline" ? uploadForm.pipelineId : null });
    toast.success("上传成功"); uploadOpen.value = false; pageNo.value = 1; await loadDocuments(1);
  } catch (error) { toast.error(getErrorMessage(error, "上传失败")); }
  finally { uploadSaving.value = false; }
}

async function openEditDialog(doc: KnowledgeDocument) {
  try {
    const detail = await getDocument(String(doc.id)); detailTarget.value = detail;
    Object.assign(detailForm, { docName: detail.docName || "", sourceLocation: detail.sourceLocation || "", scheduleEnabled: Boolean(detail.scheduleEnabled), scheduleCron: detail.scheduleCron || "", processMode: (detail.processMode || "chunk").toLowerCase(), chunkStrategy: (detail.chunkStrategy || "structure_aware").toLowerCase(), pipelineId: detail.pipelineId ? String(detail.pipelineId) : "" });
    for (const key of Object.keys(detailConfig)) delete detailConfig[key]; Object.assign(detailConfig, Object.fromEntries(Object.entries(parseChunkConfig(detail.chunkConfig)).map(([key, value]) => [key, String(value)])));
    detailNoChunk.value = detailConfig.chunkSize === String(NO_CHUNK_VALUE); detailOriginalChunkSize.value = detailNoChunk.value ? "512" : detailConfig.chunkSize || "512";
  } catch (error) { toast.error(getErrorMessage(error, "加载文档详情失败")); }
}

function applyDetailStrategyDefaults() { const strategy = chunkStrategies.value.find((item) => item.value === detailForm.chunkStrategy); if (!strategy) return; for (const key of Object.keys(detailConfig)) delete detailConfig[key]; Object.assign(detailConfig, Object.fromEntries(Object.entries(strategy.defaultConfig).map(([key, value]) => [key, String(value)]))); detailNoChunk.value = false; detailOriginalChunkSize.value = detailConfig.chunkSize || "512"; }
function toggleDetailNoChunk() { if (detailNoChunk.value) { detailConfig.chunkSize = detailOriginalChunkSize.value; detailNoChunk.value = false; } else { detailOriginalChunkSize.value = detailConfig.chunkSize || "512"; detailConfig.chunkSize = String(NO_CHUNK_VALUE); detailNoChunk.value = true; } }

async function handleDetailSave() {
  if (!detailTarget.value) return; if (!detailForm.docName.trim()) return toast.error("文档名称不能为空"); if (detailForm.processMode === "pipeline" && !detailForm.pipelineId) return toast.error("请选择数据通道"); detailSaving.value = true;
  try {
    const payload: Parameters<typeof updateDocument>[1] = { docName: detailForm.docName.trim(), processMode: detailForm.processMode };
    if (detailForm.processMode === "chunk") { if (detailIsTable.value) { payload.chunkStrategy = "fixed_size"; const config: Record<string, number | string> = { chunkSize: numberOr(detailConfig.chunkSize, 512), overlapSize: 0, rowsPerChunk: numberOr(detailConfig.rowsPerChunk, 50) }; if (detailFileExt.value !== "csv") config.excelParser = detailConfig.excelParser || "poi"; payload.chunkConfig = JSON.stringify(config); } else { payload.chunkStrategy = detailForm.chunkStrategy; const strategy = chunkStrategies.value.find((item) => item.value === detailForm.chunkStrategy); payload.chunkConfig = JSON.stringify(Object.fromEntries(Object.keys(strategy?.defaultConfig || {}).map((key) => [key, numberOr(detailConfig[key], strategy?.defaultConfig[key] || 0)]))); } } else payload.pipelineId = detailForm.pipelineId;
    if (detailTarget.value.sourceType?.toLowerCase() === "url") { payload.sourceLocation = detailForm.sourceLocation.trim(); payload.scheduleEnabled = detailForm.scheduleEnabled ? 1 : 0; payload.scheduleCron = detailForm.scheduleCron.trim(); }
    await updateDocument(String(detailTarget.value.id), payload); toast.success("更新成功"); detailTarget.value = null; await loadDocuments();
  } catch (error) { toast.error(getErrorMessage(error, "更新失败")); }
  finally { detailSaving.value = false; }
}

async function handlePreview(doc: KnowledgeDocument) { previewTarget.value = doc; previewContent.value = ""; if (doc.fileType?.toLowerCase() === "pdf" || isSpreadsheetType(doc.fileType) || isImageType(doc.fileType)) return; previewLoading.value = true; try { if (doc.fileType?.toLowerCase() === "csv") { const buffer = await fetchDocumentFile(String(doc.id)); previewContent.value = csvToMarkdown(new TextDecoder("utf-8").decode(buffer)); } else previewContent.value = await previewDocument(String(doc.id)); } catch (error) { toast.error(getErrorMessage(error, "加载预览失败")); previewTarget.value = null; } finally { previewLoading.value = false; } }
function closePreview() { previewTarget.value = null; previewContent.value = ""; }
async function handleDownload(doc: KnowledgeDocument) { try { const buffer = await fetchDocumentFile(String(doc.id)); const url = URL.createObjectURL(new Blob([buffer])); const anchor = document.createElement("a"); anchor.href = url; const name = doc.docName || `document-${doc.id}`; anchor.download = !/\.[^./\\]+$/.test(name) && doc.fileType ? `${name}.${doc.fileType.toLowerCase()}` : name; anchor.click(); URL.revokeObjectURL(url); } catch (error) { toast.error(getErrorMessage(error, "下载失败")); } }
async function openChunkLogs(doc: KnowledgeDocument) { logTarget.value = doc; logLoading.value = true; try { logData.value = await getChunkLogsPage(String(doc.id), 1, 1); } catch (error) { toast.error(getErrorMessage(error, "加载分块日志失败")); } finally { logLoading.value = false; } }

function extOf(name?: string | null) { return name?.split(".").pop()?.toLowerCase() || ""; }
function isTableExt(ext?: string | null) { return Boolean(ext && TABLE_FILE_EXTS.includes(ext.toLowerCase())); }
function isSpreadsheetType(ext?: string | null) { return ["xlsx", "xls"].includes(ext?.toLowerCase() || ""); }
function isImageType(ext?: string | null) { return ["png", "jpg", "jpeg", "svg"].includes(ext?.toLowerCase() || ""); }
function canPreview(doc: KnowledgeDocument) { const ext = doc.fileType?.toLowerCase(); return ext === "markdown" || ext === "md" || ext === "pdf" || isTableExt(ext) || isImageType(ext); }
function fileIcon(doc: KnowledgeDocument) { const ext = doc.fileType?.toLowerCase(); if (isSpreadsheetType(ext) || ext === "csv") return FileSpreadsheet; if (isImageType(ext)) return FileImage; if (doc.sourceType?.toLowerCase() === "url") return LinkIcon; return FileText; }
function fileIconColor(doc: KnowledgeDocument) { const ext = doc.fileType?.toLowerCase(); if (ext === "pdf") return "text-red-500"; if (isSpreadsheetType(ext) || ext === "csv") return "text-emerald-600"; if (isImageType(ext)) return "text-emerald-500"; if (doc.sourceType?.toLowerCase() === "url") return "text-purple-500"; return "text-blue-500"; }
function statusDotClass(status?: string | null) { return `status-dot is-${status?.toLowerCase() || "unknown"}`; }
function formatSourceLabel(type?: string | null) { if (type?.toLowerCase() === "url") return "Remote URL"; if (type?.toLowerCase() === "file") return "Local File"; return ""; }
function formatSize(size?: number | null) { if (size == null) return "-"; if (size < 1024) return `${size} B`; if (size < 1024 ** 2) return `${Number((size / 1024).toFixed(1))} KB`; if (size < 1024 ** 3) return `${Number((size / 1024 ** 2).toFixed(1))} MB`; return `${Number((size / 1024 ** 3).toFixed(1))} GB`; }
function processModeLabel(doc: KnowledgeDocument) { if (doc.processMode?.toLowerCase() === "chunk") return "Chunk"; if (doc.processMode?.toLowerCase() === "pipeline") return "Data Pipeline"; return "-"; }
function processModeTitle(doc: KnowledgeDocument) { if (doc.processMode?.toLowerCase() === "chunk") return doc.chunkStrategy ? `策略：${formatChunkStrategy(doc.chunkStrategy)}` : ""; return doc.pipelineId ? pipelineMap.value.get(String(doc.pipelineId)) || "" : ""; }
function formatChunkStrategy(strategy?: string | null) { if (strategy?.toLowerCase() === "fixed_size") return "固定大小"; if (strategy?.toLowerCase() === "structure_aware") return "语义感知（Markdown友好）"; return strategy || "-"; }
function parseChunkConfig(raw?: string | null): Record<string, unknown> { if (!raw) return {}; try { const value: unknown = JSON.parse(raw); return value && typeof value === "object" ? value as Record<string, unknown> : {}; } catch { return {}; } }
function parseFrontMatter(content: string) { if (content.startsWith("---\n")) { const end = content.indexOf("\n---\n", 4); if (end > 0) return { head: content.slice(4, end), body: content.slice(end + 5) }; } return { head: "", body: content }; }
function numberOr(value: string | undefined, fallback: number) { const parsed = Number(value); return Number.isFinite(parsed) ? parsed : fallback; }
function formatDuration(ms?: number | null) { if (ms == null) return "-"; return ms < 1000 ? `${ms}ms` : `${(ms / 1000).toFixed(2)}s`; }
function formatLogStatus(status?: string) { return status === "success" ? "成功" : status === "failed" ? "失败" : status === "running" ? "进行中" : status || "-"; }
function logStatusClass(status?: string) { return status === "success" ? "success" : status === "failed" ? "failed" : "running"; }
function logModeDetail(log: KnowledgeDocumentChunkLog) { if (log.processMode === "chunk" && log.chunkStrategy) return ` · ${formatChunkStrategy(log.chunkStrategy)}`; if (log.processMode === "pipeline" && (log.pipelineName || log.pipelineId)) return ` · ${log.pipelineName || log.pipelineId}`; return ""; }
function formatDateTime(value?: string | null) { if (!value) return "-"; const date = new Date(value); return Number.isNaN(date.getTime()) ? value : date.toLocaleString("zh-CN"); }

watch(pageNo, (value) => { void router.replace({ path: route.path, query: route.query, state: { ...history.state, page: value } }); void loadDocuments(value); });
watch([() => route.params.kbId, keyword], () => { selectedIds.value = new Set(); void loadDocuments(); });
watch(uploadFileExt, () => { if (uploadIsTable.value) uploadForm.chunkStrategy = "fixed_size"; });
onMounted(() => { void loadKnowledgeBase(); void loadDocuments(); void loadOptions(); });
</script>

<style scoped>
.action-button,.toolbar-button,.modal-button,.row-button,.icon-button,.text-button{display:inline-flex;align-items:center;justify-content:center;gap:7px}.action-button{min-height:40px;padding:0 14px;border:1px solid #e2e8f0;font-size:14px}.document-card-header{display:flex;align-items:center;justify-content:space-between;gap:18px}.document-toolbar{display:flex;flex:1;flex-wrap:wrap;align-items:center;justify-content:flex-end;gap:8px}.toolbar-input{width:min(260px,100%)}.toolbar-input,.toolbar-select{height:40px;padding:0 12px;border:1px solid #e2e8f0;font-size:14px}.toolbar-select{width:150px}.toolbar-button{height:40px;padding:0 13px;border:1px solid #e2e8f0;font-size:13px}.empty-state{padding:32px 12px;color:#64748b;text-align:center}.document-table{min-width:1280px}.document-name-cell{display:flex;min-width:0;max-width:330px;align-items:center;gap:10px}.document-link{display:block;min-width:0;overflow:hidden;color:#0f172a;font-weight:500;text-align:left;text-overflow:ellipsis;white-space:nowrap}.document-link:hover{color:#4f46e5;text-decoration:underline;text-underline-offset:4px}.document-meta{margin-top:3px;overflow:hidden;color:#64748b;font-size:11px;text-overflow:ellipsis;white-space:nowrap}.edited-badge{flex:0 0 auto;border:1px solid #fde68a;border-radius:999px;background:#fffbeb;padding:1px 6px;color:#b45309;font-size:10px}.status-cell{display:inline-flex;align-items:center;gap:8px;color:#64748b;font-size:12px}.status-dot{width:8px;height:8px;border-radius:50%;background:#94a3b8}.status-dot.is-success{background:#10b981}.status-dot.is-failed{background:#ef4444}.status-dot.is-running{background:#f59e0b}.toggle-switch{position:relative;display:inline-flex;width:36px;height:20px;align-items:center;border-radius:999px;background:#e2e8f0;transition:150ms}.toggle-switch span{width:16px;height:16px;transform:translateX(2px);border-radius:50%;background:#fff;box-shadow:0 1px 3px rgb(15 23 42/.25);transition:150ms}.toggle-switch.is-active{background:#4f46e5}.toggle-switch.is-active span{transform:translateX(18px)}.row-actions{display:flex;align-items:center;gap:6px}.row-button{height:32px;padding:0 9px;border:1px solid #e2e8f0;font-size:12px}.icon-button{width:32px;height:32px;border-radius:8px;color:#64748b}.icon-button:hover{background:#f1f5f9;color:#0f172a}.danger-button{color:#dc2626}.form-stack{display:grid;gap:18px}.compact-stack{gap:14px}.form-field{display:grid;gap:7px;color:#334155;font-size:13px;font-weight:500}.form-field small,.inline-field small{display:block;color:#64748b;font-weight:400;line-height:1.5}.form-control{width:100%;height:40px;padding:0 12px;border:1px solid #e2e8f0;font-size:14px}.muted-control{background:#f8fafc;color:#64748b}.form-panel{display:grid;gap:14px;border:1px solid #e2e8f0;border-radius:8px;padding:14px}.inline-field{display:flex;align-items:center;justify-content:space-between;gap:16px;color:#334155;font-size:13px}.form-grid{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:14px}.three-cols{grid-template-columns:repeat(3,minmax(0,1fr))}.form-hint{color:#64748b;font-size:12px;line-height:1.6}.field-switch{margin-top:3px}.file-drop{display:flex;min-height:150px;cursor:pointer;flex-direction:column;align-items:center;justify-content:center;gap:7px;border:2px dashed #cbd5e1;border-radius:8px;padding:20px;color:#64748b;text-align:center;transition:150ms}.file-drop strong{max-width:100%;overflow-wrap:anywhere;color:#334155;font-size:13px}.file-drop span{font-size:11px}.file-drop:hover,.file-drop.is-dragging{border-color:#818cf8;background:#eef2ff}.file-drop.has-file{border-color:#a5b4fc;background:#f8fafc}.text-button{color:#64748b;font-size:12px}.modal-button{min-width:76px;height:38px;padding:0 14px;border:1px solid #e2e8f0;font-size:13px}.destructive-button{border-color:#dc2626!important;background:#dc2626!important;color:#fff!important}.preview-area{min-height:420px}.preview-area.is-file-preview{height:70vh}.source-preview{width:100%;height:100%;border:0}.image-preview{display:grid;height:100%;place-items:center;overflow:auto;background:#f8fafc}.image-preview img{max-width:100%;max-height:100%;object-fit:contain}.markdown-preview{padding:4px 8px}.front-matter{margin-bottom:16px;overflow:auto;border:1px solid #e2e8f0;border-radius:8px;background:#f8fafc;padding:14px;color:#475569;font-size:12px;line-height:1.6}.log-detail{display:grid;gap:18px}.log-summary{display:flex;align-items:center;justify-content:space-between;gap:16px}.log-summary>strong{font-size:24px}.log-summary small{color:#64748b;font-size:13px;font-weight:400}.log-badge{padding:4px 9px}.log-badge.success{background:#ecfdf5;color:#047857}.log-badge.failed{background:#fef2f2;color:#dc2626}.log-badge.running{background:#fffbeb;color:#b45309}.log-metrics{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:10px}.metric{display:grid;gap:5px;border:1px solid #e2e8f0;border-radius:8px;background:#f8fafc;padding:12px}.metric span{color:#64748b;font-size:11px}.metric strong{font-size:17px}.metric.total{background:#eff6ff;color:#2563eb}.error-box,.warning-box{border-radius:8px;background:#fef2f2;padding:12px;color:#dc2626;font-size:13px}.batch-bar{position:fixed;left:50%;bottom:24px;z-index:60;display:flex;transform:translateX(-50%);align-items:center;gap:12px;border-radius:12px;background:#0f172a;padding:12px 18px;color:#fff;box-shadow:0 14px 40px rgb(15 23 42/.28);font-size:13px}.batch-bar i{width:1px;height:20px;background:rgb(255 255 255/.2)}.batch-bar button{display:inline-flex;align-items:center;gap:6px;border-radius:7px;padding:6px 9px;color:rgb(255 255 255/.8)}.batch-bar button:hover{background:rgb(255 255 255/.1);color:#fff}.batch-bar button.danger{color:#fca5a5}.batch-bar button.close{padding:6px}@media(max-width:900px){.document-card-header{align-items:flex-start;flex-direction:column}.document-toolbar{width:100%;justify-content:flex-start}.form-grid,.three-cols{grid-template-columns:1fr}.batch-bar{width:calc(100% - 24px);justify-content:center;flex-wrap:wrap}}@media(max-width:640px){.toolbar-input,.toolbar-select{width:100%}.toolbar-button{flex:1}.preview-area.is-file-preview{height:65vh}}
</style>
