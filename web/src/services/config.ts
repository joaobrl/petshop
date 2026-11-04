// Base URL de todas as chamadas do frontend: o gateway-service, que roteia
// por path para cada microsserviço. Configurável por ambiente via
// VITE_API_BASE_URL (ver web/.env.example); ausente = gateway local.
export const API_BASE_URL: string = import.meta.env.VITE_API_BASE_URL ?? "http://localhost:8080";

// Mesma forma de antes para não tocar nos *Service.ts: todos os serviços
// apontam para o gateway. notification-service não tem API REST (só consome
// Kafka), então não aparece aqui.
export const API_BASE = {
  products: API_BASE_URL,
  customers: API_BASE_URL,
  bookings: API_BASE_URL,
  staff: API_BASE_URL,
  orders: API_BASE_URL,
};
