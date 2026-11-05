// Seed de dados para desenvolvimento (SDD-021).
//
// Popula o ambiente local, SO via API (nada de SQL), para exercitar todas as
// telas do web/: produtos (inclui esgotado), staff de cada cargo, escala de
// fim de semana, clientes com pets, agendamentos (futuro/cancelado/concluido)
// e pedidos. Idempotente: checa existencia antes de criar e trata 409 como
// "ja existe". Recusa rodar contra qualquer host que nao seja local.
//
// Uso (em web/): npm run seed   -- veja web/.env.seed.example e web/README.md.

import { execFileSync } from "node:child_process";
import { existsSync, readFileSync, writeFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

const WEB_DIR = join(dirname(fileURLToPath(import.meta.url)), "..");

// ---------------------------------------------------------------------------
// Configuracao (env) e guarda de host local (RF6 / CA3) -- tudo antes de
// qualquer requisicao.
// ---------------------------------------------------------------------------

// Variaveis ja definidas no ambiente vencem; depois .env.seed.local (ignorado
// pelo git), .env.seed e, por fim, os valores de teste do .env.seed.example.
for (const file of [".env.seed.local", ".env.seed", ".env.seed.example"]) {
  const path = join(WEB_DIR, file);
  if (existsSync(path)) process.loadEnvFile(path);
}

class SeedError extends Error {}

const LOCAL_HOSTS = new Set(["localhost", "127.0.0.1"]);

function localBaseUrl(name, raw) {
  let url;
  try {
    url = new URL(raw);
  } catch {
    throw new SeedError(`${name} invalida: "${raw}".`);
  }
  if (!["http:", "https:"].includes(url.protocol) || !LOCAL_HOSTS.has(url.hostname)) {
    throw new SeedError(
      `Recusando rodar: ${name}="${raw}" nao e local. O seed so roda contra localhost/127.0.0.1.`,
    );
  }
  return url.origin;
}

function loadConfig() {
  const env = process.env;
  const base = localBaseUrl("API_BASE_URL", env.API_BASE_URL || "http://localhost:8080");
  const urls = {
    customers: localBaseUrl("CUSTOMERS_URL", env.CUSTOMERS_URL || base),
    products: localBaseUrl("PRODUCTS_URL", env.PRODUCTS_URL || base),
    staff: localBaseUrl("STAFF_URL", env.STAFF_URL || base),
    booking: localBaseUrl("BOOKING_URL", env.BOOKING_URL || base),
    orders: localBaseUrl("ORDERS_URL", env.ORDERS_URL || base),
  };
  const missing = ["BOOTSTRAP_ADMIN_EMAIL", "BOOTSTRAP_ADMIN_PASSWORD", "SEED_CUSTOMER_PASSWORD", "SEED_STAFF_PASSWORD"].filter((k) => !env[k]);
  if (missing.length > 0) {
    throw new SeedError(
      `Variaveis obrigatorias ausentes: ${missing.join(", ")} (admin de bootstrap: do .env da raiz; senhas de exemplo: do web/.env.seed.example). ` +
        "Defina no ambiente ou em web/.env.seed.local -- veja web/.env.seed.example.",
    );
  }
  return {
    urls,
    adminEmail: env.BOOTSTRAP_ADMIN_EMAIL,
    adminPassword: env.BOOTSTRAP_ADMIN_PASSWORD,
    adminNewPassword: env.SEED_ADMIN_NEW_PASSWORD || "",
    customerPassword: env.SEED_CUSTOMER_PASSWORD,
    staffPassword: env.SEED_STAFF_PASSWORD,
    kafkaContainer: env.KAFKA_CONTAINER || "petshop-kafka",
  };
}

// ---------------------------------------------------------------------------
// Dados de exemplo
// ---------------------------------------------------------------------------

// CPF valido gerado de forma deterministica: 9 digitos-base fixos por pessoa
// + 2 digitos verificadores calculados (mesmo CPF a cada execucao).
function cpfFromBase(base9) {
  const digits = base9.split("").map(Number);
  const dv = (nums) => {
    const factor = nums.length + 1;
    const sum = nums.reduce((acc, n, i) => acc + n * (factor - i), 0);
    const rest = (sum * 10) % 11;
    return rest === 10 ? 0 : rest;
  };
  const d1 = dv(digits);
  const d2 = dv([...digits, d1]);
  return `${base9}${d1}${d2}`;
}

const MAIL_DOMAIN = "seed.petshop.test";

const PRODUCTS = [
  { name: "Ração Premium Cães Adultos 15kg", description: "Alimento completo para cães adultos de porte médio e grande, rico em proteína animal.", price: 189.9, stock: 40, dimensions: { weight: 15, height: 60, width: 40, length: 12 } },
  { name: "Ração Premium Gatos Castrados 10kg", description: "Fórmula balanceada para gatos castrados, com controle de peso e saúde urinária.", price: 159.9, stock: 35, dimensions: { weight: 10, height: 50, width: 35, length: 10 } },
  { name: "Areia Higiênica Aglomerante 4kg", description: "Areia de baixa poeira com alto poder de aglomeração e controle de odor.", price: 34.9, stock: 120, dimensions: { weight: 4, height: 25, width: 20, length: 8 } },
  { name: "Brinquedo Mordedor de Corda", description: "Corda de algodão trançado, ajuda na limpeza dos dentes durante a brincadeira.", price: 24.9, stock: 80, dimensions: { weight: 0.3, height: 20, width: 8, length: 8 } },
  { name: "Coleira Ajustável Nylon", description: "Coleira resistente com fivela de engate rápido, tamanhos P ao G.", price: 39.9, stock: 50, dimensions: { weight: 0.15, height: 2, width: 40, length: 3 } },
  { name: "Shampoo Neutro para Banho 500ml", description: "Shampoo suave, livre de corantes, indicado para cães e gatos de pele sensível.", price: 29.9, stock: 45, dimensions: { weight: 0.55, height: 18, width: 6, length: 6 } },
  { name: "Arranhador para Gatos", description: "Base em sisal natural com pelúcia, estimula o comportamento natural de arranhar.", price: 119.9, stock: 15, dimensions: { weight: 3.2, height: 45, width: 35, length: 35 } },
  { name: "Caminha Acolchoada Média", description: "Caminha macia e lavável, ideal para cães e gatos de até 15kg.", price: 149.9, stock: 20, dimensions: { weight: 1.8, height: 15, width: 60, length: 50 } },
  { name: "Petisco Dental para Cães (pacote)", description: "Petisco mastigável que ajuda a reduzir o tártaro. Estoque baixo de propósito.", price: 22.5, stock: 3, dimensions: { weight: 0.25, height: 20, width: 12, length: 5 } },
  { name: "Fonte de Água Automática", description: "Fonte com filtro de carvão ativado. Esgotado de propósito, para testar o estado sem estoque.", price: 219.9, stock: 0, dimensions: { weight: 1.4, height: 20, width: 20, length: 20 } },
];

// Nomes UNICOS: o calculo de vagas do booking compara funcionario por nome.
const STAFF = [
  { name: "Carla Souza (Seed)", cpfBase: "412907356", email: `carla.recepcao@${MAIL_DOMAIN}`, phone: "11977771111", role: "RECEPTIONIST" },
  { name: "Paulo Lima (Seed)", cpfBase: "538261904", email: `paulo.tosador@${MAIL_DOMAIN}`, phone: "11977772222", role: "GROOMER" },
  { name: "Marina Alves (Seed)", cpfBase: "673018245", email: `marina.tosadora@${MAIL_DOMAIN}`, phone: "11977773333", role: "GROOMER" },
  { name: "Dr. Henrique Costa (Seed)", cpfBase: "195843627", email: `henrique.vet@${MAIL_DOMAIN}`, phone: "11977774444", role: "VETERINARIAN" },
].map((s) => ({ ...s, cpf: cpfFromBase(s.cpfBase) }));

// bookings: dayIndex = n-esimo dia util a partir de amanha; scenario = estado final.
const CUSTOMERS = [
  {
    name: "Ana Beatriz Ferreira",
    cpfBase: "356120489",
    email: `ana.ferreira@${MAIL_DOMAIN}`,
    phone: "11988881111",
    pets: [
      { petName: "Thor", petType: "DOG", petBreed: "Golden Retriever", petSize: "LARGE", weightInKg: 32 },
      { petName: "Mimi", petType: "CAT", petBreed: "Siamês", petSize: "SMALL", weightInKg: 4 },
    ],
    bookings: [
      { pet: "Thor", service: "BANHO", scenario: "SCHEDULED", dayIndex: 0, hour: 10 },
      { pet: "Mimi", service: "CONSULTA_VETERINARIA", scenario: "SCHEDULED", dayIndex: 1, hour: 14 },
      { pet: "Thor", service: "TOSA_E_BANHO", scenario: "CANCELED", dayIndex: 2, hour: 15 },
      { pet: "Thor", service: "CONSULTA_VETERINARIA", scenario: "COMPLETED", dayIndex: 3, hour: 9 },
    ],
    orders: [
      [{ product: "Ração Premium Cães Adultos 15kg", quantity: 1 }, { product: "Shampoo Neutro para Banho 500ml", quantity: 2 }],
      [{ product: "Brinquedo Mordedor de Corda", quantity: 1 }, { product: "Coleira Ajustável Nylon", quantity: 1 }],
    ],
  },
  {
    name: "Bruno Henrique Tavares",
    cpfBase: "247895013",
    email: `bruno.tavares@${MAIL_DOMAIN}`,
    phone: "11988882222",
    pets: [{ petName: "Luna", petType: "DOG", petBreed: "Poodle", petSize: "SMALL", weightInKg: 6.5 }],
    bookings: [
      { pet: "Luna", service: "TOSAGEM", scenario: "SCHEDULED", dayIndex: 0, hour: 11 },
      { pet: "Luna", service: "BANHO", scenario: "COMPLETED", dayIndex: 1, hour: 16 },
    ],
    orders: [[{ product: "Ração Premium Gatos Castrados 10kg", quantity: 1 }, { product: "Areia Higiênica Aglomerante 4kg", quantity: 2 }]],
  },
  {
    name: "Carolina Mendes Rocha",
    cpfBase: "819364572",
    email: `carolina.rocha@${MAIL_DOMAIN}`,
    phone: "11988883333",
    pets: [{ petName: "Pipoca", petType: "CAT", petBreed: "Persa", petSize: "MEDIUM", weightInKg: 5.2 }],
    bookings: [{ pet: "Pipoca", service: "CONSULTA_VETERINARIA", scenario: "SCHEDULED", dayIndex: 2, hour: 10 }],
    orders: [],
    // Item deixado no carrinho (sem checkout) para a tela de carrinho.
    cart: [{ product: "Arranhador para Gatos", quantity: 1 }],
  },
].map((c) => ({ ...c, cpf: cpfFromBase(c.cpfBase) }));

// ---------------------------------------------------------------------------
// Resumo (RF7)
// ---------------------------------------------------------------------------

const summary = { created: [], existing: [], skipped: [], warnings: [] };
const log = (msg) => console.log(msg);
const created = (what) => (summary.created.push(what), log(`  + criado:      ${what}`));
const existing = (what) => (summary.existing.push(what), log(`  = ja existia:  ${what}`));
const skipped = (what, reason) => (summary.skipped.push(`${what} -- ${reason}`), log(`  ! pulado:      ${what} (${reason})`));

// ---------------------------------------------------------------------------
// HTTP: fetch com tratamento de 429 (rate limit) e JSON tolerante
// ---------------------------------------------------------------------------

const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms));
const MAX_ATTEMPTS = 12;

