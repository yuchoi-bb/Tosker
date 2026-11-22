/**
 * Tosker 웹판.
 * 안드로이드 앱과 같은 Google Tasks 데이터를 보고, 설정은 Drive로 함께 동기화한다.
 */

import * as api from './api.js';
import * as store from './store.js';
import * as icons from './icons.js';

const SCOPES = [
  'https://www.googleapis.com/auth/tasks',
  'https://www.googleapis.com/auth/drive.appdata',
].join(' ');

const CLIENT_ID_KEY = 'tosker_client_id';
const SHORTCUT_SLOTS = 10;

const DAYS = [
  ['일', 0, '#ff5252'],
  ['월', 1, '#ff8c00'],
  ['화', 2, '#ffd600'],
  ['수', 3, '#4caf50'],
  ['목', 4, '#2196f3'],
  ['금', 5, '#3f51b5'],
  ['토', 6, '#9c27b0'],
];

const app = {
  taskLists: [],
  selectedListId: null,
  selectedDate: new Date(),
  tokenClient: null,
};

const $ = (id) => document.getElementById(id);
const show = (id) => $(id).classList.remove('hidden');
const hide = (id) => $(id).classList.add('hidden');

/* ── 시작 ─────────────────────────────────────────────── */

function boot() {
  store.load();
  store.observeSync(renderSyncStatus);

  $('origin-hint').textContent = window.location.origin;
  $('client-id-save').onclick = saveClientId;
  $('reset-client-id').onclick = () => {
    localStorage.removeItem(CLIENT_ID_KEY);
    location.reload();
  };
  $('signin').onclick = signIn;

  if (!localStorage.getItem(CLIENT_ID_KEY)) {
    show('setup');
  } else {
    show('login');
    initTokenClient();
  }

  bindMainControls();
}

function saveClientId() {
  const value = $('client-id-input').value.trim();
  if (!value) return;
  localStorage.setItem(CLIENT_ID_KEY, value);
  location.reload();
}

function initTokenClient() {
  const clientId = localStorage.getItem(CLIENT_ID_KEY);
  if (!window.google?.accounts?.oauth2) {
    // GSI 스크립트가 아직 안 왔으면 잠시 뒤 다시 시도한다.
    setTimeout(initTokenClient, 300);
    return;
  }
  app.tokenClient = google.accounts.oauth2.initTokenClient({
    client_id: clientId,
    scope: SCOPES,
    callback: (res) => {
      if (res.error) {
        $('login-error').textContent = `로그인 실패: ${res.error}`;
        return;
      }
      api.setAccessToken(res.access_token);
      onSignedIn();
    },
  });
}

function signIn() {
  $('login-error').textContent = '';
  if (!app.tokenClient) {
    $('login-error').textContent = '초기화 중입니다. 잠시 후 다시 눌러주세요.';
    initTokenClient();
    return;
  }
  app.tokenClient.requestAccessToken({ prompt: '' });
}

async function onSignedIn() {
  hide('login');
  hide('setup');
  show('main');
  try {
    app.taskLists = await api.listTaskLists();
    await store.sync();
    const ordered = store.orderedLists(app.taskLists);
    app.selectedListId = ordered[0]?.item.id || null;
    renderAll();
  } catch (e) {
    setStatus(e.message, true);
  }
}

/* ── 메인 화면 ─────────────────────────────────────────── */

function bindMainControls() {
  $('task-text').oninput = () => {
    $('upload').disabled = !$('task-text').value.trim() || !app.selectedListId;
  };
  $('task-text').onkeydown = (e) => {
    if (e.key === 'Enter') uploadTask();
  };
  $('upload').onclick = uploadTask;
  $('voice').onclick = startVoice;
  $('date-picker').onchange = (e) => {
    const [y, m, d] = e.target.value.split('-').map(Number);
    if (y) {
      app.selectedDate = new Date(y, m - 1, d);
      renderDays();
      renderDate();
    }
  };
  $('open-settings').onclick = openSettings;
  $('open-today').onclick = openTodayTasks;
  $('open-habits').onclick = () => openHabitGrid(new Date());
}

function renderAll() {
  renderCategories();
  renderDays();
  renderDate();
  renderShortcuts();
}

