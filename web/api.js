/**
 * Google Tasks / Drive REST 래퍼.
 * 안드로이드 앱과 같은 API, 같은 데이터를 본다.
 */

const TASKS_BASE = 'https://tasks.googleapis.com/tasks/v1';
const DRIVE_BASE = 'https://www.googleapis.com/drive/v3';
const DRIVE_UPLOAD = 'https://www.googleapis.com/upload/drive/v3';

let accessToken = null;

export function setAccessToken(token) {
  accessToken = token;
}

export function hasToken() {
  return Boolean(accessToken);
}

async function request(url, options = {}) {
  if (!accessToken) throw new Error('로그인이 필요합니다.');

  const res = await fetch(url, {
    ...options,
    headers: {
      Authorization: `Bearer ${accessToken}`,
      ...(options.headers || {}),
    },
  });

  if (res.status === 401) {
    accessToken = null;
    throw new Error('로그인이 만료되었습니다. 다시 로그인하세요.');
  }
  if (!res.ok) {
    let detail = '';
    try {
      const body = await res.json();
      detail = body?.error?.message || '';
    } catch (_) {
      /* 본문이 없을 수 있다 */
    }
    throw new Error(detail || `요청 실패 (${res.status})`);
  }
  if (res.status === 204) return null;
  return res.json();
}

/* ── 날짜 ─────────────────────────────────────────────── */

/** Google Tasks의 due 형식(UTC 자정)으로 변환 */
export function toDueStamp(date) {
  const y = date.getFullYear();
  const m = String(date.getMonth() + 1).padStart(2, '0');
  const d = String(date.getDate()).padStart(2, '0');
  return `${y}-${m}-${d}T00:00:00.000Z`;
}

/** due 문자열에서 날짜(YYYY-MM-DD)만 뽑는다 */
export function dueDateKey(due) {
  if (!due) return null;
  return due.slice(0, 10);
}

export function dateKey(date) {
  const y = date.getFullYear();
  const m = String(date.getMonth() + 1).padStart(2, '0');
  const d = String(date.getDate()).padStart(2, '0');
  return `${y}-${m}-${d}`;
}

/* ── Tasks ────────────────────────────────────────────── */

export async function listTaskLists() {
  const data = await request(`${TASKS_BASE}/users/@me/lists?maxResults=100`);
  return (data.items || []).map((l) => ({ id: l.id, title: l.title }));
}

/** 한 목록의 할일을 전부 가져온다(페이지 넘김 포함) */
export async function listTasks(listId, { showCompleted = false, showHidden = false, dueMax = null } = {}) {
  const out = [];
  let pageToken = null;
  do {
    const params = new URLSearchParams({
      showCompleted: String(showCompleted),
      showHidden: String(showHidden),
      maxResults: '100',
    });
    if (dueMax) params.set('dueMax', dueMax);
    if (pageToken) params.set('pageToken', pageToken);

    const data = await request(`${TASKS_BASE}/lists/${encodeURIComponent(listId)}/tasks?${params}`);
    out.push(...(data.items || []));
    pageToken = data.nextPageToken || null;
  } while (pageToken && out.length < 1000);
  return out;
}

export async function insertTask(listId, task) {
  return request(`${TASKS_BASE}/lists/${encodeURIComponent(listId)}/tasks`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(task),
  });
}

export async function patchTask(listId, taskId, patch) {
  return request(
    `${TASKS_BASE}/lists/${encodeURIComponent(listId)}/tasks/${encodeURIComponent(taskId)}`,
    {
      method: 'PATCH',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(patch),
    }
  );
}

export async function deleteTask(listId, taskId) {
  return request(
    `${TASKS_BASE}/lists/${encodeURIComponent(listId)}/tasks/${encodeURIComponent(taskId)}`,
    { method: 'DELETE' }
  );
}

/* ── Drive appDataFolder (설정 동기화) ──────────────────── */

const SETTINGS_FILE = 'tosker_settings.json';

async function findSettingsFileId() {
  const params = new URLSearchParams({
    spaces: 'appDataFolder',
    q: `name = '${SETTINGS_FILE}' and trashed = false`,
    fields: 'files(id,modifiedTime)',
    orderBy: 'modifiedTime desc',
  });
  const data = await request(`${DRIVE_BASE}/files?${params}`);
  return data.files?.[0]?.id || null;
}

export async function downloadSettings() {
  const fileId = await findSettingsFileId();
  if (!fileId) return null;
  const res = await fetch(`${DRIVE_BASE}/files/${fileId}?alt=media`, {
    headers: { Authorization: `Bearer ${accessToken}` },
  });
  if (!res.ok) throw new Error(`설정 내려받기 실패 (${res.status})`);
  return res.text();
}

export async function uploadSettings(json) {
  const fileId = await findSettingsFileId();

  if (fileId) {
    const res = await fetch(`${DRIVE_UPLOAD}/files/${fileId}?uploadType=media`, {
      method: 'PATCH',
      headers: {
        Authorization: `Bearer ${accessToken}`,
        'Content-Type': 'application/json',
      },
      body: json,
    });
    if (!res.ok) throw new Error(`설정 저장 실패 (${res.status})`);
    return;
  }

  // 새로 만들 때는 메타데이터 + 내용을 multipart로 보낸다.
  const boundary = 'tosker' + Math.random().toString(36).slice(2);
  const metadata = { name: SETTINGS_FILE, parents: ['appDataFolder'] };
  const body =
    `--${boundary}\r\nContent-Type: application/json; charset=UTF-8\r\n\r\n` +
    `${JSON.stringify(metadata)}\r\n` +
    `--${boundary}\r\nContent-Type: application/json\r\n\r\n` +
    `${json}\r\n--${boundary}--`;

  const res = await fetch(`${DRIVE_UPLOAD}/files?uploadType=multipart`, {
    method: 'POST',
    headers: {
      Authorization: `Bearer ${accessToken}`,
      'Content-Type': `multipart/related; boundary=${boundary}`,
    },
    body,
  });
  if (!res.ok) throw new Error(`설정 저장 실패 (${res.status})`);
}
