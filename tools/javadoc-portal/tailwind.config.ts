import type { Config } from "tailwindcss";

const config: Config = {
  content: [
    "./src/pages/**/*.{js,ts,jsx,tsx,mdx}",
    "./src/components/**/*.{js,ts,jsx,tsx,mdx}",
    "./src/app/**/*.{js,ts,jsx,tsx,mdx}",
  ],
  darkMode: "class",
  theme: {
    extend: {
      colors: {
        react: {
          bg: "#16181d",
          card: "#23272f",
          cardHover: "#2a2f38",
          code: "#1e2127",
          border: "#343a46",
          borderSubtle: "#2c313c",
          cyan: "#58c4dc",
          cyanHover: "#149eca",
          cyanGlow: "rgba(88, 196, 220, 0.15)",
          text: "#f6f7f9",
          textMuted: "#99a1b3",
          textSubtle: "#6d7585",
          accentYellow: "#f59e0b",
          accentRed: "#ef4444",
          accentGreen: "#22c55e",
          accentPurple: "#a855f7",
        },
      },
      fontFamily: {
        sans: [
          "Inter",
          "-apple-system",
          "BlinkMacSystemFont",
          "Segoe UI",
          "Roboto",
          "sans-serif",
        ],
        mono: [
          "JetBrains Mono",
          "Fira Code",
          "Consolas",
          "Monaco",
          "monospace",
        ],
      },
      boxShadow: {
        cyan: "0 0 20px -3px rgba(88, 196, 220, 0.2)",
        "cyan-lg": "0 0 30px -4px rgba(88, 196, 220, 0.35)",
        card: "0 4px 20px -2px rgba(0, 0, 0, 0.5)",
      },
    },
  },
  plugins: [],
};

export default config;
