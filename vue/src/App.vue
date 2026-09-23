<script setup lang="ts">
import { ref, onMounted, nextTick } from 'vue'

interface ChatMessage {
  role: 'user' | 'assistant'
  text: string
}

interface ChatResponse {
  imageUrl: string
  prompt: string
}

interface ImageRecord {
  id: number
  prompt?: string
  negativePrompt?: string
  width?: number
  height?: number
  seed?: number
  imageUrl: string
  userText?: string
  createdAt?: string
}

interface PreviewImage {
  imageUrl: string
  prompt?: string
  userText?: string
  createdAt?: string
}

const messages = ref<ChatMessage[]>([])
const input = ref('')
const loading = ref(false)
const error = ref('')
const latest = ref<ChatResponse | null>(null)
const gallery = ref<ImageRecord[]>([])
const galleryLoading = ref(false)

const chatScroll = ref<HTMLElement | null>(null)
const preview = ref<PreviewImage | null>(null)

async function send() {
  const msg = input.value.trim()
  if (!msg || loading.value) return

  messages.value.push({ role: 'user', text: msg })
  input.value = ''
  error.value = ''
  loading.value = true
  scrollToBottom()

  try {
    const res = await fetch('/api/chat', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ message: msg }),
    })
    if (!res.ok) throw new Error(`HTTP ${res.status}`)
    const data: ChatResponse = await res.json()
    messages.value.push({ role: 'assistant', text: data.prompt })
    latest.value = data
    loadGallery()
  } catch (e) {
    error.value = e instanceof Error ? e.message : String(e)
    messages.value.push({ role: 'assistant', text: `⚠️ 生成失败:${error.value}` })
  } finally {
    loading.value = false
    scrollToBottom()
  }
}

async function loadGallery() {
  galleryLoading.value = true
  try {
    const res = await fetch('/api/history')
    if (!res.ok) throw new Error(`HTTP ${res.status}`)
    gallery.value = await res.json()
  } catch (e) {
    console.error('加载历史画廊失败:', e)
  } finally {
    galleryLoading.value = false
  }
}

function scrollToBottom() {
  nextTick(() => {
    chatScroll.value?.scrollTo({ top: chatScroll.value.scrollHeight, behavior: 'smooth' })
  })
}

function openPreview(item: PreviewImage) {
  preview.value = item
}

function formatTime(value?: string) {
  if (!value) return ''
  const d = new Date(value)
  if (isNaN(d.getTime())) return value
  return d.toLocaleString('zh-CN', { hour12: false })
}

onMounted(loadGallery)
</script>

<template>
  <div class="app">
    <header class="topbar">
      <div class="brand">
        <div class="logo">🎨</div>
        <div>
          <h1>Qwen + Animagine 智能图片生成</h1>
          <p>用中文描述画面 · Qwen 翻译 · Animagine 出图 · 历史画廊</p>
        </div>
      </div>
      <div class="status" :class="{ on: !error }">
        <span class="dot"></span>
        {{ error ? '连接异常' : '就绪' }}
      </div>
    </header>

    <main class="layout">
      <!-- 左:聊天 + 输入 + 最新生成 -->
      <section class="chat-col">
        <div class="card chat-card">
          <div class="chat-body" ref="chatScroll">
            <div v-if="messages.length === 0" class="empty-chat">
              <div class="empty-emoji">✨</div>
              <p>描述你想画的画面,例如:</p>
              <button class="example" @click="input = '银发少女穿着和服站在樱花树下'">
                「银发少女穿着和服站在樱花树下」
              </button>
            </div>

            <div v-for="(m, i) in messages" :key="i" class="msg" :class="m.role">
              <div class="bubble">{{ m.text }}</div>
            </div>

            <div v-if="loading" class="msg assistant">
              <div class="bubble loading-bubble">
                <span class="dots"><span></span><span></span><span></span></span>
              </div>
            </div>
          </div>

          <div class="chat-input">
            <textarea
              v-model="input"
              rows="1"
              placeholder="描述你想画的画面,例如:银发少女穿着和服站在樱花树下"
              @keydown.enter.exact.prevent="send"
            ></textarea>
            <button class="send-btn" :disabled="loading || !input.trim()" @click="send">
              {{ loading ? '生成中…' : '生成' }}
            </button>
          </div>
        </div>

        <div v-if="latest" class="card latest-card">
          <div class="card-title">最新生成</div>
          <div class="latest-body">
            <img
              :src="latest.imageUrl"
              :alt="latest.prompt"
              @click="openPreview({ imageUrl: latest.imageUrl, prompt: latest.prompt })"
            />
            <p class="latest-prompt">{{ latest.prompt }}</p>
          </div>
        </div>
      </section>

      <!-- 右:历史画廊 -->
      <aside class="card gallery-col">
        <div class="card-title">
          历史画廊
          <button class="refresh" :disabled="galleryLoading" @click="loadGallery">刷新</button>
        </div>

        <div v-if="gallery.length === 0" class="empty-gallery">
          <p>还没有生成的图片。</p>
        </div>
        <div v-else class="gallery-grid">
          <figure
            v-for="g in gallery"
            :key="g.id"
            class="gallery-item"
            @click="openPreview(g)"
          >
            <img :src="g.imageUrl" :alt="g.userText || g.prompt" loading="lazy" />
            <figcaption>{{ g.userText || g.prompt }}</figcaption>
          </figure>
        </div>
      </aside>
    </main>

    <Teleport to="body">
      <div v-if="preview" class="preview-overlay" @click.self="preview = null">
        <div class="preview-card">
          <button class="preview-close" @click="preview = null">✕</button>
          <img :src="preview.imageUrl" :alt="preview.prompt || preview.userText" />
          <div class="preview-meta">
            <p v-if="preview.userText" class="p-user">{{ preview.userText }}</p>
            <p v-if="preview.prompt" class="p-prompt">{{ preview.prompt }}</p>
            <p v-if="preview.createdAt" class="p-time">{{ formatTime(preview.createdAt) }}</p>
          </div>
        </div>
      </div>
    </Teleport>
  </div>
