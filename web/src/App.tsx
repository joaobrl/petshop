import { Routes, Route } from "react-router-dom";
import { CartProvider } from "./context/CartContext";
import { AuthProvider } from "./context/AuthContext";

import { StoreLayout } from "./components/layout/StoreLayout";
import { InternalLayout } from "./components/layout/InternalLayout";
import { RequireAuth } from "./components/layout/RequireAuth";
import { RequireStaff } from "./components/layout/RequireStaff";

import { CatalogPage } from "./pages/store/CatalogPage";
import { ProductDetailPage } from "./pages/store/ProductDetailPage";
import { CartPage } from "./pages/store/CartPage";
import { CheckoutPage } from "./pages/store/CheckoutPage";
import { OrderConfirmationPage } from "./pages/store/OrderConfirmationPage";

import { LoginPage } from "./pages/account/LoginPage";
import { RegisterPage } from "./pages/account/RegisterPage";
import { MyPetsPage } from "./pages/account/MyPetsPage";
import { MyOrdersPage } from "./pages/account/MyOrdersPage";

import { NewBookingPage } from "./pages/booking/NewBookingPage";
import { MyBookingsPage } from "./pages/booking/MyBookingsPage";

import { StaffLoginPage } from "./pages/internal/StaffLoginPage";
import { InternalDashboardPage } from "./pages/internal/InternalDashboardPage";
import { InternalOrdersPage } from "./pages/internal/InternalOrdersPage";
import { InternalBookingsPage } from "./pages/internal/InternalBookingsPage";
import { StaffPage } from "./pages/internal/StaffPage";

function App() {
  return (
    <AuthProvider>
      <CartProvider>
        <Routes>
            <Route element={<StoreLayout />}>
              <Route path="/" element={<CatalogPage />} />
              <Route path="/produtos/:id" element={<ProductDetailPage />} />
              <Route path="/carrinho" element={<CartPage />} />
              <Route
                path="/checkout"
                element={
                  <RequireAuth>
                    <CheckoutPage />
                  </RequireAuth>
                }
              />
              <Route path="/pedido/:id" element={<OrderConfirmationPage />} />

              <Route path="/entrar" element={<LoginPage />} />
              <Route path="/cadastro" element={<RegisterPage />} />

              <Route
                path="/minha-conta/pets"
                element={
                  <RequireAuth>
                    <MyPetsPage />
                  </RequireAuth>
                }
              />
              <Route
                path="/minha-conta/pedidos"
                element={
                  <RequireAuth>
                    <MyOrdersPage />
                  </RequireAuth>
                }
              />
              <Route
                path="/minha-conta/agendamentos"
                element={
                  <RequireAuth>
                    <MyBookingsPage />
                  </RequireAuth>
                }
              />
              <Route path="/agendar" element={<NewBookingPage />} />
            </Route>

            <Route path="/interno/entrar" element={<StaffLoginPage />} />
            <Route
              path="/interno"
              element={
                <RequireStaff>
                  <InternalLayout />
                </RequireStaff>
              }
            >
              <Route index element={<InternalDashboardPage />} />
              <Route path="pedidos" element={<InternalOrdersPage />} />
              <Route path="agendamentos" element={<InternalBookingsPage />} />
              <Route path="equipe" element={<StaffPage />} />
            </Route>
        </Routes>
      </CartProvider>
    </AuthProvider>
  );
}

export default App;