async function request(method, baseUrl, path, { token, body, query } = {}) {
  const url = new URL(path, baseUrl);
  for (const [k, v] of Object.entries(query ?? {})) url.searchParams.set(k, String(v));
  for (let attempt = 1; ; attempt++) {
    let res;
    try {
      res = await fetch(url, {
        method,
        headers: {
          ...(body !== undefined ? { "Content-Type": "application/json" } : {}),
          ...(token ? { Authorization: `Bearer ${token}` } : {}),
        },
        body: body !== undefined ? JSON.stringify(body) : undefined,
      });
    } catch (err) {
      throw new SeedError(`Falha de rede em ${method} ${url.origin}${url.pathname}: ${err.cause?.code ?? err.message}. A stack esta de pe?`);
    }
    if (res.status === 429 && attempt < MAX_ATTEMPTS) {
      const retryAfter = Number(res.headers.get("retry-after"));
      const waitSec = Math.min(Number.isFinite(retryAfter) && retryAfter > 0 ? retryAfter : 12, 90);
      log(`    ... 429 (rate limit) em ${method} ${url.pathname}; aguardando ${waitSec}s (tentativa ${attempt}/${MAX_ATTEMPTS})`);
      await sleep(waitSec * 1000 + 250);
      continue;
    }
    const text = await res.text();
    let data = text;
    try {
      data = text ? JSON.parse(text) : null;
    } catch {
      /* corpo nao-JSON: mantem texto */
    }
    return { ok: res.ok, status: res.status, data };
  }
}

