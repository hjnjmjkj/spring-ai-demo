const elements = {
    form: document.getElementById('chatForm'),
    input: document.getElementById('messageInput'),
    messages: document.getElementById('messages'),
    mode: document.getElementById('modeSelect'),
    status: document.getElementById('connectionStatus'),
    send: document.getElementById('sendButton'),
    stop: document.getElementById('stopButton'),
    newChat: document.getElementById('newChatButton'),
    count: document.getElementById('characterCount'),
    knowledgeButton: document.getElementById('knowledgeButton'),
    knowledgeDialog: document.getElementById('knowledgeDialog'),
    knowledgeForm: document.getElementById('knowledgeForm'),
    knowledgeContent: document.getElementById('knowledgeContent'),
    knowledgeSource: document.getElementById('knowledgeSource'),
    knowledgeStatus: document.getElementById('knowledgeStatus'),
    saveKnowledge: document.getElementById('saveKnowledgeButton'),
    closeKnowledge: document.getElementById('closeKnowledgeButton'),
    cancelKnowledge: document.getElementById('cancelKnowledgeButton')
};

let conversationId = loadConversationId();
let activeController = null;

function loadConversationId() {
    const stored = localStorage.getItem('qwenConversationId');
    if (stored) return stored;
    const created = crypto.randomUUID();
    localStorage.setItem('qwenConversationId', created);
    return created;
}

function setStatus(type, text) {
    elements.status.className = `status ${type}`;
    elements.status.innerHTML = `<span></span>${escapeHtml(text)}`;
}

function setBusy(busy) {
    elements.send.disabled = busy;
    elements.input.disabled = busy;
    elements.stop.classList.toggle('hidden', !busy || elements.mode.value !== 'stream');
    setStatus(busy ? 'working' : 'idle', busy ? '正在思考…' : '准备就绪');
}

function addMessage(role, content = '', isError = false) {
    const article = document.createElement('article');
    article.className = `message ${role}${isError ? ' error' : ''}`;
    article.innerHTML = `<div class="avatar">${role === 'user' ? '你' : 'AI'}</div><div class="bubble"></div>`;
    const bubble = article.querySelector('.bubble');
    bubble.textContent = content;
    elements.messages.appendChild(article);
    scrollToBottom();
    return bubble;
}

function scrollToBottom() {
    elements.messages.scrollTop = elements.messages.scrollHeight;
}

function escapeHtml(value) {
    return String(value)
        .replaceAll('&', '&amp;').replaceAll('<', '&lt;').replaceAll('>', '&gt;')
        .replaceAll('"', '&quot;').replaceAll("'", '&#039;');
}

async function responseError(response, fallback) {
    try {
        const body = await response.json();
        return new Error(body.message || fallback);
    } catch (_) {
        return new Error(fallback);
    }
}

async function sendSync(payload, bubble) {
    const response = await fetch('/api/chat', {
        method: 'POST', headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(payload), signal: activeController.signal
    });
    if (!response.ok) throw await responseError(response, '请求失败');
    const body = await response.json();
    bubble.textContent = body.content || '模型没有返回内容。';
}

async function sendStream(payload, bubble) {
    bubble.classList.add('typing');
    const response = await fetch('/api/chat/stream', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', 'Accept': 'text/event-stream' },
        body: JSON.stringify(payload), signal: activeController.signal
    });
    if (!response.ok) throw await responseError(response, 'SSE 请求失败');

    const reader = response.body.getReader();
    const decoder = new TextDecoder();
    let buffer = '';
    while (true) {
        const { value, done } = await reader.read();
        buffer += decoder.decode(value || new Uint8Array(), { stream: !done });
        const frames = buffer.split(/\r?\n\r?\n/);
        buffer = frames.pop() || '';
        frames.forEach(frame => processEvent(frame, bubble));
        if (done) {
            if (buffer.trim()) processEvent(buffer, bubble);
            break;
        }
    }
    bubble.classList.remove('typing');
    if (!bubble.textContent) bubble.textContent = '模型没有返回内容。';
}

