# 签名密钥（不要提交）

`build.sh` 首次运行会在这里生成 `appspace.jks`，用于签名 APK。

- **不要**把 `.jks` 上传到 GitHub 或发给别人。
- 把它和口令一起离线备份；丢失后将无法给已安装用户发布覆盖升级。
- 后续版本必须使用同一密钥签名，否则用户需要卸载旧版再安装。
- 发布前建议通过环境变量设置自己的密钥和口令：

```bash
export APP_KEYSTORE=/secure/path/appspace.jks
export APP_KEYSTORE_PASS='your-store-password'
export APP_KEY_PASS='your-key-password'
bash build.sh
```