const describe = (r) => `${r.status} ${typeof r.data === "string" ? r.data : JSON.stringify(r.data)}`.slice(0, 300);
const fail = (what, r) => {
  throw new SeedError(`${what} falhou: ${describe(r)}`);
};
const items = (data) => (Array.isArray(data) ? data : (data?.content ?? []));

function decodeJwt(token) {
  try {
    return JSON.parse(Buffer.from(token.split(".")[1], "base64url").toString("utf8"));
  } catch {
    return {};
  }
}

// ---------------------------------------------------------------------------
// Sessoes (um login por usuario por execucao; re-login se o token expirar)
// ---------------------------------------------------------------------------

class Session {
  constructor(cfg, email, passwords) {
    this.cfg = cfg;
    this.email = email;
    this.passwords = passwords; // lista de senhas a tentar, em ordem
    this.token = null;
    this.mustChangePassword = false;
    this.workingPassword = null;
  }

  // Devolve {ok:true} ou {ok:false,status}. 401 = credencial invalida/inexistente.
  async login() {
    for (const password of this.passwords.filter(Boolean)) {
      const r = await request("POST", this.cfg.urls.customers, "/api/v1/auth/login", { body: { email: this.email, password } });
      if (r.ok) {
        this.token = r.data.accessToken;
        this.mustChangePassword = Boolean(r.data.mustChangePassword);
        this.workingPassword = password;
        return { ok: true };
      }
      if (r.status !== 401) fail(`login de ${this.email}`, r);
    }
    return { ok: false, status: 401 };
  }