function processEvent(frame, bubble) {
    let eventName = 'message';
    const dataLines = [];
    frame.split(/\r?\n/).forEach(line => {
        if (line.startsWith('event:')) eventName = line.slice(6).trim();
        if (line.startsWith('data:')) dataLines.push(line.slice(5).trimStart());
    });
    if (!dataLines.length) return;
    const rawData = dataLines.join('\n');
    let content = rawData;
    try { content = JSON.parse(rawData).content ?? ''; } catch (_) { /* 文本事件 */ }
    if (eventName === 'token') {
        bubble.textContent += content;
        scrollToBottom();
    } else if (eventName === 'error') {
        throw new Error(content || 'SSE 请求失败');
    }
}

elements.form.addEventListener('submit', async event => {
    event.preventDefault();
    const message = elements.input.value.trim();
    if (!message || activeController) return;
    addMessage('user', message);
    elements.input.value = '';
    resizeInput();
    const bubble = addMessage('assistant');
    activeController = new AbortController();
    setBusy(true);
    try {
        const payload = { conversationId, message };
        if (elements.mode.value === 'stream') await sendStream(payload, bubble);
        else await sendSync(payload, bubble);
    } catch (error) {
        bubble.classList.remove('typing');
        if (error.name === 'AbortError') {
            bubble.textContent += bubble.textContent ? '\n（已停止接收）' : '已停止接收。';
        } else {
            bubble.textContent = `出错了：${error.message}`;
            bubble.closest('.message').classList.add('error');
            setStatus('error', '请求失败');
        }
    } finally {
        activeController = null;
        setBusy(false);
        elements.input.focus();
    }
});

elements.stop.addEventListener('click', () => activeController?.abort());
elements.newChat.addEventListener('click', () => {
    activeController?.abort();
    conversationId = crypto.randomUUID();
    localStorage.setItem('qwenConversationId', conversationId);
    elements.messages.innerHTML = '';
    addMessage('assistant', '新会话已经开始。有什么可以帮你？');
    setStatus('idle', '准备就绪');
});

elements.knowledgeButton.addEventListener('click', () => {
    elements.knowledgeStatus.textContent = '';
    elements.knowledgeDialog.showModal();
    elements.knowledgeContent.focus();
});
function closeKnowledgeDialog() { elements.knowledgeDialog.close(); }
elements.closeKnowledge.addEventListener('click', closeKnowledgeDialog);
elements.cancelKnowledge.addEventListener('click', closeKnowledgeDialog);
elements.knowledgeDialog.addEventListener('click', event => {
    if (event.target === elements.knowledgeDialog) closeKnowledgeDialog();
});
elements.knowledgeForm.addEventListener('submit', async event => {
    event.preventDefault();
    const content = elements.knowledgeContent.value.trim();
    if (!content) return;
    elements.saveKnowledge.disabled = true;
    elements.knowledgeStatus.className = 'dialog-status working';
    elements.knowledgeStatus.textContent = '正在生成向量并保存…';
    try {
        const metadata = elements.knowledgeSource.value.trim()
            ? { source: elements.knowledgeSource.value.trim() } : {};
        const response = await fetch('/api/knowledge/documents', {
            method: 'POST', headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ content, metadata })
        });
        if (!response.ok) throw await responseError(response, '知识入库失败');
        elements.knowledgeContent.value = '';
        elements.knowledgeSource.value = '';
        elements.knowledgeStatus.className = 'dialog-status success';
        elements.knowledgeStatus.textContent = '保存成功，现在可以在聊天中提问。';
        setStatus('idle', '知识已更新');
    } catch (error) {
        elements.knowledgeStatus.className = 'dialog-status error';
        elements.knowledgeStatus.textContent = error.message;
    } finally {
        elements.saveKnowledge.disabled = false;
    }
});

elements.input.addEventListener('input', resizeInput);
elements.input.addEventListener('keydown', event => {
    if (event.key === 'Enter' && !event.shiftKey) {
        event.preventDefault();
        elements.form.requestSubmit();
    }
});
function resizeInput() {
    elements.input.style.height = 'auto';
    elements.input.style.height = `${Math.min(elements.input.scrollHeight, 160)}px`;
    elements.count.textContent = `${elements.input.value.length} / 4000`;
}
elements.input.focus();
