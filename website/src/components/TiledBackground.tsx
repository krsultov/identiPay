"use client";

import { useMemo } from "react";

// Deterministic hash so every render produces the same "random" delays (no hydration mismatch).
function hash(x: number, y: number): number {
  let h = (x * 374761393 + y * 668265263) | 0;
  h = ((h ^ (h >> 13)) * 1274126177) | 0;
  h = h ^ (h >> 16);
  return ((h & 0x7fffffff) / 0x7fffffff); // 0 → 1
}

export default function TiledBackground({
  className = "",
  circleColor = "rgba(0,0,0,0.06)",
  dotColor = "#ffffff",
}: {
  className?: string;
  circleColor?: string;
  dotColor?: string;
}) {
  // Circle diameter = tile spacing → circles touch edge-to-edge.
  const tileSize = 140;
  const r = tileSize / 2;       // 70
  const dotR = 21;               // ~30% of r → matches logo proportions

  // Enough tiles to fill ultra-wide screens (2560 × 1440+).
  const cols = 22;
  const rows = 14;

  const circles = useMemo(() => {
    const out: {
      col: number;
      row: number;
      delay: number;
      duration: number;
    }[] = [];
    for (let row = 0; row < rows; row++) {
      for (let col = 0; col < cols; col++) {
        const rand = hash(col, row);
        // Diagonal bias + randomness → "data flowing" feel
        const diagonalOffset = (col + row) * 0.25;
        const delay = diagonalOffset + rand * 5;       // 0–~14 s spread
        const duration = 2.8 + rand * 3.2;              // 2.8–6 s per cycle
        out.push({ col, row, delay, duration });
      }
    }
    return out;
  }, []);

  return (
    <div
      className={`absolute inset-0 overflow-hidden pointer-events-none ${className}`}
      style={{
        maskImage:
          "radial-gradient(ellipse 110% 95% at 50% 45%, black 10%, transparent 65%)",
        WebkitMaskImage:
          "radial-gradient(ellipse 110% 95% at 50% 45%, black 10%, transparent 65%)",
      }}
    >
      {/* Individual animated circles */}
      <svg
        className="absolute inset-0 w-full h-full"
        aria-hidden="true"
        style={{ minWidth: cols * tileSize, minHeight: rows * tileSize }}
      >
        {circles.map(({ col, row, delay, duration }) => {
          const cx = col * tileSize + r;
          const cy = row * tileSize + r;
          return (
            <g
              key={`${col}-${row}`}
              className="identipay-node"
              style={{
                animationDelay: `${delay.toFixed(2)}s`,
                animationDuration: `${duration.toFixed(2)}s`,
              }}
            >
              <circle cx={cx} cy={cy} r={r} fill={circleColor} />
              <circle cx={cx} cy={cy} r={dotR} fill={dotColor} />
            </g>
          );
        })}
      </svg>

      {/* Slow diagonal light sweep for atmosphere */}
      <div
        className="absolute inset-0"
        style={{
          background:
            "linear-gradient(120deg, transparent 0%, rgba(255,255,255,0.45) 25%, transparent 50%, rgba(255,255,255,0.45) 75%, transparent 100%)",
          backgroundSize: "400% 400%",
          animation: "identipay-sweep 14s ease-in-out infinite",
        }}
      />
    </div>
  );
}