  get claims() {
    return this.token ? decodeJwt(this.token) : {};
  }
  get userId() {
    return this.claims.sub;
  }
  get emailVerified() {
    return this.claims.email_verified === true;
  }

  // Chamada autenticada; se o token expirou (401), reloga uma vez.
  async call(method, baseUrl, path, opts = {}) {
    let r = await request(method, baseUrl, path, { ...opts, token: this.token });
    if (r.status === 401 && this.workingPassword) {
      const relogin = await this.login();
      if (relogin.ok) r = await request(method, baseUrl, path, { ...opts, token: this.token });
    }
    return r;
  }
}

async function changePassword(cfg, session, newPassword, { relogin = true } = {}) {
  const r = await session.call("PATCH", cfg.urls.customers, "/api/v1/auth/change-password", {
    body: { email: session.email, currentPassword: session.workingPassword, newPassword },
  });
  if (!r.ok) fail(`troca de senha de ${session.email}`, r);
  session.passwords = [newPassword];
  if (!relogin) return;
  const again = await session.login();
  if (!again.ok) throw new SeedError(`login de ${session.email} falhou apos a troca de senha`);
}

// ---------------------------------------------------------------------------
// Etapas
// ---------------------------------------------------------------------------

async function loginAdmin(cfg) {
  log("\n[1/8] Admin de bootstrap");
  const admin = new Session(cfg, cfg.adminEmail, [cfg.adminPassword, cfg.adminNewPassword]);
  const result = await admin.login();
  if (!result.ok) {
    throw new SeedError(
      "Login do admin recusado (401). Confira BOOTSTRAP_ADMIN_EMAIL/BOOTSTRAP_ADMIN_PASSWORD" +
        " (e SEED_ADMIN_NEW_PASSWORD, se o admin ja teve a senha trocada pelo seed).",
    );
  }
  if (admin.mustChangePassword) {
    if (!cfg.adminNewPassword) {
      throw new SeedError(
        "O admin de bootstrap exige troca de senha no primeiro acesso (mustChangePassword=true). " +
          "Defina SEED_ADMIN_NEW_PASSWORD (senha nova, minimo 6 caracteres) e rode de novo.",
      );
    }
    await changePassword(cfg, admin, cfg.adminNewPassword);
    summary.warnings.push(
      "A senha do admin de bootstrap foi TROCADA pelo seed para o valor de SEED_ADMIN_NEW_PASSWORD. " +
        "Daqui em diante entre no painel interno com ela (a BOOTSTRAP_ADMIN_PASSWORD deixou de valer).",
    );
    created(`troca de senha do admin ${cfg.adminEmail} (SEED_ADMIN_NEW_PASSWORD)`);
  } else {
    existing(`admin ${cfg.adminEmail} (sem troca de senha pendente)`);
  }
  return admin;
}

async function seedProducts(cfg, admin) {
  log("\n[2/8] Produtos");
  const list = await admin.call("GET", cfg.urls.products, "/api/v1/products/list", { query: { size: 500 } });
  if (!list.ok) fail("listagem de produtos", list);
  const byName = new Map(items(list.data).map((p) => [p.name, p]));
  for (const product of PRODUCTS) {
    const found = byName.get(product.name);
    if (found) {
      existing(`produto "${product.name}"`);
      continue;
    }
    const r = await admin.call("POST", cfg.urls.products, "/api/v1/products/register", { body: product });
    if (r.status === 409) {
      existing(`produto "${product.name}" (409)`);
      continue;
    }
    if (!r.ok) fail(`cadastro do produto "${product.name}"`, r);
    byName.set(product.name, r.data);
    created(`produto "${product.name}" (estoque ${product.stock})`);
  }
  return byName;
}

async function seedStaff(cfg, admin) {
  log("\n[3/8] Staff (um de cada cargo; 2 tosadores para a escala de sabado)");
  const list = await admin.call("GET", cfg.urls.customers, "/api/v1/staff/all", { query: { size: 500 } });
  if (!list.ok) fail("listagem de staff", list);
  const known = items(list.data);
  for (const staff of STAFF) {
    const found = known.find((s) => s.email?.toLowerCase() === staff.email || s.cpf === staff.cpf);
    if (found) {
      existing(`staff ${staff.role} ${staff.name}`);
    } else {
      const { cpfBase: _cpfBase, ...body } = staff;
      const r = await admin.call("POST", cfg.urls.customers, "/api/v1/staff/create", { body });
      if (r.status === 409) existing(`staff ${staff.role} ${staff.name} (409)`);
      else if (!r.ok) fail(`cadastro do staff ${staff.name}`, r);
      else created(`staff ${staff.role} ${staff.name}`);
    }
    await normalizeStaffPassword(cfg, staff);
  }
}

