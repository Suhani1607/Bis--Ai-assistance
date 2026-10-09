"use client";

import { useState, useCallback, useRef } from "react";
import { useRouter } from "next/navigation";
import Link from "next/link";
import {
  Search, X, ExternalLink, BookOpen,
  CheckCircle, AlertCircle, ChevronRight
} from "lucide-react";
import { standards as standardsApi } from "@/lib/api";
import { cn } from "@/lib/utils";
import { useAuth } from "@/hooks/useAuth";

interface ISResult {
  isNumber: string;
  title: string;
  titleHi?: string;
  year?: number;
  division?: string;
  certScheme?: string;
  isMandatory?: boolean;
  status?: string;
  replaces?: string;
  replacedBy?: string;
}

const SCHEME_LABELS: Record<string, { label: string; color: string }> = {
  SCHEME_I:    { label: "ISI Mark",   color: "bg-orange-100 text-orange-700 border-orange-200" },
  CRS:         { label: "CRS",        color: "bg-blue-100 text-blue-700 border-blue-200"       },
  FMCS:        { label: "FMCS",       color: "bg-purple-100 text-purple-700 border-purple-200" },
  HALLMARKING: { label: "Hallmark",   color: "bg-yellow-100 text-yellow-700 border-yellow-200" },
  ECO_MARK:    { label: "Eco Mark",   color: "bg-green-100 text-green-700 border-green-200"    },
  NONE:        { label: "Voluntary",  color: "bg-gray-100 text-gray-600 border-gray-200"       },
};

