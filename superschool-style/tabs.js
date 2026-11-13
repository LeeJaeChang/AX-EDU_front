/**
 * 탭 = 글 한 편 패턴
 * <article class="topic" id="..." data-tab="탭 이름"> 목록으로 상단 탭바를 자동 생성합니다.
 * - 클릭 / ←→ Home End 키보드 이동
 * - URL 해시(#id)와 동기화, 마지막으로 본 탭 기억(localStorage)
 * - 비활성 article은 hidden 처리
 */
(function () {
  const list = document.getElementById("tablist");
  const topics = Array.from(document.querySelectorAll(".topic"));
  if (!list || topics.length === 0) return;

  const ids = topics.map((t) => t.id);
  const STORAGE_KEY = "topic";

  topics.forEach((t) => {
    const btn = document.createElement("button");
    btn.className = "tab";
    btn.type = "button";
    btn.id = "tab-" + t.id;
    btn.dataset.id = t.id;
    btn.setAttribute("role", "tab");
    btn.setAttribute("aria-controls", t.id);
    const label = document.createElement("b");
    label.textContent = t.dataset.tab || t.id;
    btn.appendChild(label);
    list.appendChild(btn);

    t.setAttribute("role", "tabpanel");
    t.setAttribute("aria-labelledby", btn.id);
  });
  list.setAttribute("role", "tablist");

  function select(id, { focus = false, toTop = false } = {}) {
    if (!ids.includes(id)) id = ids[0];
    topics.forEach((t) => (t.hidden = t.id !== id));
    list.querySelectorAll('[role="tab"]').forEach((b) => {
      const on = b.dataset.id === id;
      b.setAttribute("aria-selected", String(on));
      b.tabIndex = on ? 0 : -1;
      if (on) {
        b.scrollIntoView({ block: "nearest", inline: "nearest" });
        if (focus) b.focus();
      }
    });
    if (toTop) window.scrollTo({ top: 0 });
    try { localStorage.setItem(STORAGE_KEY, id); } catch (e) {}
  }

  list.addEventListener("click", (e) => {
    const b = e.target.closest('[role="tab"]');
    if (!b) return;
    select(b.dataset.id, { toTop: true });
    try { history.replaceState(null, "", "#" + b.dataset.id); } catch (e) {}
  });

  list.addEventListener("keydown", (e) => {
    const cur = ids.indexOf(list.querySelector('[aria-selected="true"]').dataset.id);
    const next = {
      ArrowRight: (cur + 1) % ids.length,
      ArrowLeft: (cur - 1 + ids.length) % ids.length,
      Home: 0,
      End: ids.length - 1,
    }[e.key];
    if (next === undefined) return;
    e.preventDefault();
    select(ids[next], { focus: true });
  });

  window.addEventListener("hashchange", () => {
    const h = location.hash.slice(1);
    if (ids.includes(h)) select(h, { toTop: true });
  });

  let saved = null;
  try { saved = localStorage.getItem(STORAGE_KEY); } catch (e) {}
  const h = location.hash.slice(1);
  select(ids.includes(h) ? h : saved || ids[0]);
})();
