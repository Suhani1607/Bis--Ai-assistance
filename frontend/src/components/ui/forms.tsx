import * as React from "react";
import { cn } from "@/lib/utils";

// ── Select ────────────────────────────────────────────────────

export interface SelectProps extends React.SelectHTMLAttributes<HTMLSelectElement> {
  label?: string;
  error?: string;
  options: { value: string; label: string }[];
}

const Select = React.forwardRef<HTMLSelectElement, SelectProps>(
  ({ className, label, error, options, id, ...props }, ref) => {
    const selectId = id ?? label?.toLowerCase().replace(/\s+/g, "-");
    return (
      <div className="w-full space-y-1">
        {label && (
          <label htmlFor={selectId} className="block text-sm font-medium text-gray-700">
            {label}
          </label>
        )}
        <select
          id={selectId}
          ref={ref}
          className={cn(
            "w-full rounded-xl border border-gray-200 bg-white px-3 py-2.5 text-sm text-gray-900",
            "outline-none focus:border-bis-400 focus:ring-2 focus:ring-bis-100 transition-colors",
            "disabled:cursor-not-allowed disabled:bg-gray-50",
            error && "border-red-300",
            className
          )}
          {...props}
        >
          {options.map((opt) => (
            <option key={opt.value} value={opt.value}>{opt.label}</option>
          ))}
        </select>
        {error && <p className="text-xs text-red-600">{error}</p>}
      </div>
    );
  }
);
Select.displayName = "Select";

// ── Separator ─────────────────────────────────────────────────

interface SeparatorProps extends React.HTMLAttributes<HTMLDivElement> {
  orientation?: "horizontal" | "vertical";
}

function Separator({ className, orientation = "horizontal", ...props }: SeparatorProps) {
  return (
    <div
      role="separator"
      className={cn(
        "bg-gray-200",
        orientation === "horizontal" ? "h-px w-full my-3" : "w-px h-full mx-3",
        className
      )}
      {...props}
    />
  );
}

// ── Spinner ───────────────────────────────────────────────────

interface SpinnerProps { size?: "sm" | "md" | "lg"; className?: string; }

function Spinner({ size = "md", className }: SpinnerProps) {
  const sizes = { sm: "w-4 h-4 border-2", md: "w-6 h-6 border-2", lg: "w-8 h-8 border-3" };
  return (
    <span
      className={cn(
        "inline-block rounded-full border-gray-200 border-t-bis-600 animate-spin",
        sizes[size],
        className
      )}
    />
  );
}

// ── Textarea ─────────────────────────────────────────────────

export interface TextareaProps extends React.TextareaHTMLAttributes<HTMLTextAreaElement> {
  label?: string;
  error?: string;
}

const Textarea = React.forwardRef<HTMLTextAreaElement, TextareaProps>(
  ({ className, label, error, id, ...props }, ref) => {
    const taId = id ?? label?.toLowerCase().replace(/\s+/g, "-");
    return (
      <div className="w-full space-y-1">
        {label && (
          <label htmlFor={taId} className="block text-sm font-medium text-gray-700">{label}</label>
        )}
        <textarea
          id={taId}
          ref={ref}
          className={cn(
            "w-full rounded-xl border border-gray-200 bg-white px-3 py-2.5 text-sm",
            "outline-none focus:border-bis-400 focus:ring-2 focus:ring-bis-100 transition-colors",
            "resize-none disabled:bg-gray-50",
            error && "border-red-300",
            className
          )}
          {...props}
        />
        {error && <p className="text-xs text-red-600">{error}</p>}
      </div>
    );
  }
);
Textarea.displayName = "Textarea";

export { Select, Separator, Spinner, Textarea };
