"use client";

import { useEffect, useState } from "react";
import { BookOpen, ExternalLink, ArrowRight } from "lucide-react";
import Link from "next/link";
import { standards } from "@/lib/api";

interface Props {
  content: string; // assistant message content to scan for IS numbers
}

interface ISHit {
  isNumber: string;
  title?: string;
}

// Regex to capture IS numbers (e.g. IS 2347, IS 2347:2017, IS 302-2-3:2007)
const IS_RE = /\bIS\s+(\d{3,6}(?:[-/]\d+(?:[-/]\d+)?)?(?::\d{4})?)\b/gi;

// Module-level in-memory cache across component mounts
const catalogueCache = new Map<string, ISHit | null>();

function extractUniqueBaseIS(text: string): { full: string; base: string }[] {
  const matches = [...text.matchAll(IS_RE)];
  const seenBases = new Set<string>();
  const results: { full: string; base: string }[] = [];

  for (const m of matches) {
    const full = `IS ${m[1]}`.toUpperCase();
    const base = full.split(":")[0].trim();
    if (!seenBases.has(base)) {
      seenBases.add(base);
      results.push({ full, base });
    }
    if (results.length >= 3) break; // limit to 3 distinct standards per message
  }

  return results;
}

export function StandardsSuggest({ content }: Props) {
  const [hits, setHits] = useState<ISHit[]>([]);

  useEffect(() => {
    let isMounted = true;
    const candidates = extractUniqueBaseIS(content);
    if (candidates.length === 0) {
      setHits([]);
      return;
    }

    Promise.all(
      candidates.map(async ({ full, base }) => {
        if (catalogueCache.has(base)) {
          return catalogueCache.get(base)!;
        }

        try {
          const results = await standards.search(base);
          if (Array.isArray(results) && results.length > 0) {
            const exact = results.find(
              (r) =>
                r.isNumber.toUpperCase() === full ||
                r.isNumber.toUpperCase() === base ||
                full.startsWith(r.isNumber.toUpperCase()) ||
                r.isNumber.toUpperCase().startsWith(base)
            );
            const match = exact || results[0];
            const hit: ISHit = {
              isNumber: match.isNumber || base,
              title: match.title,
            };
            catalogueCache.set(base, hit);
            return hit;
          }
        } catch {
          // Ignore network errors
        }

        catalogueCache.set(base, null);
        return null;
      })
    ).then((resolved) => {
      if (isMounted) {
        setHits(resolved.filter((h): h is ISHit => h !== null));
      }
    });

    return () => {
      isMounted = false;
    };
  }, [content]);

  if (hits.length === 0) return null;

  return (
    <div className="mt-3 space-y-1.5">
      <p className="text-xs text-gray-400 flex items-center gap-1">
        <BookOpen size={11} /> Standards mentioned
      </p>
      {hits.map((hit) => (
        <div
          key={hit.isNumber}
          className="flex items-center justify-between px-3 py-2 rounded-xl
                     bg-orange-50 border border-orange-100 text-sm"
        >
          <div className="flex items-center gap-2 min-w-0 flex-1">
            <span className="font-mono font-semibold text-bis-700 text-xs shrink-0">
              {hit.isNumber}
            </span>
            {hit.title && (
              <span
                title={hit.title}
                className="text-gray-600 text-xs truncate flex-1 min-w-0"
              >
                {hit.title}
              </span>
            )}
          </div>
          <div className="flex items-center gap-2 flex-shrink-0 ml-3">
            <Link
              href={`/standards?q=${encodeURIComponent(hit.isNumber)}`}
              className="flex items-center gap-1 text-xs text-bis-600 hover:underline font-medium"
            >
              Details <ArrowRight size={10} />
            </Link>
            <a
              href="https://www.bis.gov.in"
              target="_blank"
              rel="noopener noreferrer"
              aria-label={`Official BIS portal for ${hit.isNumber}`}
              className="flex items-center text-gray-400 hover:text-gray-600 p-0.5"
            >
              <ExternalLink size={11} />
            </a>
          </div>
        </div>
      ))}
    </div>
  );
}