function renderCategories() {
  const row = $('category-row');
  row.replaceChildren();

  store.orderedLists(app.taskLists).forEach(({ item, apiIndex }) => {
    const cfg = store.listConfig(item.id);
    const label = cfg?.label ?? store.defaultLabel(apiIndex, item.title);
    const color = store.toCss(cfg?.color ?? store.defaultColor(apiIndex));
    const selected = app.selectedListId === item.id;

    const btn = document.createElement('button');
    btn.className = `category${selected ? ' selected' : ''}`;
    btn.onclick = () => {
      app.selectedListId = item.id;
      renderCategories();
      $('upload').disabled = !$('task-text').value.trim();
    };

    const bubble = document.createElement('span');
    bubble.className = 'bubble';
    bubble.textContent = label.slice(0, 2);
    bubble.style.background = selected ? color : `${color}30`;
    bubble.style.color = selected ? '#fff' : color;

    const name = document.createElement('span');
    name.className = 'name';
    name.textContent = label;
    if (selected) name.style.color = color;

    btn.append(bubble, name);
    row.append(btn);
  });
}

/** 오늘 기준 해당 요일의 가장 가까운 날짜 (오늘 포함 ~ 6일 후) */
function nextDateForDow(dow) {
  const today = new Date();
  today.setHours(0, 0, 0, 0);
  const diff = (dow - today.getDay() + 7) % 7;
  const d = new Date(today);
  d.setDate(d.getDate() + diff);
  return d;
}

function renderDays() {
  const row = $('day-row');
  row.replaceChildren();

  DAYS.forEach(([label, dow, color]) => {
    const selected = app.selectedDate.getDay() === dow;
    const btn = document.createElement('button');
    btn.className = 'day';
    btn.textContent = label;
    btn.style.background = selected ? color : `${color}26`;
    btn.style.color = selected ? '#fff' : color;
    btn.onclick = () => {
      app.selectedDate = nextDateForDow(dow);
      renderDays();
      renderDate();
    };
    row.append(btn);
  });
}

function renderDate() {
  const d = app.selectedDate;
  $('selected-date').textContent =
    `${d.getFullYear()}년 ${d.getMonth() + 1}월 ${d.getDate()}일`;
  $('date-picker').value = api.dateKey(d);
}

function setStatus(message, isError = false) {
  const el = $('status');
  el.textContent = message;
  el.className = `status ${isError ? 'bad' : 'ok'}`;
  if (message && !isError) setTimeout(() => { el.textContent = ''; }, 2500);
}

async function uploadTask() {
  const text = $('task-text').value.trim();
  if (!text || !app.selectedListId) return;
  $('upload').disabled = true;
  try {
    await api.insertTask(app.selectedListId, {
      title: text,
      due: api.toDueStamp(app.selectedDate),
    });
    $('task-text').value = '';
    setStatus('할 일이 등록되었습니다 ✓');
  } catch (e) {
    setStatus(e.message, true);
  } finally {
    $('upload').disabled = !$('task-text').value.trim();
  }
}

function startVoice() {
  const Recognition = window.SpeechRecognition || window.webkitSpeechRecognition;
  if (!Recognition) {
    setStatus('이 브라우저는 음성 입력을 지원하지 않습니다. (크롬 권장)', true);
    return;
  }
  const recognition = new Recognition();
  recognition.lang = 'ko-KR';
  recognition.interimResults = false;
  recognition.onresult = (event) => {
    const said = event.results[0][0].transcript;
    const input = $('task-text');
    input.value = input.value ? `${input.value} ${said}` : said;
    $('upload').disabled = !input.value.trim() || !app.selectedListId;
  };
  recognition.onerror = (e) => setStatus(`음성 인식 오류: ${e.error}`, true);
  recognition.start();
  setStatus('듣고 있습니다...');
}

/* ── 단축버튼 ─────────────────────────────────────────── */

