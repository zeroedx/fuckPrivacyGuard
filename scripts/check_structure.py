#!/usr/bin/env python3
"""静态一致性自查：只报告，不拦截构建（加 --strict 才返回非零）。"""
import re, sys, pathlib

root = pathlib.Path(__file__).resolve().parent.parent
strict = '--strict' in sys.argv
problems = []
notes = []

def read(p):
    return (root / p).read_text(encoding='utf-8')

cfg = read('src/io/github/zeroedx/fuckprivacyguard/Config.java')
manifest = read('AndroidManifest.xml')
scope = read('META-INF/xposed/scope.list')

# 界面代码已模块化：分组表在 Catalog.java，页面各自成文件。
# 一致性检查改为扫描整个 src/，不依赖某个具体文件。
src_dir = root / 'src'
main = '\n'.join(p.read_text(encoding='utf-8') for p in src_dir.rglob('*.java'))

# 1) 版本号一致
ver = re.search(r'VERSION\s*=\s*"([^"]+)"', cfg)
man_ver = re.search(r'versionName="([^"]+)"', manifest)
if ver and man_ver and ver.group(1) != man_ver.group(1):
    problems.append(f'Config.VERSION={ver.group(1)} 与 AndroidManifest versionName={man_ver.group(1)} 不一致')

# 2) 免责声明两份一致
a = read('DISCLAIMER.txt')
b = read('assets/DISCLAIMER.txt') if (root / 'assets/DISCLAIMER.txt').exists() else ''
if a != b:
    problems.append('DISCLAIMER.txt 与 assets/DISCLAIMER.txt 不一致（应用内显示的是 assets 那份）')

# 3) scope.list 里的包都能在界面里找到
for pkg in [l.strip() for l in scope.splitlines() if l.strip()]:
    if f'"{pkg}"' not in main:
        problems.append(f'scope.list 里的 {pkg} 在 Catalog 的 GROUPS 中没有对应条目')

# 4) 分组开关都在界面里用到
groups = re.findall(r'String (K_G_[A-Z0-9_]+)\s*=\s*"([^"]+)"', cfg)
for name, key in groups:
    if f'Config.{name}' not in main:
        problems.append(f'Config.{name}（{key}）没有在界面代码（Catalog / 页面）中使用')

# 5) scope.list 内容提示
pkgs = [l for l in scope.splitlines() if l.strip()]
notes.append(f'scope.list 共 {len(pkgs)} 个包，Config 分组 {len(groups)} 个')

print('结构自查：')
for n in notes:
    print('  ·', n)
if problems:
    for p in problems:
        print('  !', p)
    print(f'\n发现 {len(problems)} 处可能需要确认的地方。')
    if strict:
        sys.exit(1)
else:
    print('  ✓ 没有发现不一致')
