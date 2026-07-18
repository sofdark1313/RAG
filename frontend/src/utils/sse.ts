import type { CompletionPayload, MessageDeltaPayload, StreamMetaPayload } from "@/types";

export interface StreamHandlers {
  onMeta?: (payload: StreamMetaPayload) => void;
  onMessage?: (payload: MessageDeltaPayload) => void;
  onThinking?: (payload: MessageDeltaPayload) => void;
  onFinish?: (payload: CompletionPayload) => void;
  onDone?: () => void;
  onCancel?: (payload: CompletionPayload) => void;
  onReject?: (payload: MessageDeltaPayload) => void;
  onTitle?: (payload: { title: string }) => void;
  onError?: (error: Error) => void;
}

export interface StreamOptions {
  url: string;
  headers?: Record<string, string>;
  signal?: AbortSignal;
  retryCount?: number;
  retryDelayMs?: number;
}

function parseData(raw: string) {
  if (!raw) return "";
  try {
    return JSON.parse(raw);
  } catch {
    return raw;
  }
}

async function readSseStream(response: Response, handlers: StreamHandlers, signal?: AbortSignal) {
  if (!response.body) {
    throw new Error("流式响应为空");
  }

  const reader = response.body.getReader();
  const decoder = new TextDecoder("utf-8");
  let buffer = "";
  let eventName = "message";
  let dataLines: string[] = [];

  const dispatchEvent = () => {
    if (dataLines.length === 0) {
      eventName = "message";
      return;
    }

    const payload = parseData(dataLines.join("\n"));

    if (eventName === "meta") handlers.onMeta?.(payload as StreamMetaPayload);
    if (eventName === "message") {
      const messagePayload = payload as MessageDeltaPayload;
      if (messagePayload?.type === "think") handlers.onThinking?.(messagePayload);
      handlers.onMessage?.(messagePayload);
    }
    if (eventName === "finish") handlers.onFinish?.(payload as CompletionPayload);
    if (eventName === "done") handlers.onDone?.();
    if (eventName === "cancel") handlers.onCancel?.(payload as CompletionPayload);
    if (eventName === "reject") handlers.onReject?.(payload as MessageDeltaPayload);
    if (eventName === "title") handlers.onTitle?.(payload as { title: string });
    if (eventName === "error") {
      const errorPayload = payload as { error?: string };
      handlers.onError?.(new Error(errorPayload?.error || String(payload)));
    }

    eventName = "message";
    dataLines = [];
  };

  while (true) {
    if (signal?.aborted) {
      await reader.cancel();
      break;
    }
    const { value, done } = await reader.read();
    if (done) {
      dispatchEvent();
      break;
    }

    buffer += decoder.decode(value, { stream: true });
    const lines = buffer.split(/\r?\n/);
    buffer = lines.pop() ?? "";

    for (const line of lines) {
      if (!line) {
        dispatchEvent();
      } else if (line.startsWith("event:")) {
        eventName = line.slice(6).trim();
      } else if (line.startsWith("data:")) {
        dataLines.push(line.slice(5).trim());
      }
    }
  }
}

async function streamWithRetry(options: StreamOptions, handlers: StreamHandlers) {
  const retryCount = options.retryCount ?? 1;
  const retryDelayMs = options.retryDelayMs ?? 600;

  for (let attempt = 0; attempt <= retryCount; attempt += 1) {
    try {
      const response = await fetch(options.url, {
        method: "GET",
        headers: {
          Accept: "text/event-stream",
          ...options.headers
        },
        signal: options.signal
      });

      if (!response.ok) {
        throw new Error(`SSE 请求失败：${response.status}`);
      }

      await readSseStream(response, handlers, options.signal);
      return;
    } catch (error) {
      if (options.signal?.aborted || attempt >= retryCount) {
        throw error;
      }
      await new Promise((resolve) => setTimeout(resolve, retryDelayMs * 2 ** attempt));
    }
  }
}

export function createStreamResponse(options: StreamOptions, handlers: StreamHandlers) {
  const controller = new AbortController();
  const mergedOptions = {
    ...options,
    signal: options.signal ?? controller.signal
  };

  return {
    start: () => streamWithRetry(mergedOptions, handlers),
    cancel: () => controller.abort()
  };
}
