export function Logo({ height = 40 }: { height?: number }) {
  return <img src="/images/logo.webp" alt="WebCommerce PetShop" style={{ height, width: "auto" }} />;
}

// Logo mark on a white pebble + wordmark. `light` is for dark backgrounds.
export function Brand({ light = false }: { light?: boolean }) {
  return (
    <span className={light ? "brand brand-light" : "brand"}>
      <span className="brand-mark">
        <img src="/images/logo.webp" alt="" width={38} height={38} />
      </span>
      <span className="brand-text">
        <span className="brand-name">WebCommerce</span>
        <span className="brand-sub">petshop de bairro</span>
      </span>
    </span>
  );
}
