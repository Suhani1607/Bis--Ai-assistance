import { create } from "zustand";
import type { Citation, ConversationSummary, Message } from "@/types";

interface ChatState {
  conversations: ConversationSummary[];
  activeConversationId: string | null;
  messages: Message[];
  isStreaming: boolean;
  streamingContent: string;
  streamingCitations: Citation[];
  lang: "en" | "hi";
  showCitationsFor: string | null;  // message id

  // Actions
  setConversations: (c: ConversationSummary[]) => void;
  setActiveConversation: (id: string | null) => void;
  setMessages: (messages: Message[]) => void;
  appendMessage: (msg: Message) => void;
  setStreaming: (v: boolean) => void;
  appendStreamingToken: (token: string) => void;
  setStreamingCitations: (citations: Citation[]) => void;
  finaliseStreaming: (msg: Message) => void;
  resetStreaming: () => void;
  setLang: (lang: "en" | "hi") => void;
  setShowCitationsFor: (id: string | null) => void;
}

export const useChatStore = create<ChatState>((set) => ({
  conversations: [],
  activeConversationId: null,
  messages: [],
  isStreaming: false,
  streamingContent: "",
  streamingCitations: [],
  lang: "en",
  showCitationsFor: null,

  setConversations: (conversations) => set({ conversations }),
  setActiveConversation: (activeConversationId) => set({ activeConversationId }),
  setMessages: (messages) => set({ messages }),
  appendMessage: (msg) => set((s) => ({ messages: [...s.messages, msg] })),
  setStreaming: (isStreaming) => set({ isStreaming }),
  appendStreamingToken: (token) =>
    set((s) => ({ streamingContent: s.streamingContent + token })),
  setStreamingCitations: (streamingCitations) => set({ streamingCitations }),
  finaliseStreaming: (msg) =>
    set((s) => ({
      messages: [...s.messages, msg],
      streamingContent: "",
      streamingCitations: [],
      isStreaming: false,
    })),
  resetStreaming: () =>
    set({ streamingContent: "", streamingCitations: [], isStreaming: false }),
  setLang: (lang) => set({ lang }),
  setShowCitationsFor: (showCitationsFor) => set({ showCitationsFor }),
}));
