"use client";

import { useState } from "react";
import Cookies from "js-cookie";
import {
  X,
  UserCheck,
  LogIn,
  Eye,
  EyeOff,
  ShieldAlert,
} from "lucide-react";
import { auth } from "@/lib/api";
import { useAuth } from "@/hooks/useAuth";
import { cn } from "@/lib/utils";

interface SwitchAccountDialogProps {
  isOpen: boolean;
  onClose: () => void;
}

export function SwitchAccountDialog({ isOpen, onClose }: SwitchAccountDialogProps) {
  const { user } = useAuth();
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [showPw, setShowPw] = useState(false);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");

  if (!isOpen) return null;

  const handleLogin = async (loginEmail?: string, loginPassword?: string) => {
    const targetEmail = loginEmail || email;
    const targetPassword = loginPassword || password;

    if (!targetEmail || !targetPassword) {
      setError("Please enter both email and password.");
      return;
    }

    setError("");
    setLoading(true);

    try {
      const resp = await auth.login(targetEmail, targetPassword);
      Cookies.set("access_token", resp.accessToken, { expires: 1, sameSite: "lax" });
      Cookies.set("refresh_token", resp.refreshToken, { expires: 30, sameSite: "lax" });
      // Reload page to re-initialize sessions and active chat context
      window.location.reload();
    } catch (err: any) {
      setError(err.message || "Invalid credentials. Please try again.");
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/40 backdrop-blur-sm animate-fade-in">
      <div className="w-full max-w-md bg-white rounded-2xl shadow-2xl border border-gray-100 overflow-hidden transform transition-all animate-scale-in">
        {/* Header */}
        <div className="flex items-center justify-between px-6 py-4 border-b border-gray-100 bg-gray-50/50">
          <div className="flex items-center gap-2">
            <div className="p-2 rounded-lg bg-orange-100 text-bis-700">
              <UserCheck size={18} />
            </div>
            <div>
              <h3 className="text-sm font-semibold text-gray-900">Switch Account</h3>
              <p className="text-[11px] text-gray-500">Sign in with different credentials</p>
            </div>
          </div>
          <button
            onClick={onClose}
            className="p-1 rounded-lg text-gray-400 hover:text-gray-600 hover:bg-gray-100 transition-colors"
          >
            <X size={18} />
          </button>
        </div>

        <div className="p-6">
          {/* Current Active Account */}
          {user && (
            <div className="mb-4 p-3 rounded-xl bg-orange-50/60 border border-orange-100 text-xs">
              <span className="text-gray-500">Active now: </span>
              <span className="font-semibold text-gray-800">{user.name}</span>
              <span className="text-gray-400 text-[11px] block">{user.email}</span>
            </div>
          )}

          <form
            onSubmit={(e) => {
              e.preventDefault();
              handleLogin();
            }}
            className="space-y-3"
          >
            <div>
              <label className="block text-xs font-medium text-gray-700 mb-1">Email</label>
              <input
                type="email"
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                required
                placeholder="user@example.com"
                className="w-full px-3 py-2 rounded-xl border border-gray-200 text-xs outline-none focus:border-bis-400 focus:ring-2 focus:ring-bis-100 transition-colors"
              />
            </div>

            <div>
              <label className="block text-xs font-medium text-gray-700 mb-1">Password</label>
              <div className="relative">
                <input
                  type={showPw ? "text" : "password"}
                  value={password}
                  onChange={(e) => setPassword(e.target.value)}
                  required
                  placeholder="••••••••"
                  className="w-full px-3 py-2 pr-9 rounded-xl border border-gray-200 text-xs outline-none focus:border-bis-400 focus:ring-2 focus:ring-bis-100 transition-colors"
                />
                <button
                  type="button"
                  onClick={() => setShowPw(!showPw)}
                  className="absolute right-2.5 top-1/2 -translate-y-1/2 text-gray-400 hover:text-gray-600"
                >
                  {showPw ? <EyeOff size={14} /> : <Eye size={14} />}
                </button>
              </div>
            </div>

            {error && (
              <div className="bg-red-50 border border-red-200 text-red-700 text-xs p-2 rounded-lg flex items-center gap-1.5">
                <ShieldAlert size={14} className="flex-shrink-0" />
                <span>{error}</span>
              </div>
            )}

            <div className="flex gap-2 pt-2">
              <button
                type="button"
                onClick={onClose}
                className="flex-1 py-2 rounded-xl border border-gray-200 text-xs font-medium text-gray-600 hover:bg-gray-50 transition-colors"
              >
                Cancel
              </button>
              <button
                type="submit"
                disabled={loading}
                className={cn(
                  "flex-1 flex items-center justify-center gap-1.5 py-2 rounded-xl text-xs font-semibold transition-all",
                  loading
                    ? "bg-gray-200 text-gray-400 cursor-not-allowed"
                    : "bg-bis-600 text-white hover:bg-bis-700 shadow-sm"
                )}
              >
                {loading ? (
                  <span className="w-3.5 h-3.5 border-2 border-white/40 border-t-white rounded-full animate-spin" />
                ) : (
                  <LogIn size={13} />
                )}
                <span>Sign in</span>
              </button>
            </div>
          </form>
        </div>
      </div>
    </div>
  );
}
