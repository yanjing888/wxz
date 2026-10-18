import assert from 'node:assert/strict'
import { spawn } from 'node:child_process'
import { once } from 'node:events'
import { createServer } from 'node:net'
import { fileURLToPath } from 'node:url'

const root=fileURLToPath(new URL('../',import.meta.url))
const socket=createServer();socket.listen(0,'127.0.0.1');await once(socket,'listening')
const port=socket.address().port;await new Promise(resolve=>socket.close(resolve))
// Consume piped stdin like an interactive launcher so closing it actually
// delivers EOF instead of leaving the EOF buffered in a paused stream.
const child=spawn(process.execPath,['--import=data:text/javascript,process.stdin.resume()','scripts/run-vite.mjs','--host','127.0.0.1','--port',String(port),'--strictPort'],{cwd:root,env:{...process.env,CI:''},windowsHide:true,stdio:['pipe','ignore','pipe']})
let errors='';child.stderr.on('data',data=>{errors+=data})
let exited=false;child.once('exit',()=>{exited=true})
async function waitReady(){
  for(let i=0;i<60;i++){
    if(exited)throw new Error('Frontend exited during startup: '+errors)
    try{if((await fetch(`http://127.0.0.1:${port}/`)).ok)return}catch{}
    await new Promise(resolve=>setTimeout(resolve,100))
  }
  throw new Error('Frontend did not start: '+errors)
}
try {
  await waitReady()
  child.stdin.end()
  await new Promise(resolve=>setTimeout(resolve,1600))
  assert.equal(exited,false,'Closing the launcher input must not stop Vite')
  assert.equal((await fetch(`http://127.0.0.1:${port}/`)).status,200)
  // A second launch must fail rather than killing or replacing the running server.
  const second=spawn(process.execPath,['scripts/run-vite.mjs','--host','127.0.0.1','--port',String(port),'--strictPort'],{cwd:root,windowsHide:true,stdio:'ignore'})
  const [code]=await once(second,'exit');assert.notEqual(code,0)
  assert.equal(exited,false);assert.equal((await fetch(`http://127.0.0.1:${port}/`)).status,200)
  console.log('Passed: frontend survives closed stdin; strict port conflict leaves the existing server running.')
} finally {child.kill()}
