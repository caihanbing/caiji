# 财记 0.3.0 macOS 构建与安装

财记 0.3.0 使用 applicationId `app.caiji.android`、`versionCode=7`、`minSdk=26` 和 `targetSdk=34`。账本 Room schema 保持 1，加密账本备份格式保持 2；本机问题反馈使用独立数据库，主题选择保存在应用偏好中。构建入口为 `scripts/build-release-macos.sh`。

正式签名材料位于 Git 忽略的 `private-signing/caiji-release.jks` 和 `private-signing/signing.properties`，不得提交或分享。沿用现有正式签名以支持覆盖升级。

## 构建正式 APK

运行：

    ./scripts/build-release-macos.sh

脚本运行 JVM 测试和 lint，构建正式 APK，按内嵌版本号命名、生成 SHA-256 sidecar，并验证包名、minSdk、targetSdk、zipalign 和签名。

默认产物：

    app/build/outputs/apk/release/caiji-0.3.0-release.apk
    app/build/outputs/apk/release/caiji-0.3.0-release.apk.sha256

## 安装或升级

1. 核对 APK 的 SHA-256 sidecar 和包名 `app.caiji.android`。
2. 安装 APK 并启动应用。
3. 检查原账单与分类、问题记录、截图、主题选择和手势切换。
4. 如需回退，使用同签名 0.2.3 APK 覆盖安装。

0.3.0 不迁移或替换账本数据库，旧账本加密备份仍按格式 2 处理。问题反馈数据不进入账本备份；卸载应用会删除本机问题记录及截图。

## 回退

使用同签名 0.2.3 APK 覆盖安装；账本数据保持原样。问题反馈独立数据库仅由 0.3.0 使用，回退前如需保留，应先使用应用分享功能导出记录和截图。无服务端、队列或数据库服务需要部署或重启。
