import { useCallback, useEffect, useRef, useState } from 'react';

/**
 * ژست «نگه‌داشتن برای دیدن».
 *
 * انگشت را نگه می‌داری، محتوا باز می‌شود؛ برمی‌داری، بسته می‌شود.
 * یک ضربه کوتاه چیزی را باز نمی‌کند، چون تا آستانه‌ی زمانی صبر می‌کنیم؛
 * این جلوی باز و بسته شدن ناخواسته هنگام اسکرول را می‌گیرد.
 */
export function usePressAndHold(options: { delay?: number } = {}) {
  const delay = options.delay ?? 220;
  const [isHeld, setIsHeld] = useState(false);
  const timer = useRef<ReturnType<typeof setTimeout> | null>(null);

  const clear = useCallback(() => {
    if (timer.current) {
      clearTimeout(timer.current);
      timer.current = null;
    }
  }, []);

  const start = useCallback(() => {
    clear();
    timer.current = setTimeout(() => setIsHeld(true), delay);
  }, [clear, delay]);

  const end = useCallback(() => {
    clear();
    setIsHeld(false);
  }, [clear]);

  useEffect(() => clear, [clear]);

  return {
    isHeld,
    /**
     * روی عنصر هدف پخش می‌شود. رویدادهای pointer هم لمس و هم ماوس را
     * پوشش می‌دهند، پس نیازی به دو مجموعه هندلر نیست.
     */
    handlers: {
      onPointerDown: start,
      onPointerUp: end,
      onPointerLeave: end,
      onPointerCancel: end,
      // منوی متنی و انتخاب متن، ژست نگه‌داشتن را در اندروید خراب می‌کنند
      onContextMenu: (e: React.MouseEvent) => e.preventDefault(),
      style: {
        touchAction: 'none',
        WebkitUserSelect: 'none',
        userSelect: 'none',
      } as React.CSSProperties,
    },
  };
}