// A conta de staff nasce com senha = CPF e mustChangePassword. Para o login de
// exemplo ser estavel, o seed troca para SEED_STAFF_PASSWORD (idempotente).
async function normalizeStaffPassword(cfg, staff) {
  const session = new Session(cfg, staff.email, [cfg.staffPassword, staff.cpf]);
  const result = await session.login();
  if (!result.ok) {
    skipped(`senha de exemplo do staff ${staff.email}`, "login recusado com SEED_STAFF_PASSWORD e com o CPF inicial (senha alterada manualmente?)");
    return;
  }
  if (session.workingPassword === cfg.staffPassword && !session.mustChangePassword) return;
  await changePassword(cfg, session, cfg.staffPassword, { relogin: false });
  created(`senha de exemplo do staff ${staff.email} (SEED_STAFF_PASSWORD)`);
}

function nextSaturday(from = new Date()) {
  const d = new Date(from.getFullYear(), from.getMonth(), from.getDate());
  d.setDate(d.getDate() + ((6 - d.getDay() + 7) % 7));
  return d;
}

const isoDate = (d) => `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}-${String(d.getDate()).padStart(2, "0")}`;

async function seedWeekendAllocation(cfg, admin) {
  log("\n[4/8] Escala de fim de semana");
  const saturday = isoDate(nextSaturday());
  const has = async () => {
    const r = await admin.call("GET", cfg.urls.staff, "/api/v1/weekend-allocation", { query: { date: saturday } });
    if (!r.ok) fail("consulta da escala de sabado", r);
    return items(r.data).length > 0;
  };
  if (await has()) {
    existing(`escala de sabado (${saturday} ja tem alocacao)`);
    return;
  }
  // O endpoint preenche o proximo mes ainda sem escala; no maximo 2 geracoes
  // (mes corrente e, se o proximo sabado ja cair no mes seguinte, o seguinte).
  for (let i = 0; i < 2; i++) {
    const r = await admin.call("POST", cfg.urls.staff, "/api/v1/weekend-allocation/generate");
    if (!r.ok) {
      skipped("escala de fim de semana", `geracao recusada: ${describe(r)}`);
      return;
    }
    created(`escala de sabados (${items(r.data).length} alocacoes)`);
    if (await has()) return;
  }
  summary.warnings.push(`Escala gerada, mas o proximo sabado (${saturday}) segue sem alocacao.`);
}

// --- Clientes / pets / verificacao de e-mail -------------------------------

async function seedCustomers(cfg) {
  log("\n[5/8] Clientes e pets");
  const out = [];
  for (const def of CUSTOMERS) {
    const session = new Session(cfg, def.email, [cfg.customerPassword]);
    const entry = { def, session, ready: false };
    out.push(entry);
    let login = await session.login();
    if (login.ok) {
      existing(`cliente ${def.email}`);
    } else {
      const r = await request("POST", cfg.urls.customers, "/api/v1/customers/register/customer", {
        body: {
          name: def.name,
          cpf: def.cpf,
          email: def.email,
          phone: def.phone,
          password: cfg.customerPassword,
          confirmPassword: cfg.customerPassword,
        },
      });
      if (r.status === 409) {
        skipped(`cliente ${def.email}`, "e-mail/CPF ja cadastrado com outra senha; nao e possivel logar para o seed");
        continue;
      }
      if (!r.ok) fail(`cadastro do cliente ${def.email}`, r);
      created(`cliente ${def.email}`);
      login = await session.login();
      if (!login.ok) throw new SeedError(`login do cliente recem-criado ${def.email} falhou`);
    }
    entry.ready = true;
    await seedPets(cfg, entry);
  }
  return out;
}

async function seedPets(cfg, entry) {
  const { def, session } = entry;
  const listPets = async () => {
    const r = await session.call("GET", cfg.urls.customers, `/api/v1/customers/${session.userId}/pets`, { query: { size: 100 } });
    if (!r.ok) fail(`listagem de pets de ${def.email}`, r);
    return items(r.data);
  };
  let pets = await listPets();
  const missing = def.pets.filter((p) => !pets.some((x) => x.petName === p.petName));
  for (const p of def.pets) {
    if (!missing.includes(p)) existing(`pet ${p.petName} (${def.name})`);
  }
  if (missing.length > 0) {
    const r = await session.call("PATCH", cfg.urls.customers, `/api/v1/customers/register/${session.userId}/pet`, { body: missing });
    if (!r.ok) fail(`cadastro de pets de ${def.email}`, r);
    for (const p of missing) created(`pet ${p.petName} (${def.name})`);
    pets = await listPets();
  }
  entry.pets = new Map(pets.map((p) => [p.petName, p]));
}

