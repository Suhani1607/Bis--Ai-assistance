import type { Config } from "tailwindcss";

const config: Config = {
  darkMode: ["class"],
  content: [
    "./src/pages/**/*.{js,ts,jsx,tsx,mdx}",
    "./src/components/**/*.{js,ts,jsx,tsx,mdx}",
    "./src/app/**/*.{js,ts,jsx,tsx,mdx}",
  ],
  theme: {
    extend: {
      colors: {
        // BIS brand palette
        bis: {
          50:  "#fff7ed",
          100: "#ffedd5",
          200: "#fed7aa",
          400: "#fb923c",
          600: "#ea580c",   // primary orange (BIS logo)
          800: "#9a3412",
          900: "#7c2d12",
        },
        navy: {
          50:  "#eff6ff",
          100: "#dbeafe",
          400: "#60a5fa",
          600: "#1d4ed8",   // BIS header navy
          800: "#1e3a8a",
          900: "#1e3a8a",
        },
      },
      fontFamily: {
        sans: ["Inter", "system-ui", "sans-serif"],
      },
      animation: {
        "fade-in":      "fadeIn 0.2s ease-in-out",
        "slide-up":     "slideUp 0.3s ease-out",
        "typing-dot":   "typingDot 1.2s infinite",
      },
      keyframes: {
        fadeIn:    { from: { opacity: "0" },                   to: { opacity: "1" } },
        slideUp:   { from: { transform: "translateY(8px)", opacity: "0" }, to: { transform: "translateY(0)", opacity: "1" } },
        typingDot: { "0%,80%,100%": { transform: "scale(0)" }, "40%": { transform: "scale(1)" } },
      },
    },
  },
  plugins: [],
};

export default config;
