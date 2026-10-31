#!/bin/bash
# 安装/修复 Android SDK 到 /root/android-sdk（稳定位置），并接受 license
SDK=/root/android-sdk
if [ -f "$SDK/platforms/android-35/android.jar" ] && [ -f "$SDK/build-tools/35.0.0/aapt2" ]; then
  echo "SDK already OK"
  exit 0
fi
mkdir -p "$SDK"
cd "$SDK"
if [ ! -f "$SDK/latest/cmdline-tools/bin/sdkmanager" ]; then
  rm -rf cmdtools.zip cmdline-tools latest
  curl -sL -o cmdtools.zip "https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip"
  unzip -q -o cmdtools.zip
  mkdir -p latest/cmdline-tools
  mv cmdline-tools/* latest/cmdline-tools/ 2>/dev/null
  rm -rf cmdline-tools cmdtools.zip
fi
yes | "$SDK/latest/cmdline-tools/bin/sdkmanager" --sdk_root="$SDK" --licenses >/dev/null 2>&1
"$SDK/latest/cmdline-tools/bin/sdkmanager" --sdk_root="$SDK" "platform-tools" "platforms;android-35" "build-tools;35.0.0" >/dev/null 2>&1
if [ -f "$SDK/platforms/android-35/android.jar" ] && [ -f "$SDK/build-tools/35.0.0/aapt2" ]; then
  echo "SDK installed OK at $SDK"
else
  echo "SDK install FAILED"
  exit 1
fi
