"use client";

import { useEffect, useState } from "react";
import { BookOpen, ExternalLink, ArrowRight } from "lucide-react";
import Link from "next/link";

interface Props {
  content: string;  // assistant message content to scan for IS numbers
}

interface ISHit {
  isNumber: string;
  title?: string;
}

// Regex to find IS numbers mentioned in text
const IS_RE = /\bIS\s+(\d{3,6}(?::\d{4})?)\b/gi;

function extractISNumbers(text: string): string[] {
  const matches = [...text.matchAll(IS_RE)];
  const nums = matches.map((m) => `IS ${m[1]}`);
  return [...new Set(nums)].slice(0, 3); // max 3 distinct IS mentions
}

export function StandardsSuggest({ content }: Props) {
  const [hits, setHits] = useState<ISHit[]>([]);

  useEffect(() => {
    const nums = extractISNumbers(content);
    if (nums.length === 0) return;
    setHits(nums.map((n) => ({ isNumber: n })));
  }, [content]);

  if (hits.length === 0) return null;

  return (
    <div className="mt-3 space-y-1.5">
      <p className="text-xs text-gray-400 flex items-center gap-1">
        <BookOpen size={11} /> Standards mentioned
      </p>
      {hits.map((hit) => (
        <div key={hit.isNumber}
          className="flex items-center justify-between px-3 py-2 rounded-xl
                     bg-orange-50 border border-orange-100 text-sm">
          <div className="flex items-center gap-2">
            <span className="font-mono font-semibold text-bis-700 text-xs">
              {hit.isNumber}
            </span>
            {hit.title && (
              <span className="text-gray-600 text-xs truncate max-w-[200px]">{hit.title}</span>
            )}
          </div>
          <div className="flex gap-2 flex-shrink-0">
            <Link
              href={`/standards?q=${encodeURIComponent(hit.isNumber)}`}
              className="flex items-center gap-1 text-xs text-bis-600 hover:underline">
              Details <ArrowRight size={10} />
            </Link>
            <a href="https://www.bis.gov.in" target="_blank" rel="noopener noreferrer"
              className="flex items-center gap-1 text-xs text-gray-400 hover:text-gray-600">
              <ExternalLink size={10} />
            </a>
          </div>
        </div>
      ))}
    </div>
  );
}
