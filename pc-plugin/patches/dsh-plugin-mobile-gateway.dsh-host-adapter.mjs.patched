import crypto from 'node:crypto'
import os from 'node:os'

function requireRecord(value, endpoint) {
  if (value === null || typeof value !== 'object' || Array.isArray(value)) {
    throw new Error(`${endpoint} returned an invalid object`)
  }
  return value
}

function requireArray(value, endpoint) {
  if (!Array.isArray(value)) throw new Error(`${endpoint} returned an invalid array`)
  return value
}

// [V3.60 history/snapshot diag] 只记结构（版本/条数/hasMore），不记内容。
function diagLogPath() {
  return (process.env.DSH_HOME ? process.env.DSH_HOME : (os.homedir() + '\\' + '.dsh')) + '\\mobile-gateway-diag.log'
}
export const DSH_VERSION = '0.2.1-rc.2'
export const SESSION_FORMAT_VERSION = 4

import { appendFileSync } from 'node:fs'
export function readHistoryRecords(records) {
  // [V3.60 history/snapshot diag] 原来遇到非 event 记录**整页抛错** ⇒ 上层只看到"空"、且不报错。
  // 改成：**跳过**非 event 记录，并把"跳过几条 + 都是什么 type"记进诊断日志（只记结构，不记内容）。
  const list = requireArray(records, 'session history')
  const skipped = []
  const out = []
  for (const record of list) {
    const value = requireRecord(record, 'session history record')
    if (value.type !== 'event') { skipped.push(String(value.type)); continue }
    const event = requireRecord(value.event, 'session history event')
    if (!Number.isSafeInteger(event.seq) || event.seq < 0) throw new Error('invalid history sequence')
    out.push(event)
  }
  if (skipped.length > 0) {
    try {
      appendFileSync(diagLogPath(), `${new Date().toISOString()} skipped-non-event total=${list.length} events=${out.length} types=${skipped.join(',')}\n`)
    } catch { /* 诊断失败不影响主流程 */ }
  }
  return out
}

export function readSessionSnapshot(frame, sessionId) {
  requireRecord(frame, 'session/follow')
  if (frame.type !== 'snapshot' || !Number.isSafeInteger(frame.cursor) || frame.cursor < -1
    || frame.header?.id !== sessionId) throw new Error('session/follow returned an invalid snapshot')
  if (frame.header.version !== SESSION_FORMAT_VERSION) {
    throw Object.assign(new Error(`mobile-gateway requires Session format ${SESSION_FORMAT_VERSION}; received ${frame.header.version}`), {
      code: 'unsupported-session-format',
    })
  }
  try {
    appendFileSync(diagLogPath(), `${new Date().toISOString()} snapshot sid=${sessionId} v=${frame.header.version} records=${Array.isArray(frame.records) ? frame.records.length : -1} hasMore=${frame.hasMore === true}\n`)
  } catch { /* 诊断失败不影响主流程 */ }
  return {
    events: readHistoryRecords(frame.records).map(event => ({ event })),
    hasMore: frame.hasMore === true,
    projections: requireRecord(frame.projections, 'session/follow projections'),
    historyFormatVersion: frame.header.version,
    cursor: frame.cursor,
  }
}

async function firstStreamFrame(gateway, namespace, method, args, signal, peer) {
  const ownedAbort = new AbortController()
  const streamSignal = signal ? AbortSignal.any([signal, ownedAbort.signal]) : ownedAbort.signal
  let iterator
  try {
    // [local patch: DSH 0.2.1-rc.2] stream 现在同样要求 Connection Peer
    const iterable = await gateway.stream({
      namespace, method, args, signal: streamSignal,
      ...(peer === undefined ? {} : { peer }),
    })
    const iteratorFactory = iterable?.[Symbol.asyncIterator] ?? iterable?.[Symbol.iterator]
    if (typeof iteratorFactory !== 'function') throw new Error(`${namespace}/${method} returned an invalid stream`)
    iterator = iteratorFactory.call(iterable)
    const first = await iterator.next()
    if (first.done) throw new Error(`${namespace}/${method} ended before its opening frame`)
    return first.value
  } finally {
    ownedAbort.abort()
    if (typeof iterator?.return === 'function') await iterator.return()
  }
}

function requestArgs(request) {
  return { request }
}

// The SessionController names its reserved list argument
// `_request`. Typert descriptors preserve that source parameter name exactly,
// so this endpoint cannot share the normal `{ request }` wrapper.
function sessionListArgs(request) {
  return { _request: request }
}

