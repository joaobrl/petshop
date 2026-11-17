import { Outlet } from "react-router-dom";
import { Header } from "./Header";
import { Footer } from "./Footer";
import { EmailVerificationNotice } from "../account/EmailVerificationNotice";

export function StoreLayout() {
  return (
    <div className="store">
      <div className="awning" aria-hidden="true" />
      <Header />
      <EmailVerificationNotice />
      <main className="store-main">
        <Outlet />
      </main>
      <Footer />
    </div>
  );
}
