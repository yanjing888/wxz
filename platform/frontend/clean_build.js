const fs = require('fs');
const path = require('path');

const debugDir = 'd:/yanjing/97 projects/物小智-项目/frontend/android/app/build/outputs/apk/debug';
const buildDir = 'd:/yanjing/97 projects/物小智-项目/frontend/android/app/build';

console.log('Debug dir exists:', fs.existsSync(debugDir));

if (fs.existsSync(debugDir)) {
  // List contents
  const items = fs.readdirSync(debugDir, { recursive: true });
  console.log('Items in debug dir:', items);

  // Delete all files first
  for (const item of items) {
    const full = path.join(debugDir, item);
    try {
      const stat = fs.statSync(full);
      if (stat.isFile()) {
        fs.unlinkSync(full);
        console.log('Deleted file:', item);
      }
    } catch(e) {
      console.log('Failed to delete file:', item, e.code);
    }
  }

  // Try rmSync with retries
  for (let i = 0; i < 5; i++) {
    try {
      fs.rmSync(debugDir, { recursive: true, force: true });
      console.log('rmSync succeeded on attempt', i + 1);
      break;
    } catch(e) {
      console.log('rmSync attempt', i + 1, 'failed:', e.code);
      if (i < 4) {
        const sleep = require('util').promisify(setTimeout);
        await sleep(1000);
      }
    }
  }
}

console.log('Debug dir exists after cleanup:', fs.existsSync(debugDir));

// Also try to clean the entire build dir for a fresh build
if (fs.existsSync(buildDir)) {
  console.log('Attempting to clean entire build dir...');
  for (let i = 0; i < 3; i++) {
    try {
      fs.rmSync(buildDir, { recursive: true, force: true, maxRetries: 3, retryDelay: 500 });
      console.log('Build dir cleaned');
      break;
    } catch(e) {
      console.log('Build dir clean attempt', i + 1, 'failed:', e.code);
    }
  }
  console.log('Build dir exists after clean:', fs.existsSync(buildDir));
}