</template>

<style scoped>
.app {
  max-width: 1280px;
  margin: 0 auto;
  padding: 20px 24px 40px;
}

.topbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 14px 4px 20px;
}

.brand {
  display: flex;
  align-items: center;
  gap: 14px;
}

.logo {
  width: 48px;
  height: 48px;
  display: grid;
  place-items: center;
  font-size: 26px;
  border-radius: 14px;
  background: var(--accent-grad);
  box-shadow: 0 6px 18px rgba(124, 92, 255, 0.3);
}

.brand h1 {
  margin: 0;
  font-size: 20px;
  font-weight: 700;
}

.brand p {
  margin: 4px 0 0;
  font-size: 13px;
  color: var(--text-secondary);
}

.status {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  color: var(--text-secondary);
  padding: 8px 14px;
  border-radius: 999px;
  background: var(--surface);
  border: 1px solid var(--border);
}

.status .dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #c4c8d4;
}

.status.on .dot {
  background: #22c55e;
  box-shadow: 0 0 0 3px rgba(34, 197, 94, 0.15);
}

.layout {
  display: grid;
  grid-template-columns: 1.4fr 1fr;
  gap: 20px;
  align-items: start;
}

@media (max-width: 900px) {
  .layout {
    grid-template-columns: 1fr;
  }
}

.card {
  background: var(--surface);
  border: 1px solid var(--border);
  border-radius: var(--radius);
  box-shadow: var(--shadow);
  overflow: hidden;
}

.card-title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 14px 18px;
  font-weight: 600;
  font-size: 15px;
  border-bottom: 1px solid var(--border);
}

