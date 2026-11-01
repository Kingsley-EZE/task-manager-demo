/* ---------------------------------------------------------------------------
   The whole front end: plain JavaScript talking to the REST API with fetch().
   No framework and no build step — the browser runs this file as written.
   ------------------------------------------------------------------------ */

const state = {
  tasks: [],
  categories: [],
  filters: { status: '', categoryId: '', sort: 'DUE_DATE', q: '' },
  editingId: null,
};

const el = (id) => document.getElementById(id);
const list = el('list');

/* --- talking to the API -------------------------------------------------- */

/**
 * One wrapper for every call. The API reports problems as a JSON body with a
 * `message` (and `fieldErrors` for validation), so failures are shown to the
 * person using the app rather than swallowed into the console.
 */
async function api(path, options = {}) {
  const response = await fetch(`/api${path}`, {
    headers: { 'Content-Type': 'application/json' },
    ...options,
  });

  if (!response.ok) {
    const problem = await response.json().catch(() => null);
    const fields = problem?.fieldErrors ?? {};
    const detail = Object.entries(fields).map(([field, why]) => `${field} — ${why}`).join('; ');
    throw new Error(detail || problem?.message || `Request failed (${response.status})`);
  }

  return response.status === 204 ? null : response.json();
}

function showProblem(error) {
  const notice = el('notice');
  notice.textContent = error.message;
  notice.hidden = false;
}

function clearProblem() {
  el('notice').hidden = true;
}

/** Runs an action, refreshes the list, and reports anything that went wrong. */
async function run(action) {
  try {
    clearProblem();
    await action();
    await loadTasks();
  } catch (error) {
    showProblem(error);
  }
}

/* --- loading ------------------------------------------------------------- */

async function loadTasks() {
  const query = new URLSearchParams();
  const { status, categoryId, sort, q } = state.filters;
  if (status) query.set('status', status);
  if (categoryId) query.set('categoryId', categoryId);
  if (q.trim()) query.set('q', q.trim());
  query.set('sort', sort);

  state.tasks = await api(`/tasks?${query}`);
  renderTasks();
  renderSummary();
}

async function loadCategories() {
  state.categories = await api('/categories');
  renderCategoryOptions();
  renderCategoryTags();
}

/* --- drawing the list ---------------------------------------------------- */

function renderTasks() {
  list.replaceChildren();

  if (state.tasks.length === 0) {
    const empty = document.createElement('li');
    empty.className = 'empty';
    empty.textContent = state.filters.status || state.filters.categoryId || state.filters.q
      ? 'Nothing matches those filters.'
      : 'Nothing here yet. Add the first thing above.';
    list.append(empty);
    return;
  }

  state.tasks.forEach((task) => list.append(
    task.id === state.editingId ? editorRow(task) : taskRow(task),
  ));
}

const MARKS = { TODO: '☐', IN_PROGRESS: '◐', DONE: '☑' };
const NEXT_STATUS = { TODO: 'IN_PROGRESS', IN_PROGRESS: 'DONE', DONE: 'TODO' };
const STATUS_WORDS = { TODO: 'to do', IN_PROGRESS: 'in progress', DONE: 'done' };

function taskRow(task) {
  const row = el('task-template').content.firstElementChild.cloneNode(true);
  row.classList.toggle('is-done', task.status === 'DONE');

  const mark = row.querySelector('.mark');
  mark.textContent = MARKS[task.status];
  mark.setAttribute(
    'aria-label',
    `${task.title} is ${STATUS_WORDS[task.status]}. Mark as ${STATUS_WORDS[NEXT_STATUS[task.status]]}.`,
  );
  mark.addEventListener('click', () => run(() => api(`/tasks/${task.id}/status`, {
    method: 'PATCH',
    body: JSON.stringify({ status: NEXT_STATUS[task.status] }),
  })));

  const title = row.querySelector('.title');
  title.textContent = task.title;
  title.addEventListener('click', () => {
    state.editingId = task.id;
    renderTasks();
  });

  const notes = row.querySelector('.notes');
  if (task.notes) notes.textContent = task.notes; else notes.remove();

  const priority = row.querySelector('.priority');
  priority.dataset.level = task.priority;
  priority.insertAdjacentHTML(
    'afterend',
    `<span class="sr-only">${task.priority.toLowerCase()} priority</span>`,
  );

  const category = row.querySelector('.category');
  if (task.category) category.textContent = task.category.name; else category.remove();

  const due = row.querySelector('.due');
  if (task.dueDate) {
    due.textContent = describeDueDate(task.dueDate);
    due.classList.toggle('is-overdue', task.overdue);
  } else {
    due.remove();
  }

  const remove = row.querySelector('.remove');
  remove.querySelector('.sr-only').textContent = ` delete ${task.title}`;
  remove.addEventListener('click', () => run(() => api(`/tasks/${task.id}`, { method: 'DELETE' })));

  return row;
}

