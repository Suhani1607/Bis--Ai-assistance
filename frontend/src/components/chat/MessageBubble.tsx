"use client";

import { useState } from "react";
import ReactMarkdown from "react-markdown";
import remarkGfm from "remark-gfm";
import { ThumbsUp, ThumbsDown, Copy, CheckCheck, BookOpen } from "lucide-react";
import { type Message } from "@/types";
import { CitationPanel } from "./CitationPanel";
import { StandardsSuggest } from "./StandardsSuggest";
import { useChatStore } from "@/store/chatStore";
import { chat } from "@/lib/api";
import { cn } from "@/lib/utils";

interface Props {
  message: Message;
  isStreaming?: boolean;
}

export function MessageBubble({ message, isStreaming }: Props) {
  const isUser = message.role === "user";
  const { lang } = useChatStore();
  const [copied, setCopied]       = useState(false);
  const [feedback, setFeedback]   = useState<1 | -1 | null>(null);
  const [showCitations, setShowCitations] = useState(false);

  const displayContent =
    lang === "hi" && message.contentHi ? message.contentHi : message.content;

  const hasCitations = (message.citations?.length ?? 0) > 0;

  const handleCopy = async () => {
    await navigator.clipboard.writeText(message.content);
    setCopied(true);
    setTimeout(() => setCopied(false), 2000);
  };

  const handleFeedback = async (rating: 1 | -1) => {
    if (feedback !== null || message.id === "__streaming__") return;
    setFeedback(rating);
    try { await chat.feedback(message.id, rating); } catch { /* optimistic */ }
  };

  return (
    <div className={cn("flex gap-3 group animate-fade-in", isUser && "flex-row-reverse")}>
      {/* Avatar */}
      <div className={cn(
        "w-8 h-8 rounded-full flex-shrink-0 flex items-center justify-center text-white text-xs font-bold mt-1",
        isUser
          ? "bg-navy-600"
          : "bg-gradient-to-br from-bis-600 to-orange-500"
      )}>
        {isUser ? "U" : "B"}
      </div>

      <div className={cn("flex flex-col gap-1 max-w-[78%]", isUser && "items-end")}>
        {/* Bubble */}
        <div className={cn(
          "rounded-2xl px-4 py-3 text-sm leading-relaxed shadow-sm",
          isUser
            ? "bg-navy-600 text-white rounded-tr-sm"
            : "bg-white border border-gray-200 text-gray-800 rounded-tl-sm"
        )}>
          {isUser ? (
            <p className="whitespace-pre-wrap">{displayContent}</p>
          ) : (
            <div className="chat-prose">
              <ReactMarkdown remarkPlugins={[remarkGfm]}>
                {displayContent}
              </ReactMarkdown>

              {/* Streaming cursor */}
              {isStreaming && (
                <span className="inline-block w-0.5 h-4 bg-bis-600 ml-0.5 animate-pulse align-middle" />
              )}
            </div>
          )}
        </div>

        {/* Assistant action bar */}
        {!isUser && !isStreaming && (
          <div className="flex items-center gap-1 px-1 opacity-0 group-hover:opacity-100 transition-opacity">
            {/* Citations button */}
            {hasCitations && (
              <button
                onClick={() => setShowCitations(true)}
                className="flex items-center gap-1 px-2 py-1 rounded-md text-xs text-gray-500 hover:bg-gray-100 hover:text-bis-600 transition-colors"
              >
                <BookOpen size={13} />
                {message.citations!.length} source{message.citations!.length > 1 ? "s" : ""}
              </button>
            )}

            {/* Copy */}
            <button
              onClick={handleCopy}
              className="p-1.5 rounded-md text-gray-400 hover:bg-gray-100 hover:text-gray-600 transition-colors"
              title="Copy"
            >
              {copied ? <CheckCheck size={13} className="text-green-500" /> : <Copy size={13} />}
            </button>

            {/* Thumbs up/down */}
            <button
              onClick={() => handleFeedback(1)}
              className={cn(
                "p-1.5 rounded-md transition-colors",
                feedback === 1
                  ? "text-green-600 bg-green-50"
                  : "text-gray-400 hover:bg-gray-100 hover:text-green-600"
              )}
              title="Helpful"
            >
              <ThumbsUp size={13} />
            </button>
            <button
              onClick={() => handleFeedback(-1)}
              className={cn(
                "p-1.5 rounded-md transition-colors",
                feedback === -1
                  ? "text-red-500 bg-red-50"
                  : "text-gray-400 hover:bg-gray-100 hover:text-red-500"
              )}
              title="Not helpful"
            >
              <ThumbsDown size={13} />
            </button>

            {/* Meta: latency */}
            {message.latencyMs && (
              <span className="ml-1 text-xs text-gray-300">{message.latencyMs}ms</span>
            )}
          </div>
        )}

        {/* Inline citation badges (quick preview) */}
        {!isUser && hasCitations && !isStreaming && (
          <div className="flex flex-wrap gap-1 px-1 mt-0.5">
            {message.citations!.slice(0, 4).map((c, i) => (
              <button
                key={c.chunkId}
                onClick={() => setShowCitations(true)}
                className="citation-badge"
              >
                [{i + 1}] {c.isNumber ?? "BIS Doc"}
                {c.clauseRef && ` · ${c.clauseRef}`}
              </button>
            ))}
            {message.citations!.length > 4 && (
              <button
                onClick={() => setShowCitations(true)}
                className="citation-badge bg-gray-100 text-gray-600 border-gray-200"
              >
                +{message.citations!.length - 4} more
              </button>
            )}
          </div>
        )}
      </div>

      {/* IS Standards suggest — appears below assistant bubble when IS numbers detected */}
      {!isUser && !isStreaming && message.content && (
        <div className="max-w-[78%] px-11">
          <StandardsSuggest content={message.content} />
        </div>
      )}

      {/* Citation side panel */}
      {showCitations && (
        <CitationPanel
          citations={message.citations ?? []}
          onClose={() => setShowCitations(false)}
        />
      )}
    </div>
  );
}
