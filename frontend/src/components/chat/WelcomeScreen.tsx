"use client";

import { useChatStore } from "@/store/chatStore";

const SUGGESTIONS = [
  {
    icon: "📋",
    title: "Find applicable standard",
    prompt: "Which Indian Standard covers pressure cookers for household use?",
  },
  {
    icon: "🏭",
    title: "Get certified (ISI mark)",
    prompt: "How do I apply for BIS Scheme-I certification for my LED bulb manufacturing unit?",
  },
  {
    icon: "💍",
    title: "Gold hallmarking",
    prompt: "How does a jeweller register for BIS hallmarking of gold jewellery?",
  },
  {
    icon: "🧪",
    title: "Find a testing lab",
    prompt: "Which BIS-recognised labs can test LPG cylinders as per IS 3196?",
  },
  {
    icon: "🌐",
    title: "Foreign manufacturer",
    prompt: "What is the FMCS scheme and how can a foreign manufacturer export to India?",
  },
  {
    icon: "📱",
    title: "Electronics registration",
    prompt: "Does my Wi-Fi router need BIS CRS registration? What is the process?",
  },
];

export function WelcomeScreen() {
  const { lang } = useChatStore();

  // We just inject text into the ChatInput via a custom event
  const injectPrompt = (text: string) => {
    window.dispatchEvent(new CustomEvent("bis:inject-prompt", { detail: text }));
  };

  return (
    <div className="flex flex-col items-center justify-center py-12 px-4 animate-fade-in">
      {/* Logo */}
      <div className="w-16 h-16 rounded-2xl bg-gradient-to-br from-bis-600 to-orange-400 flex items-center justify-center text-white text-3xl font-bold shadow-lg mb-4">
        B
      </div>

      <h2 className="text-xl font-bold text-gray-900 mb-1">BIS AI Assistant</h2>
      <p className="text-sm text-gray-500 text-center max-w-md mb-8">
        Your guide to Indian Standards, BIS certification schemes, hallmarking, and more.
        Answers are sourced from official BIS documents with clause-level citations.
      </p>

      {/* Suggestion grid */}
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-3 w-full max-w-2xl">
        {SUGGESTIONS.map((s) => (
          <button
            key={s.title}
            onClick={() => injectPrompt(s.prompt)}
            className="flex items-start gap-3 p-4 rounded-xl border border-gray-200 bg-white hover:border-bis-300 hover:bg-orange-50 hover:shadow-sm transition-all text-left group"
          >
            <span className="text-xl flex-shrink-0">{s.icon}</span>
            <div>
              <p className="text-sm font-medium text-gray-800 group-hover:text-bis-700 transition-colors">
                {s.title}
              </p>
              <p className="text-xs text-gray-500 mt-0.5 line-clamp-2">{s.prompt}</p>
            </div>
          </button>
        ))}
      </div>

      <p className="mt-8 text-xs text-gray-400">
        {lang === "hi"
          ? "हिंदी में भी पूछ सकते हैं"
          : "You can also ask in Hindi — switch language in the input bar"}
      </p>
    </div>
  );
}
