// One-off dev seed: logs in as the bootstrap admin, registers a handful of
// catalog products and a few staff accounts so the frontend has something to
// show against the real backend. Run with: node scripts/seed-backend.mjs
// Requires docker-compose up (see ../../.env for BOOTSTRAP_ADMIN_*).

const CUSTOMERS = "http://localhost:8082";
const PRODUCTS = "http://localhost:8081";

const ADMIN_EMAIL = process.env.BOOTSTRAP_ADMIN_EMAIL ?? "admin@petshop.local";
const ADMIN_PASSWORD = process.env.BOOTSTRAP_ADMIN_PASSWORD ?? "Admin@12345";

async function json(res) {
  const text = await res.text();
  try {
    return JSON.parse(text);
  } catch {
    return text;
  }
}

async function login(email, password) {
  const res = await fetch(`${CUSTOMERS}/api/v1/auth/login`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ email, password }),
  });
  const body = await json(res);
  if (!res.ok) throw new Error(`login ${email} failed: ${res.status} ${JSON.stringify(body)}`);
  return body;
}

async function post(baseUrl, path, token, body) {
  const res = await fetch(`${baseUrl}${path}`, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
    },
    body: JSON.stringify(body),
  });
  const data = await json(res);
  return { ok: res.ok, status: res.status, data };
}

const PRODUCTS_SEED = [
  { name: "Ração Premium Cães Adultos 15kg", description: "Alimento completo para cães adultos de porte médio e grande, rico em proteína animal.", price: 189.9, stock: 40, dimensions: { weight: 15, height: 60, width: 40, length: 12 } },
  { name: "Ração Premium Gatos Castrados 10kg", description: "Fórmula balanceada para gatos castrados, com controle de peso e saúde urinária.", price: 159.9, stock: 35, dimensions: { weight: 10, height: 50, width: 35, length: 10 } },
  { name: "Areia Higiênica Aglomerante 4kg", description: "Areia de baixa poeira com alto poder de aglomeração e controle de odor.", price: 34.9, stock: 60, dimensions: { weight: 4, height: 25, width: 20, length: 8 } },
  { name: "Brinquedo Mordedor de Corda", description: "Corda de algodão trançado, ajuda na limpeza dos dentes durante a brincadeira.", price: 24.9, stock: 80, dimensions: { weight: 0.3, height: 20, width: 8, length: 8 } },
  { name: "Coleira Ajustável Nylon", description: "Coleira resistente com fivela de engate rápido, tamanhos P ao G.", price: 39.9, stock: 50, dimensions: { weight: 0.15, height: 2, width: 40, length: 3 } },
  { name: "Shampoo Neutro para Banho 500ml", description: "Shampoo suave, livre de corantes, indicado para cães e gatos de pele sensível.", price: 29.9, stock: 45, dimensions: { weight: 0.55, height: 18, width: 6, length: 6 } },
  { name: "Arranhador para Gatos", description: "Base em sisal natural com pelúcia, estimula o comportamento natural de arranhar.", price: 119.9, stock: 15, dimensions: { weight: 3.2, height: 45, width: 35, length: 35 } },
  { name: "Caminha Acolchoada Média", description: "Caminha macia e lavável, ideal para cães e gatos de até 15kg.", price: 149.9, stock: 20, dimensions: { weight: 1.8, height: 15, width: 60, length: 50 } },
];

const STAFF_SEED = [
  { name: "Carla Souza", cpf: "11111111111", email: "carla@petshop.dev", phone: "11977771111", role: "RECEPTIONIST" },
  { name: "Paulo Lima", cpf: "22222222222", email: "paulo@petshop.dev", phone: "11977772222", role: "GROOMER" },
  { name: "Dr. Henrique Costa", cpf: "44444444444", email: "henrique@petshop.dev", phone: "11977774444", role: "VETERINARIAN" },
];

async function main() {
  console.log("Logging in as bootstrap admin:", ADMIN_EMAIL);
  const loginResult = await login(ADMIN_EMAIL, ADMIN_PASSWORD);
  let token = loginResult.accessToken;
  console.log("  role:", loginResult.role, "mustChangePassword:", loginResult.mustChangePassword);

  if (loginResult.mustChangePassword) {
    const newPassword = ADMIN_PASSWORD; // already what we want; bootstrap just flags it once
    console.log("  changing password (first login requirement)...");
    const res = await fetch(`${CUSTOMERS}/api/v1/auth/change-password`, {
      method: "PATCH",
      headers: { "Content-Type": "application/json", Authorization: `Bearer ${token}` },
      body: JSON.stringify({ email: ADMIN_EMAIL, currentPassword: ADMIN_PASSWORD, newPassword }),
    });
    if (!res.ok) console.log("  change-password:", res.status, await json(res));
    const relogin = await login(ADMIN_EMAIL, newPassword);
    token = relogin.accessToken;
  }

  console.log("\nSeeding products...");
  for (const product of PRODUCTS_SEED) {
    const { ok, status, data } = await post(PRODUCTS, "/api/v1/products/register", token, product);
    console.log(` ${ok ? "OK " : "ERR"} ${status} ${product.name}`, ok ? "" : JSON.stringify(data));
  }

  console.log("\nSeeding staff (initial password = their CPF)...");
  for (const staff of STAFF_SEED) {
    const { ok, status, data } = await post(CUSTOMERS, "/api/v1/staff/create", token, staff);
    console.log(` ${ok ? "OK " : "ERR"} ${status} ${staff.name} (${staff.role}) — login ${staff.email} / ${staff.cpf}`, ok ? "" : JSON.stringify(data));
  }

  console.log("\nDone.");
}

main().catch((err) => {
  console.error(err);
  process.exit(1);
});