function renderShortcuts() {
  const grid = $('shortcut-grid');
  grid.replaceChildren();

  for (let slot = 0; slot < SHORTCUT_SLOTS; slot += 1) {
    const cfg = store.shortcut(slot);
    const btn = document.createElement('button');
    btn.className = 'shortcut';
    btn.title = cfg ? `${cfg.title} (길게 눌러 수정)` : '단축버튼 추가';

    const tile = document.createElement('span');
    tile.className = 'tile';

    if (cfg) {
      const color = store.toCss(cfg.color);
      tile.style.background = `${color}30`;
      tile.append(icons.render(cfg.icon, { size: 22, color }));
      if (!cfg.done) {
        const dot = document.createElement('span');
        dot.className = 'dot';
        dot.style.background = color;
        tile.append(dot);
      }
      const label = document.createElement('span');
      label.className = 'label';
      label.textContent = cfg.title;
      btn.append(tile, label);
      btn.onclick = () => runShortcut(slot);
    } else {
      tile.classList.add('empty');
      const plus = document.createElement('span');
      plus.className = 'material-symbols-rounded';
      plus.textContent = 'add';
      plus.style.fontSize = '18px';
      plus.style.color = 'var(--muted)';
      tile.append(plus);
      const label = document.createElement('span');
      label.className = 'label';
      btn.append(tile, label);
      btn.onclick = () => editShortcut(slot);
    }

    // 길게 누르기(모바일) / 우클릭(PC)으로 수정
    attachLongPress(btn, () => editShortcut(slot));
    grid.append(btn);
  }
}

function attachLongPress(el, handler) {
  let timer = null;
  let fired = false;

  const start = () => {
    fired = false;
    timer = setTimeout(() => { fired = true; handler(); }, 550);
  };
  const cancel = () => clearTimeout(timer);

  el.addEventListener('touchstart', start, { passive: true });
  el.addEventListener('touchend', cancel);
  el.addEventListener('touchmove', cancel);
  el.addEventListener('mousedown', start);
  el.addEventListener('mouseup', cancel);
  el.addEventListener('mouseleave', cancel);
  el.addEventListener('contextmenu', (e) => {
    e.preventDefault();
    cancel();
    fired = true;
    handler();
  });
  // 길게 눌러 수정 창이 떴으면 뒤따르는 click은 무시한다.
  el.addEventListener('click', (e) => {
    if (fired) {
      e.stopImmediatePropagation();
      e.preventDefault();
      fired = false;
    }
  }, true);
}

async function runShortcut(slot) {
  const cfg = store.shortcut(slot);
  if (!cfg) return;
  const listId = cfg.listId || app.selectedListId;
  if (!listId) return;

  const stamp = api.toDueStamp(app.selectedDate);
  const task = { title: cfg.title, due: stamp };
  if (cfg.done) {
    task.status = 'completed';
    task.completed = stamp;
  }
  try {
    await api.insertTask(listId, task);
    setStatus(`${cfg.title} 등록됨 ✓`);
  } catch (e) {
    setStatus(e.message, true);
  }
}

function editShortcut(slot) {
  const existing = store.shortcut(slot);
  const draft = {
    title: existing?.title || '',
    icon: existing?.icon || icons.DEFAULT_KEY,
    color: existing?.color || store.PALETTE[0],
    listId: existing?.listId || app.selectedListId || '',
    done: existing ? Boolean(existing.done) : true,
  };

  openModal((body, close) => {
    body.append(modalHead(existing ? '단축버튼 수정' : '단축버튼 추가', close));

    const title = inputField('할 일 내용', draft.title, (v) => { draft.title = v; });
    body.append(title);

    body.append(sectionLabel('버튼을 누르면'));
    const typeChips = document.createElement('div');
    typeChips.className = 'chips';
    const renderType = () => {
      typeChips.replaceChildren();
      [[true, '한 일 기록'], [false, '해야 할 일']].forEach(([value, text]) => {
        const chip = document.createElement('button');
        chip.className = `chip${draft.done === value ? ' on' : ''}`;
        chip.textContent = text;
        chip.onclick = () => { draft.done = value; renderType(); };
        typeChips.append(chip);
      });
    };
    renderType();
    body.append(typeChips);

    body.append(sectionLabel('등록할 목록'));
    const listChips = document.createElement('div');
    listChips.className = 'chips';
    const renderLists = () => {
      listChips.replaceChildren();
      app.taskLists.forEach((l) => {
        const chip = document.createElement('button');
        chip.className = `chip${draft.listId === l.id ? ' on' : ''}`;
        chip.textContent = l.title;
        chip.onclick = () => { draft.listId = l.id; renderLists(); };
        listChips.append(chip);
      });
    };
    renderLists();
    body.append(listChips);

    body.append(sectionLabel('색상'));
    const swatches = document.createElement('div');
    swatches.className = 'swatches';
    const renderSwatches = () => {
      swatches.replaceChildren();
      store.PALETTE.forEach((hex) => {
        const sw = document.createElement('button');
        sw.className = `swatch${draft.color === hex ? ' on' : ''}`;
        sw.style.background = store.toCss(hex);
        sw.onclick = () => { draft.color = hex; renderSwatches(); renderIconPick(); };
        swatches.append(sw);
      });
    };
    renderSwatches();
    body.append(swatches);

    body.append(sectionLabel('아이콘'));
    const iconWrap = document.createElement('div');
    const renderIconPick = () => {
      iconWrap.replaceChildren();
      icons.GROUPS.forEach(([groupName, entries]) => {
        iconWrap.append(sectionLabel(groupName));
        const pick = document.createElement('div');
        pick.className = 'icon-pick';
        entries.forEach(([key]) => {
          const b = document.createElement('button');
          if (draft.icon === key) b.classList.add('on');
          b.append(icons.render(key, { size: 20, color: store.toCss(draft.color) }));
          b.onclick = () => { draft.icon = key; renderIconPick(); };
          pick.append(b);
        });
        iconWrap.append(pick);
      });
    };
    renderIconPick();
    body.append(iconWrap);

    const actions = document.createElement('div');
    actions.className = 'modal-actions';
    if (existing) {
      const del = document.createElement('button');
      del.className = 'secondary danger';
      del.textContent = '삭제';
      del.onclick = () => { store.removeShortcut(slot); renderShortcuts(); close(); };
      actions.append(del);
    }
    const cancel = document.createElement('button');
    cancel.className = 'secondary';
    cancel.textContent = '취소';
    cancel.onclick = close;
    const save = document.createElement('button');
    save.className = 'primary';
    save.textContent = '저장';
    save.onclick = () => {
      if (!draft.title.trim() || !draft.listId) return;
      store.setShortcut(slot, { ...draft, title: draft.title.trim() });
      renderShortcuts();
      close();
    };
    actions.append(cancel, save);
    body.append(actions);
  });
}

