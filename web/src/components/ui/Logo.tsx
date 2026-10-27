export function Logo({ height = 40 }: { height?: number }) {
  return (
    <img
      src="/images/logo.webp"
      alt="WebCommerce PetShop"
      style={{ height, width: "auto" }}
    />
  );
}