export default function StandardsPage() {
  const router = useRouter();
  const { user } = useAuth();

  const [query, setQuery]           = useState("");
  const [results, setResults]       = useState<ISResult[]>([]);
  const [loading, setLoading]       = useState(false);
  const [searched, setSearched]     = useState(false);
  const [selected, setSelected]     = useState<ISResult | null>(null);
  const debounceRef = useRef<NodeJS.Timeout | null>(null);

  const doSearch = useCallback(async (q: string) => {
    if (!q.trim()) { setResults([]); setSearched(false); return; }
    setLoading(true);
    try {
      const data = await standardsApi.search(q) as ISResult[];
      setResults(data);
      setSearched(true);
    } catch (e) {
      setResults([]);
    } finally {
      setLoading(false);
    }
  }, []);

  const handleInput = (e: React.ChangeEvent<HTMLInputElement>) => {
    const val = e.target.value;
    setQuery(val);
    if (debounceRef.current) clearTimeout(debounceRef.current);
    debounceRef.current = setTimeout(() => doSearch(val), 400);
  };

  const handleAskAI = (is: ISResult) => {
    router.push(`/chat?q=${encodeURIComponent(
      `Tell me about ${is.isNumber}: ${is.title}. What does it cover and what certification scheme applies?`
    )}`);
  };

  return (
    <div className="min-h-screen bg-gray-50">
      {/* Header */}
      <header className="bg-white border-b border-gray-200 sticky top-0 z-10">
        <div className="max-w-5xl mx-auto px-4 py-4 flex items-center gap-4">
          <Link href="/chat" className="flex items-center gap-2 text-bis-600 hover:text-bis-700">
            <div className="w-8 h-8 rounded-lg bg-bis-600 text-white flex items-center justify-center font-bold text-sm">B</div>
            <span className="font-semibold text-sm hidden sm:block">BIS Assistant</span>
          </Link>
          <ChevronRight size={14} className="text-gray-400" />
          <h1 className="font-semibold text-gray-900 text-sm">IS Standards Catalogue</h1>
          <div className="ml-auto">
            <Link href="/chat"
              className="text-sm text-bis-600 hover:underline flex items-center gap-1">
              ← Back to chat
            </Link>
          </div>
        </div>
      </header>

      <div className="max-w-5xl mx-auto px-4 py-8 space-y-6">
        {/* Hero + search */}
        <div className="text-center space-y-4">
          <h2 className="text-2xl font-bold text-gray-900">Search Indian Standards</h2>
          <p className="text-sm text-gray-500 max-w-lg mx-auto">
            Search by product name, IS number, or keyword to find applicable
            Bureau of Indian Standards specifications.
          </p>

          {/* Search bar */}
          <div className="relative max-w-xl mx-auto">
            <Search size={16} className="absolute left-3.5 top-1/2 -translate-y-1/2 text-gray-400" />
            <input
              type="text"
              value={query}
              onChange={handleInput}
              placeholder="e.g. pressure cooker, IS 2902, LED bulb, LPG cylinder…"
              className="w-full pl-10 pr-10 py-3 rounded-2xl border border-gray-200 bg-white
                         text-sm outline-none focus:border-bis-400 focus:ring-2 focus:ring-bis-100
                         shadow-sm transition-all"
            />
            {query && (
              <button onClick={() => { setQuery(""); setResults([]); setSearched(false); }}
                className="absolute right-3.5 top-1/2 -translate-y-1/2 text-gray-400 hover:text-gray-600">
                <X size={15} />
              </button>
            )}
          </div>
        </div>

        {/* Quick-access chips */}
        {!searched && (
          <div className="flex flex-wrap justify-center gap-2">
            {["LED bulb","LPG cylinder","pressure cooker","gold hallmark","steel TMT bar","PVC pipe","solar inverter"].map(chip => (
              <button key={chip}
                onClick={() => { setQuery(chip); doSearch(chip); }}
                className="px-3 py-1.5 rounded-full text-xs border border-gray-200 bg-white
                           text-gray-600 hover:border-bis-300 hover:text-bis-700 transition-colors">
                {chip}
              </button>
            ))}
          </div>
        )}

        {/* Loading */}
        {loading && (
          <div className="flex justify-center py-12">
            <span className="w-6 h-6 border-2 border-bis-600 border-t-transparent rounded-full animate-spin" />
          </div>
        )}

        {/* No results */}
        {searched && !loading && results.length === 0 && (
          <div className="text-center py-16">
            <BookOpen size={40} className="mx-auto text-gray-300 mb-3" />
            <p className="text-gray-500 text-sm">No standards found for &quot;{query}&quot;</p>
            <p className="text-gray-400 text-xs mt-1">
              Try the{" "}
              <button onClick={() => handleAskAI({ isNumber: "", title: query } as ISResult)}
                className="text-bis-600 underline">AI assistant</button>{" "}
              for broader product guidance.
            </p>
          </div>
        )}

        {/* Results grid */}
        {results.length > 0 && !loading && (
          <>
            <p className="text-xs text-gray-500">{results.length} standard{results.length > 1 ? "s" : ""} found</p>
            <div className="grid gap-3">
              {results.map((is) => (
                <ISCard key={is.isNumber} is={is}
                  onSelect={() => setSelected(is)}
                  onAskAI={() => handleAskAI(is)} />
              ))}
            </div>
          </>
        )}
      </div>

      {/* Detail modal */}
      {selected && (
        <ISDetailModal is={selected}
          onClose={() => setSelected(null)}
          onAskAI={() => { setSelected(null); handleAskAI(selected); }} />
      )}
    </div>
  );
}

function ISCard({ is, onSelect, onAskAI }: {
  is: ISResult; onSelect: () => void; onAskAI: () => void;
}) {
  const scheme = SCHEME_LABELS[is.certScheme ?? "NONE"] ?? SCHEME_LABELS.NONE;
  return (
    <div
      onClick={onSelect}
      className="bg-white rounded-2xl border border-gray-200 p-4 cursor-pointer
                 hover:border-bis-300 hover:shadow-sm transition-all group"
    >
      <div className="flex items-start gap-3">
        {/* IS badge */}
        <div className="flex-shrink-0 px-2.5 py-1 rounded-lg bg-bis-50 border border-bis-200 text-bis-700 font-mono font-semibold text-xs">
          {is.isNumber}
        </div>

        <div className="flex-1 min-w-0">
          <h3 className="text-sm font-medium text-gray-900 group-hover:text-bis-700 transition-colors">
            {is.title}
          </h3>
          <div className="flex flex-wrap items-center gap-2 mt-1.5">
            {is.year && <span className="text-xs text-gray-400">{is.year}</span>}
            {is.division && <span className="text-xs text-gray-400">· {is.division}</span>}

            {/* Cert scheme */}
            <span className={cn("text-xs px-2 py-0.5 rounded-full border font-medium", scheme.color)}>
              {scheme.label}
            </span>

            {/* Mandatory chip */}
            {is.isMandatory && (
              <span className="flex items-center gap-1 text-xs text-red-600 font-medium">
                <AlertCircle size={11} /> Mandatory
              </span>
            )}

            {/* Status */}
            {is.status === "SUPERSEDED" && (
              <span className="text-xs text-amber-600">⚠ Superseded by {is.replacedBy}</span>
            )}
          </div>
        </div>

        {/* Actions */}
        <div className="flex-shrink-0 flex gap-2 opacity-0 group-hover:opacity-100 transition-opacity">
          <button onClick={(e) => { e.stopPropagation(); onAskAI(); }}
            className="px-2.5 py-1.5 rounded-lg text-xs bg-bis-600 text-white hover:bg-bis-700 transition-colors whitespace-nowrap">
            Ask AI
          </button>
        </div>
      </div>
    </div>
  );
}

