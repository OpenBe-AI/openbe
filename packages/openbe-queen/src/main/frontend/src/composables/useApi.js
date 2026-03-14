const BASE = ''

async function request(url, options = {}) {
  const r = await fetch(BASE + url, options)
  if (!r.ok) throw new Error(`${options.method || 'GET'} ${url} => ${r.status}`)
  return r.json()
}

function json(method, url, body) {
  return request(url, {
    method,
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  })
}

export function useApi() {
  return {
    // ── Bees ──────────────────────────────────────────────────
    getBees: () => request('/api/bees'),
    spawnBee: (species, name, hiveId) => json('POST', '/api/bees/spawn', { species, name, hiveId: hiveId || '' }),
    terminateBee: (type) => request(`/api/bees/${type}`, { method: 'DELETE' }),
    removeBeeById: (beeId) => request(`/api/bees/registry/${beeId}`, { method: 'DELETE' }),
    getConfig: (type) => request(`/api/bees/${type}/config`),
    putConfig: (type, config) => json('PUT', `/api/bees/${type}/config`, config),
    getBeeApiKey: (type) => request(`/api/bees/${type}/apikey`).catch(() => ({ provider: 'ollama', model: '', apiKey: '', baseUrl: '' })),
    putBeeApiKey: (type, cfg) => json('PUT', `/api/bees/${type}/apikey`, cfg),
    getStingers: () => request('/api/stingers'),
    getBeeStingers: (type) => request(`/api/bees/${type}/stingers`),
    putBeeStingers: (type, stingers) => json('PUT', `/api/bees/${type}/stingers`, stingers),
    executeStinger: (name, hiveId, args = [], approved = true) =>
      json('POST', `/api/stingers/${encodeURIComponent(name)}/execute`, { approved, hiveId, args }),
    checkStingerApproval: (name, hiveId) =>
      json('POST', `/api/stingers/${encodeURIComponent(name)}/execute`, { approved: false, hiveId }),
    forgeStinger: (payload) => json('POST', '/api/stingers/forge', payload),
    deleteStinger: (name) => request(`/api/stingers/${encodeURIComponent(name)}`, { method: 'DELETE' }),
    getStingerCode: (name) => request(`/api/stingers/${encodeURIComponent(name)}/code`),
    updateStinger: (name, payload) => json('PUT', `/api/stingers/${encodeURIComponent(name)}`, payload),
    importStinger: async (file) => {
      const fd = new FormData()
      fd.append('file', file)
      const r = await fetch('/api/stingers/import', { method: 'POST', body: fd })
      if (!r.ok) throw new Error(`导入失败: ${r.status}`)
      return r.json()
    },
    exportStingers: async (beeType) => {
      const r = await fetch(`/api/stingers/export?beeType=${encodeURIComponent(beeType || '')}`)
      if (!r.ok) throw new Error(`导出失败: ${r.status}`)
      return r.blob()
    },

    // ── Sentinel ──────────────────────────────────────────
    scheduleSentinel: (title, message, at) => json('POST', '/api/sentinel/schedule', { title, message, at }),
    getSentinelJobs: () => request('/api/sentinel/jobs').catch(() => []),
    cancelSentinelJob: (id) => request(`/api/sentinel/jobs/${id}`, { method: 'DELETE' }),

    // ── Chat ──────────────────────────────────────────────────
    sendChat: (type, message) => json('POST', `/api/bees/${type}/chat`, { question: message }),
    sendHiveChat: (hiveId, message) => json('POST', `/api/hives/${hiveId}/chat`, { question: message }),
    sendHiveBeeChat: (hiveId, beeId, message) => json('POST', `/api/hives/${hiveId}/bees/${beeId}/chat`, { question: message }),

    // ── Messages ──────────────────────────────────────────────
    loadBeeMessages: (type) => request(`/api/bees/${type}/messages`).catch(() => []),
    saveBeeMessages: (type, msgs) => json('POST', `/api/bees/${type}/messages`, msgs).catch(() => {}),
    loadHiveMessages: (hiveId) => request(`/api/hives/${hiveId}/messages`).catch(() => []),
    saveHiveMessages: (hiveId, msgs) => json('POST', `/api/hives/${hiveId}/messages`, msgs).catch(() => {}),
    loadHiveQueenMessages: (hiveId) => request(`/api/hives/${hiveId}/queen-messages`).catch(() => []),
    saveHiveQueenMessages: (hiveId, msgs) => json('POST', `/api/hives/${hiveId}/queen-messages`, msgs).catch(() => {}),
    loadHiveBeeMessages: (hiveId, beeId) => request(`/api/hives/${hiveId}/bees/${beeId}/messages`).catch(() => []),
    saveHiveBeeMessages: (hiveId, beeId, msgs) => json('POST', `/api/hives/${hiveId}/bees/${beeId}/messages`, msgs).catch(() => {}),

    // ── Hives ─────────────────────────────────────────────────
    getHives: () => request('/api/hives'),
    createHive: (hiveId, name, description) => json('POST', '/api/hives', { hiveId, name, description }),
    deleteHive: (hiveId) => request(`/api/hives/${hiveId}`, { method: 'DELETE' }),
    renameHive: (hiveId, name) => json('PATCH', `/api/hives/${hiveId}`, { name }),
    setHiveBee: (hiveId, bee) => json('PUT', `/api/hives/${hiveId}/bee`, bee),

    // ── Workspace (Queen) ─────────────────────────────────────
    listWorkspace: (hiveId) => request(`/api/hives/${hiveId}/workspace`),
    readWorkspaceFile: (hiveId, file) => request(`/api/hives/${hiveId}/workspace/${file}`).then(d => d.content || '').catch(() => ''),
    writeWorkspaceFile: (hiveId, file, content) => json('PUT', `/api/hives/${hiveId}/workspace/${file}`, { content }).catch(() => {}),
    // ── Workspace (per-bee) ───────────────────────────────────
    listBeeWorkspace: (hiveId, beeId) => request(`/api/hives/${hiveId}/bees/${beeId}/workspace`),
    readBeeWorkspaceFile: (hiveId, beeId, file) => request(`/api/hives/${hiveId}/bees/${beeId}/workspace/${file}`).then(d => d.content || '').catch(() => ''),
    writeBeeWorkspaceFile: (hiveId, beeId, file, content) => json('PUT', `/api/hives/${hiveId}/bees/${beeId}/workspace/${file}`, { content }).catch(() => {}),

    // ── Notes / Soul ──────────────────────────────────────────
    loadHiveNotes: async (hiveId) => {
      try {
        const d = await request(`/api/hives/${hiveId}/notes`)
        return d.content || ''
      } catch { return '' }
    },
    saveHiveNotes: (hiveId, content) => json('POST', `/api/hives/${hiveId}/notes`, { content }).catch(() => {}),
    loadHiveSoul: async (hiveId) => {
      try {
        const d = await request(`/api/hives/${hiveId}/soul`)
        return d.content || ''
      } catch { return '' }
    },
    saveHiveSoul: (hiveId, content) => json('POST', `/api/hives/${hiveId}/soul`, { content }).catch(() => {}),
    getSoul: () => request('/api/soul'),

    // ── API Keys ──────────────────────────────────────────────
    getApiKeys: () => request('/api/settings/apikeys'),
    createApiKey: (name) => json('POST', '/api/settings/apikeys', { name }),
    deleteApiKey: (id) => request(`/api/settings/apikeys/${id}`, { method: 'DELETE' }),

    // ── Emergency ─────────────────────────────────────────────
    emergencyStop: () => request('/api/emergency/stop', { method: 'POST' }),
    amberSealWipe: () => request('/api/emergency/wipe', { method: 'POST' }),

    // ── Queen Security ────────────────────────────────────────
    saveQueenSecurity: (payload) => json('POST', '/api/queen/security', payload),
  }
}
