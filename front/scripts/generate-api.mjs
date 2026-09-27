// Génère le client Angular depuis contract/openapi.yaml (Gradle, :contract:openApiGenerateTypescript)
// et le copie dans src/app/core/api/generated (non versionné). Java requis.
import { execFileSync } from 'node:child_process';
import { cpSync, rmSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const front = join(dirname(fileURLToPath(import.meta.url)), '..');
const root = join(front, '..');
const gradlew = join(root, process.platform === 'win32' ? 'gradlew.bat' : 'gradlew');
const source = join(root, 'contract', 'build', 'generated', 'typescript-angular');
const target = join(front, 'src', 'app', 'core', 'api', 'generated');

execFileSync(gradlew, [':contract:openApiGenerateTypescript', '--quiet'], { cwd: root, stdio: 'inherit' });
rmSync(target, { recursive: true, force: true });
cpSync(source, target, {
  recursive: true,
  filter: (path) => !/(\.openapi-generator|git_push\.sh|README\.md|\.gitignore)/.test(path),
});
console.log(`Client d'API généré dans ${target}`);
