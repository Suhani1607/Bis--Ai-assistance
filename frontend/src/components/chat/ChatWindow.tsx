"use client";

import { useEffect, useRef, useState } from "react";
import { LogOut, UserCheck, ChevronDown } from "lucide-react";
import { useChatStore } from "@/store/chatStore";
import { useAuth } from "@/hooks/useAuth";
import { MessageBubble } from "./MessageBubble";
import { ChatInput } from "./ChatInput";
import { TypingIndicator } from "./TypingIndicator";
import { WelcomeScreen } from "./WelcomeScreen";
import { SwitchAccountDialog } from "./SwitchAccountDialog";
import { chat } from "@/lib/api";

export function ChatWindow() {
  const {
    activeConversationId, messages, setMessages,
    isStreaming, streamingContent, streamingCitations,
  } = useChatStore();
  const { user, logout } = useAuth();
  const [isSwitchOpen, setIsSwitchOpen] = useState(false);
  const [userMenuOpen, setUserMenuOpen] = useState(false);
  const bottomRef = useRef<HTMLDivElement>(null);
  const menuRef = useRef<HTMLDivElement>(null);

  // Close user dropdown menu when clicking outside
  useEffect(() => {
    function handleClickOutside(event: MouseEvent) {
      if (menuRef.current && !menuRef.current.contains(event.target as Node)) {
        setUserMenuOpen(false);
      }
    }
    document.addEventListener("mousedown", handleClickOutside);
    return () => document.removeEventListener("mousedown", handleClickOutside);
  }, []);

  // Load conversation messages when active conv changes
  useEffect(() => {
    if (!activeConversationId) {
      setMessages([]);
      return;
    }
    chat.getConversation(activeConversationId)
      .then((d) => setMessages(d.messages))
      .catch(console.error);
  }, [activeConversationId, setMessages]);

  // Auto-scroll
  useEffect(() => {
    bottomRef.current?.scrollIntoView({ behavior: "smooth" });
  }, [messages, streamingContent]);

  const isEmpty = messages.length === 0 && !isStreaming;
  const userInitial = user?.name ? user.name[0].toUpperCase() : "U";

  return (
    <div className="flex flex-col h-full relative">
      {/* Header */}
      <header className="flex items-center gap-3 px-6 py-3.5 border-b border-gray-200 bg-white shadow-xs z-10">
        <div className="w-8 h-8 rounded-full bg-gradient-to-br from-bis-600 to-navy-600 flex items-center justify-center text-white text-sm font-bold shadow-xs">
          B
        </div>
        <div>
          <h1 className="font-semibold text-gray-900 leading-none text-sm">BIS AI Assistant</h1>
          <p className="text-[11px] text-gray-500 mt-0.5">Bureau of Indian Standards ·</p>
        </div>

        {/* Right Section: Status & User Menu */}
        <div className="ml-auto flex items-center gap-3">
          <div className="hidden sm:flex items-center gap-1.5 px-2.5 py-1 rounded-full bg-emerald-50 border border-emerald-100">
            <span className="w-2 h-2 rounded-full bg-emerald-500 animate-pulse" />
            <span className="text-[11px] font-medium text-emerald-700">Online</span>
          </div>

          {/* User Profile Dropdown */}
          {user && (
            <div className="relative" ref={menuRef}>
              <button
                onClick={() => setUserMenuOpen(!userMenuOpen)}
                className="flex items-center gap-2 py-1.5 px-2.5 rounded-xl border border-gray-200 hover:border-gray-300 hover:bg-gray-50 transition-all text-left"
              >
                <div className="w-7 h-7 rounded-full bg-gradient-to-tr from-bis-600 to-orange-400 flex items-center justify-center text-white text-xs font-bold shadow-xs">
                  {userInitial}
                </div>
                <div className="hidden md:block">
                  <p className="text-xs font-semibold text-gray-800 leading-none">{user.name}</p>
                  <p className="text-[10px] text-gray-400 truncate max-w-[120px] mt-0.5">{user.email}</p>
                </div>
                <ChevronDown size={14} className="text-gray-400" />
              </button>

              {/* Dropdown Menu */}
              {userMenuOpen && (
                <div className="absolute right-0 mt-1.5 w-60 bg-white rounded-xl shadow-xl border border-gray-100 py-1.5 z-50 animate-fade-in">
                  <div className="px-3.5 py-2.5 border-b border-gray-100">
                    <p className="text-xs font-semibold text-gray-900 truncate">{user.name}</p>
                    <p className="text-[11px] text-gray-500 truncate">{user.email}</p>
                  </div>

                  <button
                    onClick={() => {
                      setUserMenuOpen(false);
                      setIsSwitchOpen(true);
                    }}
                    className="w-full flex items-center gap-2 px-3.5 py-2 text-xs font-medium text-gray-700 hover:bg-gray-50 transition-colors text-left"
                  >
                    <UserCheck size={14} className="text-gray-500" />
                    <span>Switch Account</span>
                  </button>

                  <div className="border-t border-gray-100 my-1" />

                  <button
                    onClick={() => {
                      setUserMenuOpen(false);
                      logout();
                    }}
                    className="w-full flex items-center gap-2 px-3.5 py-2 text-xs font-medium text-red-600 hover:bg-red-50 transition-colors text-left"
                  >
                    <LogOut size={14} />
                    <span>Sign Out</span>
                  </button>
                </div>
              )}
            </div>
          )}
        </div>
      </header>

      {/* Messages */}
      <div className="flex-1 overflow-y-auto px-4 py-6 space-y-4">
        {isEmpty && <WelcomeScreen />}

        {messages.map((msg) => (
          <MessageBubble key={msg.id} message={msg} />
        ))}

        {/* Streaming assistant message */}
        {isStreaming && (
          <MessageBubble
            message={{
              id: "__streaming__",
              conversationId: activeConversationId ?? "",
              role: "assistant",
              content: streamingContent,
              citations: streamingCitations,
              createdAt: new Date().toISOString(),
            }}
            isStreaming
          />
        )}

        {isStreaming && streamingContent === "" && <TypingIndicator />}

        <div ref={bottomRef} />
      </div>

      {/* Input */}
      <ChatInput />

      {/* Switch Account Modal */}
      <SwitchAccountDialog isOpen={isSwitchOpen} onClose={() => setIsSwitchOpen(false)} />
    </div>
  );
}
