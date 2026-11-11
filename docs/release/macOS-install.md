# 财记 0.5.11 macOS 构建与安装

财记 0.5.11 使用 applicationId `app.caiji.android`、`versionCode=26`、`minSdk=26` 和 `targetSdk=34`。账本 Room schema 保持 1，加密账单备份格式保持 2；工资数据库 schema 为 3。0.5.11 不含数据库迁移。问题反馈、工资、主题偏好和更新提示抑制状态继续各自独立。正式构建入口为 `scripts/build-release-macos.sh`。

正式签名材料位于 Git 忽略的 `private-signing/caiji-release.jks` 和 `private-signing/signing.properties`，不得提交或分享。沿用现有正式签名以支持覆盖升级。

## 构建正式 APK

运行：

    ANDROID_SDK_ROOT=<Android SDK 路径> ./scripts/build-release-macos.sh

脚本运行 JVM 测试和 lint，构建正式 APK，按内嵌版本号命名、生成 SHA-256 sidecar，并验证包名、版本、minSdk、targetSdk、zipalign 和签名。

默认产物：

    app/build/outputs/apk/release/caiji-0.5.11-release.apk
    app/build/outputs/apk/release/caiji-0.5.11-release.apk.sha256

每次发布新版本时，将同一正式 APK 和 SHA-256 sidecar 附到匹配的 `v<version>` GitHub 与 Gitee Release。

## 安装或升级

1. 核对 APK 的 SHA-256 sidecar、包名 `app.caiji.android` 和正式签名。
2. 安装 APK 并启动应用；升级安装会保留本机账单、分类、工资、反馈和主题偏好。
3. 应用进入前台时自动优先检查 Gitee 最新正式版本，检查失败后回退 GitHub；也可在“设置 → 关于财记 → 检查更新”手动检查。
4. 若发现更新，检查版本号与 Release 说明后点击“下载更新”；系统浏览器打开 APK，安装仍由用户确认。
5. 离线时更新检查失败不影响本机账本、工资、反馈与备份功能。

0.5.11 不改变账本 Room schema、工资 Room schema 或加密账单备份格式。正式 APK 沿用现有签名，可覆盖升级并保留本机数据。发布若需回滚，使用更高 versionCode 的兼容修正版，不运行 destructive migration。

## 回退

发布后若发现问题，使用沿用工资 schema 3、账本 schema 1 和加密账单备份格式 2 的兼容修正版 APK。无服务端、队列或数据库服务需要部署或重启。