/**
 * The only module allowed to know DSH Remote endpoint names and argument
 * descriptors. Its public methods intentionally match mobile-gateway domain
 * operations, not the upstream transport envelope.
 */
export function createDshHostAdapter(typertGateway, peer) {
  if (!typertGateway || typeof typertGateway.invoke !== 'function' || typeof typertGateway.stream !== 'function') {
    throw new Error('mobile-gateway requires the DSH Remote Gateway host service')
  }
  // [local patch: DSH 0.2.1-rc.2] 内核在 invoke/stream 入口 assertPeerLive(request.peer)，
  // 缺 peer 时抛 "Cannot read properties of undefined (reading 'signal')"。
  if (!peer || !(peer.signal instanceof AbortSignal)) {
    throw new Error('mobile-gateway requires a DSH Connection Peer (DSH 0.2.1-rc.2 or later)')
  }

  const invoke = (namespace, method, args, signal) => typertGateway.invoke({
    namespace,
    method,
    args,
    peer,
    ...(signal === undefined ? {} : { signal }),
  })

  // --- local patch: subagent durable addressing (pc-plugin/patches) -------
  // A Session whose header origin is 'subagent' rejects a plain session
  // address with session/agent-busy; it must be addressed by its durable
  // direct parent. Host SessionAddress:
  //   { kind: 'session', sessionId }
  // | { kind: 'subagent', parentSessionId, childSessionId,
  //     mode: 'one-shot' | 'continuable' | 'unknown' }
  // 'unknown' is the read-only discriminator: the host's validateAddress
  // skips its mode comparison for it and still enforces direct-parent
  // ownership. Parent sessions, unknown ids, and every lookup failure keep
  // the original session address, so the parent path never changes.
  const addressSessionId = address => (address && address.kind === 'subagent'
    ? address.childSessionId
    : address && address.sessionId)
  const CHILD_ADDRESS_TTL_MS = 2_000
  const childAddresses = new Map()
  let childAddressAt = 0
  let childAddressLookup
  const rememberSessionAddresses = result => {
    if (!result || !Array.isArray(result.items)) return
    for (const item of result.items) {
      if (!item || typeof item.sessionId !== 'string') continue
      if (item.origin === 'subagent' && typeof item.parentSessionId === 'string' && item.parentSessionId) {
        childAddresses.set(item.sessionId, {
          kind: 'subagent',
          parentSessionId: item.parentSessionId,
          childSessionId: item.sessionId,
          mode: 'unknown',
        })
      } else {
        childAddresses.delete(item.sessionId)
      }
    }
  }
  const refreshSessionAddresses = async () => {
    if (childAddressLookup) { await childAddressLookup.catch(() => {}); return }
    if (Date.now() - childAddressAt < CHILD_ADDRESS_TTL_MS) return
    childAddressAt = Date.now() // rate-limit failures too
    const pending = invoke('session', 'list', sessionListArgs({}), undefined)
    childAddressLookup = pending
    try {
      rememberSessionAddresses(await pending)
    } finally {
      if (childAddressLookup === pending) childAddressLookup = undefined
    }
  }
  // Never throws: an unresolvable child falls back to the original address.
  const sessionAddress = async sessionId => {
    const fallback = { kind: 'session', sessionId }
    if (typeof sessionId !== 'string' || !sessionId) return fallback
    try {
      if (!childAddresses.has(sessionId)) await refreshSessionAddresses()
      return childAddresses.get(sessionId) ?? fallback
    } catch {
      return fallback
    }
  }
  // ------------------------------------------------------------------------
  const sessionSnapshot = async (request, signal) => {
    const frame = requireRecord(await firstStreamFrame(
      typertGateway,
      'session',
      'follow',
      requestArgs(request),
      signal,
      peer,
    ), 'session/follow')
    return readSessionSnapshot(frame, addressSessionId(request.address))
  }

  const history = async (payload, signal) => {
    const request = {
      address: await sessionAddress(payload.sessionId),
      // Older-page requests need only the opening cursor/projections, not another large latest page.
      // [V3.60 history/snapshot diag] 原来"无 beforeSeq 且无 maxMessages"时**请求里没有 maxMessages**
      // ⇒ 核心可能回**不含 records 的基线快照** ⇒ App 侧表现为"整屏空、且不报错"。这里补一页尾部。
      ...(payload.beforeSeq !== undefined ? { maxMessages: 1 }
        : payload.maxMessages === undefined ? { maxMessages: 200 } : { maxMessages: payload.maxMessages }),
    }
    const snapshot = await sessionSnapshot(request, signal)
    let events = snapshot.events
    let hasMore = snapshot.hasMore
    if (payload.beforeSeq !== undefined) {
      const page = requireRecord(await invoke('session', 'page', requestArgs({
        address: request.address,
        throughSeq: snapshot.cursor,
        beforeSeq: payload.beforeSeq,
        ...(payload.maxMessages === undefined ? {} : { maxMessages: payload.maxMessages }),
      }), signal), 'session/page')
      events = readHistoryRecords(page.records).map(event => ({ event }))
      hasMore = page.hasMore === true
    }
    return {
      ...snapshot,
      events,
      hasMore,
    }
  }

  const modelCatalog = async (signal) => requireRecord(
    await invoke('session', 'modelCatalog', {}, signal),
    'session/modelCatalog',
  )

  // Control and conversation sockets share this adapter. Admit a prompt only
  // after an earlier preset switch finishes, and re-check later switches.
  const sessionOperations = new Map()
  const admittedPrompts = new Set()
  const serializeSession = (sessionId, operation) => {
    const task = (sessionOperations.get(sessionId) ?? Promise.resolve()).then(operation)
    const guard = task.catch(() => {})
    sessionOperations.set(sessionId, guard)
    return task.finally(() => {
      if (sessionOperations.get(sessionId) === guard) sessionOperations.delete(sessionId)
    })
  }
  const sessionPreset = async (sessionId, signal) => {
    const snapshot = await sessionSnapshot({ address: await sessionAddress(sessionId), maxMessages: 1 }, signal)
    const values = snapshot.projections.values
    const metadata = values?.sessionListMetadata
    if (typeof values?.agentPreset !== 'string' || !metadata || typeof metadata.blank !== 'boolean'
      || !(metadata.lastPromptAt === null || Number.isFinite(metadata.lastPromptAt))) {
      throw Object.assign(new Error('session preset state is unavailable'), { code: 'agent-preset/unavailable' })
    }
    const durableLock = !metadata.blank || metadata.lastPromptAt !== null
    if (durableLock) admittedPrompts.delete(sessionId)
    return { sessionId, agentPreset: values.agentPreset, locked: durableLock || admittedPrompts.has(sessionId) }
  }

  const commands = {
    list: (sessionId, signal) => invoke('commands', 'list', { agentId: sessionId }, signal),
    // DSH accepts `submittedAttachments`. The parsed
    // wire images already carry the { type: 'image', ... } shape it expects, so
    // only the field name changes.
    execute: (sessionId, line, attachments, signal) => invoke(
      'commands',
      'execute',
      { agentId: sessionId, line, submittedAttachments: attachments },
      signal,
    ),
  }

  const goalRefValue = (value, endpoint) => {
    const goal = requireRecord(value, endpoint)
    if (typeof goal.id !== 'string' || !Number.isSafeInteger(goal.revision)) {
      throw new Error(`${endpoint} returned an invalid goal`)
    }
    return { ref: { id: goal.id, revision: goal.revision } }
  }

  const describeHost = async (signal) => {
    const [sessions, catalog, canOpenPath] = await Promise.all([
      invoke('session', 'list', sessionListArgs({}), signal),
      modelCatalog(signal),
      invoke('session', 'canOpenWorkspacePath', {}, signal).catch(() => false),
    ])
    return {
      version: 'remote-gateway',
      dshVersion: DSH_VERSION,
      historyFormatVersion: SESSION_FORMAT_VERSION,
      cwd: os.homedir(),
      attachedSessions: Array.isArray(sessions?.items) ? sessions.items.length : 0,
      canOpenPath: canOpenPath === true,
      defaultProvider: catalog.default?.provider,
      defaultModel: catalog.default?.model,
    }
  }

  return {
    sessions: {
      list: async (payload = {}, signal) => {
        const result = await invoke('session', 'list', sessionListArgs(payload), signal)
        // Warm the subagent address cache from the list the client just asked for.
        rememberSessionAddresses(result)
        return result
      },
      search: (payload, signal) => invoke('session', 'search', requestArgs(payload), signal),
      create: (payload, signal) => invoke('session', 'create', requestArgs(payload), signal),
      prompt: (payload, signal) => serializeSession(payload.sessionId, async () => {
        // Accepted input may not have reached turn/start yet. Keep that gap
        // locked; the persistent event or a subsequent read retires the marker.
        const alreadyAdmitted = admittedPrompts.has(payload.sessionId)
        admittedPrompts.add(payload.sessionId)
        try {
          return await invoke('session', 'prompt', requestArgs({ requestId: crypto.randomUUID(), ...payload }), signal)
        } catch (error) {
          if (!alreadyAdmitted) admittedPrompts.delete(payload.sessionId)
          throw error
        }
      }),
      attachment: (payload, signal) => invoke('session', 'attachment', requestArgs(payload), signal),
      fork: (payload, signal) => invoke('session', 'fork', requestArgs(payload), signal),
      cancel: (payload, signal) => invoke('session', 'cancel', requestArgs(payload), signal),
      updateQueue: (payload, signal) => invoke('session', 'updateQueue', requestArgs(payload), signal),
      rename: (payload, signal) => invoke('session', 'rename', requestArgs(payload), signal),
      selectModel: (payload, signal) => invoke('session', 'selectModel', requestArgs(payload), signal),
      history,
      async models(payload, signal) {
        const [catalog, snapshot] = await Promise.all([
          modelCatalog(signal),
          sessionSnapshot({ address: await sessionAddress(payload.sessionId), maxMessages: 1 }, signal),
        ])
        const current = snapshot.projections?.values?.modelSelection?.next ?? catalog.default
        return {
          current,
          routable: typeof current?.provider === 'string'
            && requireArray(catalog.routableProviders, 'session/modelCatalog routableProviders').includes(current.provider),
          groups: requireArray(catalog.groups, 'session/modelCatalog groups'),
          failures: requireArray(catalog.failures, 'session/modelCatalog failures'),
        }
      },
    },
    workspace: {
      async list(_payload = {}, signal) {
        const frame = requireRecord(await firstStreamFrame(typertGateway, 'workspace', 'follow', {}, signal, peer), 'workspace/follow')
        if (frame.type !== 'baseline') throw new Error('workspace/follow returned an invalid opening baseline')
        return requireRecord(frame.value, 'workspace/follow baseline')
      },
      create: (payload, signal) => invoke('workspace', 'create', requestArgs(payload), signal),
      archiveSession: (payload, signal) => invoke('workspace', 'archiveSession', requestArgs(payload), signal),
    },
    settings: {
      describe: (_payload = {}, signal) => invoke('settings', 'describe', {}, signal),
      mutate: (payload, signal) => invoke('settings', 'mutate', {
        ns: payload.ns,
        ops: payload.ops,
        expectedRevision: payload.expectedRevision,
      }, signal),
      update: (payload, signal) => invoke('settings', 'update', {
        ns: payload.ns,
        patch: payload.patch,
        ...(payload.expectedRevision === undefined ? {} : { expectedRevision: payload.expectedRevision }),
      }, signal),
    },
    plugins: {
      inventory: (_payload = {}, signal) => invoke('pluginInventory', 'list', {}, signal),
      listBundles: (signal) => invoke('pluginManager', 'listBundles', {}, signal),
      listPlugins: (signal) => invoke('pluginManager', 'listPlugins', {}, signal),
      registries: (signal) => invoke('pluginManager', 'registries', {}, signal),
      inspect: (spec, options, signal) => invoke('pluginManager', 'inspect', {
        spec, ...(options === undefined ? {} : { options }),
      }, signal),
      installBundle: (spec, options) => invoke('pluginManager', 'installBundle', { spec, options }),
      waitForInstall: (requestId) => invoke('pluginManager', 'waitForInstall', { requestId }),
      cancelInstall: (requestId) => invoke('pluginManager', 'cancelInstall', { requestId }),
      setBundleEnabled: (name, enabled) => invoke('pluginManager', 'setBundleEnabled', { name, enabled }),
      setPluginEnabled: (id, enabled) => invoke('pluginManager', 'setPluginEnabled', { id, enabled }),
      removeBundle: (name) => invoke('pluginManager', 'removeBundle', { name }),
      listVersionExemptions: () => invoke('pluginManager', 'listVersionExemptions', {}),
      setVersionExemption: (packageVersion, runtimeVersion, enabled, acceptRisk) => invoke(
        'pluginManager', 'setVersionExemption', {
          packageVersion, runtimeVersion, enabled,
          ...(acceptRisk === undefined ? {} : { acceptRisk }),
        },
      ),
    },
    permissionPresets: {
      catalog: (_payload = {}, signal) => invoke('permissionPresets', 'catalog', {}, signal),
    },
    schedule: {
      async catalog(_payload = {}, signal) {
        return { items: requireArray(await invoke('schedule', 'catalog', {}, signal), 'schedule/catalog') }
      },
      async list(payload, signal) {
        return { items: requireArray(await invoke('schedule', 'list', requestArgs(payload), signal), 'schedule/list') }
      },
      history: (payload, signal) => invoke('schedule', 'history', requestArgs(payload), signal),
      update: (payload, signal) => invoke('schedule', 'update', requestArgs(payload), signal),
      delete: (payload, signal) => invoke('schedule', 'delete', requestArgs(payload), signal),
    },
    skills: {
      list: (payload, signal) => invoke('skills', 'list', requestArgs(payload), signal),
    },
    agentPresets: {
      list: (_payload = {}, signal) => invoke('agentPresets', 'list', {}, signal),
      session: (payload, signal) => serializeSession(payload.sessionId, () => sessionPreset(payload.sessionId, signal)),
      select: (payload, signal) => serializeSession(payload.sessionId, async () => {
        const state = await sessionPreset(payload.sessionId, signal)
        if (state.locked) {
          throw Object.assign(new Error('session has already started; its agent preset is fixed'), { code: 'agent-preset/locked' })
        }
        // Host validates the live catalog, mounts the composition, checks its
        // own turn boundary and persists agent-preset/selected on this ID.
        const selected = await invoke('agentPresets', 'select', {
          agentId: payload.sessionId, agentPreset: payload.agentPreset,
        }, signal)
        if (typeof selected !== 'string' || !selected) throw new Error('agentPresets/select returned an invalid preset')
        return { sessionId: payload.sessionId, agentPreset: selected }
      }),
    },
    llm: {
      async models(_payload = {}, signal) {
        const catalog = await modelCatalog(signal)
        return { groups: catalog.groups, failures: catalog.failures }
      },
      async providers(_payload = {}, signal) {
        const providers = await invoke('llm', 'listConfigurableProviders', {}, signal)
        return { providers: requireArray(providers, 'llm/listConfigurableProviders') }
      },
    },
    goals: {
      async edit(payload) {
        return goalRefValue(await invoke('goals', 'edit', {
          agentId: payload.sessionId,
          ref: payload.ref,
          request: {
            ...(payload.objective === undefined ? {} : { objective: payload.objective }),
            ...(payload.maxGoalRounds === undefined ? {} : { maxGoalRounds: payload.maxGoalRounds }),
          },
        }), 'goals/edit')
      },
      async pause(payload) {
        return goalRefValue(
          await invoke('goals', 'pause', { agentId: payload.sessionId, ref: payload.ref }),
          'goals/pause',
        )
      },
      async resume(payload) {
        return goalRefValue(
          await invoke('goals', 'resume', { agentId: payload.sessionId, ref: payload.ref }),
          'goals/resume',
        )
      },
      clear: async payload => {
        await invoke('goals', 'clear', { agentId: payload.sessionId, ref: payload.ref })
        return { cleared: true }
      },
    },
    commands,
    // dsh-mobile:file-upload-adapter
    // 通用文件附件：宿主 prompt 的内容块 { type:'file', receiptId } 需要一个 receipt，
    // receipt 只能由宿主 fileUploads 服务签发（app.asar:372760 fileUploads/upload）。
    // 该服务与 commands/list 同形：scope {context:'agent', wire:'agentId'}，
    // 所以这里与 commands.list 一样传纯字符串 sessionId 作 agentId。
    // 返回 { receiptId, file:{attachmentId,name,bytes} }；失败按 Remote 错误抛出。
    uploadFile: (agentId, data, name, signal) => invoke('fileUploads', 'upload', {
      agentId,
      request: { data, ...(name === undefined || name === '' ? {} : { name }) },
    }, signal),
    host: {
      describe: (_payload = {}, signal) => describeHost(signal),
    },
    describeHost,
    observeSessionEvent(sessionId, event) {
      if (event.type === 'turn/start') admittedPrompts.delete(sessionId)
    },
    async openSessionStream(sessionId, signal) {
      return typertGateway.stream({
        namespace: 'session', method: 'follow',
        // Bound the opening window at the Host; older records remain available via session.page.
        args: requestArgs({ address: await sessionAddress(sessionId), maxMessages: 12, assistantStream: true }),
        peer,
        signal,
      })
    },
    openControlStream(signal) {
      return typertGateway.stream({ namespace: 'session', method: 'control', args: {}, peer, signal })
    },
    openWorkspaceStream(signal) {
      return typertGateway.stream({ namespace: 'workspace', method: 'follow', args: {}, peer, signal })
    },
  }
}
