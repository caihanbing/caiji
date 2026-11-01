# 财记 0.2.0 macOS 构建与安装

财记 0.2.0 使用 applicationId `app.caiji.android`、`versionCode=3`、`minSdk=26` 和 `targetSdk=34`。Room schema 保持 1，加密备份格式保持 2。项目使用 Gradle 8.7，macOS 构建入口为 `scripts/build-release-macos.sh`。

## 构建正式 APK

本机需要 JDK 与 Android API 34 platform/build-tools。SDK 根目录可通过 `ANDROID_SDK_ROOT` 或 `ANDROID_HOME` 指定。正式签名材料保存在忽略的 `private-signing/caiji-release.jks` 和 `private-signing/signing.properties`，不得提交或分享。该签名与 0.1.1 相同，支持覆盖升级。

运行：

    ./scripts/build-release-macos.sh

脚本运行单元测试、lint 和正式构建，根据 APK 内的实际版本名复制版本化安装包，生成 SHA-256 sidecar，并核验包名、Android 版本下限、zipalign 和签名。

默认产物：

    app/build/outputs/apk/release/caiji-0.2.0-release.apk
    app/build/outputs/apk/release/caiji-0.2.0-release.apk.sha256

## 从 0.1.1 升级

0.2.0 与 0.1.1 使用相同签名。可在安装的 0.1.1 上直接安装 0.2.0；Room schema 和现存账单格式不变。更新前仍建议在“设置 → 加密备份”导出文件并验证密码。

1. 核对 APK 的 SHA-256 sidecar 和包名 `app.caiji.android`。
2. 安装 `caiji-0.2.0-release.apk` 并打开应用。
3. 查看月账单/年账单、常用分类、最近账单和月度汇总，确认旧数据仍在。
4. 如需恢复更新前的账本，使用刚才导出的 `.cjbackup` 文件和密码。

## 回退到 0.1.1

`0.2.0` 新增了分类图标键，0.1.1 不认识这些键。0.2.0 备份若包含新图标分类，0.1.1 会拒绝恢复该文件。需要回退时应先用更新前创建的备份，并在卸载/降级前保存好该文件和密码。卸载应用会删除应用私有数据库；卸载前务必确认所需备份可读取。

0.1.1 原本无法在更低版本号 APK 上覆盖安装；需卸载 0.2.0，再安装原始 0.1.1 APK。回退不会涉及数据库迁移或服务端操作。

本应用没有后端、队列或其他需要部署/重启的服务；交付和升级只涉及 Android APK。
