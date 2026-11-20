# 从零发布到 GitHub（GitHub 账号 AYUAN233 / 作者 AYUAN）

本文以 GitHub 用户名 **AYUAN233**、作者显示名 **AYUAN**、仓库名 **app-space-helper** 为例。
如果你用别的仓库名，把下面所有 `app-space-helper` 和 URL 换成你的即可。

- GitHub 主页：<https://github.com/AYUAN233>
- 建议仓库地址：<https://github.com/AYUAN233/app-space-helper>
- 项目许可证：**GPL-3.0-only**
- 当前版本：**v1.0.4**

---

## 1. 先检查本地项目

```bash
cd /root/workspace/app-space-helper
bash build.sh
```

确认产物存在：

```bash
ls -lh release/AppSpaceHelper-v1.0.4.apk
```

**不要提交签名密钥**。`.gitignore` 已经排除了：

- `signing/*.jks`
- `build/`
- `dist/`

---

## 2. 在 GitHub 创建空仓库

1. 打开 <https://github.com/new>。
2. Repository name 填 `app-space-helper`。
3. 选择 **Public**。
4. **不要**勾选 Add a README、Add .gitignore、Choose a license——本地项目里已经有了。
5. 点击 **Create repository**。

创建后会看到仓库地址：

```text
https://github.com/AYUAN233/app-space-helper.git
```

---

## 3. 配置 Git 身份

在本机执行：

```bash
git config --global user.name "AYUAN"
git config --global user.email "你的GitHub邮箱"
```

如果不想公开邮箱，可以在 GitHub 的
**Settings → Emails → Keep my email addresses private** 中查到一个
`xxxxx+AYUAN233@users.noreply.github.com` 地址，用它作为 Git 邮箱。

---

## 4. 初始化并推送

```bash
cd /root/workspace/app-space-helper
git init
git branch -M main
git add .
git status --short
```

**`git status --short` 里不应该出现 `signing/appspace.jks`。**
然后提交：

```bash
git commit -m "Release v1.0.4"
git remote add origin https://github.com/AYUAN233/app-space-helper.git
git push -u origin main
```

### 推送时的登录

GitHub 不接受账户密码作为 Git 密码，三选一：

**A. GitHub CLI（推荐，如果本机有 `gh`）**

```bash
gh auth login
```

按提示浏览器登录后，再执行 `git push`。

**B. Personal Access Token（PAT）**

1. GitHub → Settings → Developer settings → Personal access tokens
   → **Fine-grained tokens** → Generate new token。
2. Repository access 选 **Only select repositories**，选中
   `AYUAN233/app-space-helper`。
3. Permissions：
   - **Contents: Read and write**
   - **Metadata: Read-only**
4. 生成后，在 `git push` 提示输入密码时粘贴这个 token。

Token 只应保存在系统凭据管理里：**不要写进仓库、README、聊天或截图。**

**C. SSH**

在 GitHub 添加你的 SSH 公钥，然后把远程地址改成：

```bash
git remote set-url origin git@github.com:AYUAN233/app-space-helper.git
git push -u origin main
```

---

## 5. 在 GitHub 打开发布

推送完成后，打开仓库页面，先检查：

- README 顶部显示作者 AYUAN（账号 AYUAN233）；
- 右侧 License 显示 **GPL-3.0**；
- `release/AppSpaceHelper-v1.0.4.apk` 可以在仓库里下载。

然后创建 Release：

1. 仓库右侧点击 **Releases** → **Draft a new release**。
2. **Choose a tag** 输入 `v1.0.4`，选择 **Create new tag**。
3. Title 填 `v1.0.4`。
4. 发布说明可以从 `CHANGELOG.md` 复制，建议包含：
   - ColorOS 用户必须先加「手机管家 → 风险行为拦截」白名单；
   - 必须手动开启无障碍；
   - 该版本使用 GPL-3.0-only；
   - **对应源代码见本 Release 的 Source code (zip/tar.gz) 归档**。
5. 上传 `release/AppSpaceHelper-v1.0.4.apk`。
6. Publish release。

GitHub 会自动给每个 Release 附上当前 tag 的源码 zip/tar.gz。
**不要只把 APK 发到别处而不提供源码**：GPL 要求分发二进制时提供对应的完整源代码。

也可以使用 GitHub CLI：

```bash
gh release create v1.0.4 \
  release/AppSpaceHelper-v1.0.4.apk \
  --title "v1.0.4" \
  --notes-file CHANGELOG.md
```

发布后在 Release 里补一条源码链接和 GPL 声明。

---

## 6. 建议的仓库设置

- Description：
  `Android 无障碍工具：逐个关闭应用详情页的「自动释放应用空间」开关`
- Topics：
  `android`, `accessibility-service`, `coloros`, `automation`, `java`, `oppo`
- 开启 Issues（`.github/ISSUE_TEMPLATE` 已经有 bug 模板）
- 置顶 Release
- 不要上传 `signing/appspace.jks`
- 不要上传 GitHub token、设备信息或用户截图原图

---

## 7. 后续版本怎么发

1. 修改 `AndroidManifest.xml`：
   - `versionCode` +1
   - `versionName` 改成新版本号
2. 更新 `CHANGELOG.md` 和 README 里的版本/下载链接。
3. 生成新 APK：

   ```bash
   bash build.sh
   cp dist/AppSpaceHelper-v新版本.apk release/
   ```

4. 用**同一个签名密钥**签名；否则老用户不能覆盖安装。
5. 更新 `release/SHA256SUMS.txt`：

   ```bash
   cd release
   sha256sum AppSpaceHelper-v新版本.apk > SHA256SUMS.txt
   ```

6. 提交并推送：

   ```bash
   git add .
   git commit -m "Release v新版本"
   git push
   ```

7. 创建新的 GitHub Release，附 APK 和源码归档。

---

## 8. 常见问题

**`src refspec main does not match any`**

还没有提交内容，先执行 `git add .` 和 `git commit`。

**推送认证失败**

GitHub 不接受账户密码。使用 GitHub CLI、PAT 或 SSH；PAT 要有
`Contents: Read and write`。

**GitHub 没识别 GPL-3.0**

确认根目录 `LICENSE` 是完整的 GPLv3 文本，不要只写一句
“GPL-3.0”。本仓库已经放好。

**GPL 源码怎么提供**

使用 GitHub Release 自动附带的 Source code 归档，或把同版本源码
zip 放进 Release。只在私人网盘放 APK 而不提供源码，不符合 GPL 的分发要求。

**仓库文件太大**

不要提交 `build/`、`dist/`、APK 签名密钥。Release 资产可以单独上传。

---

## 9. 发布前检查清单

- [ ] `bash build.sh` 成功
- [ ] APK 能安装并打开
- [ ] 无障碍和 ColorOS 白名单提示正常
- [ ] `LICENSE` 为 GPL-3.0
- [ ] README 作者为 AYUAN（GitHub 账号 AYUAN233）
- [ ] `.gitignore` 排除了签名密钥
- [ ] Release 附带 APK 和同版本源码
- [ ] 发布说明写明 GPL-3.0-only 和源码位置
- [ ] 检查 GitHub 上 License 显示为 GPL-3.0
