<p align="center">
  <img src="docs/assets/icon.svg" width="112" alt="应用空间助手图标">
</p>

<h1 align="center">应用空间助手</h1>

<p align="center">逐台关闭 Android 应用详情页中的「自动释放应用空间」开关，并记录结果。</p>

<p align="center"><code>GPL-3.0-only</code> · Android 8+ · 无网络权限</p>

<p align="center">作者 <a href="https://github.com/AYUAN233">AYUAN</a></p>

<p align="center">
  <a href="release/AppSpaceHelper-v1.0.4.apk">下载 APK</a> ·
  <a href="docs/coloros.md">ColorOS 必看</a> ·
  <a href="PRIVACY.md">隐私说明</a> ·
  <a href="CHANGELOG.md">更新日志</a>
</p>

> [!IMPORTANT]
> **ColorOS / OPPO / OnePlus / realme 用户先看这里。**
> 如果应用只处理一个应用就停止，或无障碍服务被自动关闭，请到
> **手机管家 → 风险行为拦截**，查看是否出现「应用空间助手 · 异常操控设备」
> 的拦截记录，并把本应用加入白名单。
> 详细步骤见 [ColorOS 风险拦截说明](docs/coloros.md)。

## 功能

- **一键开始**：打开系统「应用管理」，随后逐个打开应用详情页。
- **增量关闭**：跳过已记录的应用，只处理新安装、上次未完成或失败的应用。
- **全量关闭**：忽略历史记录，重新检查全部用户应用。
- **只改一个开关**：定位「自动释放应用空间」，关闭后重新读取状态确认；原本已关闭的只记录，不重复点击。
- **记录**：保存应用名、包名、处理前后状态、结果和时间。
- **可暂停、可停止**：全屏半透明控制层；暂停后按钮变「继续」；停止需第二次确认，确认后回到应用主界面。
- **前台保活**：运行期间显示前台通知（Android 13+ 首次启动会请求通知权限），降低切换系统页面后被清理的概率。
- **中断提示**：如果进程/无障碍被系统关闭，下次打开会显示中断记录和排查提示。
- **默认本地运行**：无网络权限，不上传数据。

## 安装

1. 从 [release/AppSpaceHelper-v1.0.4.apk](release/AppSpaceHelper-v1.0.4.apk) 下载。
   仓库同时保留 `release/` 目录；正式发布时建议上传到 GitHub Releases。
   校验和见 [release/SHA256SUMS.txt](release/SHA256SUMS.txt)。
2. 允许安装未知来源应用。
3. 安装后首次打开应用，点击「去开启」。
4. 在系统 **设置 → 无障碍 → 已下载的应用** 中开启
   **「应用空间助手 · 自动关闭」**。
5. Android 13+ 侧载应用如果无障碍开关是灰色：
   打开应用信息 → 右上角菜单 → **允许受限设置**，再开启无障碍。
6. ColorOS 用户**先完成白名单设置**，再回到应用运行。

运行要求：

- Android 8.0（API 26）及以上；
- Android 13+ 首次启动会请求通知权限；拒绝也不影响使用，但看不到保活通知；
- 必须开启无障碍服务；
- 不需要 Root；
- 不需要单独授予「悬浮窗」权限（控制层使用无障碍覆盖窗口）；
- 必须保持屏幕解锁，并把屏幕留在系统设置页。

## ColorOS 风险行为拦截

ColorOS 会把无障碍自动点击识别为「异常操控设备」，可能导致：

- 处理一个应用后自动停止；
- 悬浮控制层突然消失；
- 系统无障碍里的本应用被关闭。

处理路径：

**手机管家 → 风险行为拦截 → 找到应用空间助手 → 加入白名单**，
然后到无障碍里重新开启服务。

也建议设置：

- 应用信息 → **耗电管理 → 允许后台运行**；
- 允许自启动；
- 最近任务里锁定应用；
- 关闭省电模式 / 睡眠待机优化；
- 允许通知，以显示保活通知。

系统更新、手机管家更新或重装应用后，请检查白名单是否仍然有效。

完整说明：[docs/coloros.md](docs/coloros.md)

## 两种模式

| 模式 | 行为 | 适合 |
| --- | --- | --- |
| 增量关闭 | 跳过已经记录为处理过的应用，只处理新安装、上次中断未完成、上次失败的应用 | 日常补漏、中断后继续 |
| 全量关闭 | 忽略历史记录，从第一个应用重新检查全部用户应用 | 首次使用、重新核对 |

默认只处理用户安装的应用。勾选「包含预装的系统应用」后，会额外处理有桌面入口的预装应用。

## 运行时控制层

- 全屏半透明覆盖，显示当前进度、应用名和统计。
- **暂停**：按钮变为「继续」，自动化暂停在安全步骤。
- **停止**：第一次点击变为「确认停止」，第二次才停止；停止后回到应用主界面。
- 控制层位于屏幕上方，不遮挡应用详情页底部的目标开关。
- 运行时屏幕保持点亮。

## 权限

| 权限 | 用途 |
| --- | --- |
| `BIND_ACCESSIBILITY_SERVICE` / 无障碍服务 | 读取系统设置页、定位开关、执行点击 |
| `QUERY_ALL_PACKAGES` | 枚举已安装应用，生成处理列表 |
| `FOREGROUND_SERVICE` / `FOREGROUND_SERVICE_SPECIAL_USE` | 运行时前台保活 |
| `POST_NOTIFICATIONS` | 显示保活通知（可拒绝） |