// Le o token de verificacao dos comandos de e-mail no Kafka (em dev nao ha
// e-mail real). Devolve Map email -> ultimo token, ou null se nao der.
function readVerificationTokens(cfg) {
  const script =
    "printf 'security.protocol=SASL_PLAINTEXT\\nsasl.mechanism=PLAIN\\nsasl.jaas.config=org.apache.kafka.common.security.plain.PlainLoginModule required username=\"%s\" password=\"%s\";\\n' " +
    '"$KAFKA_INTER_BROKER_USER" "$KAFKA_INTER_BROKER_PASSWORD" > /tmp/seed.properties && ' +
    "/opt/bitnami/kafka/bin/kafka-console-consumer.sh --bootstrap-server localhost:9092 --topic notification-commands " +
    "--from-beginning --timeout-ms 10000 --consumer.config /tmp/seed.properties 2>/dev/null";
  let output;
  try {
    output = execFileSync("docker", ["exec", cfg.kafkaContainer, "bash", "-c", script], {
      encoding: "utf8",
      maxBuffer: 64 * 1024 * 1024,
      timeout: 90_000,
      stdio: ["ignore", "pipe", "ignore"],
    });
  } catch {
    return null;
  }
  const tokens = new Map();
  for (const line of output.split(/\r?\n/)) {
    if (!line.includes("VERIFICACAO_EMAIL")) continue;
    try {
      const cmd = JSON.parse(line);
      if ((cmd.tipo ?? cmd.type) !== "VERIFICACAO_EMAIL") continue;
      const token = new URL(cmd.params.verificationLink).searchParams.get("token");
      if (cmd.to && token) tokens.set(String(cmd.to).toLowerCase(), token);
    } catch {
      /* linha que nao e um comando valido: ignora */
    }
  }
  return tokens;
}

async function verifyEmails(cfg, customers) {
  log("\n[6/8] Verificacao de e-mail dos clientes (SDD-020)");
  const pending = customers.filter((c) => c.ready && !c.session.emailVerified);
  for (const c of customers.filter((x) => x.ready && x.session.emailVerified)) existing(`e-mail verificado de ${c.def.email}`);
  if (pending.length === 0) return;

  const tryVerify = async (entry, tokens) => {
    const token = tokens?.get(entry.def.email.toLowerCase());
    if (!token) return false;
    const r = await request("POST", cfg.urls.customers, "/api/v1/auth/verify-email", { body: { token } });
    if (r.status === 400) return false; // token usado/expirado/invalido: tenta reenviar
    if (!r.ok) fail(`verificacao de e-mail de ${entry.def.email}`, r);
    return true;
  };

  let tokens = readVerificationTokens(cfg);
  if (tokens === null) {
    for (const c of pending) {
      c.ready = false;
      skipped(`agendamentos/pedidos de ${c.def.email}`, `e-mail nao verificado e nao foi possivel ler o Kafka via "docker exec ${cfg.kafkaContainer}" (Docker/Kafka indisponivel?)`);
    }
    return;
  }

  const stillPending = [];
  for (const c of pending) {
    if (!(await tryVerify(c, tokens))) stillPending.push(c);
    else await finishVerification(cfg, c);
  }
  if (stillPending.length > 0) {
    for (const c of stillPending) {
      await request("POST", cfg.urls.customers, "/api/v1/auth/resend-verification", { body: { email: c.def.email } });
    }
    await sleep(3000);
    tokens = readVerificationTokens(cfg);
    for (const c of stillPending) {
      if (await tryVerify(c, tokens)) await finishVerification(cfg, c);
      else {
        c.ready = false;
        skipped(`agendamentos/pedidos de ${c.def.email}`, "token de verificacao nao encontrado no Kafka mesmo apos reenvio");
      }
    }
  }
}

async function finishVerification(cfg, entry) {
  const login = await entry.session.login();
  if (!login.ok || !entry.session.emailVerified) {
    entry.ready = false;
    skipped(`agendamentos/pedidos de ${entry.def.email}`, "verificacao confirmada, mas o novo login nao trouxe email_verified=true");
    return;
  }
  created(`e-mail verificado de ${entry.def.email}`);
}

// --- Agendamentos ----------------------------------------------------------

function businessDays(count, from = new Date()) {
  const days = [];
  const d = new Date(from.getFullYear(), from.getMonth(), from.getDate());
  while (days.length < count) {
    d.setDate(d.getDate() + 1);
    if (d.getDay() !== 0 && d.getDay() !== 6) days.push(isoDate(d));
  }
  return days;
}

