// mock-gateway.mjs —— 脱离 DSH 调试「DSH 掌上通」的模拟网关
// 用法: node mock-gateway.mjs --port 3091 --fault none
//   --fault none|nohello|silent|emptyclose|resetstream|proto4|nocaps|gatewayoff
// 启动即打印 PAIRING_STRING=<base64url>，把它写进 pairing.txt 即可喂给现有 harness：
//   java -cp "harness/out;harness/lib/json-20240303.jar" Harness pairing.txt "只回复四个字：联调成功"
// 每帧收发与每次 close（含方向/码/原因）都会追加到 mock-gateway.jsonl —— 这是定性 R-C2 的关键证据。
import http from 'node:http'; import crypto from 'node:crypto'; import fs from 'node:fs'
const arg=(k,d)=>{const i=process.argv.indexOf('--'+k);return i>0?process.argv[i+1]:d}
const PORT=Number(arg('port',3091)), FAULT=arg('fault','none')
// [落盘增补③] --host：配对串里 publicUrl 用的主机名。默认 127.0.0.1 只适合「App 与网关同在
// 本机」的 JVM harness；跑安卓模拟器要填 10.0.2.2（模拟器里访问宿主机的专用地址），
// 真机走局域网则填电脑的 192.168.x.y。不填就会让 App 连它自己的 localhost 而永远连不上。
const HOST=arg('host','127.0.0.1')
const TOKEN='mock-device-token-0001', PAIR='mockpaircode0001', DEV='mock-device-uuid-0001'
const CAPS=['split-channels','assistant-stream-v1','history-format-version','projection-baseline','images','session-create','commands','tasks','goals','session-cancel','queue-control','session-archive','session-rename','file-downloads']
const SID='session-mock-1'
const log=o=>{const l=JSON.stringify({t:new Date().toISOString(),...o});console.log(l);fs.appendFileSync('mock-gateway.jsonl',l+'\n')}
function ws(sock){
  let buf=Buffer.alloc(0)
  // [落盘修复①] 原文在第 38 行直接调用模块级 onMsg(m)，this 为 undefined，
  // 于是 onMsg 里 `const P=this` 得到 undefined，第一帧（sessions/ping）就抛
  // TypeError 把 node 进程打死（实测：配对成功后 ~100ms 连接被 reset，网关退出）。
  // 改为经 api 间接调用，并把 api.onMsg 绑定到连接上下文（下面的 upgrade 处已如此赋值）。
  let api=null
  const send=o=>{const p=Buffer.from(JSON.stringify(o),'utf8');let h
    if(p.length<126)h=Buffer.from([0x81,p.length])
    else if(p.length<=0xffff){h=Buffer.alloc(4);h[0]=0x81;h[1]=126;h.writeUInt16BE(p.length,2)}
    else{h=Buffer.alloc(10);h[0]=0x81;h[1]=127;h.writeBigUInt64BE(BigInt(p.length),2)}
    sock.write(Buffer.concat([h,p]));log({dir:'out',frame:o})}
  const close=(code,reason='')=>{const r=Buffer.from(reason,'utf8'),b=Buffer.alloc(2+r.length)
    b.writeUInt16BE(code,0);r.copy(b,2);sock.write(Buffer.concat([Buffer.from([0x88,b.length]),b]))
    log({dir:'out',close:{code,reason}});sock.end()}
  const closeEmpty=()=>{sock.write(Buffer.from([0x88,0]));log({dir:'out',close:{code:null,reason:''},note:'EMPTY close frame'});sock.end()}
  const feed=chunk=>{buf=Buffer.concat([buf,chunk])
    for(;;){ if(buf.length<2)return
      const fin=(buf[0]&0x80)!==0,op=buf[0]&0x0f,masked=(buf[1]&0x80)!==0
      let len=buf[1]&0x7f,off=2
      if(len===126){if(buf.length<4)return;len=buf.readUInt16BE(2);off=4}
      else if(len===127){if(buf.length<10)return;len=Number(buf.readBigUInt64BE(2));off=10}
      const need=off+(masked?4:0)+len; if(buf.length<need)return
      let mask=null; if(masked){mask=buf.subarray(off,off+4);off+=4}
      const data=Buffer.from(buf.subarray(off,off+len)); if(mask)for(let i=0;i<data.length;i++)data[i]^=mask[i&3]
      buf=buf.subarray(need)
      if(op===0x9){sock.write(Buffer.concat([Buffer.from([0x8a,data.length]),data]))}
      else if(op===0x8){log({dir:'in',close:{code:data.length>=2?data.readUInt16BE(0):null,reason:data.length>2?data.subarray(2).toString():''}});sock.end()}
      else if((op===0x1||op===0x0)&&fin){let m=null;try{m=JSON.parse(data.toString('utf8'))}catch(e){log({dir:'in',parseError:String(e)});return}
        log({dir:'in',frame:m});api.onMsg.call(api,m)}
    }}
  api={send,close,closeEmpty,feed,onMsg}
  return api
}
const snapEvents=[{type:'user/message',seq:1,time:Date.now(),data:{text:'（mock）你好'}},{type:'assistant/message',seq:2,time:Date.now(),data:{turn:1,step:0,text:'（mock）历史回复'}}]
function onMsg(m){
  const P=this
  // [落盘修复②] 原文的 silent 档只打了一行 log，onMsg 里没有任何 FAULT 判断 —— 也就是
  // 完全不静默（仍会回 pong / sessions），P0-1「假连接」根本验证不到。这里按它的自述
  // （"hello 后完全静默，不回 pong、不应答"）在入口处真正掐断所有上行应答。
  if(FAULT==='silent'){ log({dir:'in',frame:m,note:'FAULT silent: 收到但不回任何帧'}); return }
  switch(m.type){
    case 'ping': return P.send({kind:'pong',at:Date.now()})
    case 'sessions': return P.send({kind:'sessions',items:[{sessionId:SID,cwd:'/mock',running:false,blank:false,updatedAt:Date.now()}],archivedSessionIds:[]})
    case 'subscribe':{
      P.send({kind:'subscribed',sessionId:m.sessionId||SID,assistantStream:!!m.assistantStream,subscriptionId:'sub-mock'})
      return P.send({kind:'session-snapshot',sessionId:m.sessionId||SID,replace:true,cursor:2,historyFormatVersion:4,hasMore:false,nextBeforeSeq:1,events:snapEvents,assistantStream:null})
    }
    case 'message':{
      const sid=m.sessionId||SID
      P.send({kind:'sent',sessionId:sid,mode:m.mode||'queue'})
      P.send({kind:'session-snapshot',sessionId:sid,replace:true,cursor:3,historyFormatVersion:4,hasMore:false,events:snapEvents,assistantStream:null})
      P.send({kind:'assistant-stream',sessionId:sid,frame:{type:'start',attemptId:'a1'}})
      P.send({kind:'approval-requested',sessionId:sid,rpcId:'rpc-a1',approvalId:'ap-a1',toolName:'pwsh',reason:'（mock）沙箱升权'})
      P.send({kind:'question-requested',sessionId:sid,rpcId:'rpc-q1',questions:[{id:'q1',header:'（mock）选一个',options:[{label:'A'},{label:'B'}]}]})
      if(FAULT==='resetstream'){ P.send({kind:'assistant-stream',sessionId:sid,frame:{type:'chunk',attemptId:'a1',chunk:{type:'text',text:'（mock）半个字'}}}); return setTimeout(()=>P.send({kind:'session-stream-reset',sessionId:sid,code:'stream-interrupted',message:'（mock）跟随失败'}),300) }
      let i=0; const t=setInterval(()=>{ i++
        P.send({kind:'assistant-stream',sessionId:sid,frame:{type:'chunk',attemptId:'a1',chunk:{type:'text',text:'（mock）块'+i+' '}}})
        if(i>=4){clearInterval(t)
          P.send({kind:'assistant-stream',sessionId:sid,frame:{type:'end',attemptId:'a1',outcome:{kind:'committed'}}})
          P.send({kind:'event',sessionId:sid,seq:3,time:Math.floor(Date.now()/1000),event:{type:'assistant/message',turn:1,step:0,text:'（mock）块1 （mock）块2 （mock）块3 （mock）块4'}})
          P.send({kind:'event',sessionId:sid,seq:4,time:Math.floor(Date.now()/1000),event:{type:'turn/end',turn:1,step:0}})
          P.send({kind:'session-title-changed',sessionId:sid,title:'（mock）一句话标题',seq:5,time:Math.floor(Date.now()/1000)})
          if(FAULT!=='emptyclose') P.send({kind:'session-queues',queues:{}})
        }},200)
      return
    }
    case 'history': return P.send({kind:'history',sessionId:m.sessionId||SID,historyFormatVersion:4,cursor:2,bytes:120,view:m.view||'conversation',hasMore:false,events:snapEvents})
    case 'tasks': return P.send({kind:'tasks',sessionId:m.sessionId||SID,asOfSeq:2,todos:[{content:'（mock）跑通流式',status:'completed'},{content:'（mock）修 U1',status:'pending'}]})
    case 'goal': return P.send({kind:'goal',sessionId:m.sessionId||SID,asOfSeq:2,goal:null})
    case 'attachment': return P.send({kind:'attachment',sessionId:m.sessionId,attachment:{attachmentId:m.attachmentId,mediaType:'image/png',bytes:68,width:1,height:1},data:'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8DwHwAFAAH/q842iQAAAABJRU5ErkJggg=='})
    case 'file-download-open':{
      const tid='tr-1', name=(m.path||'f.bin').split('/').pop(), data=Buffer.from('mock-file-bytes-0123456789'.repeat(64))
      P.dl={tid,data,off:0}; P.send({kind:'file-download-opened',requestId:m.requestId,transferId:tid,sessionId:m.sessionId,path:m.path,name,mediaType:'application/octet-stream',size:data.length,chunkBytes:512*1024}); return
    }
    case 'file-download-read':{
      const cut=P.dl.data.subarray(P.dl.off), eof=cut.length<=8, chunk=eof?cut:cut.subarray(0,8)
      P.dl.off+=chunk.length
      return P.send({kind:'file-download-chunk',transferId:P.dl.tid,offset:P.dl.off-chunk.length,data:chunk.toString('base64'),eof,...(eof?{sha256:crypto.createHash('sha256').update(P.dl.data).digest('hex')}:{})})
    }
    case 'file-download-cancel': return P.send({kind:'file-download-cancelled',transferId:m.transferId})
    case 'session-cancel': return P.send({kind:'session-cancelled',sessionId:m.sessionId,accepted:true})
    case 'session-archive': return P.send({kind:'session-archived',sessionId:m.sessionId,archivedSessionIds:[m.sessionId]})
    case 'session-rename': return P.send({kind:'session-renamed',sessionId:m.sessionId,title:m.title,seq:9})
    case 'approval-response': return P.send({kind:'approval-resolved',rpcId:m.rpcId,sessionId:m.sessionId,outcome:m.outcome})
    case 'question-answer': return P.send({kind:'question-response',rpcId:m.rpcId,sessionId:m.sessionId,action:'answer',accepted:true})
    case 'question-cancel': return P.send({kind:'question-response',rpcId:m.rpcId,sessionId:m.sessionId,action:'cancel',accepted:true})
    case 'workspaces': return P.send({kind:'workspaces',workspaces:[{workspaceId:'w1',path:'/mock',title:'mock',sessionIds:[SID]}]})
    case 'host': return P.send({kind:'host',host:{name:'mock-host'}})
    default: return P.send({kind:'error',code:'bad-request',requestType:m.type,message:'mock: unsupported '+m.type})
  }
}
const server=http.createServer((q,r)=>{r.writeHead(404);r.end('only /ws/mobile')})
server.on('upgrade',(req,sock)=>{
  const u=new URL(req.url,'http://x')
  const protos=String(req.headers['sec-websocket-protocol']||'').split(',').map(s=>s.trim())
  const pair=protos.find(p=>p.startsWith('dsh-pair.'))?.slice(9)
  const tok=protos.find(p=>p.startsWith('dsh-auth.'))?.slice(9)
  const hdr=/^Bearer\s+(.+)$/i.exec(String(req.headers['authorization']||''))?.[1]
  const dev=req.headers['x-dsh-device-id']
  const reject=(code,msg)=>{log({dir:'reject',code,msg,path:u.pathname});sock.write(`HTTP/1.1 ${code} ${msg}\r\nConnection: close\r\n\r\n`);sock.destroy()}
  if(u.pathname!=='/ws/mobile')return reject(404,'Not Found')
  if(FAULT==='gatewayoff')return reject(503,'Service Unavailable')
  if(pair){ if(!dev)return reject(400,'pairing requires X-DSH-Device-ID'); if(pair!==PAIR)return reject(401,'Unauthorized') }
  else if(tok||hdr){ if((tok||hdr)!==TOKEN)return reject(401,'Unauthorized') }
  else return reject(401,'Unauthorized')
  const key=crypto.createHash('sha1').update(String(req.headers['sec-websocket-key'])+'258EAFA5-E914-47DA-95CA-C5AB0DC85B11').digest('base64')
  sock.write(['HTTP/1.1 101 Switching Protocols','Upgrade: websocket','Connection: Upgrade','Sec-WebSocket-Accept: '+key,'Sec-WebSocket-Protocol: dsh-mobile-v1','',''].join('\r\n'))
  log({dir:'upgrade',ok:true,authenticated:{pair:!!pair,token:tok||hdr||null,deviceId:dev||null}})
  const c=ws(sock); c.onMsg=onMsg.bind(c)
  const ctx=c
  sock.on('data',d=>ctx.feed(d)); sock.on('error',()=>{}); sock.on('close',()=>log({dir:'socket',event:'closed'}))
  ctx.send({kind:'session-archives',archivedSessionIds:[]}); ctx.send({kind:'session-queues',queues:{}})
  if(pair)ctx.send({kind:'paired',token:TOKEN,device:{id:dev||DEV,name:'mock',createdAt:Date.now(),lastSeenAt:Date.now(),online:true,connections:1},gatewayId:'mock-gateway',gatewayName:'mock'})
  if(FAULT==='nohello'){log({note:'FAULT nohello: 不发 hello'});return}
  setTimeout(()=>{ ctx.send({kind:'hello',protocol:FAULT==='proto4'?4:3,dshVersion:'0.2.0-rc.2(mock)',historyFormatVersion:4,authenticated:true,port:PORT,clients:1,gatewayId:'mock-gateway',gatewayName:'mock',capabilities:FAULT==='nocaps'?['session-cancel']:CAPS})
    if(FAULT==='emptyclose'){const t=setInterval(()=>{try{c.closeEmpty()}catch{clearInterval(t)}},1500);sock.on('close',()=>clearInterval(t))}
  },20)
  if(FAULT==='silent'){log({note:'FAULT silent: hello(仍会发)后完全静默，不回 pong、不应答'})}
})
server.listen(PORT,'0.0.0.0',()=>{
  const payload=Buffer.from(JSON.stringify({version:2,publicUrl:`ws://${HOST}:${PORT}/ws/mobile`,pairingCode:PAIR,expiresAt:Date.now()+3600e3,gatewayId:'mock-gateway',endpoints:[`ws://${HOST}:${PORT}/ws/mobile`]}),'utf8').toString('base64url')
  console.log('mock gateway on ws://0.0.0.0:'+PORT+'/ws/mobile  fault='+FAULT+'  host='+HOST)
  console.log('PAIRING_STRING='+payload)
  console.log('token='+TOKEN+'  deviceId='+DEV)
  // [落盘增补④] 顺手把配对串写盘，方便扫码/粘贴（手工复制 base64 很容易漏字符导致 401）
  try{ fs.writeFileSync('pairing.txt',payload) ; console.log('已写入 pairing.txt（无换行，可直接粘贴/生成二维码）') }catch(e){ console.log('写 pairing.txt 失败: '+e.message) }
  if(HOST==='127.0.0.1') console.log('提示：此配对串只适合 App 与网关同机（JVM harness）。跑模拟器请加 --host 10.0.2.2，真机请填电脑局域网 IP。')
})
