"use client";

import { useEffect, useRef } from "react";
import { X, ExternalLink, FileText } from "lucide-react";
import { type Citation } from "@/types";
import { cn } from "@/lib/utils";

interface Props {
  citations: Citation[];
  onClose: () => void;
}

export function CitationPanel({ citations, onClose }: Props) {
  const panelRef = useRef<HTMLDivElement>(null);

  // Close on Escape
  useEffect(() => {
    const handler = (e: KeyboardEvent) => { if (e.key === "Escape") onClose(); };
    window.addEventListener("keydown", handler);
    return () => window.removeEventListener("keydown", handler);
  }, [onClose]);

  // Close on outside click
  useEffect(() => {
    const handler = (e: MouseEvent) => {
      if (panelRef.current && !panelRef.current.contains(e.target as Node)) onClose();
    };
    // slight delay so the open-click doesn't immediately close
    setTimeout(() => window.addEventListener("mousedown", handler), 100);
    return () => window.removeEventListener("mousedown", handler);
  }, [onClose]);

  const schemeLabel = (isNumber?: string): string => {
    if (!isNumber) return "BIS Document";
    if (isNumber.startsWith("IS 15820") || isNumber.includes("Hallmark")) return "Hallmarking";
    return "Indian Standard";
  };

  return (
    <>
      {/* Backdrop */}
      <div className="fixed inset-0 bg-black/20 z-40 animate-fade-in" />

      {/* Panel */}
      <div
        ref={panelRef}
        className="fixed right-0 top-0 h-full w-96 bg-white shadow-2xl z-50 flex flex-col animate-slide-up"
        style={{ animationName: "slideInRight" }}
      >
        {/* Header */}
        <div className="flex items-center justify-between px-5 py-4 border-b border-gray-200 bg-gray-50">
          <div className="flex items-center gap-2">
            <FileText size={16} className="text-bis-600" />
            <h2 className="font-semibold text-gray-900 text-sm">
              Sources & Citations
            </h2>
            <span className="text-xs bg-bis-100 text-bis-800 px-1.5 py-0.5 rounded-full font-medium">
              {citations.length}
            </span>
          </div>
          <button
            onClick={onClose}
            className="p-1.5 rounded-md text-gray-400 hover:bg-gray-200 hover:text-gray-600 transition-colors"
          >
            <X size={16} />
          </button>
        </div>

        {/* Citation list */}
        <div className="flex-1 overflow-y-auto p-4 space-y-3">
          {citations.map((c, i) => (
            <CitationCard key={c.chunkId} citation={c} index={i + 1} />
          ))}
        </div>

        {/* Footer */}
        <div className="px-5 py-3 border-t border-gray-200 bg-gray-50">
          <p className="text-xs text-gray-400 text-center">
            Sources retrieved from BIS official documents via semantic search
          </p>
        </div>
      </div>

      <style jsx>{`
        @keyframes slideInRight {
          from { transform: translateX(100%); opacity: 0; }
          to   { transform: translateX(0);    opacity: 1; }
        }
      `}</style>
    </>
  );
}

function CitationCard({ citation, index }: { citation: Citation; index: number }) {
  const score = citation.relevanceScore ? Math.round(citation.relevanceScore * 100) : null;

  return (
    <div className="rounded-xl border border-gray-200 bg-white p-4 hover:border-bis-300 transition-colors">
      {/* Index + IS number */}
      <div className="flex items-start justify-between gap-2 mb-2">
        <div className="flex items-center gap-2">
          <span className="flex-shrink-0 w-5 h-5 rounded-full bg-bis-600 text-white text-xs flex items-center justify-center font-bold">
            {index}
          </span>
          {citation.isNumber && (
            <span className="text-sm font-semibold text-bis-700">
              {citation.isNumber}
            </span>
          )}
        </div>
        {score !== null && (
          <span className={cn(
            "text-xs px-1.5 py-0.5 rounded font-medium",
            score > 80 ? "bg-green-100 text-green-700" :
            score > 60 ? "bg-yellow-100 text-yellow-700" :
                         "bg-gray-100 text-gray-600"
          )}>
            {score}% match
          </span>
        )}
      </div>

      {/* Clause ref */}
      {citation.clauseRef && (
        <p className="text-xs font-medium text-gray-500 mb-1">
          {citation.clauseRef}
          {citation.sectionTitle && ` — ${citation.sectionTitle}`}
        </p>
      )}

      {/* Excerpt */}
      {citation.excerpt && (
        <blockquote className="text-xs text-gray-600 leading-relaxed border-l-2 border-bis-200 pl-3 mt-2 italic">
          {citation.excerpt}
        </blockquote>
      )}

      {/* BIS link hint */}
      <a
        href={`https://www.bis.gov.in`}
        target="_blank"
        rel="noopener noreferrer"
        className="mt-2 inline-flex items-center gap-1 text-xs text-bis-600 hover:underline"
      >
        <ExternalLink size={11} />
        View on BIS portal
      </a>
    </div>
  );
}
