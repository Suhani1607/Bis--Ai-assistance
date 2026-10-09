"use client";

import { useState, useRef, useEffect, useCallback } from "react";
import { Send, Mic, MicOff, Globe } from "lucide-react";
import { useChatStore } from "@/store/chatStore";
import { chat as chatApi, streamMessage } from "@/lib/api";
import { cn } from "@/lib/utils";
import type { Citation } from "@/types";

const PLACEHOLDER_EN = "Ask about Indian Standards, BIS certification, hallmarking…";
const PLACEHOLDER_HI = "भारतीय मानकों, BIS प्रमाणन, हॉलमार्किंग के बारे में पूछें…";

export function ChatInput() {
  const [input, setInput]       = useState("");
  const [listening, setListening] = useState(false);
  const textareaRef = useRef<HTMLTextAreaElement>(null);
  const stopStreamRef = useRef<(() => void) | null>(null);

  const {
    activeConversationId, isStreaming, lang,
    setLang, appendMessage, setStreaming,
    appendStreamingToken, setStreamingCitations,
    finaliseStreaming, resetStreaming, setActiveConversation,
    conversations, setConversations,
  } = useChatStore();

  // Auto-resize textarea
  useEffect(() => {
    const ta = textareaRef.current;
    if (!ta) return;
    ta.style.height = "auto";
    ta.style.height = Math.min(ta.scrollHeight, 160) + "px";
  }, [input]);

  const canSend = input.trim().length > 0 && !isStreaming;

  const handleSend = useCallback(async () => {
    const content = input.trim();
    if (!content || isStreaming) return;
    setInput("");

    // Optimistically add user message
    const tempUserMsg = {
      id: `temp-${Date.now()}`,
      conversationId: activeConversationId ?? "",
      role: "user" as const,
      content,
      createdAt: new Date().toISOString(),
    };
    appendMessage(tempUserMsg);
    setStreaming(true);

    // Stream the assistant response
    const cancel = streamMessage(
      { content, conversationId: activeConversationId ?? undefined, lang, stream: true },
      (token) => appendStreamingToken(token),
      (citations) => setStreamingCitations(citations as Citation[]),
      async () => {
        // Streaming done — fetch the persisted message (has ID + citations)
        try {
          // If no active conversation yet, the backend created one; reload list
          const convs = await chatApi.listConversations();
          setConversations(convs);
          if (!activeConversationId && convs.length > 0) {
            const newConv = convs[0];
            setActiveConversation(newConv.id);
            const detail = await chatApi.getConversation(newConv.id);
            const lastMsg = detail.messages[detail.messages.length - 1];
            finaliseStreaming(lastMsg);
          } else if (activeConversationId) {
            const detail = await chatApi.getConversation(activeConversationId);
            const lastMsg = detail.messages[detail.messages.length - 1];
            finaliseStreaming(lastMsg);
          }
        } catch {
          resetStreaming();
        }
      },
      (err) => {
        console.error("Stream error:", err);
        finaliseStreaming({
          id: `err-${Date.now()}`,
          conversationId: activeConversationId ?? "",
          role: "assistant",
          content: `⚠️ ${err}`,
          createdAt: new Date().toISOString(),
        });
      },
    );

    stopStreamRef.current = cancel;
  }, [
    input, isStreaming, activeConversationId, lang,
    appendMessage, setStreaming, appendStreamingToken,
    setStreamingCitations, finaliseStreaming, resetStreaming,
    setConversations, setActiveConversation,
  ]);

  // Enter to send (Shift+Enter = newline)
  const handleKeyDown = (e: React.KeyboardEvent) => {
    if (e.key === "Enter" && !e.shiftKey) {
      e.preventDefault();
      handleSend();
    }
  };

  // Cancel stream
  const handleCancel = () => {
    stopStreamRef.current?.();
    resetStreaming();
  };

  // Voice input (WebSpeech API)
  const toggleVoice = () => {
    const SpeechRecognition =
      (window as any).SpeechRecognition || (window as any).webkitSpeechRecognition;
    if (!SpeechRecognition) {
      alert("Voice input is not supported in this browser.");
      return;
    }
    if (listening) {
      setListening(false);
      return;
    }
    const recognition = new SpeechRecognition();
    recognition.lang = lang === "hi" ? "hi-IN" : "en-IN";
    recognition.interimResults = false;
    recognition.onresult = (event: any) => {
      const transcript = event.results[0][0].transcript;
      setInput((prev) => prev + (prev ? " " : "") + transcript);
      setListening(false);
    };
    recognition.onerror = () => setListening(false);
    recognition.onend   = () => setListening(false);
    recognition.start();
    setListening(true);
  };

  return (
    <div className="border-t border-gray-200 bg-white px-4 py-3">
      <div className="max-w-3xl mx-auto">
        <div className={cn(
          "flex items-end gap-2 rounded-2xl border bg-gray-50 px-3 py-2 transition-colors",
          "focus-within:border-bis-400 focus-within:bg-white focus-within:shadow-sm",
          isStreaming ? "border-orange-300" : "border-gray-200"
        )}>
          {/* Lang toggle */}
          <button
            onClick={() => setLang(lang === "en" ? "hi" : "en")}
            className="flex-shrink-0 flex items-center gap-1 px-2 py-1 rounded-lg text-xs font-medium text-gray-500 hover:bg-gray-200 transition-colors mb-0.5"
            title="Toggle language"
          >
            <Globe size={13} />
            {lang === "en" ? "EN" : "HI"}
          </button>

          {/* Textarea */}
          <textarea
            ref={textareaRef}
            value={input}
            onChange={(e) => setInput(e.target.value)}
            onKeyDown={handleKeyDown}
            placeholder={lang === "hi" ? PLACEHOLDER_HI : PLACEHOLDER_EN}
            rows={1}
            disabled={isStreaming}
            className="flex-1 bg-transparent resize-none outline-none text-sm text-gray-800 placeholder-gray-400 py-1 max-h-40 disabled:opacity-50"
          />

          {/* Voice */}
          <button
            onClick={toggleVoice}
            className={cn(
              "flex-shrink-0 p-2 rounded-xl transition-colors mb-0.5",
              listening
                ? "bg-red-100 text-red-500 animate-pulse"
                : "text-gray-400 hover:bg-gray-200 hover:text-gray-600"
            )}
            title={listening ? "Stop recording" : "Voice input"}
          >
            {listening ? <MicOff size={16} /> : <Mic size={16} />}
          </button>

          {/* Send / Cancel */}
          {isStreaming ? (
            <button
              onClick={handleCancel}
              className="flex-shrink-0 px-3 py-2 rounded-xl bg-orange-100 text-orange-600 hover:bg-orange-200 text-xs font-medium transition-colors mb-0.5"
            >
              Stop
            </button>
          ) : (
            <button
              onClick={handleSend}
              disabled={!canSend}
              className={cn(
                "flex-shrink-0 p-2 rounded-xl transition-all mb-0.5",
                canSend
                  ? "bg-bis-600 text-white hover:bg-bis-700 shadow-sm"
                  : "text-gray-300 cursor-not-allowed"
              )}
              title="Send"
            >
              <Send size={16} />
            </button>
          )}
        </div>

        <p className="text-center text-xs text-gray-400 mt-2">
          Answers sourced from official BIS documents · Always verify critical decisions at{" "}
          <a href="https://www.bis.gov.in" target="_blank" rel="noopener noreferrer"
             className="text-bis-600 hover:underline">bis.gov.in</a>
        </p>
      </div>
    </div>
  );
}
