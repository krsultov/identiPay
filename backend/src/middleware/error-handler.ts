import type { Context, ErrorHandler } from "hono";

// Use duck-typing to detect AppError to avoid instanceof issues across module boundaries
function isAppError(error: unknown): error is {
  statusCode: number;
  code: string;
  message: string;
  details?: unknown;
} {
  return (
    error !== null &&
    typeof error === "object" &&
    "statusCode" in error &&
    "code" in error &&
    typeof (error as Record<string, unknown>).statusCode === "number" &&
    typeof (error as Record<string, unknown>).code === "string"
  );
}

/**
 * Hono onError handler. Register with app.onError(errorHandler).
 */
export const errorHandler: ErrorHandler = (error: Error, c: Context) => {
  if (isAppError(error)) {
    return c.json(
      {
        error: {
          code: error.code,
          message: error.message,
          ...(error.details ? { details: error.details } : {}),
        },
      },
      error.statusCode as 400,
    );
  }

  console.error("Unhandled error:", error);
  return c.json(
    {
      error: {
        code: "INTERNAL_ERROR",
        message: "An unexpected error occurred",
      },
    },
    500,
  );
};
