import test from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
const source = readFileSync(new URL("../web/js/ledger.js", import.meta.url), "utf8");
const L = await import(`data:text/javascript;base64,${Buffer.from(source).toString("base64")}`);
const day = new Date(2026, 8, 10).getTime();
function fixture() {
  const s = { clients: [], payments: [], workouts: [] };
  L.addClient(s, "Иванов", "Иван");
  L.addWorkout(s, 1, day - 86400000, "старый", "DEBT");
  L.addWorkout(s, 1, day, "новый", "DEBT");
  return s;
}
test("partial settlement, oldest first, balances and statistics", () => {
  const s = fixture();
  L.addPayment(s, 1, day, 5000, 5, 1);
  assert.equal(s.workouts.length, 2);
  assert.equal(s.workouts[0].settledByPaymentId, 1);
  assert.equal(L.remainingOf(s.payments, s.workouts, 1), 4);
  assert.equal(L.debtOf(s.workouts, 1), 1);
  assert.equal(L.periodStats(s, day - 86400000, day, 1).debtWorkouts, 1);
  L.updatePayment(s, 1, day, 5000, 5, 2);
  assert.equal(L.remainingOf(s.payments, s.workouts, 1), 3);
  L.deletePayment(s, 1);
  assert.equal(L.debtOf(s.workouts, 1), 2);
  assert.equal(L.remainingOf(s.payments, s.workouts, 1), 0);
});
test("deleting settled workout returns credit; changing type releases settlement", () => {
  const s = fixture();
  L.addPayment(s, 1, day, 2000, 2, 2);
  L.deleteWorkout(s, 1);
  assert.equal(L.remainingOf(s.payments, s.workouts, 1), 1);
  L.updateWorkout(s, 2, day, "подарок", "GIFT");
  assert.equal(L.remainingOf(s.payments, s.workouts, 1), 2);
});
test("invalid settlement is atomic and cannot use another payment's debts", () => {
  const s = fixture();
  L.addPayment(s, 1, day, 1000, 1, 1);
  const before = JSON.stringify(s);
  assert.throws(() => L.addPayment(s, 1, day, 2000, 2, 2));
  assert.throws(() => L.updatePayment(s, 1, day, 1000, 0, 1));
  assert.throws(() => L.addPayment(s, 1, day, 1000, 1.5, 0));
  assert.equal(JSON.stringify(s), before);
});
test("legacy zero and one payments and deleted auto workouts", () => {
  const s = fixture();
  L.addPayment(s, 1, day, 1000, 0);
  assert.equal(L.debtOf(s.workouts, 1), 2);
  L.addPayment(s, 1, day, 1000, 1);
  const auto = s.payments[1].autoWorkoutId;
  assert.equal(s.workouts.length, 3);
  L.deleteWorkout(s, auto);
  L.updatePayment(s, 2, day, 1500, 1, 0);
  assert.equal(s.workouts.length, 2);
  L.updatePayment(s, 2, day, 1500, 1, 1);
  assert.equal(L.debtOf(s.workouts, 1), 1);
  L.updatePayment(s, 2, day, 1500, 1, 0);
  assert.equal(s.workouts.length, 3);
  L.updateWorkout(s, s.payments[1].autoWorkoutId, day, "", "GIFT");
  L.deletePayment(s, 2);
  assert.equal(s.workouts.length, 3);
});
test("client details, duplicate prevention, dates and birthday age", () => {
  const s = fixture();
  assert.throws(() => L.addClient(s, " ИВАНОВ ", " иван  "));
  const id = L.addClient(s, "Петров", "Пётр");
  assert.throws(() => L.updateClient(s, id, "иванов", "Иван"));
  assert.throws(() => L.updateClient(s, id, "Петров", "Пётр", { birthDate: "2020-02-31" }));
  L.updateClient(s, id, "Петров", "Пётр", { birthDate: "2000-09-11", phone: " 123 ", comment: "Текст" });
  assert.equal(s.clients[1].phone, "123");
  assert.equal(L.ageOf("2000-09-11", day), 25);
  assert.equal(L.ageOf("2000-09-10", day), 26);
  assert.equal(L.ageOf(null, day), null);
});
test("new backup round trip, old backups and invalid settlement links", () => {
  const s = fixture();
  L.updateClient(s, 1, "Иванов", "Иван", { phone: "123", birthDate: "2000-01-01", comment: "Заметка" });
  L.addPayment(s, 1, day, 2000, 2, 2);
  assert.deepEqual(L.restoreBackup(JSON.parse(JSON.stringify(L.exportBackup(s)))), s);
  assert.deepEqual(L.restoreBackup({ clients: [] }), { clients: [], payments: [], workouts: [] });
  const backup = L.exportBackup(s);
  backup.payments = [];
  assert.throws(() => L.restoreBackup(backup));
});
