import * as React from "react";
import { cn } from "@/lib/utils";

export interface InputProps extends React.InputHTMLAttributes<HTMLInputElement> {
  error?: string;
  label?: string;
  hint?: string;
}

const Input = React.forwardRef<HTMLInputElement, InputProps>(
  ({ className, type, error, label, hint, id, ...props }, ref) => {
    const inputId = id ?? label?.toLowerCase().replace(/\s+/g, "-");

    return (
      <div className="w-full space-y-1">
        {label && (
          <label
            htmlFor={inputId}
            className="block text-sm font-medium text-gray-700"
          >
            {label}
            {props.required && <span className="text-red-500 ml-0.5">*</span>}
          </label>
        )}

        <input
          id={inputId}
          type={type ?? "text"}
          ref={ref}
          className={cn(
            "w-full rounded-xl border bg-white px-3 py-2.5 text-sm text-gray-900",
            "placeholder:text-gray-400 outline-none transition-colors",
            "focus:border-bis-400 focus:ring-2 focus:ring-bis-100",
            "disabled:cursor-not-allowed disabled:bg-gray-50 disabled:text-gray-400",
            error
              ? "border-red-300 focus:border-red-400 focus:ring-red-100"
              : "border-gray-200",
            className
          )}
          {...props}
        />

        {error && (
          <p className="text-xs text-red-600 flex items-center gap-1">
            <span>⚠</span> {error}
          </p>
        )}
        {hint && !error && (
          <p className="text-xs text-gray-400">{hint}</p>
        )}
      </div>
    );
  }
);
Input.displayName = "Input";

export { Input };
