import { defineStore } from "pinia";

import { stopTask, submitFeedback as submitFeedbackRequest } from "@/services/chatService";
import {
  deleteSession as deleteSessionRequest,
  listMessages,
  listSessions,
  renameSession as renameSessionRequest
} from "@/services/sessionService";
import type { CompletionPayload, FeedbackValue, Message, MessageDeltaPayload, Session } from "@/types";
import { buildQuery } from "@/utils/helpers";
import { createStreamResponse } from "@/utils/sse";
import { storage } from "@/utils/storage";
import { toast } from "@/utils/toast";

interface ChatState {
  sessions: Session[];
  currentSessionId: string | null;
  messages: Message[];
  isLoading: boolean;
  sessionsLoaded: boolean;
  isStreaming: boolean;
  isCreatingNew: boolean;
  deepThinkingEnabled: boolean;
  thinkingStartAt: number | null;
  streamTaskId: string | null;
  streamAbort: (() => void) | null;
  streamingMessageId: string | null;
  cancelRequested: boolean;
}

function mapVoteToFeedback(vote?: number | null): FeedbackValue {
  if (vote === 1) return "like";
  if (vote === -1) return "dislike";
  return null;
}

function computeThinkingDuration(startAt?: number | null) {
  if (!startAt) return undefined;
  return Math.max(1, Math.round((Date.now() - startAt) / 1000));
}

function upsertSession(sessions: Session[], next: Session) {
  const index = sessions.findIndex((session) => session.id === next.id);
  const updated = [...sessions];
  if (index >= 0) {
    updated[index] = { ...updated[index], ...next };
  } else {
    updated.unshift(next);
  }
  return updated.sort((a, b) => {
    const timeA = a.lastTime ? new Date(a.lastTime).getTime() : 0;
    const timeB = b.lastTime ? new Date(b.lastTime).getTime() : 0;
    return timeB - timeA;
  });
}

const API_BASE_URL = (import.meta.env.VITE_API_BASE_URL || "").replace(/\/$/, "");

