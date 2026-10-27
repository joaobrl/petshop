export function Footer() {
  return (
    <footer style={{ borderTop: "1px solid var(--border)", marginTop: "var(--space-8)" }}>
      <div
        className="container"
        style={{
          display: "flex",
          justifyContent: "space-between",
          alignItems: "center",
          padding: "var(--space-6) var(--space-6)",
          flexWrap: "wrap",
          gap: "var(--space-4)",
        }}
      >
        <span className="caption" style={{ color: "var(--ink-muted)" }}>
          WebCommerce PetShop — projeto de estudo, sem deploy público.
        </span>
        <span className="caption" style={{ color: "var(--ink-muted)" }}>
          Produtos e serviços para quem ama pets.
        </span>
      </div>
    </footer>
  );
}
