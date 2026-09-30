import { loadState, saveState } from "./db.js";
import { buildXlsx } from "./xlsx.js";
import * as L from "./ledger.js";

const root = document.getElementById("app");
// Заглушка готова на всех экранах, но не попадает в интерфейс до подключения отправки отзывов.
const FEEDBACK_ENABLED = false;
const ui = {
  tab: "clients",
  clientId: null,
  sort: L.SORT.BY_UPDATED,
  clientSort: "name",
  clientSortAsc: true,
  clientSortMenu: null,
  clientRemainingFrom: 0,
  clientRemainingTo: 10,
  statsFrom: L.startOfMonth(),
  statsTo: L.startOfDay(),
  statsClientId: null,
  statsSort: "name",
  statsSortAsc: true,
  modal: null,
  menu: null,
  toast: "",
  iosHint: shouldShowIosHint(),
  clientQuery: "",
  quickAction: null,
};

let state = { clients: [], payments: [], workouts: [] };

function shouldShowIosHint() {
  const iOS = /iPad|iPhone|iPod/.test(navigator.userAgent);
  const standalone = window.navigator.standalone === true ||
    window.matchMedia("(display-mode: standalone)").matches;
  return iOS && !standalone && !localStorage.getItem("hideIosHint");
}

async function persist() {
  await saveState(state);
  render();
}

function toast(text) {
  ui.toast = text;
  render();
  setTimeout(() => {
    if (ui.toast === text) {
      ui.toast = "";
      render();
    }
  }, 2200);
}

function downloadBlob(bytes, name, type) {
  const blob = new Blob([bytes], { type });
  const url = URL.createObjectURL(blob);
  const a = document.createElement("a");
  a.href = url;
  a.download = name;
  a.click();
  setTimeout(() => URL.revokeObjectURL(url), 1000);
}

function workoutLabel(type) {
  if (type === "GIFT") return "Подарочная";
  if (type === "DEBT") return "В долг";
  return "Оплаченная";
}

function openModal(modal) {
  ui.modal = modal;
  render();
}

function closeModal() {
  ui.modal = null;
  render();
}

