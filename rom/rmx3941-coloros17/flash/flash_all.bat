@echo off
rem Flash the ColorOS 17 port to a realme C75 (RMX3941) with an UNLOCKED bootloader.
rem Start in bootloader fastboot mode. This ERASES ALL DATA and writes to slot A.
rem Keep the stock realme package: it is your way back if the port does not boot.
setlocal enabledelayedexpansion
cd /d "%~dp0"
if "%FASTBOOT%"=="" set FASTBOOT=fastboot

for /f "tokens=2 delims=: " %%u in ('%FASTBOOT% getvar unlocked 2^>^&1 ^| findstr /b "unlocked:"') do set UNLOCKED=%%u
if not "%UNLOCKED%"=="yes" (
  echo The bootloader is not unlocked. Stop.
  exit /b 1
)
for /f "tokens=2 delims=: " %%u in ('%FASTBOOT% getvar product 2^>^&1 ^| findstr /b "product:"') do set PRODUCT=%%u
echo fastboot reports product=%PRODUCT%
echo Verify the image checksums first with:  certutil -hashfile ^<file^> SHA256  against SHA256SUMS
set /p A=This wipes the phone and flashes an untested port. Type YES to continue:
if not "%A%"=="YES" exit /b 1

%FASTBOOT% --set-active=a || exit /b 1
set /p FIRMWARE=<firmware_partitions.txt
for %%p in (%FIRMWARE%) do (
  if "%%p"=="vbmeta" (
    %FASTBOOT% --disable-verity --disable-verification flash vbmeta_a vbmeta.img || exit /b 1
  ) else (
    %FASTBOOT% flash %%p_a %%p.img || exit /b 1
  )
)

%FASTBOOT% reboot fastboot || exit /b 1
%FASTBOOT% snapshot-update cancel
%FASTBOOT% wipe-super super_empty.img || exit /b 1
set /p LOGICAL=<logical_partitions.txt
for %%p in (%LOGICAL%) do (
  %FASTBOOT% flash %%p %%p.img || exit /b 1
)
%FASTBOOT% -w
%FASTBOOT% reboot
echo Done. The first boot can take 5-10 minutes.