/* ── 오늘 할일 ─────────────────────────────────────────── */

async function openTodayTasks() {
  const ordered = store.orderedLists(app.taskLists);
  const todayList =
    ordered.find((e) => store.isTodayList(e.item.title))?.item || ordered[0]?.item;
  const sources = ordered.map((e) => e.item).filter((l) => l.id !== todayList?.id);

  openModal((body, close) => {
    body.append(modalHead('오늘 할일', close));
    const info = document.createElement('p');
    info.className = 'muted';
    info.textContent = `선택한 항목을 ${todayList?.title || '2day'}(으)로 옮깁니다`;
    body.append(info);

    const listWrap = document.createElement('div');
    listWrap.textContent = '불러오는 중...';
    body.append(listWrap);

    const actions = document.createElement('div');
    actions.className = 'modal-actions';
    const cancel = document.createElement('button');
    cancel.className = 'secondary';
    cancel.textContent = '닫기';
    cancel.onclick = close;
    const move = document.createElement('button');
    move.className = 'primary';
    move.textContent = '옮기기';
    move.disabled = true;
    actions.append(cancel, move);
    body.append(actions);

    const selected = new Set();
    const todayKey = api.dateKey(new Date());
    const dueMax = `${todayKey}T23:59:59.999Z`;

    (async () => {
      try {
        const collected = [];
        for (const list of sources) {
          const tasks = await api.listTasks(list.id, { dueMax });
          tasks.forEach((t) => {
            const key = api.dueDateKey(t.due);
            if (!t.id || !t.title?.trim() || !key || key > todayKey) return;
            collected.push({
              id: t.id,
              listId: list.id,
              listTitle: list.title,
              title: t.title.trim(),
              notes: t.notes || '',
              dueKey: key,
            });
          });
        }
        // 목록 순서로 묶고, 묶음 안에서 마감일 순으로 정렬한다.
        const listOrder = new Map(sources.map((l, i) => [l.id, i]));
        collected.sort((a, b) => {
          const d = (listOrder.get(a.listId) ?? 0) - (listOrder.get(b.listId) ?? 0);
          return d !== 0 ? d : a.dueKey.localeCompare(b.dueKey);
        });

        listWrap.replaceChildren();
        if (!collected.length) {
          listWrap.textContent = '오늘 마감이거나 기한이 지난 할일이 없습니다.';
          return;
        }

        let lastList = null;
        collected.forEach((item) => {
          if (item.listTitle !== lastList) {
            lastList = item.listTitle;
            const count = collected.filter((c) => c.listTitle === lastList).length;
            const head = document.createElement('div');
            head.className = 'task-group';
            head.textContent = `${lastList} · ${count}개`;
            listWrap.append(head, document.createElement('hr'));
          }

          const row = document.createElement('div');
          row.className = 'task-row';
          const cb = document.createElement('input');
          cb.type = 'checkbox';
          cb.id = `t_${item.listId}_${item.id}`;
          cb.onchange = () => {
            if (cb.checked) selected.add(item); else selected.delete(item);
            move.disabled = selected.size === 0;
            move.textContent = selected.size ? `${selected.size}개 옮기기` : '옮기기';
          };
          const label = document.createElement('label');
          label.htmlFor = cb.id;
          label.textContent = item.title;
          const meta = document.createElement('span');
          const overdue = item.dueKey < todayKey;
          meta.className = `task-meta${overdue ? ' overdue' : ''}`;
          const [, mm, dd] = item.dueKey.split('-');
          meta.textContent = `${Number(mm)}/${Number(dd)}${overdue ? ' 기한 지남' : ''}`;
          row.append(cb, label, meta);
          listWrap.append(row);
        });
      } catch (e) {
        listWrap.textContent = e.message;
      }
    })();

    move.onclick = async () => {
      if (!todayList || !selected.size) return;
      move.disabled = true;
      move.textContent = '옮기는 중...';
      const stamp = api.toDueStamp(new Date());
      let moved = 0;
      let failed = 0;
      for (const item of selected) {
        try {
          await api.insertTask(todayList.id, {
            title: item.title,
            notes: item.notes,
            due: stamp,
          });
          await api.deleteTask(item.listId, item.id);
          moved += 1;
        } catch (_) {
          failed += 1;
        }
      }
      close();
      setStatus(failed ? `${moved}개 이동, ${failed}개 실패` : `${moved}개 이동 ✓`, failed > 0);
    };
  });
}

