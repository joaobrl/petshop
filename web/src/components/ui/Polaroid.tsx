import type { CSSProperties } from "react";

interface PolaroidProps {
  src: string;
  caption: string;
  tilt?: number;
  tile?: string;
  eager?: boolean;
}

// A pet snapshot taped to the shop wall. Decorative: the caption carries the
// meaning, so the image itself has an empty alt.
export function Polaroid({ src, caption, tilt = -3, tile, eager = false }: PolaroidProps) {
  const style = { "--tilt": `${tilt}deg`, "--tape": `${-tilt * 1.5}deg`, "--tile": tile } as CSSProperties;
  return (
    <figure className="polaroid" style={style}>
      <img src={src} alt="" width={240} height={240} loading={eager ? "eager" : "lazy"} />
      <figcaption>{caption}</figcaption>
    </figure>
  );
}
