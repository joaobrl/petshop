const PAW = (
  <>
    <path d="M12 11.2c-3 0-5.6 2.9-5.6 5.7 0 2 1.5 3.1 3.2 3.1 1 0 1.6-.5 2.4-.5s1.4.5 2.4.5c1.7 0 3.2-1.1 3.2-3.1 0-2.8-2.6-5.7-5.6-5.7z" />
    <ellipse cx="5.2" cy="10.4" rx="2.1" ry="2.6" transform="rotate(-18 5.2 10.4)" />
    <ellipse cx="9.3" cy="5.8" rx="2.2" ry="2.8" transform="rotate(-6 9.3 5.8)" />
    <ellipse cx="14.7" cy="5.8" rx="2.2" ry="2.8" transform="rotate(6 14.7 5.8)" />
    <ellipse cx="18.8" cy="10.4" rx="2.1" ry="2.6" transform="rotate(18 18.8 10.4)" />
  </>
);

const STEPS = [
  { x: 14, y: 150, r: 28 },
  { x: 62, y: 128, r: 52 },
  { x: 72, y: 84, r: 30 },
  { x: 120, y: 66, r: 54 },
  { x: 132, y: 22, r: 32 },
];

// Paw prints walking across a corner — purely decorative.
export function PawTrail() {
  return (
    <svg className="paw-trail" viewBox="0 0 200 200" fill="currentColor" aria-hidden="true" focusable="false">
      {STEPS.map((step) => (
        <g key={`${step.x}-${step.y}`} transform={`translate(${step.x} ${step.y}) rotate(${step.r} 12 12) scale(1.7)`}>
          {PAW}
        </g>
      ))}
    </svg>
  );
}
