// TypingIndicator.tsx
export function TypingIndicator() {
  return (
    <div className="flex gap-3 animate-fade-in">
      <div className="w-8 h-8 rounded-full flex-shrink-0 bg-gradient-to-br from-bis-600 to-orange-500 flex items-center justify-center text-white text-xs font-bold mt-1">
        B
      </div>
      <div className="bg-white border border-gray-200 rounded-2xl rounded-tl-sm px-4 py-3 shadow-sm">
        <div className="flex items-center gap-1.5">
          {[0, 1, 2].map((i) => (
            <span
              key={i}
              className="typing-dot w-2 h-2 rounded-full bg-gray-400 animate-typing-dot"
              style={{ animationDelay: `${i * 0.15}s` }}
            />
          ))}
        </div>
      </div>
    </div>
  );
}