root.addEventListener("click", async (e) => {
  const btn = e.target.closest("[data-action]");
  if (!btn) {
    if (e.target.closest(".overlay") && !e.target.closest(".sheet")) closeModal();
    return;
  }
  const action = btn.dataset.action;
  const id = Number(btn.dataset.id);

  if (action === "tab") {
    ui.tab = btn.dataset.tab;
    ui.clientId = null;
    ui.menu = null;
    render();
  } else if (action === "today-stats") {
    const today = L.startOfDay();
    ui.statsFrom = today;
    ui.statsTo = today;
    ui.statsClientId = null;
    ui.tab = "stats";
    ui.clientId = null;
    ui.menu = null;
    render();
  } else if (action === "open-client") {
    ui.tab = "client";
    ui.clientId = id;
    render();
  } else if (action === "back") {
    ui.tab = "clients";
    ui.clientId = null;
    render();
  } else if (action === "sort-menu") {
    ui.menu = ui.menu === "sort" ? null : "sort";
    render();
  } else if (action === "more-menu") {
    ui.menu = ui.menu === "more" ? null : "more";
    render();
  } else if (action === "sort") {
    ui.sort = btn.dataset.sort;
    ui.menu = null;
    render();
  } else if (action === "hide-hint") {
    localStorage.setItem("hideIosHint", "1");
    ui.iosHint = false;
    render();
  } else if (action === "add-client") {
    openModal({ type: "client" });
  } else if (action === "edit-client") {
    const c = state.clients.find((x) => x.id === ui.clientId);
    openModal({ type: "client", client: c });
  } else if (action === "delete-client") {
    openModal({ type: "confirm", title: "Удалить клиента?", text: "Оплаты и тренировки тоже удалятся.", confirm: "Удалить", onYes: async () => {
      L.deleteClient(state, ui.clientId);
      ui.tab = "clients";
      ui.clientId = null;
      await persist();
    } });
  } else if (action === "add-payment") {
    openModal({ type: "payment" });
  } else if (action === "quick-payment" || action === "quick-workout") {
    ui.quickAction = action === "quick-payment" ? "payment" : "workout";
    ui.clientQuery = "";
    openModal({ type: "client-picker" });
  } else if (action === "select-quick-client") {
    ui.clientId = id;
    openModal({ type: ui.quickAction });
  } else if (action === "feedback") {
    openModal({ type: "feedback" });
  } else if (action === "edit-payment") {
    openModal({ type: "payment", payment: state.payments.find((p) => p.id === id) });
  } else if (action === "delete-payment") {
    openModal({ type: "confirm", title: "Удалить оплату?", text: "Погашенные этой оплатой долги снова станут неоплаченными. Автоматическая тренировка тоже удалится.", confirm: "Удалить", onYes: async () => {
      L.deletePayment(state, id);
      await persist();
    } });
  } else if (action === "add-workout") {
    openModal({ type: "workout" });
  } else if (action === "edit-workout") {
    openModal({ type: "workout", workout: state.workouts.find((w) => w.id === id) });
  } else if (action === "delete-workout") {
    openModal({ type: "confirm", title: "Удалить тренировку?", text: "Остаток оплаченных занятий пересчитается.", confirm: "Удалить", onYes: async () => {
      L.deleteWorkout(state, id);
      await persist();
    } });
  } else if (action === "export") {
    const json = JSON.stringify(L.exportBackup(state), null, 2);
    downloadBlob(json, `trainer-ledger-${L.formatFileDate()}.json`, "application/json");
    toast("Копия скачана");
    ui.menu = null;
  } else if (action === "import") {
    ui.menu = null;
    document.getElementById("file-import").click();
  } else if (action === "demo") {
    if (state.clients.length && !confirm("Заменить текущие данные примером?")) return;
    state = L.seedDemo();
    ui.menu = null;
    await persist();
    toast("Загружен пример");
  } else if (action === "excel") {
    const filterId = ui.statsClientId ? Number(ui.statsClientId) : null;
    const stats = L.periodStats(state, ui.statsFrom, ui.statsTo, filterId);
    const clients = filterId == null ? state.clients : state.clients.filter((c) => c.id === filterId);
    const bytes = buildXlsx({
      from: ui.statsFrom,
      to: ui.statsTo,
      clients,
      payments: stats.periodPayments.filter((p) => filterId == null || p.clientId === filterId),
      workouts: stats.periodWorkouts.filter((w) => filterId == null || w.clientId === filterId),
      daysInRange: L.daysInRange,
      startOfDay: L.startOfDay,
      formatShort: L.formatShort,
      formatMoney: L.formatMoney,
    });
    downloadBlob(bytes, `trener-${L.formatFileDate(ui.statsFrom)}-${L.formatFileDate(ui.statsTo)}.xlsx`, "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
    toast("Excel скачан");
  } else if (action === "client-alpha-menu") {
    ui.clientSortMenu = ui.clientSortMenu === "alpha" ? null : "alpha";
    render();
  } else if (action === "client-workout-sort") {
    ui.clientSort = "workouts";
    ui.clientSortAsc = true;
    ui.clientSortMenu = null;
    render();
  } else if (action === "client-alpha-choice") {
    ui.clientSort = "name";
    ui.clientSortAsc = btn.dataset.direction === "asc";
    ui.clientSortMenu = null;
    render();
  } else if (action === "stats-sort") {
    const field = btn.dataset.field;
    if (ui.statsSort === field) ui.statsSortAsc = !ui.statsSortAsc;
    else {
      ui.statsSort = field;
      ui.statsSortAsc = true;
    }
    render();
  } else if (action === "close-modal") {
    closeModal();
  }
});

root.addEventListener("change", (e) => {
  if (e.target.id === "file-import") {
    const file = e.target.files[0];
    e.target.value = "";
    if (!file) return;
    if (!confirm("Текущие записи будут заменены файлом копии. Продолжить?")) return;
    file.text().then(async (text) => {
      try {
        state = L.restoreBackup(JSON.parse(text));
        await persist();
        toast("Данные восстановлены");
      } catch {
        toast("Не удалось прочитать файл");
      }
    });
  }
  if (e.target.id === "stats-from") {
    ui.statsFrom = L.fromInputDate(e.target.value);
    render();
  }
  if (e.target.id === "stats-to") {
    ui.statsTo = L.fromInputDate(e.target.value);
    render();
  }
  if (e.target.id === "stats-client") {
    ui.statsClientId = e.target.value || null;
    render();
  }
  if (e.target.id === "client-remaining-from" || e.target.id === "client-remaining-to") {
    render();
  }
});

root.addEventListener("submit", async (e) => {
  e.preventDefault();
  const form = e.target;
  if (form.id === "client-form") {
    const last = form.lastName.value;
    const first = form.firstName.value;
    const details = { phone: form.phone.value, comment: form.comment.value, birthDate: form.birthDate.value };
    try {
      if (ui.modal.client) L.updateClient(state, ui.modal.client.id, last, first, details);
      else L.addClient(state, last, first, details);
    } catch (error) { form.querySelector(".form-error").textContent = error.message; return; }
    closeModal();
    await persist();
  }
  if (form.id === "payment-form") {
    const date = L.fromInputDate(form.date.value);
    const amount = L.parseMoney(form.amount.value);
    const count = Number(form.count.value);
    if (amount == null || amount < 0 || !Number.isInteger(count) || count < 0) return;
    try {
      const debt = Number(form.debtCount.value);
      if (ui.modal.payment) L.updatePayment(state, ui.modal.payment.id, date, amount, count, debt);
      else L.addPayment(state, ui.clientId, date, amount, count, debt);
    } catch (error) { form.querySelector(".form-error").textContent = error.message; return; }
    closeModal();
    ui.quickAction = null;
    ui.clientQuery = "";
    await persist();
  }
  if (form.id === "workout-form") {
    const date = L.fromInputDate(form.date.value);
    const comment = form.comment.value;
    const type = form.type.value;
    if (ui.modal.workout) L.updateWorkout(state, ui.modal.workout.id, date, comment, type);
    else L.addWorkout(state, ui.clientId, date, comment, type);
    closeModal();
    ui.quickAction = null;
    ui.clientQuery = "";
    await persist();
  }
});

root.addEventListener("input", (e) => {
  if (e.target.id === "client-search") {
    ui.clientQuery = e.target.value;
    render();
    const input = document.getElementById("client-search");
    if (input) { input.focus(); input.setSelectionRange(input.value.length, input.value.length); }
  } else if (e.target.id === "client-remaining-from") {
    ui.clientRemainingFrom = Math.min(Number(e.target.value), ui.clientRemainingTo);
    e.target.value = ui.clientRemainingFrom;
    e.target.closest("label").querySelector("strong").textContent = ui.clientRemainingFrom;
  } else if (e.target.id === "client-remaining-to") {
    ui.clientRemainingTo = Math.max(Number(e.target.value), ui.clientRemainingFrom);
    e.target.value = ui.clientRemainingTo;
    e.target.closest("label").querySelector("strong").textContent = ui.clientRemainingTo;
  }
});

function renderList() {
  const query = ui.clientQuery.trim().toLocaleLowerCase("ru");
  const rows = L.sortClients(state.clients, state.payments, state.workouts, ui.sort)
    .filter((row) => !query || L.displayName(row.client).toLocaleLowerCase("ru").includes(query))
    .filter((row) => ui.clientSort !== "workouts" ||
      (row.remaining >= ui.clientRemainingFrom && row.remaining <= ui.clientRemainingTo))
    .sort((a, b) => {
      let result;
      if (ui.clientSort === "workouts") result = a.remaining - b.remaining;
      else result = L.displayName(a.client).localeCompare(L.displayName(b.client), "ru");
      return ui.clientSortAsc ? result : -result;
    });
  return `
    <header class="top">
      <div>
        <h1>Клиенты</h1>
      </div>
      <div class="actions">
        <button class="icon-btn" data-action="more-menu" title="Ещё">⋯</button>
      </div>
    </header>
    ${ui.menu === "more" ? `<div class="menu">
      <button data-action="export">Сохранить копию JSON</button>
      <button data-action="import">Восстановить из файла</button>
      <button data-action="demo">Загрузить пример</button>
    </div>` : ""}
    <div class="page">
      ${ui.iosHint ? `<div class="hint">На iPhone: «Поделиться» → «На экран Домой» — журнал будет как приложение и без интернета. <button class="linkish" data-action="hide-hint">Скрыть</button></div>` : ""}
      <div class="row-btns">
        <button class="btn" data-action="quick-payment" ${state.clients.length ? "" : "disabled"}>Добавить оплату</button>
        <button class="btn secondary" data-action="quick-workout" ${state.clients.length ? "" : "disabled"}>Добавить тренировку</button>
      </div>
      <div class="search"><input id="client-search" type="search" placeholder="Поиск по фамилии и имени" value="${esc(ui.clientQuery)}"></div>
      <div class="sort-buttons">
        <button class="chip ${ui.clientSort === "name" ? "active" : ""}" data-action="client-alpha-menu">Алфавит: ${ui.clientSortAsc ? "А–Я" : "Я–А"}</button>
        <button class="chip ${ui.clientSort === "workouts" ? "active" : ""}" data-action="client-workout-sort">Тренировки: ${ui.clientRemainingFrom}–${ui.clientRemainingTo}</button>
      </div>
      ${ui.clientSortMenu === "alpha" ? `<div class="sort-choice-menu">
        <button data-action="client-alpha-choice" data-direction="asc">От А до Я</button>
        <button data-action="client-alpha-choice" data-direction="desc">От Я до А</button>
      </div>` : ""}
      ${ui.clientSort === "workouts" ? `<div class="remaining-range" aria-label="Диапазон оставшихся тренировок">
        <label>От <strong>${ui.clientRemainingFrom}</strong>
          <input id="client-remaining-from" type="range" min="0" max="10" step="1" value="${ui.clientRemainingFrom}">
        </label>
        <label>До <strong>${ui.clientRemainingTo}</strong>
          <input id="client-remaining-to" type="range" min="0" max="10" step="1" value="${ui.clientRemainingTo}">
        </label>
      </div>` : ""}
      ${rows.length === 0 ? `<div class="empty">${state.clients.length ? "Клиенты не найдены." : "Пока нет клиентов.<br>Добавьте первого кнопкой «+» или загрузите пример."}</div>` : rows.map((row) => `
        <article class="card" data-action="open-client" data-id="${row.client.id}">
          <div class="card-row">
            <div>
              <h3>${esc(L.displayName(row.client))}</h3>
              ${row.debt ? `<div class="meta">В долг: ${row.debt}</div>` : ""}
            </div>
            <span class="badge">осталось ${row.remaining}</span>
          </div>
        </article>
      `).join("")}
    </div>
    <button class="fab" data-action="add-client">＋ Добавить клиента</button>
  `;
}

function renderClient() {
  const client = state.clients.find((c) => c.id === ui.clientId);
  if (!client) return renderList();
  const remaining = L.remainingOf(state.payments, state.workouts, client.id);
  const payments = L.sortByEventDate(state.payments.filter((p) => p.clientId === client.id));
  const workouts = L.sortByEventDate(state.workouts.filter((w) => w.clientId === client.id));
  return `
    <header class="top">
      <div>
        <button class="linkish" data-action="back">← Назад</button>
        <h2>${esc(L.displayName(client))}</h2>
        <div class="sub">Осталось тренировок: ${remaining}</div>
        <div class="sub">В долг: ${L.debtOf(state.workouts, client.id)}</div>
        ${client.phone ? `<div class="sub">Телефон: ${esc(client.phone)}</div>` : ""}
        ${client.birthDate ? `<div class="sub">Дата рождения: ${L.formatShort(L.fromInputDate(client.birthDate))} · Возраст: ${L.ageOf(client.birthDate)}</div>` : ""}
        ${client.comment ? `<div class="sub">${esc(client.comment)}</div>` : ""}
      </div>
      <div class="actions">
        <button class="icon-btn" data-action="more-menu">⋯</button>
      </div>
    </header>
    ${ui.menu === "more" ? `<div class="menu">
      <button data-action="edit-client">Изменить карточку</button>
      <button data-action="delete-client">Удалить клиента</button>
    </div>` : ""}
    <div class="page">
      <div class="row-btns">
        <button class="btn" data-action="add-payment">Оплата</button>
        <button class="btn secondary" data-action="add-workout">Тренировка</button>
      </div>
      <h3>Оплаты</h3>
      ${payments.length === 0 ? `<p class="meta">Пока нет оплат</p>` : payments.map((p) => `
        <article class="card">
          <div class="card-row">
            <div>
              <h3>${L.formatMoney(p.amount)}</h3>
              ${L.settledCount(state, p.id) ? `<div class="meta">Погашено в долг: ${L.settledCount(state, p.id)}</div>` : ""}
              <div class="meta">${L.formatDisplay(p.date)} · ${p.workoutCount === 0 ? "без начисления занятий" : `${p.workoutCount} тр.`}${p.autoWorkoutId ? " · авто-тренировка" : ""}</div>
            </div>
            <div class="item-actions">
              <button class="linkish" data-action="edit-payment" data-id="${p.id}">Изм.</button>
              <button class="linkish" data-action="delete-payment" data-id="${p.id}">Удал.</button>
            </div>
          </div>
        </article>
      `).join("")}
      <h3>Тренировки</h3>
      ${workouts.length === 0 ? `<p class="meta">Пока нет тренировок</p>` : workouts.map((w) => `
        <article class="card">
          <div class="card-row">
            <div>
              <h3>${L.formatDisplay(w.date)} <span class="type-pill ${w.type === "GIFT" ? "gift" : w.type === "DEBT" && w.settledByPaymentId == null ? "debt" : ""}">${w.settledByPaymentId != null ? "Была в долг · оплачена" : workoutLabel(w.type)}</span></h3>
              <div class="meta">${esc(w.comment || "Без комментария")}</div>
            </div>
            <div class="item-actions">
              <button class="linkish" data-action="edit-workout" data-id="${w.id}">Изм.</button>
              <button class="linkish" data-action="delete-workout" data-id="${w.id}">Удал.</button>
            </div>
          </div>
        </article>
      `).join("")}
    </div>
  `;
}

function renderStats() {
  const filterId = ui.statsClientId ? Number(ui.statsClientId) : null;
  const stats = L.periodStats(state, ui.statsFrom, ui.statsTo, filterId);
  const sortedRows = [...stats.perClient].sort((a, b) => {
    let result;
    if (ui.statsSort === "workouts") result = a.completedWorkouts - b.completedWorkouts;
    else if (ui.statsSort === "payments") result = a.income - b.income;
    else result = L.displayName(a.client).localeCompare(L.displayName(b.client), "ru");
    return ui.statsSortAsc ? result : -result;
  });
  return `
    <header class="top">
      <div><h1>Статистика</h1></div>
      <div class="actions">
        <button class="icon-btn" data-action="excel" title="Excel">▦</button>
      </div>
    </header>
    <div class="page">
      <div class="filters">
        <label>С <input id="stats-from" type="date" value="${L.toInputDate(ui.statsFrom)}"></label>
        <label>По <input id="stats-to" type="date" value="${L.toInputDate(ui.statsTo)}"></label>
        <label>Клиент
          <select id="stats-client">
            <option value="">Все клиенты</option>
            ${state.clients.map((c) => `<option value="${c.id}" ${filterId === c.id ? "selected" : ""}>${esc(L.displayName(c))}</option>`).join("")}
          </select>
        </label>
      </div>
      ${filterId == null ? `<div class="sort-buttons">
        ${sortButton("stats-sort", "name", "Алфавит", ui.statsSort, ui.statsSortAsc)}
        ${sortButton("stats-sort", "workouts", "Тренировки", ui.statsSort, ui.statsSortAsc)}
        ${sortButton("stats-sort", "payments", "Оплаты", ui.statsSort, ui.statsSortAsc)}
      </div>` : ""}
      <article class="card"><div class="meta">Проведено тренировок</div><div class="stat">${stats.totalWorkouts}</div></article>
      <article class="card"><div class="meta">Из них подарочных</div><div class="stat">${stats.giftWorkouts}</div></article>
      <article class="card"><div class="meta">Из них в долг</div><div class="stat">${stats.debtWorkouts}</div></article>
      <article class="card"><div class="meta">Приход денег</div><div class="stat">${L.formatMoney(stats.totalIncome)}</div></article>
      ${filterId == null ? sortedRows.map((row) => `
        <article class="card">
          <h3>${esc(L.displayName(row.client))}</h3>
          <div class="meta">Тренировки: ${row.completedWorkouts} (подарки: ${row.giftWorkouts}, долг: ${row.debtWorkouts})</div>
          <div class="meta">Приход: ${L.formatMoney(row.income)}</div>
        </article>
      `).join("") : ""}
      <button class="btn" data-action="excel">Выгрузить период в Excel</button>
    </div>
  `;
}

function renderModal() {
  const m = ui.modal;
  if (!m) return "";
  if (m.type === "confirm") {
    return `<div class="overlay"><div class="sheet">
      <h3>${esc(m.title)}</h3>
      <p>${esc(m.text)}</p>
      <div class="row-btns">
        <button class="btn secondary" data-action="close-modal">Отмена</button>
        <button class="btn danger" id="confirm-yes">${esc(m.confirm)}</button>
      </div>
    </div></div>`;
  }
  if (m.type === "client") {
    const c = m.client;
    return `<div class="overlay"><form class="sheet" id="client-form">
      <h3>${c ? "Карточка клиента" : "Новый клиент"}</h3>
      <label>Фамилия</label><input name="lastName" required value="${esc(c?.lastName || "")}">
      <label>Имя</label><input name="firstName" required value="${esc(c?.firstName || "")}">
      <label>Телефон</label><input name="phone" type="tel" value="${esc(c?.phone || "")}">
      <label>Дата рождения</label><input name="birthDate" type="date" max="${L.toInputDate(Date.now())}" value="${esc(c?.birthDate || "")}">
      <label>Комментарий</label><textarea name="comment" rows="2">${esc(c?.comment || "")}</textarea>
      <p class="form-error" role="alert"></p>
      <div class="row-btns">
        <button type="button" class="btn secondary" data-action="close-modal">Отмена</button>
        <button class="btn" type="submit">Сохранить</button>
      </div>
    </form></div>`;
  }
  if (m.type === "client-picker") {
    const query = ui.clientQuery.trim().toLocaleLowerCase("ru");
    const clients = [...state.clients]
      .sort((a, b) => L.displayName(a).localeCompare(L.displayName(b), "ru"))
      .filter((c) => !query || L.displayName(c).toLocaleLowerCase("ru").includes(query));
    return `<div class="overlay"><div class="sheet"><h3>Выберите клиента</h3>
      <input id="client-search" type="search" placeholder="Фамилия или имя" value="${esc(ui.clientQuery)}">
      <div class="picker-list">${clients.length ? clients.map((c) => `<button class="picker-client" data-action="select-quick-client" data-id="${c.id}">${esc(L.displayName(c))}</button>`).join("") : `<p class="meta">Клиенты не найдены</p>`}</div>
      <button class="btn secondary" data-action="close-modal">Отмена</button></div></div>`;
  }
  if (m.type === "feedback") {
    return `<div class="overlay"><div class="sheet"><h3>Обратная связь</h3>
      <p>Здесь можно будет оставить отзыв или сообщить о проблеме.</p>
      <button class="btn" data-action="close-modal">Понятно</button></div></div>`;
  }
  if (m.type === "payment") {
    const p = m.payment;
    const debt = L.debtOf(state.workouts, ui.clientId);
    const defaultDebt = p ? L.settledCount(state, p.id) : debt;
    const defaultCount = p ? p.workoutCount : Math.max(1, debt);
    return `<div class="overlay"><form class="sheet" id="payment-form">
      <h3>${p ? "Оплата" : "Новая оплата"}</h3>
      <label>Дата</label><input name="date" type="date" required value="${L.toInputDate(p?.date || L.startOfDay())}">
      <label>Сумма</label><input name="amount" inputmode="decimal" required value="${p ? p.amount : ""}">
      <label>Всего оплачено занятий</label><input name="count" id="payment-count" type="number" inputmode="numeric" required min="0" step="1" value="${defaultCount}">
      <label>Из них закрыть в долг</label><input name="debtCount" id="payment-debt" type="number" required min="0" max="${debt + (p ? defaultDebt : 0)}" step="1" value="${defaultDebt}">
      <p class="meta">Доступно для погашения: ${debt + (p ? defaultDebt : 0)}. Сначала закрываются старые тренировки.</p>
      <p class="meta" id="payment-count-hint">${paymentCountHint(defaultCount, defaultDebt)}</p>
      <p class="form-error" role="alert"></p>
      <div class="row-btns">
        <button type="button" class="btn secondary" data-action="close-modal">Отмена</button>
        <button class="btn" type="submit">Сохранить</button>
      </div>
    </form></div>`;
  }
  if (m.type === "workout") {
    const w = m.workout;
    const type = w?.type || "PAID";
    return `<div class="overlay"><form class="sheet" id="workout-form">
      <h3>${w ? "Тренировка" : "Новая тренировка"}</h3>
      <label>Дата</label><input name="date" type="date" required value="${L.toInputDate(w?.date || L.startOfDay())}">
      <label>Комментарий</label><textarea name="comment" rows="2">${esc(w?.comment || "")}</textarea>
      <label class="radio"><input type="radio" name="type" value="PAID" ${type === "PAID" ? "checked" : ""}><span>Оплаченная — списывается с пакета</span></label>
      <label class="radio"><input type="radio" name="type" value="DEBT" ${type === "DEBT" ? "checked" : ""}><span>В долг — пакет не списывается</span></label>
      <label class="radio"><input type="radio" name="type" value="GIFT" ${type === "GIFT" ? "checked" : ""}><span>Подарочная — бесплатная</span></label>
      <div class="row-btns">
        <button type="button" class="btn secondary" data-action="close-modal">Отмена</button>
        <button class="btn" type="submit">Сохранить</button>
      </div>
    </form></div>`;
  }
  return "";
}

function render() {
  const showNav = ui.tab !== "client";
  const body = ui.tab === "stats" ? renderStats() : ui.tab === "client" ? renderClient() : renderList();
  root.innerHTML = `
    ${body}
    ${showNav ? `<nav class="nav">
      <button data-action="tab" data-tab="clients" class="${ui.tab === "clients" ? "active" : ""}">Клиенты</button>
      <button data-action="tab" data-tab="stats" class="${ui.tab === "stats" ? "active" : ""}">Статистика</button>
    </nav>` : ""}
    <input id="file-import" type="file" accept="application/json,.json" hidden>
    ${renderModal()}
    ${ui.toast ? `<div class="toast">${esc(ui.toast)}</div>` : ""}
    ${FEEDBACK_ENABLED ? `<button class="feedback-placeholder btn" data-action="feedback">Обратная связь</button>` : ""}
  `;
  const yes = document.getElementById("confirm-yes");
  if (yes && ui.modal?.onYes) {
    yes.onclick = async () => {
      const fn = ui.modal.onYes;
      ui.modal = null;
      await fn();
    };
  }
  const countInput = document.getElementById("payment-count");
  const countHint = document.getElementById("payment-count-hint");
  if (countInput && countHint) {
    const debtInput = document.getElementById("payment-debt");
    const updateHint = () => {
      const n = Number(countInput.value);
      countHint.textContent = Number.isInteger(n) && n >= 0
        ? paymentCountHint(n, Number(debtInput.value))
        : "Укажите число занятий, можно 0.";
    };
    countInput.addEventListener("input", updateHint);
    debtInput.addEventListener("input", updateHint);
  }
}

function paymentCountHint(count, debt = 0) {
  if (!Number.isInteger(debt) || debt < 0 || debt > count) return "Число погашений должно быть от 0 до числа оплаченных занятий.";
  if (debt > 0) return `Погасится долгов: ${debt}. В пакет поступит: ${count - debt}. Новая тренировка не создаётся.`;
  if (count === 0) {
    return "Только сумма: занятия не начисляются, долг не погашается.";
  }
  if (count === 1) {
    return "Оплата за одну тренировку: занятие добавится автоматически на дату оплаты.";
  }
  return "Указанное число занятий начислится в пакет (остаток). Новая тренировка сама не появится.";
}

function sortButton(action, field, label, selectedField, ascending) {
  const selected = field === selectedField;
  const arrow = selected ? (ascending ? " ↑" : " ↓") : "";
  return `<button class="chip ${selected ? "active" : ""}" data-action="${action}" data-field="${field}">${label}${arrow}</button>`;
}

function esc(s) {
  return String(s ?? "")
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll('"', "&quot;");
}

if ("serviceWorker" in navigator) {
  const wasControlled = Boolean(navigator.serviceWorker.controller);
  navigator.serviceWorker.addEventListener("controllerchange", () => {
    if (wasControlled && !ui.modal) location.reload();
  });
  navigator.serviceWorker.register("./sw.js", { updateViaCache: "none" }).then((registration) => {
    window.addEventListener("focus", () => { registration.update().catch(() => {}); });
  }).catch(() => {});
}

function renderToday() {
  const today = L.startOfDay();
  const stats = L.periodStats(state, today, today, null);
  return `<header class="top"><div><h1>Сегодня</h1><div class="sub">${L.formatDisplay(today)}</div></div></header>
    <div class="page">
      <div class="today-stats">
        <button class="card today-stat" data-action="today-stats"><span class="meta">Проведено тренировок</span><span class="stat">${stats.totalWorkouts}</span></button>
        <button class="card today-stat" data-action="today-stats"><span class="meta">Пришло денег</span><span class="stat">${L.formatMoney(stats.totalIncome)}</span></button>
      </div>
      <div class="row-btns">
        <button class="btn" data-action="quick-workout" ${state.clients.length ? "" : "disabled"}><span aria-hidden="true">🏋️</span> Тренировка</button>
        <button class="btn secondary" data-action="quick-payment" ${state.clients.length ? "" : "disabled"}>Оплата</button>
      </div>
      <h3>Расписание дня</h3>
      <article class="card calendar-placeholder">Здесь будет календарь</article>
    </div>`;
}

loadState().then((saved) => {
  state = saved;
  render();
});
