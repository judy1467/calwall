# 아키텍처

CalWall(`dev.dsh.calwall`)은 **잠금 화면 배경화면을 매일 다시 그리는** 안드로이드 앱이다.
서버·계정·외부 라이브러리가 없고, 화면 렌더링은 전부 `android.graphics` 캔버스로 한다.

- 패키지: `dev.dsh.calwall`
- minSdk 31 (Android 12) / targetSdk 36
- APK 약 45KB (dex 1개)

---

## 1. 구성 요소

| 파일 | 역할 |
| --- | --- |
| `MainActivity` | 설정 화면(프리뷰·슬라이더·색·사진·적용 버튼). 모든 설정은 여기서 `Prefs`에 저장 |
| `PreviewView` | 프리뷰 위에서 캘린더 블록을 **이동/가로·세로 크기 조절** (가이드 사각형) |
| `PhotoCropView` | 사진 **영역(크롭)** 지정 — 드래그 이동, 핀치·버튼 확대 |
| `ColorWheelView` / `ValueBarView` / `ColorPickerDialog` | HSV 색상 휠 + 밝기 바 + 헥스/RGB 입력 |
| `WallpaperRenderer` | 실제 배경화면 비트맵을 그리는 순수 드로잉 코드 |
| `Applier` | 렌더 → `WallpaperManager` 적용, 프리뷰 PNG 저장 |
| `Scheduler` | 00:05 정확 알람 + 6시간 주기 잡 등록/취소 |
| `AlarmRx` / `BootRx` / `TickJob` | 트리거 수신 후 백그라운드에서 다시 그리기 |
| `CalendarSource` | `CalendarContract.Instances` 조회 → 일정 목록 |
| `Prefs` | SharedPreferences 래퍼(모든 설정·상태 키) |

---

## 2. 갱신 파이프라인

```
AlarmManager (매일 00:05, exactAllowWhileIdle)
JobScheduler (6시간 주기, persisted)        ──> 재렌더 + FLAG_LOCK 적용
BOOT_COMPLETED / MY_PACKAGE_REPLACED /
TIME_SET / TIMEZONE_CHANGED / DATE_CHANGED
```

- **정확 알람**(`USE_EXACT_ALARM`)이 주 경로다. 00:05에 깨어나 렌더 후 잠금 화면에 적용하고, **다음 날 알람을 스스로 다시 건다**(`Scheduler.scheduleDaily`).
- **6시간 주기 잡**(`setPersisted(true)`)은 보조 경로다. 알람이 배터리 최적화·강제종료로 누락되면 "오늘 아직 적용 안 됨"(`Prefs.staleToday`)을 판단해 재적용한다. persisted 잡이라 **재부팅 후에도 살아 있다**.
- `BootRx`는 부팅·앱 업데이트·시간 변경 시 알람을 재등록하고, 오늘이 아직 갱신 전이면 즉시 한 번 적용한다.
- 리시버는 `goAsync()` + `PARTIAL_WAKE_LOCK`으로 렌더링(수백 ms)을 마칠 시간을 확보한다.

> 초기 설계에서 참고한 Layered(중단된 앱)는 헤드리스 JS 서비스를 `startService()`로 띄우다 Android 8+의 백그라운드 서비스 제한에 매번 걸렸다. CalWall은 **서비스 기동 자체가 필요 없는 구조**라 같은 문제가 없다.

---

## 3. 배경화면 캔버스 모델

시스템은 배경화면을 자체 버퍼로 보관한다(실측: Z Fold8에서 **2448×2448**) 그리고 화면에는 그 버퍼의 **중앙 세로 슬라이스**만 보여준다(1248×1972 화면 기준 가로 약 63%).

따라서 렌더러는:

1. `WallpaperManager.getDesiredMinimumWidth/Height()`로 **캔버스 크기**를 얻는다(없으면 화면의 2배, 최대 3072).
2. 캔버스 폭·높이 중 화면 비율(`screenW/screenH`)만큼의 **세로 컬럼**을 잡고 그 안에 내용을 배치한다.
3. 컬럼 바깥은 배경(사진/그라데이션)이 채운다.

이 모델을 쓰지 않으면 사진·캘린더가 2배 확대되어 잘리는 문제가 생긴다(초기 구현에서 실제로 발생).

---

## 4. 레이아웃과 사용자 변형

캘린더는 **카드 사각형** 하나로 정의된다(기본: `x 5.5~94.5%`, `y 32~93%`).

- 카드 안: 요일 헤더 → 4주(주차별 날짜 행 + 일정 레인) 순.
- 사용자 조작은 **카드 사각형 변형**으로만 반영된다:
  - `scaleX`: 카드 폭(칸 간격) — **글자 크기는 그대로** → 칸이 넓어져 제목이 더 보인다
  - `scaleY`: 카드 높이(주 간 여백)
  - `dx/dy`: 카드 이동
