import * as React from "react";
import { cva, type VariantProps } from "class-variance-authority";
import { cn } from "@/lib/utils";

const buttonVariants = cva(
  "inline-flex items-center justify-center gap-2 whitespace-nowrap rounded-xl text-sm font-medium transition-all focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-bis-400 disabled:pointer-events-none disabled:opacity-50",
  {
    variants: {
      variant: {
        default:   "bg-bis-600 text-white shadow-sm hover:bg-bis-700",
        secondary: "bg-gray-100 text-gray-800 hover:bg-gray-200",
        outline:   "border border-gray-200 bg-white text-gray-700 hover:bg-gray-50 hover:border-bis-300",
        ghost:     "text-gray-600 hover:bg-gray-100 hover:text-gray-900",
        danger:    "bg-red-600 text-white hover:bg-red-700",
        link:      "text-bis-600 underline-offset-4 hover:underline p-0 h-auto",
      },
      size: {
        sm:   "h-8  px-3 text-xs",
        md:   "h-9  px-4",
        lg:   "h-11 px-6 text-base",
        icon: "h-9  w-9 p-0",
      },
    },
    defaultVariants: {
      variant: "default",
      size: "md",
    },
  }
);

export interface ButtonProps
  extends React.ButtonHTMLAttributes<HTMLButtonElement>,
    VariantProps<typeof buttonVariants> {
  isLoading?: boolean;
}

const Button = React.forwardRef<HTMLButtonElement, ButtonProps>(
  ({ className, variant, size, isLoading, children, disabled, ...props }, ref) => {
    return (
      <button
        ref={ref}
        className={cn(buttonVariants({ variant, size }), className)}
        disabled={disabled || isLoading}
        {...props}
      >
        {isLoading && (
          <span className="w-4 h-4 border-2 border-current border-t-transparent rounded-full animate-spin" />
        )}
        {children}
      </button>
    );
  }
);
Button.displayName = "Button";

export { Button, buttonVariants };
