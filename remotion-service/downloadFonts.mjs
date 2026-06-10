import fs from 'fs';
import path from 'path';
import https from 'https';

const FONTS = [
  {
    name: 'Noto Sans SC',
    url: 'https://github.com/notofonts/noto-cjk/raw/main/Sans/SubsetOTF/SC/NotoSansSC-Regular.otf',
    file: 'NotoSansSC-Regular.otf'
  },
  {
    name: 'Noto Serif SC',
    url: 'https://github.com/notofonts/noto-cjk/raw/main/Serif/SubsetOTF/SC/NotoSerifSC-Regular.otf',
    file: 'NotoSerifSC-Regular.otf'
  },
  {
    name: 'ZCOOL XiaoWei',
    url: 'https://github.com/google/fonts/raw/main/ofl/zcoolxiaowei/ZCOOLXiaoWei-Regular.ttf',
    file: 'ZCOOLXiaoWei-Regular.ttf'
  },
  {
    name: 'Ma Shan Zheng',
    url: 'https://github.com/google/fonts/raw/main/ofl/mashanzheng/MaShanZheng-Regular.ttf',
    file: 'MaShanZheng-Regular.ttf'
  },
  {
    name: 'LXGW WenKai TC',
    url: 'https://github.com/lxgw/LxgwWenKai-TC/releases/download/v1.330/LXGWWenKaiTC-Regular.ttf',
    file: 'LXGWWenKaiTC-Regular.ttf'
  },
  {
    name: 'Inter',
    url: 'https://github.com/rsms/inter/raw/master/docs/font-files/Inter-Regular.woff2',
    file: 'Inter-Regular.woff2'
  },
  {
    name: 'Bebas Neue',
    url: 'https://github.com/google/fonts/raw/main/ofl/bebasneue/BebasNeue-Regular.ttf',
    file: 'BebasNeue-Regular.ttf'
  }
];

const FONTS_DIR = path.join(process.cwd(), 'public', 'fonts');

if (!fs.existsSync(FONTS_DIR)) {
  fs.mkdirSync(FONTS_DIR, { recursive: true });
}

async function downloadFile(url, dest) {
  return new Promise((resolve, reject) => {
    const file = fs.createWriteStream(dest);
    https.get(url, (response) => {
      if (response.statusCode === 301 || response.statusCode === 302) {
        return downloadFile(response.headers.location, dest).then(resolve).catch(reject);
      }
      if (response.statusCode !== 200) {
        return reject(new Error(`Failed to get '${url}' (${response.statusCode})`));
      }
      response.pipe(file);
      file.on('finish', () => {
        file.close();
        resolve();
      });
    }).on('error', (err) => {
      fs.unlink(dest, () => reject(err));
    });
  });
}

async function run() {
  console.log('Downloading unified fonts...');
  for (const font of FONTS) {
    const dest = path.join(FONTS_DIR, font.file);
    if (!fs.existsSync(dest)) {
      console.log(`Downloading ${font.name}...`);
      try {
        await downloadFile(font.url, dest);
        console.log(`✅ Downloaded ${font.name}`);
      } catch (e) {
        console.error(`❌ Failed to download ${font.name}:`, e.message);
      }
    } else {
      console.log(`⏭️  Skipped ${font.name} (already exists)`);
    }
  }

  const cssContent = FONTS.map(font => `
@font-face {
  font-family: '${font.name}';
  font-style: normal;
  font-weight: 400;
  font-display: swap;
  src: url('/fonts/${font.file}') format('${font.file.endsWith('.woff2') ? 'woff2' : font.file.endsWith('.otf') ? 'opentype' : 'truetype'}');
}
`).join('\n');

  fs.writeFileSync(path.join(process.cwd(), 'src', 'fonts.css'), cssContent);
  console.log('✅ Generated src/fonts.css');
}

run();
