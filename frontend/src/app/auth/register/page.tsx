"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import Link from "next/link";
import Cookies from "js-cookie";
import { UserPlus } from "lucide-react";
import { auth, setAccessToken } from "@/lib/api";
import { cn } from "@/lib/utils";

export default function RegisterPage() {
  const router = useRouter();
  const [form, setForm] = useState({ name: "", email: "", password: "", preferredLang: "en" });
  const [loading, setLoading] = useState(false);
  const [error, setError]     = useState("");

  const set = (k: string) => (e: React.ChangeEvent<HTMLInputElement | HTMLSelectElement>) =>
    setForm((f) => ({ ...f, [k]: e.target.value }));

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError("");
    if (form.password.length < 8) { setError("Password must be at least 8 characters."); return; }
    setLoading(true);
    try {
      const resp = await auth.register(form);
      setAccessToken(resp.accessToken);
      Cookies.set("access_token",  resp.accessToken,  { expires: 1,  sameSite: "lax", path: "/" });
      Cookies.set("refresh_token", resp.refreshToken, { expires: 30, sameSite: "lax", path: "/" });
      window.location.href = "/chat";
    } catch (err: any) {
      setError(err.message || "Registration failed. Please try again.");
    } finally {
      setLoading(false);
    }
  };

  const field = (label: string, key: string, type = "text", placeholder = "") => (
    <div>
      <label className="block text-sm font-medium text-gray-700 mb-1">{label}</label>
      <input
        type={type}
        value={(form as any)[key]}
        onChange={set(key)}
        required
        placeholder={placeholder}
        className="w-full px-3 py-2.5 rounded-xl border border-gray-200 text-sm outline-none
                   focus:border-bis-400 focus:ring-2 focus:ring-bis-100 transition-colors"
      />
    </div>
  );

  return (
    <div className="min-h-screen flex items-center justify-center bg-gradient-to-br from-orange-50 via-white to-blue-50 p-4">
      <div className="w-full max-w-md">
        <div className="bg-white rounded-2xl shadow-xl border border-gray-100 p-8">
          <div className="flex flex-col items-center mb-8">
            <div className="w-14 h-14 rounded-2xl bg-gradient-to-br from-bis-600 to-orange-400 flex items-center justify-center text-white text-2xl font-bold shadow-md mb-3">
              B
            </div>
            <h1 className="text-xl font-bold text-gray-900">Create account</h1>
            <p className="text-sm text-gray-500 mt-1">BIS AI Assistant</p>
          </div>

          <form onSubmit={handleSubmit} className="space-y-4">
            {field("Full name", "name", "text", "Your name")}
            {field("Email", "email", "email", "you@example.com")}
            {field("Password", "password", "password", "Min. 8 characters")}

            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1">Preferred language</label>
              <select
                value={form.preferredLang}
                onChange={set("preferredLang")}
                className="w-full px-3 py-2.5 rounded-xl border border-gray-200 text-sm outline-none
                           focus:border-bis-400 focus:ring-2 focus:ring-bis-100 transition-colors bg-white"
              >
                <option value="en">English</option>
                <option value="hi">हिंदी (Hindi)</option>
              </select>
            </div>

            {error && (
              <div className="bg-red-50 border border-red-200 text-red-700 text-sm px-3 py-2 rounded-lg">
                {error}
              </div>
            )}

            <button
              type="submit"
              disabled={loading}
              className={cn(
                "w-full flex items-center justify-center gap-2 py-2.5 rounded-xl text-sm font-semibold transition-all",
                loading
                  ? "bg-gray-200 text-gray-400 cursor-not-allowed"
                  : "bg-bis-600 text-white hover:bg-bis-700 shadow-sm"
              )}
            >
              {loading
                ? <span className="w-4 h-4 border-2 border-white/40 border-t-white rounded-full animate-spin" />
                : <UserPlus size={15} />}
              {loading ? "Creating account…" : "Create account"}
            </button>
          </form>

          <p className="text-center text-sm text-gray-500 mt-6">
            Already have an account?{" "}
            <Link href="/auth/login" className="text-bis-600 font-medium hover:underline">Sign in</Link>
          </p>
        </div>
      </div>
    </div>
  );
}
