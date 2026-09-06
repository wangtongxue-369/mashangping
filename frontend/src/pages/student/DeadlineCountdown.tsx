import { useEffect, useState } from 'react';

/** naive ISO（YYYY-MM-DDTHH:mm:ss，无时区）按本地时间解析，与后端 LocalDateTime 显示口径一致。 */
function parseNaiveMs(iso: string): number {
  return new Date(iso.replace(' ', 'T')).getTime();
}

function fmt(ms: number): string {
  const totalSec = Math.max(0, Math.floor(ms / 1000));
  const d = Math.floor(totalSec / 86400);
  const h = Math.floor((totalSec % 86400) / 3600);
  const m = Math.floor((totalSec % 3600) / 60);
  const s = totalSec % 60;
  const pad = (n: number) => String(n).padStart(2, '0');
  return d > 0 ? `${d} 天 ${pad(h)}:${pad(m)}:${pad(s)}` : `${pad(h)}:${pad(m)}:${pad(s)}`;
}

/**
 * 每秒刷新的截止倒计时；targetIso 为基准截止时间，lateDays>0 时把最终宽限截止算进去
 * （宽限截止 = dueAt + lateDays 天）。时间已过显示「已截止」。
 */
export default function DeadlineCountdown({
  targetIso,
  lateDays = 0,
  label = '截止',
}: {
  targetIso: string;
  lateDays?: number;
  label?: string;
}) {
  const [, tick] = useState(0);
  useEffect(() => {
    const timer = window.setInterval(() => tick((t) => t + 1), 1000);
    return () => window.clearInterval(timer);
  }, []);

  const endMs = parseNaiveMs(targetIso) + lateDays * 86_400_000;
  const ms = endMs - Date.now();
  return <span style={{ fontVariantNumeric: 'tabular-nums' }}>{ms <= 0 ? '已截止' : `${label}剩余 ${fmt(ms)}`}</span>;
}
