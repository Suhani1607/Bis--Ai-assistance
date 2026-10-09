import Cookies from "js-cookie";
import type {
  AuthResponse, ConversationDetail, ConversationSummary,
  Message, SendMessageRequest,
} from "@/types";

const BASE = process.env.NEXT_PUBLIC_API_URL || "/api/v1";

let inMemoryAccessToken: string | null = null;

export function getAccessToken(): string | null {
  if (inMemoryAccessToken) return inMemoryAccessToken;
  if (typeof window !== "undefined") {
    const sessionToken = sessionStorage.getItem("access_token");
    if (sessionToken) {
      inMemoryAccessToken = sessionToken;
      return sessionToken;
    }
    const localToken = localStorage.getItem("access_token");
    if (localToken) {
      inMemoryAccessToken = localToken;
      sessionStorage.setItem("access_token", localToken);
      return localToken;
    }
  }
  // Short-lived cookie fallback if present (e.g. for SSR / middleware)
  const cookieToken = Cookies.get("access_token") ?? null;
  if (cookieToken) inMemoryAccessToken = cookieToken;
  return cookieToken;
}

export function setAccessToken(token: string | null) {
  inMemoryAccessToken = token;
  if (typeof window !== "undefined") {
    if (token) {
      sessionStorage.setItem("access_token", token);
      localStorage.setItem("access_token", token);
      Cookies.set("access_token", token, { sameSite: "lax", expires: 7 });
    } else {
      sessionStorage.removeItem("access_token");
      localStorage.removeItem("access_token");
      Cookies.remove("access_token");
    }
  }
}

async function request<T>(
  path: string,
  options: RequestInit = {},
): Promise<T> {
  const token = getAccessToken();
  const res = await fetch(`${BASE}${path}`, {
    ...options,
    credentials: "include",
    headers: {
      "Content-Type": "application/json",
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
      ...options.headers,
    },
  });

  // Handle both 401 and 403 as auth/session expiration
  if ((res.status === 401 || res.status === 403) && !path.startsWith("/auth/")) {
    const refreshed = await tryRefresh();
    if (!refreshed) {
      setAccessToken(null);
      if (typeof window !== "undefined") {
        window.location.href = "/auth/login";
      }
      throw new Error("Your session has expired. Please log in again.");
    }
    return request<T>(path, options);
  }

  if (!res.ok) {
    let message = `Request failed (HTTP ${res.status})`;
    try {
      const errJson = await res.json();
      message = errJson.message || errJson.error || errJson.detail || message;
    } catch {
      const errText = await res.text().catch(() => "");
      if (errText) message = errText;
    }
    throw new Error(message);
  }

  if (res.status === 204) return undefined as T;
  return res.json();
}

async function tryRefresh(): Promise<boolean> {
  try {
    const res = await fetch(`${BASE}/auth/refresh`, {
      method: "POST",
      credentials: "include",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({}),
    });
    if (!res.ok) return false;
    const data: AuthResponse = await res.json();
    setAccessToken(data.accessToken);
    return true;
  } catch {
    return false;
  }
}

// ── Auth ─────────────────────────────────────────────────────

export const auth = {
  register: async (body: { email: string; name: string; password: string; preferredLang?: string }): Promise<AuthResponse> => {
    const resp = await request<AuthResponse>("/auth/register", {
      method: "POST",
      body: JSON.stringify(body),
    });
    setAccessToken(resp.accessToken);
    return resp;
  },

  login: async (email: string, password: string): Promise<AuthResponse> => {
    const resp = await request<AuthResponse>("/auth/login", {
      method: "POST",
      body: JSON.stringify({ email, password }),
    });
    setAccessToken(resp.accessToken);
    return resp;
  },

  logout: async (refreshToken?: string): Promise<void> => {
    try {
      await request<void>("/auth/logout", {
        method: "POST",
        body: JSON.stringify({ refreshToken: refreshToken || "" }),
      });
    } finally {
      setAccessToken(null);
    }
  },
};

// ── Chat ─────────────────────────────────────────────────────

export const chat = {
  sendMessage: (body: SendMessageRequest) =>
    request<Message>("/chat/message", { method: "POST", body: JSON.stringify(body) }),

  listConversations: (page = 0, size = 20) =>
    request<ConversationSummary[]>(`/chat/conversations?page=${page}&size=${size}`),

  getConversation: (id: string) =>
    request<ConversationDetail>(`/chat/conversations/${id}`),

  archiveConversation: (id: string) =>
    request<void>(`/chat/conversations/${id}`, { method: "DELETE" }),

  feedback: (messageId: string, rating: 1 | -1, comment?: string) =>
    request<void>(`/chat/messages/${messageId}/feedback`, {
      method: "POST",
      body: JSON.stringify({ rating, comment }),
    }),
};

// ── Standards ────────────────────────────────────────────────

export const standards = {
  search: (q: string) =>
    request<{ isNumber: string; title: string; certScheme: string }[]>(
      `/standards/search?q=${encodeURIComponent(q)}&limit=10`
    ),

  getByIsNumber: (isNumber: string) =>
    request<Record<string, unknown>>(`/standards/${encodeURIComponent(isNumber)}`),

  suggest: (product: string) =>
    request<{ isNumber: string; title: string }[]>(
      `/standards/suggest?product=${encodeURIComponent(product)}`
    ),
};

// ── Streaming ─────────────────────────────────────────────────

/**
 * Opens an SSE connection for streaming chat.
 * Calls onToken for each token, onCitations for citation events, onDone when complete.
 */
export function streamMessage(
  body: SendMessageRequest,
  onToken: (token: string) => void,
  onCitations: (citations: unknown[]) => void,
  onDone: () => void,
  onError: (err: string) => void,
): () => void {
  const token = getAccessToken();
  const controller = new AbortController();

  fetch(`${BASE}/chat/message/stream`, {
    method: "POST",
    credentials: "include",
    signal: controller.signal,
    headers: {
      "Content-Type": "application/json",
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
    },
    body: JSON.stringify({ ...body, stream: true }),
  }).then(async (res) => {
    if (!res.ok) {
      let errorMsg = `Stream request failed (HTTP ${res.status})`;
      try {
        const errJson = await res.json();
        errorMsg = errJson.message || errJson.error || errorMsg;
      } catch {
        const text = await res.text().catch(() => "");
        if (text) errorMsg = text;
      }

      if (res.status === 401 || res.status === 403) {
        setAccessToken(null);
        onError("Session expired or unauthorized. Redirecting to login...");
        if (typeof window !== "undefined") {
          setTimeout(() => { window.location.href = "/auth/login"; }, 1500);
        }
        return;
      }

      onError(errorMsg);
      return;
    }
    const reader = res.body!.getReader();
    const decoder = new TextDecoder();
    let buffer = "";

    while (true) {
      const { done, value } = await reader.read();
      if (done) { onDone(); break; }
      buffer += decoder.decode(value, { stream: true });
      const lines = buffer.split("\n");
      buffer = lines.pop() ?? "";

      for (const line of lines) {
        const trimmed = line.trim();
        if (!trimmed.startsWith("data:")) continue;
        const raw = trimmed.replace(/^data:\s*/, "").trim();
        if (raw === "[DONE]") { onDone(); return; }
        try {
          const parsed = JSON.parse(raw) as { type: string; data: unknown };
          if (parsed.type === "token")     onToken(parsed.data as string);
          if (parsed.type === "citations") onCitations(parsed.data as unknown[]);
        } catch { /* partial JSON, skip */ }
      }
    }
  }).catch((err) => {
    if (err.name !== "AbortError") onError(String(err));
  });

  return () => controller.abort();
}
