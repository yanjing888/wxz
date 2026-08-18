const tar = require('tar');
const fs = require('fs');
const path = require('path');

const frontendDir = 'd:/yanjing/97 projects/物小智-项目/frontend';
const androidDir = path.join(frontendDir, 'android');
const cliAssetsDir = path.join(frontendDir, 'node_modules/@capacitor/cli/assets');

const androidTemplate = path.join(cliAssetsDir, 'android-template.tar.gz');
const pluginsTemplate = path.join(cliAssetsDir, 'capacitor-cordova-android-plugins.tar.gz');

async function main() {
  // Ensure android dir exists (mkdirp)
  fs.mkdirSync(androidDir, { recursive: true });
  console.log('Android dir:', androidDir);

  // Extract android template
  console.log('Extracting android template...');
  await tar.extract({ file: androidTemplate, cwd: androidDir });
  console.log('Android template extracted.');

  // Extract cordova plugins template
  const pluginsDir = path.join(androidDir, 'capacitor-cordova-android-plugins');
  fs.mkdirSync(pluginsDir, { recursive: true });
  console.log('Extracting cordova plugins template...');
  await tar.extract({ file: pluginsTemplate, cwd: pluginsDir });
  console.log('Cordova plugins template extracted.');

  // Verify
  const files = fs.readdirSync(androidDir);
  console.log('\nAndroid dir contents:', files);

  // Check key files
  const keyFiles = ['build.gradle', 'settings.gradle', 'gradle.properties', 'gradlew', 'app/build.gradle', 'app/src/main/AndroidManifest.xml'];
  for (const f of keyFiles) {
    const exists = fs.existsSync(path.join(androidDir, f));
    console.log(`  ${f}: ${exists ? 'OK' : 'MISSING'}`);
  }
}

main().catch(e => { console.error('Error:', e); process.exit(1); });