async function pickSlot(cfg, service, days, startIndex, preferredHour) {
  const earliest = Date.now() + 2 * 60 * 60 * 1000 + 5 * 60 * 1000;
  for (let i = startIndex; i < Math.min(days.length, startIndex + 5); i++) {
    const r = await request("GET", cfg.urls.booking, "/api/v1/bookings/available-slots", {
      query: { serviceType: service, date: days[i], range: "DAY" },
    });
    if (!r.ok) fail(`horarios disponiveis (${service} ${days[i]})`, r);
    const slots = items(r.data).filter((s) => s.availableStaff > 0 && new Date(s.dateTime).getTime() > earliest);
    if (slots.length === 0) continue;
    const hourOf = (s) => Number(s.dateTime.slice(11, 13));
    slots.sort((a, b) => Math.abs(hourOf(a) - preferredHour) - Math.abs(hourOf(b) - preferredHour));
    return slots[0].dateTime;
  }
  return null;
}

async function seedBookings(cfg, admin, customers) {
  log("\n[7/8] Agendamentos");
  const days = businessDays(14);
  for (const entry of customers) {
    const { def, session } = entry;
    if (!entry.ready) continue;
    const listed = await session.call("GET", cfg.urls.booking, "/api/v1/bookings/list", { query: { size: 200 } });
    if (!listed.ok) fail(`listagem de agendamentos de ${def.email}`, listed);
    const mine = items(listed.data);
    for (const b of def.bookings) {
      const label = `agendamento ${b.service} ${b.scenario} (${b.pet}, ${def.name})`;
      const pet = entry.pets?.get(b.pet);
      if (!pet) {
        skipped(label, "pet nao encontrado");
        continue;
      }
      if (mine.some((x) => x.petId === pet.id && x.serviceType?.serviceType === b.service && x.status === b.scenario)) {
        existing(label);
        continue;
      }
      const dateTime = await pickSlot(cfg, b.service, days, b.dayIndex, b.hour);
      if (!dateTime) {
        skipped(label, "nenhum horario disponivel nos proximos dias uteis");
        continue;
      }
      const r = await session.call("POST", cfg.urls.booking, "/api/v1/bookings/create", {
        body: {
          petId: pet.id,
          ownerName: def.name,
          ownerCpf: def.cpf,
          ownerContact: def.phone,
          ownerEmail: def.email,
          serviceType: b.service,
          bookingDateTime: dateTime,
          observations: "Agendamento de exemplo (seed).",
        },
      });
      if (r.status === 409) {
        existing(`${label} (409)`);
        continue;
      }
      if (!r.ok) {
        skipped(label, `criacao recusada: ${describe(r)}`);
        continue;
      }
      const id = r.data.id;
      if (b.scenario === "CANCELED") {
        const c = await session.call("DELETE", cfg.urls.booking, `/api/v1/bookings/cancel/${id}`);
        if (!c.ok) {
          skipped(label, `criado, mas o cancelamento falhou: ${describe(c)}`);
          continue;
        }
      } else if (b.scenario === "COMPLETED") {
        // Nao ha caminho pela API para criar no passado (@FutureOrPresent); conclui-se um
        // agendamento futuro como ADMIN (PATCH update?status=COMPLETED nao valida data).
        const c = await admin.call("PATCH", cfg.urls.booking, `/api/v1/bookings/update/${id}`, { query: { status: "COMPLETED" }, body: {} });
        if (!c.ok) {
          skipped(label, `criado, mas a conclusao falhou: ${describe(c)}`);
          continue;
        }
        const p = await admin.call("PATCH", cfg.urls.booking, `/api/v1/bookings/confirm-payment/${id}`, { body: { paymentMethod: "PIX" } });
        if (!p.ok) summary.warnings.push(`${label}: concluido, mas a confirmacao de pagamento falhou: ${describe(p)}`);
      }
      created(`${label} em ${dateTime}`);
    }
  }
}

// --- Pedidos ---------------------------------------------------------------

// order-service nao tem "listar pedidos". Para ser idempotente o seed guarda os
// ids que criou em web/.seed-state.local (ignorado pelo git via *.local) e
// confirma cada um por GET /orders/{id}; alem disso conta as compras ja pagas
// no historico do cliente (customer-management), que cobre o caso de o arquivo
// ter sido apagado.
const STATE_FILE = join(WEB_DIR, ".seed-state.local");

function loadState() {
  try {
    return JSON.parse(readFileSync(STATE_FILE, "utf8"));
  } catch {
    return { orders: {} };
  }
}

