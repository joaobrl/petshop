// Base URLs for the real microsserviços (docker-compose ports, exposed to
// the host). notification-service has no REST surface (Kafka consumer
// only), so it's not listed here.
export const API_BASE = {
  products: "http://localhost:8081",
  customers: "http://localhost:8082", // also issues the JWT (login/register/pets/staff)
  bookings: "http://localhost:8083",
  staff: "http://localhost:8085", // availability + weekend allocation only
  orders: "http://localhost:8086",
};