/* ── 습관 격자 ─────────────────────────────────────────── */

async function openHabitGrid(monthDate) {
  const listId = store.habitListId();
  if (!listId) {
    openModal((body, close) => {
      body.append(modalHead('습관 기록', close));
      const p = document.createElement('p');
      p.className = 'muted';
      p.textContent = '설정에서 습관 목록을 먼저 지정하세요.';
      body.append(p);
    });
    return;
  }

  openModal((body, close) => {
    body.append(modalHead('습관 기록', close));
    const content = document.createElement('div');
    content.textContent = '불러오는 중...';
    body.append(content);

    let current = new Date(monthDate.getFullYear(), monthDate.getMonth(), 1);

    const draw = async () => {
      content.replaceChildren();
      content.textContent = '불러오는 중...';
      try {
        const tasks = await api.listTasks(listId, { showCompleted: true, showHidden: true });
        const habits = tasks
          .filter((t) => !t.due && t.status !== 'completed' && t.title?.trim())
          .map((t) => ({ id: t.id, title: t.title.trim() }));

        const year = current.getFullYear();
        const month = current.getMonth();
        const prefix = `${year}-${String(month + 1).padStart(2, '0')}`;
        const lastDay = new Date(year, month + 1, 0).getDate();

        // (습관 제목, 날짜) -> 기록 할일 id
        const marks = new Map();
        tasks.forEach((t) => {
          if (t.status !== 'completed' || !t.title?.trim()) return;
          const key = api.dueDateKey(t.due);
          if (!key || !key.startsWith(prefix)) return;
          marks.set(`${t.title.trim()}|${key}`, t.id);
        });

        content.replaceChildren();
        content.append(
          habitHeader(current, (delta) => {
            current = new Date(year, month + delta, 1);
            draw();
          })
        );

        if (!habits.length) {
          const p = document.createElement('p');
          p.className = 'muted';
          p.textContent =
            '습관이 없습니다. 아래 버튼으로 추가하거나, Google Tasks의 습관 목록에 마감일 없는 할일을 만드세요.';
          content.append(p);
        } else {
          content.append(habitGrid(habits, marks, year, month, lastDay, listId, draw));
        }

        const add = document.createElement('button');
        add.className = 'secondary';
        add.textContent = '+ 습관 추가';
        add.onclick = async () => {
          const name = prompt('습관 이름');
          if (!name?.trim()) return;
          try {
            await api.insertTask(listId, { title: name.trim() });
            draw();
          } catch (e) {
            setStatus(e.message, true);
          }
        };
        content.append(add);
      } catch (e) {
        content.replaceChildren();
        content.textContent = e.message;
      }
    };
    draw();
  });
}