/* 聊天区 */
.chat-col {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.chat-card {
  display: flex;
  flex-direction: column;
}

.chat-body {
  height: 460px;
  overflow-y: auto;
  padding: 18px;
  display: flex;
  flex-direction: column;
  gap: 12px;
  background: #fafbfe;
}

.empty-chat {
  margin: auto;
  text-align: center;
  color: var(--text-secondary);
  padding: 0 20px;
}

.empty-emoji {
  font-size: 40px;
  margin-bottom: 8px;
}

.empty-chat p {
  margin: 0 0 12px;
}

.example {
  background: #f1f0ff;
  color: var(--accent);
  padding: 10px 16px;
  border-radius: 10px;
  font-size: 14px;
  transition: 0.2s;
}

.example:hover {
  background: #e6e3ff;
}

.msg {
  display: flex;
}

.msg.user {
  justify-content: flex-end;
}

.msg.assistant {
  justify-content: flex-start;
}

.bubble {
  max-width: 78%;
  padding: 10px 14px;
  border-radius: 14px;
  font-size: 14px;
  line-height: 1.55;
  white-space: pre-wrap;
  word-break: break-word;
}

.msg.user .bubble {
  background: var(--accent-grad);
  color: #fff;
  border-bottom-right-radius: 4px;
}

.msg.assistant .bubble {
  background: #eef0f6;
  color: var(--text);
  border-bottom-left-radius: 4px;
}

.loading-bubble {
  display: flex;
  align-items: center;
  padding: 14px 16px;
}

.dots {
  display: inline-flex;
  gap: 5px;
}

.dots span {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: #a7adbd;
  animation: blink 1.2s infinite ease-in-out;
}

.dots span:nth-child(2) {
  animation-delay: 0.2s;
}

.dots span:nth-child(3) {
  animation-delay: 0.4s;
}

@keyframes blink {
  0%,
  80%,
  100% {
    opacity: 0.25;
    transform: translateY(0);
  }
  40% {
    opacity: 1;
    transform: translateY(-3px);
  }
}

.chat-input {
  display: flex;
  gap: 10px;
  padding: 14px;
  border-top: 1px solid var(--border);
}

.chat-input textarea {
  flex: 1;
  resize: none;
  border: 1px solid var(--border);
  border-radius: 10px;
  padding: 11px 14px;
  font-size: 14px;
  line-height: 1.5;
  outline: none;
  max-height: 120px;
}

.chat-input textarea:focus {
  border-color: var(--accent);
  box-shadow: 0 0 0 3px rgba(124, 92, 255, 0.12);
}

.send-btn {
  padding: 0 22px;
  border-radius: 10px;
  background: var(--accent-grad);
  color: #fff;
  font-size: 14px;
  font-weight: 600;
  transition: 0.2s;
  white-space: nowrap;
}

.send-btn:hover:not(:disabled) {
  filter: brightness(1.06);
  box-shadow: 0 6px 16px rgba(124, 92, 255, 0.3);
}

.send-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

/* 最新生成 */
.latest-card .latest-body {
  padding: 16px;
}

.latest-body img {
  width: 100%;
  border-radius: 12px;
  cursor: zoom-in;
  background: #f0f1f5;
}

.latest-prompt {
  margin: 12px 0 0;
  font-size: 12.5px;
  color: var(--text-secondary);
  line-height: 1.6;
  word-break: break-word;
}

/* 历史画廊 */
.gallery-col {
  max-height: 640px;
  display: flex;
  flex-direction: column;
}

.refresh {
  background: transparent;
  color: var(--accent);
  font-size: 13px;
  padding: 6px 12px;
  border-radius: 8px;
  transition: 0.2s;
}

.refresh:hover:not(:disabled) {
  background: #f1f0ff;
}

.refresh:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.empty-gallery {
  padding: 40px 20px;
  text-align: center;
  color: var(--text-secondary);
  font-size: 14px;
}

.gallery-grid {
  padding: 16px;
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(120px, 1fr));
  gap: 12px;
  overflow-y: auto;
}

.gallery-item {
  margin: 0;
  cursor: pointer;
  border-radius: 10px;
  overflow: hidden;
  border: 1px solid var(--border);
  background: #f0f1f5;
  transition: 0.2s;
}

.gallery-item:hover {
  transform: translateY(-3px);
  box-shadow: 0 10px 20px rgba(31, 35, 51, 0.12);
}

.gallery-item img {
  width: 100%;
  aspect-ratio: 2 / 3;
  object-fit: cover;
}

.gallery-item figcaption {
  padding: 8px 10px;
  font-size: 11px;
  color: var(--text-secondary);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

/* 大图预览 */
.preview-overlay {
  position: fixed;
  inset: 0;
  background: rgba(15, 17, 26, 0.72);
  display: grid;
  place-items: center;
  padding: 24px;
  z-index: 100;
}

.preview-card {
  position: relative;
  max-width: 720px;
  width: 100%;
  max-height: 90vh;
  display: flex;
  flex-direction: column;
  background: var(--surface);
  border-radius: 16px;
  overflow: hidden;
  box-shadow: 0 30px 60px rgba(0, 0, 0, 0.4);
}

.preview-card img {
  max-height: 70vh;
  object-fit: contain;
  background: #14161f;
}

.preview-close {
  position: absolute;
  top: 12px;
  right: 12px;
  width: 34px;
  height: 34px;
  border-radius: 50%;
  background: rgba(15, 17, 26, 0.55);
  color: #fff;
  font-size: 15px;
  z-index: 2;
}

.preview-meta {
  padding: 14px 18px;
  max-height: 180px;
  overflow-y: auto;
}

.preview-meta p {
  margin: 4px 0;
  font-size: 13px;
}

.p-user {
  color: var(--text);
  font-weight: 600;
}

.p-prompt {
  color: var(--text-secondary);
  word-break: break-word;
  line-height: 1.6;
}

.p-time {
  color: #9aa0af;
  font-size: 12px;
}
</style>
