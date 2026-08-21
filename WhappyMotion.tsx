"use client";

import { useEffect } from "react";

export function WhappyMotion() {
  useEffect(() => {
    const root = document.querySelector<HTMLElement>(".nova-shell");
    if (!root || window.matchMedia("(prefers-reduced-motion: reduce)").matches) return;

    let frame = 0;
    let currentX = window.innerWidth * 0.72;
    let currentY = window.innerHeight * 0.32;
    let targetX = currentX;
    let targetY = currentY;
    const ripples = new Set<HTMLElement>();

    function renderMotion() {
      currentX += (targetX - currentX) * 0.13;
      currentY += (targetY - currentY) * 0.13;
      root?.style.setProperty("--motion-x", `${currentX}px`);
      root?.style.setProperty("--motion-y", `${currentY}px`);
      if (Math.abs(targetX - currentX) > 0.2 || Math.abs(targetY - currentY) > 0.2) frame = window.requestAnimationFrame(renderMotion);
      else frame = 0;
    }

    function handlePointer(event: PointerEvent) {
      targetX = event.clientX;
      targetY = event.clientY;
      if (!frame) frame = window.requestAnimationFrame(renderMotion);
    }

    function handlePress(event: PointerEvent) {
      const target = event.target as HTMLElement | null;
      if (!target?.closest("button, a, [role='button']")) return;
      const ripple = document.createElement("span");
      ripple.className = "whappy-click-wave";
      ripple.style.left = `${event.clientX}px`;
      ripple.style.top = `${event.clientY}px`;
      document.body.appendChild(ripple);
      ripples.add(ripple);
      ripple.addEventListener("animationend", () => {
        ripples.delete(ripple);
        ripple.remove();
      }, { once: true });
    }

    window.addEventListener("pointermove", handlePointer, { passive: true });
    window.addEventListener("pointerdown", handlePress, { passive: true });
    return () => {
      window.removeEventListener("pointermove", handlePointer);
      window.removeEventListener("pointerdown", handlePress);
      if (frame) window.cancelAnimationFrame(frame);
      ripples.forEach((ripple) => ripple.remove());
    };
  }, []);

  return <div className="whappy-motion-field" aria-hidden="true">
    <i className="motion-pointer" />
    <i className="motion-orb orb-one" />
    <i className="motion-orb orb-two" />
    <i className="motion-orb orb-three" />
  </div>;
}