function habitHeader(current, onChangeMonth) {
  const head = document.createElement('div');
  head.className = 'habit-head';
  const prev = document.createElement('button');
  prev.className = 'icon-btn';
  prev.innerHTML = '<span class="material-symbols-rounded">chevron_left</span>';
  prev.onclick = () => onChangeMonth(-1);
  const title = document.createElement('b');
  title.textContent = `${current.getFullYear()}. ${current.getMonth() + 1}`;
  const next = document.createElement('button');
  next.className = 'icon-btn';
  next.innerHTML = '<span class="material-symbols-rounded">chevron_right</span>';
  next.onclick = () => onChangeMonth(1);
  head.append(prev, title, next);
  return head;
}

function habitGrid(habits, marks, year, month, lastDay, listId, redraw) {
  const todayKey = api.dateKey(new Date());
  const days = Array.from({ length: lastDay }, (_, i) => {
    const d = String(i + 1).padStart(2, '0');
    return `${year}-${String(month + 1).padStart(2, '0')}-${d}`;
  });

  const wrap = document.createElement('div');
  wrap.className = 'habit-wrap';

  // 왼쪽 고정: 습관 이름 + 달성률
  const names = document.createElement('div');
  names.className = 'habit-names';
  const spacer = document.createElement('div');
  spacer.style.height = '30px';
  names.append(spacer);
  habits.forEach((h) => {
    const done = days.filter((d) => marks.has(`${h.title}|${d}`)).length;
    const cell = document.createElement('div');
    cell.className = 'habit-name-cell';
    const t = document.createElement('span');
    t.className = 't';
    t.textContent = h.title;
    t.title = h.title;
    const c = document.createElement('span');
    c.className = 'c';
    c.textContent = `${done}/${lastDay}`;
    cell.append(t, c);
    names.append(cell);
  });

  // 오른쪽: 날짜 머리글 + X 격자
  const grid = document.createElement('div');
  grid.className = 'habit-days';

  const headRow = document.createElement('div');
  headRow.className = 'habit-row';
  days.forEach((key) => {
    const h = document.createElement('div');
    h.className = `habit-day-head${key === todayKey ? ' today' : ''}`;
    h.textContent = Number(key.slice(-2));
    headRow.append(h);
  });
  grid.append(headRow);

  habits.forEach((habit) => {
    const row = document.createElement('div');
    row.className = 'habit-row';
    days.forEach((key) => {
      const markKey = `${habit.title}|${key}`;
      const cell = document.createElement('button');
      cell.className = `habit-cell${key === todayKey ? ' today' : ''}`;
      cell.textContent = marks.has(markKey) ? 'X' : '';
      cell.onclick = async () => {
        const existingId = marks.get(markKey);
        cell.disabled = true;
        try {
          if (existingId) {
            await api.deleteTask(listId, existingId);
            marks.delete(markKey);
            cell.textContent = '';
          } else {
            const stamp = `${key}T00:00:00.000Z`;
            const created = await api.insertTask(listId, {
              title: habit.title,
              due: stamp,
              status: 'completed',
              completed: stamp,
            });
            marks.set(markKey, created.id);
            cell.textContent = 'X';
          }
          redraw();
        } catch (e) {
          setStatus(e.message, true);
        } finally {
          cell.disabled = false;
        }
      };
      row.append(cell);
    });
    grid.append(row);
  });

  wrap.append(names, grid);
  return wrap;
}

/* ── 설정 ─────────────────────────────────────────────── */