export const useChatStore = defineStore("chat", {
  state: (): ChatState => ({
    sessions: [],
    currentSessionId: null,
    messages: [],
    isLoading: false,
    sessionsLoaded: false,
    isStreaming: false,
    isCreatingNew: false,
    deepThinkingEnabled: false,
    thinkingStartAt: null,
    streamTaskId: null,
    streamAbort: null,
    streamingMessageId: null,
    cancelRequested: false
  }),
  actions: {
    async fetchSessions() {
      this.isLoading = true;
      try {
        const data = await listSessions();
        this.sessions = data
          .map((item) => ({
            id: item.conversationId,
            title: item.title || "新对话",
            lastTime: item.lastTime
          }))
          .sort((a, b) => {
            const timeA = a.lastTime ? new Date(a.lastTime).getTime() : 0;
            const timeB = b.lastTime ? new Date(b.lastTime).getTime() : 0;
            return timeB - timeA;
          });
      } catch (error) {
        toast.error((error as Error).message || "加载会话失败");
      } finally {
        this.isLoading = false;
        this.sessionsLoaded = true;
      }
    },
    createSession() {
      if (this.isStreaming) this.cancelGeneration();
      this.currentSessionId = null;
      this.messages = [];
      this.isCreatingNew = true;
      this.deepThinkingEnabled = false;
      this.thinkingStartAt = null;
      this.streamTaskId = null;
      this.streamAbort = null;
      this.streamingMessageId = null;
      this.cancelRequested = false;
    },
    async deleteSession(sessionId: string) {
      try {
        await deleteSessionRequest(sessionId);
        this.sessions = this.sessions.filter((session) => session.id !== sessionId);
        if (this.currentSessionId === sessionId) {
          this.currentSessionId = null;
          this.messages = [];
        }
        toast.success("已删除会话");
      } catch (error) {
        toast.error((error as Error).message || "删除会话失败");
      }
    },
    async renameSession(sessionId: string, title: string) {
      const nextTitle = title.trim();
      if (!nextTitle) return;
      try {
        await renameSessionRequest(sessionId, nextTitle);
        this.sessions = this.sessions.map((session) =>
          session.id === sessionId ? { ...session, title: nextTitle } : session
        );
        toast.success("已重命名");
      } catch (error) {
        toast.error((error as Error).message || "重命名失败");
      }
    },
    async selectSession(sessionId: string) {
      if (!sessionId) return;
      if (this.currentSessionId === sessionId && this.messages.length > 0) return;
      if (this.isStreaming) this.cancelGeneration();

      this.isLoading = true;
      this.currentSessionId = sessionId;
      this.isCreatingNew = false;
      this.thinkingStartAt = null;

      try {
        const data = await listMessages(sessionId);
        if (this.currentSessionId !== sessionId) return;
        this.messages = data.map((item) => ({
          id: String(item.id),
          role: item.role === "assistant" ? "assistant" : "user",
          content: item.content,
          thinking: item.thinkingContent || undefined,
          thinkingDuration: item.thinkingDuration || undefined,
          isDeepThinking: Boolean(item.thinkingContent),
          createdAt: item.createTime,
          feedback: mapVoteToFeedback(item.vote),
          status: "done"
        }));
      } catch (error) {
        toast.error((error as Error).message || "加载消息失败");
      } finally {
        this.isLoading = false;
        this.isStreaming = false;
        this.streamTaskId = null;
        this.streamAbort = null;
        this.streamingMessageId = null;
        this.cancelRequested = false;
      }
    },
    updateSessionTitle(sessionId: string, title: string) {
      this.sessions = this.sessions.map((session) =>
        session.id === sessionId ? { ...session, title } : session
      );
    },
    async sendMessage(content: string) {
      const trimmed = content.trim();
      if (!trimmed || this.isStreaming) return;

      const deepThinkingEnabled = this.deepThinkingEnabled;
      const userMessage: Message = {
        id: `user-${Date.now()}`,
        role: "user",
        content: trimmed,
        status: "done",
        createdAt: new Date().toISOString()
      };
      const assistantId = `assistant-${Date.now()}`;
      const assistantMessage: Message = {
        id: assistantId,
        role: "assistant",
        content: "",
        thinking: deepThinkingEnabled ? "" : undefined,
        isDeepThinking: deepThinkingEnabled,
        isThinking: deepThinkingEnabled,
        status: "streaming",
        feedback: null,
        createdAt: new Date().toISOString()
      };

      this.messages.push(userMessage, assistantMessage);
      this.isStreaming = true;
      this.streamingMessageId = assistantId;
      this.thinkingStartAt = null;
      this.streamTaskId = null;
      this.cancelRequested = false;

      const query = buildQuery({
        question: trimmed,
        conversationId: this.currentSessionId || undefined,
        deepThinking: deepThinkingEnabled || undefined
      });
      const token = storage.getToken();
      const handlers = this.createStreamHandlers(assistantId);
      const { start, cancel } = createStreamResponse(
        {
          url: `${API_BASE_URL}/rag/v3/chat${query}`,
          headers: token ? { Authorization: token } : undefined,
          retryCount: 1
        },
        handlers
      );

      this.streamAbort = cancel;

      try {
        await start();
      } catch (error) {
        if ((error as Error).name !== "AbortError") {
          handlers.onError?.(error as Error);
        }
      } finally {
        if (this.streamingMessageId === assistantId) {
          this.isStreaming = false;
          this.streamTaskId = null;
          this.streamAbort = null;
          this.streamingMessageId = null;
          this.cancelRequested = false;
        }
      }
    },
    createStreamHandlers(assistantId: string) {
      return {
        onMeta: (payload: { conversationId: string; taskId: string }) => {
          if (this.streamingMessageId !== assistantId) return;
          const nextId = payload.conversationId || this.currentSessionId;
          if (!nextId) return;
          const existing = this.sessions.find((session) => session.id === nextId);
          this.currentSessionId = nextId;
          this.isCreatingNew = false;
          this.streamTaskId = payload.taskId;
          this.sessions = upsertSession(this.sessions, {
            id: nextId,
            title: existing?.title || "新对话",
            lastTime: new Date().toISOString()
          });
          if (this.cancelRequested) {
            stopTask(payload.taskId).catch(() => null);
          }
        },
        onMessage: (payload: MessageDeltaPayload) => {
          if (payload?.type === "response") this.appendStreamContent(payload.delta);
        },
        onThinking: (payload: MessageDeltaPayload) => {
          if (payload?.type === "think") this.appendThinkingContent(payload.delta);
        },
        onReject: (payload: MessageDeltaPayload) => {
          if (payload?.delta) this.appendStreamContent(payload.delta);
        },
        onFinish: (payload: CompletionPayload) => this.finishAssistantMessage(payload),
        onCancel: (payload: CompletionPayload) => this.cancelAssistantMessage(payload),
        onDone: () => this.clearStreamingState(),
        onTitle: (payload: { title: string }) => {
          if (payload?.title && this.currentSessionId) {
            this.updateSessionTitle(this.currentSessionId, payload.title);
          }
        },
        onError: (error: Error) => {
          this.messages = this.messages.map((message) =>
            message.id === this.streamingMessageId
              ? {
                  ...message,
                  status: "error",
                  isThinking: false,
                  thinkingDuration: message.thinkingDuration ?? computeThinkingDuration(this.thinkingStartAt)
                }
              : message
          );
          this.clearStreamingState();
          toast.error(error.message || "生成失败");
        }
      };
    },
    appendStreamContent(delta: string) {
      if (!delta) return;
      const duration = computeThinkingDuration(this.thinkingStartAt);
      const shouldFinalizeThinking = this.thinkingStartAt != null;
      this.messages = this.messages.map((message) => {
        if (message.id !== this.streamingMessageId) return message;
        if (message.status === "cancelled" || message.status === "error") return message;
        return {
          ...message,
          content: message.content + delta,
          isThinking: shouldFinalizeThinking ? false : message.isThinking,
          thinkingDuration:
            shouldFinalizeThinking && !message.thinkingDuration ? duration : message.thinkingDuration
        };
      });
      if (shouldFinalizeThinking) this.thinkingStartAt = null;
    },
    appendThinkingContent(delta: string) {
      if (!delta) return;
      this.thinkingStartAt = this.thinkingStartAt ?? Date.now();
      this.messages = this.messages.map((message) =>
        message.id === this.streamingMessageId && message.status !== "cancelled" && message.status !== "error"
          ? {
              ...message,
              thinking: `${message.thinking ?? ""}${delta}`,
              isThinking: true
            }
          : message
      );
    },
    finishAssistantMessage(payload: CompletionPayload) {
      if (payload?.title && this.currentSessionId) {
        this.updateSessionTitle(this.currentSessionId, payload.title);
      }
      if (this.currentSessionId) {
        const existingTitle =
          this.sessions.find((session) => session.id === this.currentSessionId)?.title || "新对话";
        this.sessions = upsertSession(this.sessions, {
          id: this.currentSessionId,
          title: payload?.title || existingTitle,
          lastTime: new Date().toISOString()
        });
      }

      this.messages = this.messages.map((message) =>
        message.id === this.streamingMessageId
          ? {
              ...message,
              id: payload?.messageId ? String(payload.messageId) : message.id,
              status: "done",
              isThinking: false,
              thinkingDuration: message.thinkingDuration ?? computeThinkingDuration(this.thinkingStartAt)
            }
          : message
      );
    },
    cancelAssistantMessage(payload: CompletionPayload) {
      this.messages = this.messages.map((message) => {
        if (message.id !== this.streamingMessageId) return message;
        const suffix = message.content.includes("（已停止生成）") ? "" : "\n\n（已停止生成）";
        return {
          ...message,
          id: payload?.messageId ? String(payload.messageId) : message.id,
          content: message.content + suffix,
          status: "cancelled",
          isThinking: false,
          thinkingDuration: message.thinkingDuration ?? computeThinkingDuration(this.thinkingStartAt)
        };
      });
      this.clearStreamingState();
    },
    cancelGeneration() {
      if (!this.isStreaming) return;
      this.cancelRequested = true;
      if (this.streamTaskId) {
        stopTask(this.streamTaskId).catch(() => null);
      } else {
        this.streamAbort?.();
      }
    },
    clearStreamingState() {
      this.isStreaming = false;
      this.thinkingStartAt = null;
      this.streamTaskId = null;
      this.streamAbort = null;
      this.streamingMessageId = null;
      this.cancelRequested = false;
    },
    async submitFeedback(messageId: string, feedback: FeedbackValue) {
      const vote = feedback === "like" ? 1 : feedback === "dislike" ? -1 : null;
      const previous = this.messages.find((message) => message.id === messageId)?.feedback ?? null;
      this.messages = this.messages.map((message) =>
        message.id === messageId ? { ...message, feedback } : message
      );
      if (vote === null) return;

      try {
        await submitFeedbackRequest(messageId, vote);
        toast.success(feedback === "like" ? "已点赞" : "已点踩");
      } catch (error) {
        this.messages = this.messages.map((message) =>
          message.id === messageId ? { ...message, feedback: previous } : message
        );
        toast.error((error as Error).message || "反馈保存失败");
      }
    }
  }
});
