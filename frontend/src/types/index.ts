export interface Citation {
  chunkId: string;
  isNumber?: string;
  clauseRef?: string;
  sectionTitle?: string;
  excerpt?: string;
  relevanceScore?: number;
  sortOrder: number;
}

export interface Message {
  id: string;
  conversationId: string;
  role: "user" | "assistant" | "system";
  content: string;
  contentHi?: string;
  citations?: Citation[];
  tokensUsed?: number;
  latencyMs?: number;
  createdAt: string;
}

export interface ConversationSummary {
  id: string;
  title?: string;
  lang: string;
  createdAt: string;
  updatedAt: string;
  messageCount: number;
}

export interface ConversationDetail {
  id: string;
  title?: string;
  lang: string;
  messages: Message[];
  createdAt: string;
  updatedAt: string;
}

export interface User {
  id: string;
  email: string;
  name: string;
  role: string;
  preferredLang: string;
}

export interface AuthResponse {
  accessToken: string;
  refreshToken: string;
  expiresIn: number;
  user: User;
}

export type Lang = "en" | "hi";

export interface SendMessageRequest {
  content: string;
  conversationId?: string;
  lang?: Lang;
  stream?: boolean;
}