/** The same row, opened up for editing. */
function editorRow(task) {
  const row = document.createElement('li');
  row.className = 'task';

  const form = document.createElement('form');
  form.className = 'edit';
  form.innerHTML = `
    <input class="edit-title" name="title" maxlength="200" aria-label="Title" required>
    <label class="field grow"><span>Notes</span><input name="notes" maxlength="2000"></label>
    <label class="field"><span>Category</span><select name="categoryId"></select></label>
    <label class="field"><span>Due</span><input type="date" name="dueDate"></label>
    <label class="field"><span>Priority</span>
      <select name="priority">
        <option value="LOW">Low</option><option value="MEDIUM">Medium</option><option value="HIGH">High</option>
      </select>
    </label>
    <div class="edit-actions">
      <button type="button" class="cancel">Cancel</button>
      <button type="submit">Save changes</button>
    </div>
  `;

  // Reached through form.elements: form.title would read the element's title
  // attribute rather than the input named "title".
  const fields = form.elements;
  fillCategorySelect(fields.categoryId, 'None');
  fields.title.value = task.title;
  fields.notes.value = task.notes ?? '';
  fields.categoryId.value = task.category?.id ?? '';
  fields.dueDate.value = task.dueDate ?? '';
  fields.priority.value = task.priority;

  form.querySelector('.cancel').addEventListener('click', () => {
    state.editingId = null;
    clearProblem();
    renderTasks();
  });

  form.addEventListener('submit', (event) => {
    event.preventDefault();
    run(async () => {
      await api(`/tasks/${task.id}`, {
        method: 'PUT',
        body: JSON.stringify({
          title: fields.title.value,
          notes: fields.notes.value || null,
          categoryId: fields.categoryId.value || null,
          dueDate: fields.dueDate.value || null,
          priority: fields.priority.value,
          status: task.status,
          // The version this edit started from. If someone saved in the
          // meantime, the server refuses with a 409 rather than overwrite them.
          version: task.version,
        }),
      });
      state.editingId = null;
    });
  });

  const body = document.createElement('div');
  body.className = 'body';
  body.style.gridColumn = '1 / -1';
  body.append(form);
  row.append(body);
  fields.title.focus();
  return row;
}

function renderSummary() {
  const open = state.tasks.filter((task) => task.status !== 'DONE');
  const overdue = open.filter((task) => task.overdue).length;
  const today = new Date().toISOString().slice(0, 10);
  const dueToday = open.filter((task) => task.dueDate === today).length;

  const parts = [];
  if (overdue) parts.push(`${overdue} overdue`);
  if (dueToday) parts.push(`${dueToday} due today`);

  el('summary').textContent = parts.length
    ? `${sentenceCase(parts.join(', '))}.`
    : open.length
      ? `Nothing due today. ${open.length} still open.`
      : 'Nothing left to do.';
}

/* --- categories ---------------------------------------------------------- */

function fillCategorySelect(select, noneLabel) {
  select.replaceChildren(new Option(noneLabel, ''));
  state.categories.forEach((category) => select.append(new Option(category.name, category.id)));
}

function renderCategoryOptions() {
  const forNew = el('new-category');
  const forFilter = el('filter-category');
  const keptFilter = forFilter.value;

  fillCategorySelect(forNew, 'None');
  fillCategorySelect(forFilter, 'All');
  forFilter.value = keptFilter;
}

function renderCategoryTags() {
  const tags = el('tags');
  tags.replaceChildren();

  if (state.categories.length === 0) {
    const none = document.createElement('li');
    none.textContent = 'None yet.';
    tags.append(none);
    return;
  }

  state.categories.forEach((category) => {
    const item = document.createElement('li');
    item.textContent = category.name;

    const remove = document.createElement('button');
    remove.type = 'button';
    remove.innerHTML = '&times;';
    remove.setAttribute('aria-label', `Delete the ${category.name} category`);
    remove.addEventListener('click', () => run(async () => {
      await api(`/categories/${category.id}`, { method: 'DELETE' });
      await loadCategories();
    }));

    item.append(remove);
    tags.append(item);
  });
}

/* --- dates --------------------------------------------------------------- */

const DAY_MONTH = new Intl.DateTimeFormat(undefined, { weekday: 'short', day: 'numeric', month: 'short' });

function describeDueDate(isoDate) {
  const due = new Date(`${isoDate}T00:00:00`);
  const today = new Date();
  today.setHours(0, 0, 0, 0);
  const days = Math.round((due - today) / 86_400_000);

  if (days === 0) return 'Today';
  if (days === 1) return 'Tomorrow';
  if (days === -1) return 'Yesterday';
  return DAY_MONTH.format(due);
}

const sentenceCase = (text) => text.charAt(0).toUpperCase() + text.slice(1);

/* --- wiring -------------------------------------------------------------- */

el('compose').addEventListener('submit', (event) => {
  event.preventDefault();
  const form = event.target;

  run(async () => {
    await api('/tasks', {
      method: 'POST',
      body: JSON.stringify({
        title: el('new-title').value,
        categoryId: el('new-category').value || null,
        dueDate: el('new-due').value || null,
        priority: el('new-priority').value,
      }),
    });
    form.reset();
    el('new-priority').value = 'MEDIUM';
    el('new-title').focus();
  });
});

el('add-category').addEventListener('submit', (event) => {
  event.preventDefault();
  const input = el('new-category-name');

  run(async () => {
    await api('/categories', { method: 'POST', body: JSON.stringify({ name: input.value }) });
    input.value = '';
    await loadCategories();
  });
});

el('statuses').addEventListener('click', (event) => {
  const chip = event.target.closest('.chip');
  if (!chip) return;

  state.filters.status = chip.dataset.status;
  el('statuses').querySelectorAll('.chip').forEach((other) => {
    other.classList.toggle('is-on', other === chip);
  });
  run(() => Promise.resolve());
});

el('filter-category').addEventListener('change', (event) => {
  state.filters.categoryId = event.target.value;
  run(() => Promise.resolve());
});

el('filter-sort').addEventListener('change', (event) => {
  state.filters.sort = event.target.value;
  run(() => Promise.resolve());
});

let searchTimer;
el('filter-query').addEventListener('input', (event) => {
  state.filters.q = event.target.value;
  clearTimeout(searchTimer);                       // wait for a pause in typing
  searchTimer = setTimeout(() => run(() => Promise.resolve()), 250);
});

(async function start() {
  try {
    await loadCategories();
    await loadTasks();
  } catch (error) {
    showProblem(error);
  }
})();
