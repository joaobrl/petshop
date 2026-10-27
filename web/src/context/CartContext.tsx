import { createContext, useCallback, useContext, useEffect, useState, type ReactNode } from "react";
import * as orderService from "../services/orderService";
import type { Cart } from "../types/order";
import { useAuth } from "./AuthContext";

interface CartContextValue {
  cart: Cart | null;
  loading: boolean;
  addItem: (productId: number, quantity?: number) => Promise<void>;
  removeItem: (productId: number) => Promise<void>;
  refresh: () => Promise<void>;
  clearLocal: () => void;
  totalItems: number;
}

const CartContext = createContext<CartContextValue | null>(null);

export function CartProvider({ children }: { children: ReactNode }) {
  const { user } = useAuth();
  const [cart, setCart] = useState<Cart | null>(null);
  const [loading, setLoading] = useState(false);

  const refresh = useCallback(async () => {
    if (!user) {
      setCart(null);
      return;
    }
    setLoading(true);
    try {
      setCart(await orderService.getCart(user.id));
    } finally {
      setLoading(false);
    }
  }, [user]);

  useEffect(() => {
    refresh();
  }, [refresh]);

  async function addItem(productId: number, quantity = 1) {
    if (!user) throw new Error("É preciso estar logado para adicionar itens ao carrinho.");
    setLoading(true);
    try {
      setCart(await orderService.addCartItem(user.id, productId, quantity));
    } finally {
      setLoading(false);
    }
  }

  async function removeItem(productId: number) {
    if (!user) return;
    setLoading(true);
    try {
      setCart(await orderService.removeCartItem(user.id, productId));
    } finally {
      setLoading(false);
    }
  }

  const totalItems = cart?.items.reduce((sum, item) => sum + item.quantity, 0) ?? 0;

  const value: CartContextValue = {
    cart,
    loading,
    addItem,
    removeItem,
    refresh,
    clearLocal: () => setCart(null),
    totalItems,
  };

  return <CartContext.Provider value={value}>{children}</CartContext.Provider>;
}

export function useCart() {
  const ctx = useContext(CartContext);
  if (!ctx) throw new Error("useCart must be used within a CartProvider");
  return ctx;
}
