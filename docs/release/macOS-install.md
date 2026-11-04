# 财记 0.3.4 macOS 构建与安装

财记 0.3.4 使用 applicationId `app.caiji.android`、`versionCode=11`、`minSdk=26` 和 `targetSdk=34`。账本 Room schema 保持 1，加密账本备份格式保持 2；反馈和主题继续保存在账本数据之外。构建入口为 `scripts/build-release-macos.sh`。

正式签名材料位于 Git 忽略的 `private-signing/caiji-release.jks` 和 `private-signing/signing.properties`，不得提交或分享。沿用现有正式签名以支持覆盖升级。

## 构建正式 APK

运行：

    ./scripts/build-release-macos.sh

脚本运行 JVM 测试和 lint，构建正式 APK，按内嵌版本号命名、生成 SHA-256 sidecar，并验证包名、minSdk、targetSdk、zipalign 和签名。

默认产物：

    app/build/outputs/apk/release/caiji-0.3.4-release.apk
    app/build/outputs/apk/release/caiji-0.3.4-release.apk.sha256

## 安装或升级

1. 核对 APK 的 SHA-256 sidecar 和包名 `app.caiji.android`。
2. 安装 APK 并启动应用。
3. 检查原账单与分类、反馈截图、主题选择、记账分类完整显示及快速左右滑动后分类仍在。
4. 如需回退，使用同签名 0.3.3 APK 覆盖安装。

0.3.4 不迁移或替换账本数据库或反馈数据库；旧账本加密备份仍按格式 2 处理。卸载应用会删除本机问题记录及截图。

每次交付新版本时，除在 `main` 推送源码外，还要创建匹配版本号的 GitHub Release，附上经校验的正式 APK 及 `.sha256` 文件。

## 回退

使用同签名 0.3.3 APK 覆盖安装；账本、反馈记录、截图和主题偏好继续留在相同应用私有存储中。无服务端、队列或数据库服务需要部署或重启。
