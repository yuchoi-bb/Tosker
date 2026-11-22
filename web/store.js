/**
 * 설정 저장소.
 * 안드로이드 앱과 똑같은 JSON 스키마를 써서 Drive appDataFolder로 양방향 동기화된다.
 *
 * 색상은 안드로이드가 쓰는 ARGB 16진 문자열("fff4788a")로 저장한다.
 */

import * as api from './api.js';

const LOCAL_KEY = 'tosker_settings';

export const PALETTE = [
  'fff4788a', // 핑크
  'ff9b97d3', // 라벤더
  'ff7bb8d4', // 블루
  'ffb5b0cc', // 퍼플
  'ffff8c00', // 주황
  'ff4caf50', // 초록
  'ff2196f3', // 파랑
  'ff9c27b0', // 보라
];

const DEFAULT_LABELS = ['P', 'W', 'S', 'R'];
const TODAY_KEYWORDS = ['2day', 'today', '투데이', '오늘'];

/** ARGB 16진 문자열 → CSS 색 */
export function toCss(argbHex) {
  const hex = String(argbHex || '').padStart(8, 'f');
  return `#${hex.slice(-6)}`;
}

export function isTodayList(title) {
  const t = (title || '').trim().toLowerCase();
  return TODAY_KEYWORDS.some((k) => t.includes(k));
}

export function defaultLabel(index, title) {
  const fallback = (title || '').trim().slice(0, 6) || '?';
  return DEFAULT_LABELS[index] ?? fallback;
}

export function defaultColor(index) {
  return PALETTE[index % PALETTE.length];
}

export function defaultOrder(index, title) {
  return isTodayList(title) ? -1 : index;
}

/* ── 상태 ─────────────────────────────────────────────── */

let state = emptyState();

function emptyState() {
  return {
    version: 1,
    updatedAt: 0,
    listConfigs: {},
    shortcuts: {},
    habitListId: '',
    reminderEnabled: true,
  };
}

export function get() {
  return state;
}

export function load() {
  try {
    const raw = localStorage.getItem(LOCAL_KEY);
    if (raw) state = { ...emptyState(), ...JSON.parse(raw) };
  } catch (_) {
    state = emptyState();
  }
  return state;
}

function persistLocal() {
  try {
    localStorage.setItem(LOCAL_KEY, JSON.stringify(state));
  } catch (_) {
    /* 사생활 보호 모드 등에서 실패할 수 있다 */
  }
}

/** 설정을 바꾸고 저장한다. 바뀐 시각을 찍어 동기화 우선순위를 정한다. */
function mutate(fn) {
  fn(state);
  state.updatedAt = Date.now();
  persistLocal();
  pushLater();
}

/* ── 목록 버튼 설정 ───────────────────────────────────── */

export function listConfig(listId) {
  return state.listConfigs[listId] || null;
}

export function setListConfig(listId, { label, color, order }) {
  mutate((s) => {
    const prev = s.listConfigs[listId] || {};
    const next = {
      label: label ?? prev.label,
      color: color ?? prev.color,
    };
    const nextOrder = order ?? prev.order;
    if (nextOrder !== undefined) next.order = nextOrder;
    s.listConfigs[listId] = next;
  });
}

/** 사용자가 정한 순서대로 목록을 정렬한다(안드로이드와 같은 규칙). */
export function orderedLists(taskLists) {
  return taskLists
    .map((item, apiIndex) => ({ item, apiIndex }))
    .sort((a, b) => {
      const ao = state.listConfigs[a.item.id]?.order ?? defaultOrder(a.apiIndex, a.item.title);
      const bo = state.listConfigs[b.item.id]?.order ?? defaultOrder(b.apiIndex, b.item.title);
      return ao - bo;
    });
}

export function moveList(taskLists, listId, up) {
  const ordered = orderedLists(taskLists);
  const pos = ordered.findIndex((e) => e.item.id === listId);
  if (pos < 0) return;
  const target = up ? pos - 1 : pos + 1;
  if (target < 0 || target >= ordered.length) return;

  const [moved] = ordered.splice(pos, 1);
  ordered.splice(target, 0, moved);

  mutate((s) => {
    ordered.forEach((entry, newOrder) => {
      const prev = s.listConfigs[entry.item.id] || {};
      s.listConfigs[entry.item.id] = {
        label: prev.label ?? defaultLabel(entry.apiIndex, entry.item.title),
        color: prev.color ?? defaultColor(entry.apiIndex),
        order: newOrder,
      };
    });
  });
}

/* ── 단축버튼 ─────────────────────────────────────────── */

export function shortcut(slot) {
  return state.shortcuts[String(slot)] || null;
}

export function setShortcut(slot, { title, icon, color, listId, done }) {
  mutate((s) => {
    s.shortcuts[String(slot)] = {
      title,
      icon,
      color,
      ...(listId ? { listId } : {}),
      done: Boolean(done),
    };
  });
}

export function removeShortcut(slot) {
  mutate((s) => {
    delete s.shortcuts[String(slot)];
  });
}

/* ── 습관 목록 ────────────────────────────────────────── */

export function habitListId() {
  return state.habitListId || '';
}

export function setHabitListId(listId) {
  mutate((s) => {
    s.habitListId = listId || '';
  });
}

/* ── Drive 동기화 ─────────────────────────────────────── */

let pushTimer = null;
let onSyncChange = () => {};

export function observeSync(fn) {
  onSyncChange = fn;
}

/** 저장이 몰릴 때 마지막 한 번만 올린다. */
function pushLater() {
  if (!api.hasToken()) return;
  clearTimeout(pushTimer);
  pushTimer = setTimeout(push, 1200);
}

async function push() {
  if (!api.hasToken()) return;
  try {
    onSyncChange({ status: 'syncing' });
    await api.uploadSettings(JSON.stringify(state));
    onSyncChange({ status: 'synced', at: Date.now() });
  } catch (e) {
    onSyncChange({ status: 'error', message: e.message });
  }
}

/**
 * Drive와 맞춘다. 더 최근에 바뀐 쪽이 이긴다.
 * 실패해도 브라우저에 저장된 설정으로 계속 동작한다.
 */
export async function sync() {
  if (!api.hasToken()) return;
  try {
    onSyncChange({ status: 'syncing' });
    const remoteRaw = await api.downloadSettings();
    const remote = remoteRaw ? JSON.parse(remoteRaw) : null;
    const remoteAt = remote?.updatedAt || 0;

    if (remote && remoteAt > (state.updatedAt || 0)) {
      state = { ...emptyState(), ...remote };
      persistLocal();
    } else if ((state.updatedAt || 0) > remoteAt) {
      await api.uploadSettings(JSON.stringify(state));
    }
    onSyncChange({ status: 'synced', at: Date.now() });
  } catch (e) {
    onSyncChange({ status: 'error', message: e.message });
  }
}
