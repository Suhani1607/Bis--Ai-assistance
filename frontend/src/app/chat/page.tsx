"use client";

import { useEffect } from "react";
import { ConversationSidebar } from "@/components/chat/ConversationSidebar";
import { ChatWindow } from "@/components/chat/ChatWindow";
import { useChatStore } from "@/store/chatStore";
import { chat } from "@/lib/api";

export default function ChatPage() {
  const { setConversations } = useChatStore();

  useEffect(() => {
    chat.listConversations().then(setConversations).catch(console.error);
  }, [setConversations]);

  return (
    <div className="flex h-screen bg-gray-50">
      <ConversationSidebar />
      <main className="flex-1 flex flex-col min-w-0">
        <ChatWindow />
      </main>
    </div>
  );
}