**没有 `INTERNET` 权限**，应用不会联网，也没有广告或统计 SDK。

详细说明：[PRIVACY.md](PRIVACY.md)

## 实现方式

1. 扫描应用列表，按应用名排序。
2. 打开系统「应用管理」，再逐个打开应用详情页。
3. 通过无障碍节点查找文字「自动释放应用空间」，并按位置找到同一行的开关。
4. 开关开启时执行点击并重新读取状态；已关闭时直接记录。
5. 记录结果，按返回键离开详情页，处理下一个。
6. 全屏覆盖来自无障碍服务的 `TYPE_ACCESSIBILITY_OVERLAY`，不申请悬浮窗权限。
7. 中断/强制结束会通过持久化运行状态标记，在下次打开时提示。

## 已知限制

- 按中文文字 **「自动释放应用空间」** 定位。系统改了这个名称或页面结构时，会记录为「无此开关」。
- 不同厂商的「应用详情」页面差异较大，本项目主要在 ColorOS / Android 17 上验证。
- ColorOS 的安全模块可能拦截自动化；必须按上方白名单设置允许。系统更新可能重置该设置。
- 这不是卸载工具，不会卸载应用或清除数据，只修改目标开关。
- 自动化依赖系统无障碍框架，不能保证在所有系统版本上 100% 成功；遇到异常会留下记录，不会伪造成功。
- 渠道分发：`QUERY_ALL_PACKAGES` 等权限可能不符合 Google Play 政策，本项目面向 GitHub 侧载发布。

## 构建

项目不依赖 Gradle，直接使用 Android SDK 构建工具和 JDK 17+。

```bash
git clone https://github.com/AYUAN233/app-space-helper.git
cd app-space-helper
export ANDROID_SDK_ROOT=/path/to/android-sdk
bash build.sh
```

需要：

- Android SDK Platform 34 的 `android.jar`；
- build-tools（`d8.jar`、`apksigner.jar`、`aapt`、`zipalign`）；
- JDK 17+；
- `zip`。

在 ARM64 Linux 上，SDK 里的原生 `aapt`/`zipalign` 是 x86_64 版本，无法直接运行。
脚本会自动改用系统包：

```bash
sudo apt install aapt zipalign openjdk-17-jdk-headless zip
```

产物在 `dist/AppSpaceHelper-v1.0.4.apk`。

签名：首次构建会在 `signing/appspace.jks` 生成自签名密钥，该目录已被 `.gitignore` 忽略。
发布前建议用环境变量指定自己的密钥和口令：

```bash
export APP_KEYSTORE=/secure/path/appspace.jks
export APP_KEYSTORE_PASS='your-store-password'
export APP_KEY_PASS='your-key-password'
```

发布包必须用同一个密钥签名，后续版本才能覆盖升级。更换密钥后需要先卸载旧版。

## 目录结构

```text
app/src/main/AndroidManifest.xml
app/src/main/java/com/dsh/appspace/
  MainActivity.java                 主界面、记录、ColorOS 提示
  AutoAccessibilityService.java     应用遍历、开关定位与点击、记录
  OverlayController.java            全屏半透明控制层
  AutomationForegroundService.java  运行时保活
  AppScanner.java                   应用列表
  RecordStore.java                  本地记录和运行状态
app/src/main/res/                   图标、无障碍配置、主题
docs/coloros.md                     ColorOS 风险拦截专页
docs/release.md                     发布步骤
build.sh                            可移植构建脚本
```

## 发布与贡献

发布流程：[docs/release.md](docs/release.md)

问题反馈请附上：

- 手机品牌、型号、Android / ColorOS 版本；
- 应用版本号；
- 是否已开启无障碍、是否已加 ColorOS 白名单；
- 「上次运行」或「最近记录」的截图；
- 复现步骤。

> 请不要在 issue 中粘贴账号、设备序列号或其他隐私信息。

## 作者

**AYUAN**  
GitHub 账号：[AYUAN233](https://github.com/AYUAN233)

## 声明

- 本项目与 OPPO、OnePlus、realme、ColorOS、Google 无隶属关系。
- 应用只应在**你拥有或获授权管理**的设备上，处理你明确知道的应用设置。
- 自动化会操作系统设置页；请在运行前确认自己理解并接受该行为。
- 本项目按 GNU GPL v3.0-only 开源，不提供任何形式的担保。

## 许可证

本项目使用 **GNU General Public License v3.0 only**（SPDX：`GPL-3.0-only`）。
Copyright (C) 2026 AYUAN（GitHub：[AYUAN233](https://github.com/AYUAN233)）。
完整协议文本见 [LICENSE](LICENSE)，应用内底部也有「开源许可」入口。

这意味着：

- 你可以运行、研究、修改和分发本程序；
- 分发本程序（包括修改后的 APK）时，必须同时提供对应的完整源代码和 GPL 许可证；
- 修改版必须保留版权与许可声明，并以相同许可证发布，不得附加额外限制；
- 本程序不提供担保。

发布 APK 时，请把同一版本的源码（例如 GitHub 的 Source code 归档）和
`LICENSE` 放在下载页旁边。仓库内的 [release/](release/) 保留了构建的 APK；
正式发布建议使用 GitHub Releases 自动附带的源码包。

项目名称「应用空间助手」和图标不随代码许可证授权。发布修改版时请使用不同的
名称和图标，避免与本项目混淆。
