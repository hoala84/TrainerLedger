export const SORT = { BY_UPDATED: "updated", ALPHABETICAL: "alpha" };

export function startOfDay(ms = Date.now()) {
  const d = new Date(ms);
  d.setHours(0, 0, 0, 0);
  return d.getTime();
}

export function endOfDay(ms) {
  const d = new Date(startOfDay(ms));
  d.setHours(23, 59, 59, 999);
  return d.getTime();
}

export function startOfMonth(ms = Date.now()) {
  const d = new Date(ms);
  d.setDate(1);
  d.setHours(0, 0, 0, 0);
  return d.getTime();
}

export function fromInputDate(value) {
  if (!value) return startOfDay();
  const [y, m, d] = value.split("-").map(Number);
  return new Date(y, m - 1, d).getTime();
}

export function toInputDate(ms) {
  const d = new Date(ms);
  const p = (n) => String(n).padStart(2, "0");
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}`;
}

export function formatDisplay(ms) {
  return new Date(ms).toLocaleDateString("ru-RU", {
    day: "numeric",
    month: "long",
    year: "numeric",
  });
}

export function formatShort(ms) {
  return new Date(ms).toLocaleDateString("ru-RU");
}

export function formatFileDate(ms = Date.now()) {
  const d = new Date(ms);
  const p = (n) => String(n).padStart(2, "0");
  return `${d.getFullYear()}${p(d.getMonth() + 1)}${p(d.getDate())}`;
}

export function formatMoney(amount) {
  const n = Number(amount) || 0;
  return new Intl.NumberFormat("ru-RU", {
    style: "currency",
    currency: "RUB",
    maximumFractionDigits: n % 1 === 0 ? 0 : 2,
  }).format(n);
}

export function parseMoney(raw) {
  const normalized = String(raw).trim().replace(/\s/g, "").replace(",", ".");
  if (!normalized) return null;
  const n = Number(normalized);
  return Number.isFinite(n) ? n : null;
}

export function displayName(client) {
  return `${client.lastName} ${client.firstName}`.trim();
}

export function remainingOf(payments, workouts, clientId) {
  const paid = payments
    .filter((p) => p.clientId === clientId)
    .reduce((sum, p) => sum + p.workoutCount, 0);
  const used = workouts.filter((w) => w.clientId === clientId && (w.type === "PAID" || w.settledByPaymentId != null)).length;
  return paid - used;
}

export function debtOf(workouts, clientId) {
  return workouts.filter((w) => w.clientId === clientId && w.type === "DEBT" && w.settledByPaymentId == null).length;
}

function nextId(items) {
  return items.reduce((max, item) => Math.max(max, item.id || 0), 0) + 1;
}

export function sortClients(clients, payments, workouts, sort) {
  const rows = clients.map((client) => ({
    client,
    remaining: remainingOf(payments, workouts, client.id),
    debt: debtOf(workouts, client.id),
  }));
  if (sort === SORT.ALPHABETICAL) {
    rows.sort((a, b) =>
      displayName(a.client).localeCompare(displayName(b.client), "ru", { sensitivity: "base" }),
    );
  } else {
    rows.sort((a, b) => b.client.updatedAt - a.client.updatedAt);
  }
  return rows;
}

export function sortByEventDate(items) {
  return [...items].sort((a, b) => b.date - a.date || b.id - a.id);
}

function touch(state, clientId) {
  const now = Date.now();
  state.clients = state.clients.map((c) =>
    c.id === clientId ? { ...c, updatedAt: now } : c,
  );
}

function clientDetails(state, id, lastName, firstName, details) {
  const clean = (s) => s.trim().replace(/\s+/g, " ");
  lastName = clean(lastName);
  firstName = clean(firstName);
  if (!lastName || !firstName) throw new Error("Укажите имя и фамилию");
  if (state.clients.some((c) => c.id !== id && clean(c.lastName).toLocaleLowerCase("ru") === lastName.toLocaleLowerCase("ru") && clean(c.firstName).toLocaleLowerCase("ru") === firstName.toLocaleLowerCase("ru"))) {
    throw new Error("Клиент с таким именем и фамилией уже существует");
  }
  const birthDate = details.birthDate || null;
  if (birthDate && (!/^\d{4}-\d{2}-\d{2}$/.test(birthDate) || toInputDate(fromInputDate(birthDate)) !== birthDate || birthDate > toInputDate(Date.now()))) {
    throw new Error("Укажите корректную дату рождения, не позже сегодняшней");
  }
  return { lastName, firstName, phone: (details.phone || "").trim(), comment: (details.comment || "").trim(), birthDate };
}

export function ageOf(birthDate, now = Date.now()) {
  if (!birthDate) return null;
  const today = toInputDate(now);
  return Number(today.slice(0, 4)) - Number(birthDate.slice(0, 4)) - (today.slice(5) < birthDate.slice(5) ? 1 : 0);
}

export function addClient(state, lastName, firstName, details = {}) {
  const client = {
    id: nextId(state.clients),
    ...clientDetails(state, null, lastName, firstName, details),
    updatedAt: Date.now(),
  };
  state.clients = [...state.clients, client];
  return client.id;
}

export function updateClient(state, id, lastName, firstName, details = {}) {
  const fields = clientDetails(state, id, lastName, firstName, { ...state.clients.find((c) => c.id === id), ...details });
  state.clients = state.clients.map((c) =>
    c.id === id
      ? { ...c, ...fields, updatedAt: Date.now() }
      : c,
  );
}

export function deleteClient(state, id) {
  state.clients = state.clients.filter((c) => c.id !== id);
  state.payments = state.payments.filter((p) => p.clientId !== id);
  state.workouts = state.workouts.filter((w) => w.clientId !== id);
}

export function settledCount(state, paymentId) {
  return state.workouts.filter((w) => w.settledByPaymentId === paymentId).length;
}

function savePayment(state, existing, clientId, date, amount, count, debtCount) {
  if (!Number.isFinite(amount) || amount < 0) throw new Error("Укажите корректную сумму");
  if (!Number.isInteger(count) || count < 0 || !Number.isInteger(debtCount) || debtCount < 0 || debtCount > count) {
    throw new Error("Число погашений должно быть от 0 до числа оплаченных занятий");
  }
  const id = existing?.id ?? nextId(state.payments);
  const available = state.workouts.filter((w) => w.clientId === clientId && w.type === "DEBT" && (w.settledByPaymentId == null || w.settledByPaymentId === id))
    .sort((a, b) => Number(b.settledByPaymentId === id) - Number(a.settledByPaymentId === id) || a.date - b.date || a.id - b.id);
  if (debtCount > available.length) throw new Error("Долгов стало меньше. Проверьте количество");
  const selected = new Set(available.slice(0, debtCount).map((w) => w.id));
  const previouslySettled = existing ? settledCount(state, id) : 0;
  const day = startOfDay(date);
  let autoId = state.workouts.find((w) => w.id === existing?.autoWorkoutId && w.type === "PAID")?.id ?? null;
  state.workouts = state.workouts.map((w) => ({ ...w, settledByPaymentId: selected.has(w.id) ? id : w.settledByPaymentId === id ? null : w.settledByPaymentId ?? null }));
  if (count === 1 && debtCount === 0) {
    if (autoId != null) {
      state.workouts = state.workouts.map((w) => w.id === autoId ? { ...w, date: day } : w);
    } else if (!existing || existing.workoutCount !== 1 || previouslySettled > 0) {
      autoId = nextId(state.workouts);
      state.workouts.push({ id: autoId, clientId, date: day, comment: "По оплате", type: "PAID", settledByPaymentId: null });
    }
  } else if (autoId != null) {
    state.workouts = state.workouts.filter((w) => w.id !== autoId);
    autoId = null;
  }
  const payment = { id, clientId, date: day, amount, workoutCount: count, autoWorkoutId: autoId };
  state.payments = existing ? state.payments.map((p) => p.id === id ? payment : p) : [...state.payments, payment];
  touch(state, clientId);
}

export function addPayment(state, clientId, date, amount, workoutCount, debtCount = 0) {
  savePayment(state, null, clientId, date, amount, workoutCount, debtCount);
}

export function updatePayment(state, paymentId, date, amount, workoutCount, debtCount = settledCount(state, paymentId)) {
  const existing = state.payments.find((p) => p.id === paymentId);
  if (existing) savePayment(state, existing, existing.clientId, date, amount, workoutCount, debtCount);
}

export function deletePayment(state, paymentId) {
  const existing = state.payments.find((p) => p.id === paymentId);
  if (!existing) return;
  state.workouts = state.workouts.filter((w) => !(w.id === existing.autoWorkoutId && w.type === "PAID"))
    .map((w) => w.settledByPaymentId === paymentId ? { ...w, settledByPaymentId: null } : w);
  state.payments = state.payments.filter((p) => p.id !== paymentId);
  touch(state, existing.clientId);
}
export function addWorkout(state, clientId, date, comment, type) {
  state.workouts = [
    ...state.workouts,
    {
      id: nextId(state.workouts),
      clientId,
      date: startOfDay(date),
      comment: comment.trim(),
      type,
    },
  ];
  touch(state, clientId);
}

export function updateWorkout(state, workoutId, date, comment, type) {
  const existing = state.workouts.find((w) => w.id === workoutId);
  if (!existing) return;
  if (existing.type !== type) state.payments = state.payments.map((p) => p.autoWorkoutId === workoutId ? { ...p, autoWorkoutId: null } : p);
  state.workouts = state.workouts.map((w) =>
    w.id === workoutId
      ? { ...w, date: startOfDay(date), comment: comment.trim(), type, settledByPaymentId: type === "DEBT" ? w.settledByPaymentId ?? null : null }
      : w,
  );
  touch(state, existing.clientId);
}

export function deleteWorkout(state, workoutId) {
  const existing = state.workouts.find((w) => w.id === workoutId);
  if (!existing) return;
  state.payments = state.payments.map((p) =>
    p.autoWorkoutId === workoutId ? { ...p, autoWorkoutId: null } : p,
  );
  state.workouts = state.workouts.filter((w) => w.id !== workoutId);
  touch(state, existing.clientId);
}

export function periodStats(state, from, to, clientId) {
  const start = startOfDay(from);
  const end = endOfDay(to);
  const clients = [...state.clients].sort((a, b) =>
    displayName(a).localeCompare(displayName(b), "ru", { sensitivity: "base" }),
  );
  const scoped = clientId == null ? clients : clients.filter((c) => c.id === clientId);
  const periodPayments = state.payments.filter((p) => p.date >= start && p.date <= end);
  const periodWorkouts = state.workouts.filter((w) => w.date >= start && w.date <= end);
  const perClient = scoped.map((client) => {
    const cPay = periodPayments.filter((p) => p.clientId === client.id);
    const cWork = periodWorkouts.filter((w) => w.clientId === client.id);
    return {
      client,
      completedWorkouts: cWork.length,
      giftWorkouts: cWork.filter((w) => w.type === "GIFT").length,
      debtWorkouts: debtOf(cWork, client.id),
      income: cPay.reduce((sum, p) => sum + p.amount, 0),
    };
  });
  return {
    totalWorkouts: perClient.reduce((s, r) => s + r.completedWorkouts, 0),
    giftWorkouts: perClient.reduce((s, r) => s + r.giftWorkouts, 0),
    debtWorkouts: perClient.reduce((s, r) => s + r.debtWorkouts, 0),
    totalIncome: perClient.reduce((s, r) => s + r.income, 0),
    perClient,
    periodPayments,
    periodWorkouts,
  };
}

export function daysInRange(from, to) {
  const days = [];
  const cursor = new Date(startOfDay(from));
  const end = startOfDay(to);
  while (cursor.getTime() <= end) {
    days.push(cursor.getTime());
    cursor.setDate(cursor.getDate() + 1);
  }
  return days;
}

export function exportBackup(state) {
  return {
    version: 1,
    exportedAt: Date.now(),
    clients: state.clients,
    payments: state.payments,
    workouts: state.workouts,
  };
}

export function restoreBackup(raw) {
  if (!raw || !Array.isArray(raw.clients)) {
    throw new Error("Неверный файл копии");
  }
  if ((raw.payments != null && !Array.isArray(raw.payments)) || (raw.workouts != null && !Array.isArray(raw.workouts))) throw new Error("Неверный файл копии");
  const payments = new Map((raw.payments || []).map((p) => [p.id, p]));
  const settled = new Map();
  for (const w of raw.workouts || []) {
    if (w.settledByPaymentId == null) continue;
    const p = payments.get(w.settledByPaymentId);
    if (!p || p.clientId !== w.clientId || w.type !== "DEBT") throw new Error("Некорректная связь погашения долга");
    const count = (settled.get(p.id) || 0) + 1;
    if (count > p.workoutCount) throw new Error("Число погашений превышает оплату");
    settled.set(p.id, count);
  }
  return {
    clients: raw.clients || [],
    payments: raw.payments || [],
    workouts: raw.workouts || [],
  };
}

export function seedDemo() {
  const state = { clients: [], payments: [], workouts: [] };
  const today = startOfDay();
  const d = (offset) => today + offset * 86400000;
  addClient(state, "Иванова", "Мария");
  addClient(state, "Петров", "Алексей");
  addClient(state, "Сидорова", "Анна");
  addPayment(state, 1, d(-10), 8000, 8);
  addPayment(state, 2, d(-3), 1500, 1);
  addPayment(state, 3, d(-1), 4000, 4);
  addWorkout(state, 1, d(-8), "Силовая", "PAID");
  addWorkout(state, 1, d(-5), "Подарок на ДР", "GIFT");
  addWorkout(state, 1, d(-2), "", "PAID");
  addWorkout(state, 3, d(0), "Ещё не оплачена", "DEBT");
  return state;
}
