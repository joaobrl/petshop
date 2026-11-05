import { Outlet } from "react-router-dom";
import { Header } from "./Header";
import { Footer } from "./Footer";
import { EmailVerificationNotice } from "../account/EmailVerificationNotice";

export function StoreLayout() {
  return (
    <div style={{ display: "flex", flexDirection: "column", minHeight: "100%" }}>
      <Header />
      <EmailVerificationNotice />
      <main className="container" style={{ flex: 1, padding: "var(--space-8) var(--space-6)" }}>
        <Outlet />
      </main>
      <Footer />
    </div>
  );
}
