# 财记 0.2.2 macOS 构建与安装

财记 0.2.2 使用 applicationId `app.caiji.android`、`versionCode=5`、`minSdk=26` 和 `targetSdk=34`。Room schema 保持 1，加密备份格式保持 2。项目使用 Gradle 8.7，macOS 构建入口为 `scripts/build-release-macos.sh`。

## 构建正式 APK

本机需要 JDK 与 Android API 34 platform/build-tools。SDK 根目录可通过 `ANDROID_SDK_ROOT` 或 `ANDROID_HOME` 指定。正式签名材料位于 Git 忽略的 `private-signing/caiji-release.jks` 和 `private-signing/signing.properties`，不得提交或分享。沿用既有正式签名以支持覆盖升级。

运行：

    ./scripts/build-release-macos.sh

脚本运行单元测试、lint 和正式构建，根据 APK 内版本名复制版本化安装包，生成 SHA-256 sidecar，并核验包名、Android 版本下限、zipalign 和签名。

默认产物：

    app/build/outputs/apk/release/caiji-0.2.2-release.apk
    app/build/outputs/apk/release/caiji-0.2.2-release.apk.sha256

## 安装或升级

1. 核对 APK 的 SHA-256 sidecar 和包名 `app.caiji.android`。
2. 安装 `caiji-0.2.2-release.apk` 并打开应用。
3. 查看明细、月/年账单、分类和记账页，确认既有账本仍在。
4. 如需回退，先导出加密备份，再使用同签名旧版本覆盖安装。

版本 0.2.2 不改变数据库或备份格式，不需要数据库迁移。更新前仍建议导出加密备份并妥善保留密码。

## 回退

使用同签名的 0.2.1 APK 覆盖安装；若应用存储状态异常，再从安装前导出的格式 2 加密备份恢复。无后端服务、队列或数据库服务需要部署或重启。不要卸载应用后再回退，因为卸载会删除应用私有账本。
