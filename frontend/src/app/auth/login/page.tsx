"use client";

import { useState, Suspense } from "react";
import { useSearchParams } from "next/navigation";
import Link from "next/link";
import Cookies from "js-cookie";
import { Eye, EyeOff, LogIn, ShieldAlert, ArrowRight, LogOut, CheckCircle2 } from "lucide-react";
import { auth, setAccessToken } from "@/lib/api";
import { useAuth } from "@/hooks/useAuth";
import { cn } from "@/lib/utils";

function LoginContent() {
  const searchParams = useSearchParams();
  const from = searchParams.get("from") || "/chat";

  const { user, logout } = useAuth();
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [showPw, setShowPw] = useState(false);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");

  const doLogin = async (loginEmail: string, loginPassword: string) => {
    setError("");
    setLoading(true);
    try {
      const resp = await auth.login(loginEmail, loginPassword);
      setAccessToken(resp.accessToken);
      Cookies.set("access_token", resp.accessToken, { expires: 1, sameSite: "lax", path: "/" });
      Cookies.set("refresh_token", resp.refreshToken, { expires: 30, sameSite: "lax", path: "/" });
      window.location.href = from;
    } catch (err: any) {
      setError(err.message || "Invalid credentials. Please try again.");
    } finally {
      setLoading(false);
    }
  };

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    if (!email || !password) {
      setError("Please enter both email and password.");
      return;
    }
    doLogin(email, password);
  };

  return (
    <div className="min-h-screen flex items-center justify-center bg-gradient-to-br from-orange-50 via-white to-blue-50 p-4">
      <div className="w-full max-w-md">
        <div className="bg-white rounded-2xl shadow-xl border border-gray-100 p-8">
          {/* Logo & Header */}
          <div className="flex flex-col items-center mb-6">
            <div className="w-14 h-14 rounded-2xl bg-gradient-to-br from-bis-600 to-orange-400 flex items-center justify-center text-white text-2xl font-bold shadow-md mb-3">
              B
            </div>
            <h1 className="text-xl font-bold text-gray-900">BIS AI Assistant</h1>
            <p className="text-sm text-gray-500 mt-1">Sign in to your account</p>
          </div>

          {/* Active Session Notification if already logged in */}
          {user && (
            <div className="mb-5 p-3.5 rounded-xl bg-orange-50/80 border border-orange-200 text-xs">
              <div className="flex items-center gap-2 text-orange-900 font-semibold mb-1">
                <CheckCircle2 size={14} className="text-orange-600" />
                <span>Currently signed in as {user.name}</span>
              </div>
              <p className="text-gray-600 mb-2 truncate">{user.email}</p>
              <div className="flex items-center gap-2">
                <button
                  type="button"
                  onClick={() => (window.location.href = from)}
                  className="flex-1 py-1.5 px-2 rounded-lg bg-bis-600 text-white font-medium hover:bg-bis-700 flex items-center justify-center gap-1 transition-colors"
                >
                  <span>Continue to Chat</span>
                  <ArrowRight size={13} />
                </button>
                <button
                  type="button"
                  onClick={() => logout()}
                  className="py-1.5 px-2.5 rounded-lg border border-gray-200 text-gray-600 hover:text-red-600 hover:bg-red-50 font-medium flex items-center gap-1 transition-colors"
                >
                  <LogOut size={13} />
                  <span>Sign Out</span>
                </button>
              </div>
            </div>
          )}

          {/* Direct Login Form */}
          <form onSubmit={handleSubmit} className="space-y-4">
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1">Email</label>
              <input
                type="email"
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                required
                placeholder="you@example.com"
                className="w-full px-3 py-2.5 rounded-xl border border-gray-200 text-sm outline-none focus:border-bis-400 focus:ring-2 focus:ring-bis-100 transition-colors"
              />
            </div>

            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1">Password</label>
              <div className="relative">
                <input
                  type={showPw ? "text" : "password"}
                  value={password}
                  onChange={(e) => setPassword(e.target.value)}
                  required
                  placeholder="••••••••"
                  className="w-full px-3 py-2.5 pr-10 rounded-xl border border-gray-200 text-sm outline-none focus:border-bis-400 focus:ring-2 focus:ring-bis-100 transition-colors"
                />
                <button
                  type="button"
                  onClick={() => setShowPw(!showPw)}
                  className="absolute right-3 top-1/2 -translate-y-1/2 text-gray-400 hover:text-gray-600"
                >
                  {showPw ? <EyeOff size={15} /> : <Eye size={15} />}
                </button>
              </div>
            </div>

            {error && (
              <div className="bg-red-50 border border-red-200 text-red-700 text-xs px-3 py-2 rounded-lg flex items-center gap-2">
                <ShieldAlert size={14} className="flex-shrink-0" />
                <span>{error}</span>
              </div>
            )}

            <button
              type="submit"
              disabled={loading}
              className={cn(
                "w-full flex items-center justify-center gap-2 py-2.5 rounded-xl text-sm font-semibold transition-all",
                loading
                  ? "bg-gray-200 text-gray-400 cursor-not-allowed"
                  : "bg-bis-600 text-white hover:bg-bis-700 shadow-sm hover:shadow-md"
              )}
            >
              {loading ? (
                <span className="w-4 h-4 border-2 border-white/40 border-t-white rounded-full animate-spin" />
              ) : (
                <LogIn size={15} />
              )}
              {loading ? "Signing in…" : "Sign in"}
            </button>
          </form>

          <p className="text-center text-sm text-gray-500 mt-6">
            Don&apos;t have an account?{" "}
            <Link href="/auth/register" className="text-bis-600 font-semibold hover:underline">
              Register
            </Link>
          </p>
        </div>

        <p className="text-center text-xs text-gray-400 mt-4">
          Bureau of Indian Standards · Official AI Assistant
        </p>
      </div>
    </div>
  );
}

export default function LoginPage() {
  return (
    <Suspense
      fallback={
        <div className="min-h-screen flex items-center justify-center bg-gradient-to-br from-orange-50 via-white to-blue-50">
          <div className="w-8 h-8 border-2 border-bis-600 border-t-transparent rounded-full animate-spin" />
        </div>
      }
    >
      <LoginContent />
    </Suspense>
  );
}