function openSettings() {
  openModal((body, close) => {
    body.append(modalHead('설정', close));

    // 동기화
    body.append(sectionLabel('기기 간 설정 동기화'));
    const syncRow = document.createElement('div');
    syncRow.className = 'modal-actions';
    const syncNow = document.createElement('button');
    syncNow.className = 'secondary';
    syncNow.textContent = '지금 동기화';
    syncNow.onclick = () => store.sync();
    syncRow.append(syncNow);
    const syncInfo = document.createElement('p');
    syncInfo.className = 'muted';
    syncInfo.textContent = '단축버튼·버튼 설정이 Google 계정에 저장되어 폰 앱과 공유됩니다.';
    body.append(syncInfo, syncRow);

    body.append(document.createElement('hr'));

    // 습관 목록
    body.append(sectionLabel('습관 목록'));
    const habitInfo = document.createElement('p');
    habitInfo.className = 'muted';
    habitInfo.textContent =
      '이 목록의 마감일 없는 할일이 습관이 되고, 완료 기록이 격자에 X로 표시됩니다.';
    body.append(habitInfo);

    const habitChips = document.createElement('div');
    habitChips.className = 'chips';
    const renderHabitChips = () => {
      habitChips.replaceChildren();
      app.taskLists.forEach((l) => {
        const chip = document.createElement('button');
        chip.className = `chip${store.habitListId() === l.id ? ' on' : ''}`;
        chip.textContent = l.title;
        chip.onclick = () => {
          store.setHabitListId(store.habitListId() === l.id ? '' : l.id);
          renderHabitChips();
        };
        habitChips.append(chip);
      });
    };
    renderHabitChips();
    body.append(habitChips);

    body.append(document.createElement('hr'));

    // 목록 버튼 설정
    body.append(sectionLabel('버튼 이름·색상·순서 (맨 위가 최좌측)'));
    const listWrap = document.createElement('div');
    const renderListConfigs = () => {
      listWrap.replaceChildren();
      const ordered = store.orderedLists(app.taskLists);
      ordered.forEach(({ item, apiIndex }, pos) => {
        const cfg = store.listConfig(item.id);
        const label = cfg?.label ?? store.defaultLabel(apiIndex, item.title);
        const color = cfg?.color ?? store.defaultColor(apiIndex);

        const block = document.createElement('div');
        block.style.margin = '10px 0';

        const head = document.createElement('div');
        head.className = 'modal-head';
        const name = document.createElement('b');
        name.textContent = `${pos + 1}. ${item.title}`;
        const arrows = document.createElement('div');
        [['keyboard_arrow_up', true], ['keyboard_arrow_down', false]].forEach(([sym, up]) => {
          const b = document.createElement('button');
          b.className = 'icon-btn';
          b.innerHTML = `<span class="material-symbols-rounded">${sym}</span>`;
          b.disabled = up ? pos === 0 : pos === ordered.length - 1;
          b.onclick = () => {
            store.moveList(app.taskLists, item.id, up);
            renderListConfigs();
            renderCategories();
          };
          arrows.append(b);
        });
        head.append(name, arrows);
        block.append(head);

        block.append(inputField('표시 이름', label, (v) => {
          store.setListConfig(item.id, { label: v, color });
          renderCategories();
        }));

        const swatches = document.createElement('div');
        swatches.className = 'swatches';
        store.PALETTE.forEach((hex) => {
          const sw = document.createElement('button');
          sw.className = `swatch${color === hex ? ' on' : ''}`;
          sw.style.background = store.toCss(hex);
          sw.onclick = () => {
            store.setListConfig(item.id, { color: hex });
            renderListConfigs();
            renderCategories();
          };
          swatches.append(sw);
        });
        block.append(swatches);
        listWrap.append(block);
      });
    };
    renderListConfigs();
    body.append(listWrap);
  });
}

function renderSyncStatus(state) {
  if (state.status === 'error') setStatus(`동기화 실패: ${state.message}`, true);
}

/* ── 작은 UI 도우미 ───────────────────────────────────── */

function modalHead(titleText, onClose) {
  const head = document.createElement('div');
  head.className = 'modal-head';
  const h = document.createElement('h2');
  h.textContent = titleText;
  const close = document.createElement('button');
  close.className = 'icon-btn';
  close.innerHTML = '<span class="material-symbols-rounded">close</span>';
  close.onclick = onClose;
  head.append(h, close);
  return head;
}

function sectionLabel(text) {
  const el = document.createElement('div');
  el.className = 'section-label';
  el.textContent = text;
  return el;
}

function inputField(labelText, value, onInput) {
  const wrap = document.createElement('div');
  wrap.append(sectionLabel(labelText));
  const input = document.createElement('input');
  input.type = 'text';
  input.value = value;
  input.oninput = () => onInput(input.value);
  wrap.append(input);
  return wrap;
}

function openModal(build) {
  const backdrop = $('modal-backdrop');
  const modal = $('modal');
  modal.replaceChildren();

  const close = () => {
    backdrop.classList.add('hidden');
    modal.replaceChildren();
  };
  backdrop.onclick = (e) => { if (e.target === backdrop) close(); };

  build(modal, close);
  backdrop.classList.remove('hidden');
}

boot();
