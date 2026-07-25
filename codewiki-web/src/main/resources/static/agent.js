(() => {
  'use strict';
  const demoQuestion = '查询2026年7月1日至7月7日短信敏感词拦截情况，找到异常平台，并查询负责人，生成告警摘要';
  const state = { source: null, current: null, startedAt: 0, seen: new Set() };
  const $ = (id) => document.getElementById(id);
  const messages = $('messages');
  const input = $('messageInput');

  function requestId() {
    return 'req-' + Date.now().toString(36) + '-' + Math.random().toString(36).slice(2, 7);
  }

  function appendMessage(kind, text) {
    const card = document.createElement('article');
    card.className = 'message ' + kind;
    const meta = document.createElement('div');
    meta.className = 'meta';
    const badge = document.createElement('span');
    badge.className = 'badge';
    badge.textContent = kind === 'user' ? 'User' : 'Agent';
    meta.appendChild(badge);
    const content = document.createElement('div');
    content.className = 'answer';
    content.textContent = text;
    card.appendChild(meta);
    card.appendChild(content);
    messages.appendChild(card);
    messages.scrollTop = messages.scrollHeight;
    return card;
  }

  function ensureAgentCard(conversationId, requestId) {
    const card = appendMessage('agent', '正在理解请求');
    card.dataset.conversationId = conversationId;
    const meta = card.querySelector('.meta');
    ['conversationId: ' + conversationId, 'requestId: ' + requestId].forEach((t) => {
      const span = document.createElement('span'); span.textContent = t; meta.appendChild(span);
    });
    const exportBtn = document.createElement('button');
    exportBtn.type = 'button';
    exportBtn.className = 'secondary';
    exportBtn.textContent = '导出 Conversation';
    exportBtn.addEventListener('click', () => exportConversation(conversationId));
    card.appendChild(exportBtn);
    const events = document.createElement('div');
    events.className = 'events';
    card.appendChild(events);
    return card;
  }

  function labelSource(source) {
    if (source === 'LOCAL_JAVA') return 'Local';
    if (source === 'MCP') return 'MCP';
    if (source === 'PYTHON') return 'Python';
    return source || '';
  }

  function renderEvent(event) {
    if (!state.current || !event || state.seen.has(event.sequence)) return;
    state.seen.add(event.sequence);
    const list = state.current.querySelector('.events');
    const row = document.createElement('div');
    row.className = 'event';
    const head = document.createElement('div');
    head.className = 'event-head';
    const left = document.createElement('span');
    left.textContent = '#' + event.sequence + ' ' + event.type + ' · ' + event.status + ' · ' + (event.progress || 0) + '%';
    const right = document.createElement('span');
    const elapsed = state.startedAt ? Date.now() - state.startedAt : 0;
    right.textContent = '耗时 ' + elapsed + 'ms';
    head.appendChild(left); head.appendChild(right);
    const stage = document.createElement('div');
    stage.className = 'event-stage';
    stage.textContent = event.stage || '';
    const msg = document.createElement('div');
    msg.className = 'event-msg';
    msg.textContent = event.message || '';
    row.appendChild(head); row.appendChild(stage); row.appendChild(msg);
    const md = event.metadata || {};
    if (md.toolName || md.toolSource || md.executionMode || md.iteration) {
      const details = document.createElement('div');
      details.className = 'meta';
      const parts = [];
      if (md.toolName) parts.push('Tool: ' + md.toolName);
      if (md.toolSource) parts.push('来源: ' + labelSource(md.toolSource));
      if (md.executionMode) parts.push('executionMode: ' + md.executionMode);
      if (md.iteration) parts.push('iteration: ' + md.iteration);
      details.textContent = parts.join(' · ');
      row.appendChild(details);
    }
    list.appendChild(row);
    const answer = state.current.querySelector('.answer');
    answer.textContent = event.message || event.stage || '运行中';
    messages.scrollTop = messages.scrollHeight;
  }

  async function loadFinal(conversationId) {
    const res = await fetch('/api/agent/conversations/' + encodeURIComponent(conversationId));
    if (!res.ok) return;
    const data = await res.json();
    if (state.current && data.answer) {
      state.current.querySelector('.answer').textContent = data.answer;
      const meta = state.current.querySelector('.meta');
      const status = document.createElement('span');
      status.className = 'badge ' + (data.status === 'COMPLETED' ? 'ok' : 'fail');
      status.textContent = data.status + ' · Conversation Duration ' + (Date.now() - state.startedAt) + 'ms';
      meta.appendChild(status);
    }
  }


  async function exportConversation(conversationId) {
    const res = await fetch('/api/agent/conversations/' + encodeURIComponent(conversationId) + '/export');
    if (!res.ok) { appendMessage('agent', '导出失败：HTTP ' + res.status); return; }
    const data = await res.json();
    const blob = new Blob([JSON.stringify(data, null, 2)], { type: 'application/json' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = conversationId + '-export.json';
    a.click();
    URL.revokeObjectURL(url);
  }

  async function send() {
    const message = input.value.trim();
    if (!message) return;
    if (state.source) state.source.close();
    appendMessage('user', message);
    input.value = '';
    state.startedAt = Date.now();
    state.seen = new Set();
    const payload = { userId: $('userId').value || 'E10086', sessionId: $('sessionId').value || 'web-demo', requestId: requestId(), message };
    const res = await fetch('/api/agent/conversations', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(payload) });
    if (!res.ok) { appendMessage('agent', '创建 Conversation 失败：HTTP ' + res.status); return; }
    const created = await res.json();
    state.current = ensureAgentCard(created.conversationId, created.requestId);
    state.source = new EventSource(created.eventsUrl);
    state.source.onmessage = (ev) => { try { renderEvent(JSON.parse(ev.data)); } catch (_) {} };
    const eventTypes = ['REQUEST_ACCEPTED','AGENT_STARTED','CONTEXT_LOADED','SKILL_DISCOVERY_STARTED','SKILL_MATCHED','SKILL_LOADED','TOOL_CALL_STARTED','TOOL_CALL_COMPLETED','TOOL_CALL_FAILED','MCP_DISCOVERY_STARTED','MCP_DISCOVERY_COMPLETED','MCP_TOOL_CALL_STARTED','MCP_TOOL_CALL_COMPLETED','MODEL_CALL_STARTED','MODEL_CALL_COMPLETED','MODEL_TOOL_SELECTION','ANSWER_GENERATION_STARTED','ANSWER_GENERATION_COMPLETED','AGENT_COMPLETED','AGENT_FAILED','HEARTBEAT'];
    eventTypes.forEach((type) => state.source.addEventListener(type, (ev) => { try { renderEvent(JSON.parse(ev.data)); } catch (_) {} }));
    state.source.onerror = () => { state.source.close(); loadFinal(created.conversationId); };
    state.source.addEventListener('AGENT_COMPLETED', () => { state.source.close(); loadFinal(created.conversationId); });
    state.source.addEventListener('AGENT_FAILED', () => { state.source.close(); loadFinal(created.conversationId); });
  }

  $('demoBtn').addEventListener('click', () => { input.value = demoQuestion; input.focus(); });
  $('sendBtn').addEventListener('click', send);
  $('clearBtn').addEventListener('click', () => { if (state.source) state.source.close(); messages.textContent = ''; });
  input.addEventListener('keydown', (ev) => { if (ev.key === 'Enter' && !ev.shiftKey) { ev.preventDefault(); send(); } });
  input.value = demoQuestion;
})();