- 프리뷰(`PreviewView`)는 렌더러와 **같은 수식**으로 가이드 사각형을 그린다. 그래야 "보이는 그대로" 적용된다.

---

## 5. 일정 데이터 처리

`CalendarContract.Instances`를 `[주 시작, +4주]` 범위로 조회한다.

**종일 일정의 날짜 버그(수정됨)** — 종일 일정은 `eventTimezone=UTC`로 저장되고 `dtstart`는 UTC 자정, `dtend`는 **다음 날 자정(배타적)** 이다. 이를 현지 시간(KST)으로 변환하면 종료일이 하루 밀려 모든 종일 일정이 이틀짜리로 그려졌다.

```java
static long dayOf(long millis, boolean allDay) {
    long offset = allDay ? 0L : TimeZone.getDefault().getOffset(millis);
    return Math.floorDiv(millis + offset, 86400000L);
}
```

- 시작일 = `dayOf(begin)`, 종료일 = `dayOf(end - 1)`
- 주차 컬럼 = `clamp(day - weekStartDay, 0, 6)`

렌더링 규칙:

- 한 주에 최대 **3레인**까지 배치(겹치면 다음 레인)
- 막대 배경은 일정 색(캘린더 색)의 옅은 톤, 글자는 라이트 테마에서 검정
- **제목은 막대 폭 안에서만** 그린다(넘치면 말줄임) — 옆 칸까지 그리면 하루짜리 일정이 여러 날처럼 보이기 때문
- 2칸 이상 막대에만 색 점(칩)을 그린다(1칸 막대는 칩 없이 글자 공간 확보)

---

## 6. 사진 배경 / 크롭

- 사진은 `ACTION_OPEN_DOCUMENT`로 고르고 `takePersistableUriPermission`으로 권한을 보존한다.
- 렌더 시 **cover**(비율 유지, 넘치는 부분 잘림)로 배치하고, 사용자 값 `photoZoom(100~400%)`, `photoDx/Dy(-100~100)`로 위치를 조정한다.
- 크롭 다이얼로그의 프레임은 **잠금 화면에 실제로 보이는 영역**과 같은 비율이라, 프레임에 보이는 구도가 그대로 적용된다.
- 홈 화면용(`선택한 사진을 홈 화면에 적용`)은 **같은 크롭 값**으로 사진만 `FLAG_SYSTEM`에 넣는다 → 잠금 해제 시 같은 사진이 이어진다.

---

## 7. 설정 키 (SharedPreferences `calwall`)

| 키 | 의미 | 기본값 |
| --- | --- | --- |
| `dark` | 테마 | false(라이트) |
| `accent` | 강조색 인덱스(블루/코랄/민트) | 0 |
| `auto` | 자동 갱신 사용 | true |
| `cardAlpha` / `contentAlpha` | 카드 배경 / 내용 투명도(%) | 94 / 100 |
| `scaleX` / `scaleY` / `dx` / `dy` | 캘린더 변형 | 100 / 100 / 0 / 0 |
| `photoUri` | 선택한 사진 URI | "" |
| `photoZoom` / `photoDx` / `photoDy` | 사진 크롭 | 100 / 0 / 0 |
| `cWeekday` / `cDate` / `cEvent` | 글자 색(0 = 자동) | 0 |
| `lastApplyDay` | 마지막 적용 날짜(연*1000+일) | 0 |

---

## 8. 알려진 한계

- 화면 비율·월페이퍼 버퍼 크기는 기기마다 다르다. 지금 모델은 "중앙 세로 슬라이스"를 가정하므로, 가로 화면 위주 기기에서는 여백이 생길 수 있다.
- 일정 알림·수정은 잠금 화면에 즉시 반영되지 않는다(다음 갱신 = 00:05 또는 6시간 주기). 앱을 열어 `잠금 화면에 적용`을 누르면 즉시 반영된다.
- 사진 URI는 MediaStore 항목이라, 원본 사진을 삭제하면 다음 렌더에서 사진 없이(그라데이션) 그려질 수 있다.
- 앱 서명 키를 잃으면 같은 앱으로 업데이트할 수 없다(재설치 필요).

---

## 9. 검증 체크리스트 (실기기)

- [ ] 앱이 백그라운드일 때 알람 → 렌더 → 잠금 화면 반영
- [ ] `dumpsys alarm | grep dev.dsh.calwall.ALARM` 에 00:05 항목 존재
- [ ] 재부팅 후 알람·잡 재등록, 오늘 미적용이면 즉시 적용
- [ ] 종일 일정이 정확히 하루 칸에만 표시(주 경계 포함)
- [ ] 프리뷰 가이드와 실제 렌더의 위치·크기 일치
- [ ] `adb install -r` 로 덮어쓰기 시 데이터 유지