async function seedOrders(cfg, customers, products) {
  log("\n[8/8] Carrinho e pedidos");
  const state = loadState();
  state.orders ??= {};
  for (const entry of customers) {
    const { def, session } = entry;
    if (!entry.ready) continue;
    const productId = (name) => {
      const p = products.get(name);
      if (!p?.id) throw new SeedError(`produto "${name}" nao encontrado para o pedido de ${def.email}`);
      return p.id;
    };

    // Pedidos que ja existem de verdade (ids do estado confirmados na API).
    const known = [];
    for (const id of state.orders[def.email] ?? []) {
      const r = await session.call("GET", cfg.urls.orders, `/api/v1/orders/${id}`);
      if (r.ok) known.push(id);
    }
    state.orders[def.email] = known;
    const hist = await session.call("GET", cfg.urls.customers, `/api/v1/history/purchases/${session.userId}`);
    const paidCount = hist.ok ? items(hist.data).length : 0;
    const alreadyHave = Math.max(known.length, paidCount);

    for (let i = 0; i < def.orders.length; i++) {
      const label = `pedido ${i + 1} de ${def.name}`;
      if (i < alreadyHave) {
        existing(label);
        continue;
      }
      const lines = def.orders[i];
      const cartNow = await session.call("GET", cfg.urls.orders, "/api/v1/cart", { query: { customerId: session.userId } });
      const inCart = new Map(items(cartNow.data?.items).map((x) => [x.productId, x.quantity]));
      for (const line of lines) {
        const id = productId(line.product);
        if ((inCart.get(id) ?? 0) >= line.quantity) continue;
        const add = await session.call("POST", cfg.urls.orders, "/api/v1/cart/items", {
          body: { customerId: session.userId, productId: id, quantity: line.quantity - (inCart.get(id) ?? 0) },
        });
        if (!add.ok) {
          skipped(label, `item "${line.product}" recusado no carrinho: ${describe(add)}`);
          break;
        }
      }
      const r = await session.call("POST", cfg.urls.orders, "/api/v1/orders/checkout", {
        query: { customerId: session.userId },
        body: { productIds: lines.map((l) => productId(l.product)) },
      });
      if (!r.ok) {
        skipped(label, `checkout recusado: ${describe(r)}`);
        continue;
      }
      state.orders[def.email].push(r.data.id);
      writeFileSync(STATE_FILE, JSON.stringify(state, null, 2));
      created(`${label} (${r.data.status}, R$ ${Number(r.data.totalAmount).toFixed(2)})`);
    }

    for (const line of def.cart ?? []) {
      const id = productId(line.product);
      const cartNow = await session.call("GET", cfg.urls.orders, "/api/v1/cart", { query: { customerId: session.userId } });
      if (items(cartNow.data?.items).some((x) => x.productId === id)) {
        existing(`item no carrinho "${line.product}" (${def.name})`);
        continue;
      }
      const add = await session.call("POST", cfg.urls.orders, "/api/v1/cart/items", {
        body: { customerId: session.userId, productId: id, quantity: line.quantity },
      });
      if (add.ok) created(`item no carrinho "${line.product}" (${def.name})`);
      else skipped(`item no carrinho "${line.product}" (${def.name})`, describe(add));
    }
  }
  writeFileSync(STATE_FILE, JSON.stringify(state, null, 2));
}

// ---------------------------------------------------------------------------
// Resumo final (RF7)
// ---------------------------------------------------------------------------

function printSummary(cfg) {
  log("\n==================== RESUMO ====================");
  log(`Criados:      ${summary.created.length}`);
  log(`Ja existiam:  ${summary.existing.length}`);
  log(`Pulados:      ${summary.skipped.length}`);
  for (const s of summary.skipped) log(`  - ${s}`);
  for (const w of summary.warnings) log(`\nATENCAO: ${w}`);
  log("\nLogins de exemplo");
  log(`  Admin (painel interno): ${cfg.adminEmail}`);
  log(`    senha: SEED_ADMIN_NEW_PASSWORD (a definida por voce; nao e impressa aqui)`);
  log("  Staff (painel interno) -- senha: SEED_STAFF_PASSWORD (.env.seed / .env.seed.example)");
  for (const s of STAFF) log(`    ${s.role.padEnd(13)} ${s.email}`);
  log("  Clientes (loja) -- senha: SEED_CUSTOMER_PASSWORD (.env.seed / .env.seed.example)");
  for (const c of CUSTOMERS) log(`    ${c.email}`);
  log("\nObs.: o pagamento dos pedidos e simulado pelo order-service (aprova apos ~30 s); pedidos nao pagos expiram em 30 min.");
}

async function main() {
  const cfg = loadConfig(); // aborta aqui, antes de qualquer requisicao, se a URL nao for local
  log(`Seed SDD-021 -> ${cfg.urls.customers}`);
  const admin = await loginAdmin(cfg);
  const products = await seedProducts(cfg, admin);
  await seedStaff(cfg, admin);
  await seedWeekendAllocation(cfg, admin);
  const customers = await seedCustomers(cfg);
  await verifyEmails(cfg, customers);
  await seedBookings(cfg, admin, customers);
  await seedOrders(cfg, customers, products);
  printSummary(cfg);
}

main().catch((err) => {
  console.error(`\nERRO: ${err instanceof SeedError ? err.message : (err.stack ?? err)}`);
  process.exit(1);
});
