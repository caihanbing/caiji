# 财记 0.1.1 macOS 构建与安装

财记 0.1.1 使用 applicationId app.caiji.android、versionCode 2、minSdk 26 和 targetSdk 34。项目使用 Gradle 8.7。macOS 使用仓库内的 ./gradlew 和 scripts/build-release-macos.sh。

## 构建正式 APK

首次设置需要 JDK、Android command-line tools 和 API 34 SDK。使用官方 Android CLI 安装所需包；运行命令时传入 --no-metrics，关闭 CLI 可选使用数据收集：

    export ANDROID_SDK_ROOT="$HOME/Library/Android/sdk"
    export ANDROID_HOME="$ANDROID_SDK_ROOT"
    "$ANDROID_SDK_ROOT/cmdline-tools/latest/bin/android" --no-metrics --sdk="$ANDROID_SDK_ROOT" sdk install platform-tools platforms/android-34 build-tools/34.0.0 emulator system-images/android-34/google_apis/arm64-v8a
    "$ANDROID_SDK_ROOT/cmdline-tools/latest/bin/avdmanager" create avd --name Caiji_API_34 --package "system-images;android-34;google_apis;arm64-v8a" --device pixel_6

构建机使用标准 ~/Library/Android/sdk 路径时设置 ANDROID_SDK_ROOT；也可设置 ANDROID_HOME。初次使用前应审阅并接受 Android SDK License Agreement。

Android SDK 许可证由开发者在本机 SDK manager 显示时审阅并接受。此工作区不会自动接受许可证协议。

正式签名密钥位于忽略的 private-signing/caiji-release.jks，签名配置在忽略的 private-signing/signing.properties。两个文件不得提交、上传或复制到源码仓库。请在独立的安全备份位置保存签名材料和口令，以便将来更新同一签名渠道。

运行构建：

    ./scripts/build-release-macos.sh

脚本运行单元测试、lint 和 release APK 构建，随后打印 APK 包名、版本、平台下限、签名证书 SHA-256 和 APK SHA-256。输出文件为：

    app/build/outputs/apk/release/app-release.apk

单独校验 APK SHA-256：

    shasum -a 256 app/build/outputs/apk/release/app-release.apk

## 本次交付校验

- APK SHA-256：`839afe282231dd5d48557db51db764a4c6099faa2ddff510360f4ac80d274e95`
- 签名证书 SHA-256：`692d2387ba18f63ed43548d153af2a611384d8bffe1c603c3314df56ef006e1a`
- 包名与版本：`app.caiji.android`，`0.1.1`（`versionCode=2`）
- 最低 Android 版本：8.0（API 26）；目标版本 API 34。

## 从 0.1.0 安装 0.1.1

0.1.1 使用全新的签名证书，不能覆盖安装到原签名的 0.1.0。旧版账单只保存在应用私有数据库中；卸载前必须备份。

1. 在已安装的 0.1.0 中打开“设置 → 加密备份”，设置并确认密码，保存 .cjbackup 文件。
2. 确认备份文件已保存到设备之外的位置，并妥善保管密码。忘记密码后无法解密备份。
3. 保留原 0.1.0 APK 作为回退包；完成备份确认后，再从 Android 设置中卸载旧版。
4. 将 0.1.1 APK 安装到 Android 8.0 或以上的设备。核对应用包名为 app.caiji.android。
5. 打开“设置 → 从备份恢复”，选择 .cjbackup 文件，输入密码并检查解密摘要。
6. 确认恢复。应用恢复全部账单和分类后，按月份核对数量、分类及收支汇总。

## 恢复失败与回退

密码错误、文件取消或损坏不会改变正在运行的应用数据库。保留原始 .cjbackup 文件和密码，并重新选择正确的备份文件。

如需回退，先保留恢复文件及 0.1.1 APK。卸载新版后安装原始 0.1.0 APK，再从同一份加密备份恢复。新旧 APK 签名不同，因此卸载是版本回退的必要步骤；切勿在确认备份文件及口令之前卸载。

本应用无后端、队列或其他需要部署/重启的服务；本次安装仅涉及本机 APK。
