# 빌드와 릴리스

## 요구 사항

- Windows + PowerShell 7 (`pwsh`)
- **JDK 21** (`javac`, `keytool`)
- **Android SDK build-tools 36** — `aapt2`, `d8`, `zipalign`, `apksigner`
  (기본 경로: `%LOCALAPPDATA%\Android\Sdk\build-tools\36.0.0`)
- 릴리스 배포 시: `gh` CLI 로그인

Gradle은 쓰지 않는다. `build.ps1`이 `aapt2 → javac → d8 → zipalign → apksigner` 순서로 직접 빌드한다.

## 빌드

```powershell
pwsh -File build.ps1                                   # 1.0.0 기본값
pwsh -File build.ps1 -VersionName 1.0.1 -VersionCode 2  # 버전 올리기
pwsh -File build.ps1 -Install                          # 빌드 + 기기 설치 + 실행
```

산출물:

| 경로 | 설명 |
| --- | --- |
| `build\calwall.apk` | 서명된 APK (항상 같은 이름) |
| `CalWall-<version>.apk` | 릴리스 첨부용 사본 |

빌드 후 확인:

```powershell
$aapt2 = "$env:LOCALAPPDATA\Android\Sdk\build-tools\36.0.0\aapt2.exe"
& $aapt2 dump badging build\calwall.apk | Select-String 'package:'

$apksigner = "$env:LOCALAPPDATA\Android\Sdk\build-tools\36.0.0\apksigner.bat"
& $apksigner verify --print-certs build\calwall.apk

adb install -r CalWall-<version>.apk   # 기존 설치 위에 덮어쓰기(데이터 유지) 확인
```

## 서명 키

- 위치: `signing\calwall.keystore` (별칭 `calwall`, 저장소에는 **커밋하지 않음** — `.gitignore`)
- **절대 공개 저장소에 올리지 않는다.** 키가 노출되면 누구든 같은 서명으로 "가짜 업데이트"를 만들어 기존 설치 위에 덮어쓸 수 있다.
- **반드시 백업**한다(암호 관리자·암호 걸린 압축·개인 클라우드). 분실하면 다음 버전을 덮어쓸 수 없고, 사용자는 삭제 후 재설치해야 한다.
- `build.ps1`이 키를 자동 생성하므로, 백업 없이 `build\`를 지우면 **새 키가 만들어져 서명이 바뀐다**(업데이트 불가). 그래서 키를 `signing\`으로 분리했다.
- 비밀번호는 현재 `build.ps1`에 `calwall`로 하드코딩되어 있다(개인용). 더 엄격하게 하려면 환경변수로 옮긴다.

## 릴리스 절차

```powershell
# 1) 버전 올려 빌드
pwsh -File build.ps1 -VersionName 1.0.1 -VersionCode 2

# 2) 변경 사항 커밋/푸시
git add -A; git commit -m "..."; git push

# 3) 릴리스 노트 작성 후 업로드
gh release create v1.0.1 CalWall-1.0.1.apk --title "CalWall 1.0.1" --notes-file notes.md
```

## 배포 후 확인

```powershell
# 로그인 없이 받을 수 있는지(공개 자산) + 무결성
$url = "https://github.com/judy1467/calwall/releases/download/v1.0.1/CalWall-1.0.1.apk"
Invoke-WebRequest $url -Method Head -UseBasicParsing          # 200 + 올바른 용량
(Get-FileHash (Invoke-WebRequest $url -OutFile "$env:TEMP\a.apk" -PassThru).FullName).Hash
```

- [ ] 릴리스가 **public** 이고 자산 다운로드가 200으로 응답
- [ ] 내려받은 APK의 SHA-256이 로컬 빌드와 일치
- [ ] `adb install -r` 로 기존 설치 위에 업그레이드되고 데이터가 유지됨
- [ ] 앱 실행 후 잠금 화면에 정상 적용

## 원칙

- **한 번 배포한 태그는 다시 쓰지 않는다.** 수정이 필요하면 버전만 올려 새 태그를 만든다.
- `versionCode`는 반드시 **증가**해야 한다(안드로이드가 업데이트로 인식).
- 서명 키를 잃으면 새로운 앱 신원이 되므로, 새 키로 배포할 때는 릴리스 노트에 "삭제 후 재설치 필요"를 명시한다.
