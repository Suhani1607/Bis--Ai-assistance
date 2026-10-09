"use client";

import { useCallback } from "react";
import { useRouter } from "next/navigation";
import useSWR from "swr";
import Cookies from "js-cookie";
import { setAccessToken } from "@/lib/api";
import type { User } from "@/types";

const BASE = process.env.NEXT_PUBLIC_API_URL ?? "/api/v1";

// SWR fetcher — returns null (not throws) when unauthenticated so
// the hook gracefully handles logged-out state without error boundaries.
async function fetchMe(): Promise<User | null> {
  const token = Cookies.get("access_token");
  if (!token) return null;

  const res = await fetch(`${BASE}/auth/me`, {
    headers: { Authorization: `Bearer ${token}` },
  });

  if (res.status === 401) {
    setAccessToken(null);
    Cookies.remove("access_token", { path: "/" });
    Cookies.remove("refresh_token", { path: "/" });
    return null;
  }
  if (!res.ok) return null;
  return res.json();
}

interface UseAuthReturn {
  user: User | null;
  isLoading: boolean;
  isAuthenticated: boolean;
  logout: () => Promise<void>;
}

export function useAuth(): UseAuthReturn {
  const router = useRouter();

  const { data: user, isLoading, mutate } = useSWR<User | null>(
    "auth/me",
    fetchMe,
    {
      revalidateOnFocus: false,
      shouldRetryOnError: false,
      dedupingInterval: 60_000,       // re-check at most once per minute
    }
  );

  const logout = useCallback(async () => {
    const refreshToken = Cookies.get("refresh_token");
    const token        = Cookies.get("access_token");

    // Call backend to revoke refresh token (best-effort)
    if (refreshToken && token) {
      try {
        await fetch(`${BASE}/auth/logout`, {
          method: "POST",
          headers: {
            "Content-Type": "application/json",
            Authorization: `Bearer ${token}`,
          },
          body: JSON.stringify({ refreshToken }),
        });
      } catch { /* ignore — we clear cookies regardless */ }
    }

    setAccessToken(null);
    Cookies.remove("access_token", { path: "/" });
    Cookies.remove("refresh_token", { path: "/" });

    // Clear SWR cache for this key
    await mutate(null, { revalidate: false });

    window.location.href = "/auth/login";
  }, [mutate]);

  return {
    user:            user ?? null,
    isLoading,
    isAuthenticated: !!user,
    logout,
  };
}