function ISDetailModal({ is, onClose, onAskAI }: {
  is: ISResult; onClose: () => void; onAskAI: () => void;
}) {
  const scheme = SCHEME_LABELS[is.certScheme ?? "NONE"] ?? SCHEME_LABELS.NONE;

  return (
    <>
      <div className="fixed inset-0 bg-black/30 z-40 animate-fade-in" onClick={onClose} />
      <div className="fixed inset-x-4 top-1/2 -translate-y-1/2 max-w-lg mx-auto bg-white
                      rounded-2xl shadow-2xl z-50 p-6 animate-slide-up">
        {/* Header */}
        <div className="flex items-start justify-between gap-3 mb-5">
          <div>
            <span className="inline-block px-2.5 py-1 rounded-lg bg-bis-50 border border-bis-200
                             text-bis-700 font-mono font-bold text-sm mb-2">
              {is.isNumber}
            </span>
            <h2 className="text-base font-semibold text-gray-900 leading-snug">{is.title}</h2>
            {is.titleHi && <p className="text-sm text-gray-500 mt-0.5">{is.titleHi}</p>}
          </div>
          <button onClick={onClose}
            className="p-1.5 rounded-lg text-gray-400 hover:bg-gray-100 flex-shrink-0">
            <X size={16} />
          </button>
        </div>

        {/* Details grid */}
        <div className="grid grid-cols-2 gap-3 mb-5">
          <Detail label="Year"         value={is.year?.toString()} />
          <Detail label="Division"     value={is.division} />
          <Detail label="Certification" value={
            <span className={cn("text-xs px-2 py-0.5 rounded-full border font-medium", scheme.color)}>
              {scheme.label}
            </span>
          } />
          <Detail label="Mandatory"    value={
            is.isMandatory
              ? <span className="flex items-center gap-1 text-red-600 text-xs"><AlertCircle size={12}/> Yes</span>
              : <span className="flex items-center gap-1 text-green-600 text-xs"><CheckCircle size={12}/> No</span>
          } />
          {is.replaces   && <Detail label="Replaces"    value={is.replaces} />}
          {is.replacedBy && <Detail label="Replaced by" value={is.replacedBy} />}
        </div>

        {/* Actions */}
        <div className="flex gap-2">
          <button onClick={onAskAI}
            className="flex-1 py-2.5 rounded-xl bg-bis-600 text-white text-sm font-medium
                       hover:bg-bis-700 transition-colors">
            Ask AI about this standard
          </button>
          <a href={`https://www.bis.gov.in`} target="_blank" rel="noopener noreferrer"
            className="px-4 py-2.5 rounded-xl border border-gray-200 text-gray-600 text-sm
                       hover:bg-gray-50 transition-colors flex items-center gap-1.5">
            <ExternalLink size={14} /> BIS
          </a>
        </div>
      </div>
    </>
  );
}

function Detail({ label, value }: { label: string; value?: React.ReactNode }) {
  if (!value) return null;
  return (
    <div>
      <p className="text-xs text-gray-400 mb-0.5">{label}</p>
      <div className="text-sm text-gray-800">{value}</div>
    </div>
  );
}
