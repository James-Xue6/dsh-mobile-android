@echo off
chcp 65001 >nul
set ADB=C:\Users\Administrator\emu_copy\adb\adb.exe
set APK=%~dp0dist\dsh-mobile.apk
echo 等待手机（A7QK023224004563）...
"%ADB%" wait-for-device
"%ADB%" -s A7QK023224004563 install -r "%APK%"
echo 完成。若提示 INSTALL_FAILED_UPDATE_INCOMPATIBLE，说明签名不一致，需要先卸载旧版。
pause