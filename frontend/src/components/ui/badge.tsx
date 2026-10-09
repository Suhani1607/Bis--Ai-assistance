import * as React from "react";
import { cva, type VariantProps } from "class-variance-authority";
import { cn } from "@/lib/utils";

const badgeVariants = cva(
  "inline-flex items-center gap-1 rounded-full border px-2 py-0.5 text-xs font-medium transition-colors",
  {
    variants: {
      variant: {
        default:     "bg-bis-100 text-bis-800 border-bis-200",
        secondary:   "bg-gray-100 text-gray-700 border-gray-200",
        success:     "bg-green-100 text-green-700 border-green-200",
        warning:     "bg-yellow-100 text-yellow-700 border-yellow-200",
        danger:      "bg-red-100 text-red-700 border-red-200",
        outline:     "bg-transparent text-gray-600 border-gray-300",
        // BIS certification scheme variants
        scheme_i:    "bg-orange-100 text-orange-700 border-orange-200",
        crs:         "bg-blue-100 text-blue-700 border-blue-200",
        fmcs:        "bg-purple-100 text-purple-700 border-purple-200",
        hallmarking: "bg-yellow-100 text-yellow-700 border-yellow-200",
      },
    },
    defaultVariants: {
      variant: "default",
    },
  }
);

export interface BadgeProps
  extends React.HTMLAttributes<HTMLSpanElement>,
    VariantProps<typeof badgeVariants> {}

function Badge({ className, variant, ...props }: BadgeProps) {
  return (
    <span className={cn(badgeVariants({ variant }), className)} {...props} />
  );
}

export { Badge, badgeVariants };
