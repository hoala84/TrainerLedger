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
  const used = workouts.filter((w) => w.clientId === clientId && w.type === "PAID").length;
  return paid - used;
}

export function debtOf(workouts, clientId) {
  return workouts.filter((w) => w.clientId === clientId && w.type === "DEBT").length;
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

export function addClient(state, lastName, firstName) {
  const client = {
    id: nextId(state.clients),
    lastName: lastName.trim(),
    firstName: firstName.trim(),
    updatedAt: Date.now(),
  };
  state.clients = [...state.clients, client];
  return client.id;
}

export function updateClient(state, id, lastName, firstName) {
  state.clients = state.clients.map((c) =>
    c.id === id
      ? { ...c, lastName: lastName.trim(), firstName: firstName.trim(), updatedAt: Date.now() }
      : c,
  );
}

export function deleteClient(state, id) {
  state.clients = state.clients.filter((c) => c.id !== id);
  state.payments = state.payments.filter((p) => p.clientId !== id);
  state.workouts = state.workouts.filter((w) => w.clientId !== id);
}

export function addPayment(state, clientId, date, amount, workoutCount) {
  const day = startOfDay(date);
  const count = Math.max(0, Number(workoutCount) || 0);
  const payment = {
    id: nextId(state.payments),
    clientId,
    date: day,
    amount,
    workoutCount: count,
    autoWorkoutId: null,
  };
  state.payments = [...state.payments, payment];
  if (count === 1) {
    const workout = {
      id: nextId(state.workouts),
      clientId,
      date: day,
      comment: "По оплате",
      type: "PAID",
    };
    state.workouts = [...state.workouts, workout];
    payment.autoWorkoutId = workout.id;
  }
  touch(state, clientId);
}

export function updatePayment(state, paymentId, date, amount, workoutCount) {
  const existing = state.payments.find((p) => p.id === paymentId);
  if (!existing) return;
  const day = startOfDay(date);
  const count = Math.max(0, Number(workoutCount) || 0);
  let autoId = existing.autoWorkoutId;
  if (count === 1) {
    if (autoId) {
      const auto = state.workouts.find((w) => w.id === autoId);
      if (auto) {
        state.workouts = state.workouts.map((w) => (w.id === autoId ? { ...w, date: day } : w));
      } else {
        const workout = {
          id: nextId(state.workouts),
          clientId: existing.clientId,
          date: day,
          comment: "По оплате",
          type: "PAID",
        };
        state.workouts = [...state.workouts, workout];
        autoId = workout.id;
      }
    } else {
      const workout = {
        id: nextId(state.workouts),
        clientId: existing.clientId,
        date: day,
        comment: "По оплате",
        type: "PAID",
      };
      state.workouts = [...state.workouts, workout];
      autoId = workout.id;
    }
  } else if (autoId) {
    state.workouts = state.workouts.filter((w) => w.id !== autoId);
    autoId = null;
  }
  state.payments = state.payments.map((p) =>
    p.id === paymentId
      ? { ...p, date: day, amount, workoutCount: count, autoWorkoutId: autoId }
      : p,
  );
  touch(state, existing.clientId);
}

export function deletePayment(state, paymentId) {
  const existing = state.payments.find((p) => p.id === paymentId);
  if (!existing) return;
  if (existing.autoWorkoutId) {
    state.workouts = state.workouts.filter((w) => w.id !== existing.autoWorkoutId);
  }
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
  state.workouts = state.workouts.map((w) =>
    w.id === workoutId
      ? { ...w, date: startOfDay(date), comment: comment.trim(), type }
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
      debtWorkouts: cWork.filter((w) => w.type === "DEBT").length,
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
