const DB_NAME = "trainer-ledger";
const DB_VERSION = 1;

function openDb() {
  return new Promise((resolve, reject) => {
    const req = indexedDB.open(DB_NAME, DB_VERSION);
    req.onupgradeneeded = () => {
      const db = req.result;
      if (!db.objectStoreNames.contains("kv")) {
        db.createObjectStore("kv");
      }
    };
    req.onsuccess = () => resolve(req.result);
    req.onerror = () => reject(req.error);
  });
}

export async function loadState() {
  const db = await openDb();
  const empty = { clients: [], payments: [], workouts: [] };
  return new Promise((resolve, reject) => {
    const tx = db.transaction("kv", "readonly");
    const req = tx.objectStore("kv").get("ledger");
    req.onsuccess = () => resolve(req.result || empty);
    req.onerror = () => reject(req.error);
  });
}

export async function saveState(state) {
  const db = await openDb();
  return new Promise((resolve, reject) => {
    const tx = db.transaction("kv", "readwrite");
    tx.objectStore("kv").put(
      {
        clients: state.clients,
        payments: state.payments,
        workouts: state.workouts,
      },
      "ledger",
    );
    tx.oncomplete = () => resolve();
    tx.onerror = () => reject(tx.error);
  });
}
