"use client";

import { useState } from "react";
import { PlusCircle, Search, MessageSquare, Trash2, ChevronLeft, ChevronRight, LogOut } from "lucide-react";
import { useChatStore } from "@/store/chatStore";
import { useAuth } from "@/hooks/useAuth";
import { chat } from "@/lib/api";
import { cn } from "@/lib/utils";
import { formatDistanceToNow } from "@/lib/dateUtils";

export function ConversationSidebar() {
  const [collapsed, setCollapsed] = useState(false);
  const [searchQ, setSearchQ]     = useState("");

  const {
    conversations, setConversations,
    activeConversationId, setActiveConversation,
    setMessages,
  } = useChatStore();
  const { user, logout } = useAuth();

  const filtered = conversations.filter((c) =>
    (c.title ?? "New conversation").toLowerCase().includes(searchQ.toLowerCase())
  );

  const handleNew = () => {
    setActiveConversation(null);
    setMessages([]);
  };

  const handleSelect = async (id: string) => {
    if (id === activeConversationId) return;
    setActiveConversation(id);
  };

  const handleDelete = async (e: React.MouseEvent, id: string) => {
    e.stopPropagation();
    await chat.archiveConversation(id);
    const updated = conversations.filter((c) => c.id !== id);
    setConversations(updated);
    if (activeConversationId === id) {
      setActiveConversation(null);
      setMessages([]);
    }
  };

  return (
    <aside className={cn(
      "flex flex-col h-full bg-white border-r border-gray-200 transition-all duration-300",
      collapsed ? "w-14" : "w-64"
    )}>
      {/* Logo header */}
      <div className="flex items-center gap-2 px-3 py-4 border-b border-gray-100">
        {!collapsed && (
          <div className="flex items-center gap-2 flex-1 min-w-0">
            <div className="w-7 h-7 rounded-md bg-bis-600 flex items-center justify-center text-white font-bold text-sm flex-shrink-0">
              B
            </div>
            <span className="font-bold text-gray-900 text-sm truncate">BIS Assistant</span>
          </div>
        )}
        <button
          onClick={() => setCollapsed(!collapsed)}
          className="p-1 rounded-md text-gray-400 hover:bg-gray-100 hover:text-gray-600 transition-colors ml-auto"
        >
          {collapsed ? <ChevronRight size={16} /> : <ChevronLeft size={16} />}
        </button>
      </div>

      {/* New chat button */}
      <div className="px-2 pt-3 pb-2">
        <button
          onClick={handleNew}
          className={cn(
            "w-full flex items-center gap-2 px-3 py-2 rounded-xl text-sm font-medium transition-colors",
            "bg-bis-600 text-white hover:bg-bis-700 shadow-sm",
            collapsed && "justify-center px-2"
          )}
        >
          <PlusCircle size={16} />
          {!collapsed && "New conversation"}
        </button>
      </div>

      {/* Search */}
      {!collapsed && (
        <div className="px-2 pb-2">
          <div className="flex items-center gap-2 px-3 py-2 rounded-lg bg-gray-50 border border-gray-200">
            <Search size={13} className="text-gray-400 flex-shrink-0" />
            <input
              type="text"
              value={searchQ}
              onChange={(e) => setSearchQ(e.target.value)}
              placeholder="Search chats…"
              className="bg-transparent outline-none text-xs text-gray-700 placeholder-gray-400 w-full"
            />
          </div>
        </div>
      )}

      {/* Conversation list */}
      <div className="flex-1 overflow-y-auto px-2 pb-4 space-y-0.5">
        {!collapsed && filtered.length === 0 && (
          <p className="text-xs text-gray-400 text-center py-8 px-4">
            {searchQ ? "No conversations match" : "Start a new conversation above"}
          </p>
        )}

        {filtered.map((conv) => (
          <button
            key={conv.id}
            onClick={() => handleSelect(conv.id)}
            className={cn(
              "w-full flex items-center gap-2 px-3 py-2.5 rounded-xl text-left transition-colors group",
              activeConversationId === conv.id
                ? "bg-orange-50 border border-bis-200 text-bis-800"
                : "text-gray-700 hover:bg-gray-100"
            )}
          >
            <MessageSquare
              size={14}
              className={cn(
                "flex-shrink-0",
                activeConversationId === conv.id ? "text-bis-600" : "text-gray-400"
              )}
            />

            {!collapsed && (
              <>
                <div className="flex-1 min-w-0">
                  <p className="text-xs font-medium truncate">
                    {conv.title ?? "New conversation"}
                  </p>
                  <p className="text-xs text-gray-400 truncate">
                    {formatDistanceToNow(conv.updatedAt)} · {conv.messageCount} msgs
                  </p>
                </div>

                {/* Delete on hover */}
                <button
                  onClick={(e) => handleDelete(e, conv.id)}
                  className="opacity-0 group-hover:opacity-100 p-1 rounded-md text-gray-400 hover:text-red-500 hover:bg-red-50 transition-all"
                >
                  <Trash2 size={12} />
                </button>
              </>
            )}
          </button>
        ))}
      </div>

      {/* Bottom: User Session & BIS info */}
      <div className="border-t border-gray-100 bg-gray-50/50">
        {user && (
          <div className={cn("p-2.5 flex items-center gap-2", collapsed && "justify-center")}>
            <div className="w-7 h-7 rounded-full bg-gradient-to-tr from-bis-600 to-orange-400 flex items-center justify-center text-white text-xs font-bold shadow-2xs flex-shrink-0">
              {user.name ? user.name[0].toUpperCase() : "U"}
            </div>
            {!collapsed && (
              <div className="flex-1 min-w-0">
                <p className="text-xs font-semibold text-gray-800 truncate leading-tight">{user.name}</p>
                <p className="text-[10px] text-gray-400 truncate">{user.email}</p>
              </div>
            )}
            <button
              onClick={() => logout()}
              title="Sign Out"
              className="p-1.5 rounded-lg text-gray-400 hover:text-red-600 hover:bg-red-50 transition-colors flex-shrink-0"
            >
              <LogOut size={14} />
            </button>
          </div>
        )}

        {!collapsed && (
          <div className="px-3 py-2 border-t border-gray-100/60">
            <p className="text-[11px] text-gray-400 leading-relaxed">
              Powered by BIS official documents ·{" "}
              <a href="https://www.bis.gov.in" target="_blank" rel="noopener noreferrer"
                 className="text-bis-600 hover:underline">bis.gov.in</a>
            </p>
          </div>
        )}
      </div>
    </aside>
  );
}
