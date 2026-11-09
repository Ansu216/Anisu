# Horizontal Player Layouts - Visual Reference

## 📱 Full Player Screen Layout

```
┌─────────────────────────────────────────────────────┐
│                    VIDEO PLAYER                      │
│                                                       │
│                                                       │
│              (Video Content Area)                    │
│                                                       │
│                                                       │
│                                                       │
├──────────────────────────────────────────────────────┤
│  [←]  Blue Box S1E16              [≡]               │
│       Unfair Woman                                   │
├──────────────────────────────────────────────────────┤
│                                                       │
│                  [⏮] [⏯] [⏭]                        │
│                                                       │
├──────────────────────────────────────────────────────┤
│ |————————————●————————————————|                      │
│ 00:02                      25:38                     │
└─────────────────────────────────────────────────────┘
```

---

## 1️⃣ SOURCE SELECTION SHEET (Horizontal)

### Layout Structure
```
┌─────────────────────────────────────────┐
│ Select source                      [×]  │
├─────────────────────────────────────────┤
│                                         │
│  [Source 1]  [Source 2]  [Source 3]   │
│   1080p Sub   1080p Sub   720p Sub     │
│                                         │
│  [Source 4]  [Source 5]                │
│   720p Sub    480p Sub                 │
│                                         │
│                                         │
│  ← Swipe for more →                    │
│                                         │
└─────────────────────────────────────────┘
```

### Component Composition
```
SourceSelectSheet
├── Header Row
│   ├── Title: "Select source"
│   └── Close Button [×]
│
└── LazyRow (Horizontal Scrollable)
    ├── SourceChip
    │   ├── Resolution Text
    │   ├── Language Text
    │   └── Selection State
    │
    ├── SourceChip
    │   └── ...
    │
    └── (More items scrollable)
```

### Visual State Examples

#### Unselected Chip
```
┌──────────────────┐
│  1080p Sub       │
│  Source 1        │
│                  │
│  84ms            │
└──────────────────┘
Background: White (10% opacity)
Text Color: White
```

#### Selected Chip
```
┌──────────────────┐
│  1080p Sub       │  ← Accent Color
│  Source 1        │  Background
│                  │
│  84ms            │
└──────────────────┘
Background: AnsuColors.Accent
Text Color: Black
```

### Spacing Details
```
Horizontal Gap: 12.dp
│         │
└─Chip─┘  └─Chip─┘
       ↑
    Gap
```

---

## 2️⃣ SUBTITLE SETTINGS SHEET

### Overall Layout
```
┌─────────────────────────────────────────┐
│ Subtitles                          [×]  │
├─────────────────────────────────────────┤
│                                         │
│ Language                                │
│ [OFF] [EN] [ES] [DE] [FR] [JA] →      │
│                                         │
│ Style                                   │
│ [S] [M] [L]                            │
│                                         │
│ Background                              │
│ [None] [Shadow] [Box]                  │
│                                         │
│ Position                                │
│ [Low] [High]                           │
│                                         │
│ Delay - In sync                        │
│                                         │
│ [-]  |————●————|  [+]                  │
│           0.0s                         │
│                                         │
└─────────────────────────────────────────┘
```

### Section Breakdown

#### Language Section
```
Header: "Language" (Gray, small text)

Chips (Horizontal Scrollable):
┌────────────────────────────────────────┐
│                                        │
│ ┌────────┐ ┌────────┐ ┌────────┐     │
│ │ OFF    │ │ English│ │Spanish │...  │
│ └────────┘ └────────┘ └────────┘     │
│                                        │
│ Radio Selection: Only one active       │
│ Width: Flexible within LazyRow         │
│                                        │
└────────────────────────────────────────┘
```

#### Style Section (Size)
```
Header: "Style"

Three Equal Buttons:
┌────────────────────────────────────────┐
│                                        │
│ [S]           [M]           [L]        │
│ Small         Medium        Large      │
│                                        │
│ Each: 1/3 width - 8.dp gap             │
│                                        │
└────────────────────────────────────────┘
```

#### Background Section
```
Header: "Background"

Three Equal Buttons:
┌────────────────────────────────────────┐
│                                        │
│ [None]        [Shadow]        [Box]    │
│                                        │
│ Each: 1/3 width - 8.dp gap             │
│                                        │
└────────────────────────────────────────┘
```

#### Position Section
```
Header: "Position"

Two Equal Buttons:
┌────────────────────────────────────────┐
│                                        │
│   [Low]              [High]            │
│   Bottom             Middle            │
│                                        │
│   Each: 1/2 width - 8.dp gap           │
│                                        │
└────────────────────────────────────────┘
```

#### Delay Section
```
Header: "Delay - In sync"

Icon Button + Slider + Icon Button:
┌────────────────────────────────────────┐
│                                        │
│ [-]  |———●———|  [+]                   │
│       -1s  0s  +1s                     │
│                                        │
│ Range: -1000ms to +1000ms              │
│ Display: "0.0s" centered               │
│                                        │
└────────────────────────────────────────┘
```

### Color Coding
- **Unselected**: White (10% opacity) background
- **Selected**: Accent color background
- **Text (Selected)**: Black
- **Text (Unselected)**: White
- **Headers**: White (70% opacity)

---

## 3️⃣ STREAM SELECTION SHEET

### Layout Structure
```
┌─────────────────────────────────────────┐
│ Select Stream                      [×]  │
├─────────────────────────────────────────┤
│                                         │
│ [Sub]             [Dub]                │
│                                         │
│ Streams                                 │
│                                         │
│  ┌─────────┐  ┌─────────┐  ┌─────────┐│
│  │ 1080p   │  │  720p   │  │ 1080p   ││
│  │Source 1 │  │Source 1 │  │Source 2 ││
│  │  84ms   │  │  84ms   │  │ 122ms   ││
│  └─────────┘  └─────────┘  └─────────┘│
│                                         │
│  ┌─────────┐                           │
│  │  720p   │  ...                      │
│  │Source 3 │                           │
│  │ 310ms   │                           │
│  └─────────┘                           │
│                                         │
│  ← Swipe for more →                    │
│                                         │
└─────────────────────────────────────────┘
```

### Component Structure
```
StreamSelectionSheet
├── Header Row
│   ├── Title: "Select Stream"
│   └── Close Button [×]
│
├── Language Toggle Row
│   ├── LanguageToggleButton "Sub"
│   └── LanguageToggleButton "Dub"
│
├── Section Header
│   └── Text: "Streams"
│
└── LazyRow (Horizontal Scrollable)
    ├── StreamCard
    │   ├── Resolution (1080p)
    │   ├── Source (Source 1)
    │   └── Delay (84ms)
    │
    ├── StreamCard
    │   └── ...
    │
    └── (Scrollable horizontally)
```

### Stream Card Details
```
┌───────────────────┐
│                   │
│     1080p         │  ← Large, Bold
│   Source 1        │  ← Medium, Small
│     84ms          │  ← Small, Smallest
│                   │
│                   │
└───────────────────┘
```

#### Unselected Card
```
Background: Color.White.copy(alpha = 0.1f)
Text Color: White
BorderRadius: 12.dp
Padding: 16.dp
```

#### Selected Card
```
Background: AnsuColors.Accent
Text Color: Black
BorderRadius: 12.dp
Padding: 16.dp (with highlight effect)
```

### Spacing Metrics
```
Top padding: 8.dp
Bottom padding: 24.dp
Horizontal padding: 16.dp
Cards gap: 12.dp
Content padding: 8.dp (internal)
```

---

## 4️⃣ RESPONSIVE BEHAVIOR

### Landscape Mode (Wide Screen)
```
┌───────────────────────────────────────────────────┐
│                                                   │
│              VIDEO PLAYER (16:9)                  │
│                                                   │
├───────────────┬───────────────────────────────────┤
│               │                                   │
│   Controls    │   Settings Panel                  │
│   (Vertical)  │   (Responsive Chips)             │
│               │                                   │
└───────────────┴───────────────────────────────────┘
```

### Portrait Mode (Narrow Screen)
```
┌──────────────────────┐
│  VIDEO (Full Width)  │
│                      │
├──────────────────────┤
│  Controls (Horizontal)
│                      │
├──────────────────────┤
│ Settings Sheets      │
│ (Bottom Sheet Full)  │
│                      │
└──────────────────────┘
```

---

## 5️⃣ TOUCH INTERACTION STATES

### Button States
```
Normal State:
┌──────────────┐
│ [OFF]        │
│              │
└──────────────┘

Pressed/Focused State:
┌──────────────┐
│ [OFF]   ╔════╗   ← Ripple effect
│        ╚════╝
└──────────────┘

Selected State:
┌──────────────┐
│ [OFF] ← Accent background
│ Black text   │
└──────────────┘
```

### Scroll Behavior
```
Horizontal Scroll Indicator:
┌─────────────────────────────────┐
│ ◄  [Item] [Item] [Item]  ▶      │
│    ↑ Visible items ↑             │
│                                  │
│ Auto-scroll when reaching edge   │
└─────────────────────────────────┘

Momentum Scrolling:
- iOS style smooth scrolling
- Snap to item on iOS
- Fling gesture support
```

---

## 6️⃣ ANIMATION TIMELINE

### Sheet Opening
```
Time: 0ms      300ms      500ms
│              │          │
Sheet closed   ──────► Sliding up
↓
└──────────────► Fully visible
                Fade in content
```

### Chip Selection
```
State Change: Unselected → Selected

Color:    White(10%) ──→ Accent
          (200ms ease-in-out)

Text:     White ──→ Black
          (200ms ease-in-out)

Scale:    1.0 ──→ 1.02 ──→ 1.0
          (100ms ease-out, bounce)
```

---

## 7️⃣ TYPOGRAPHY HIERARCHY

```
Title
└─ "Select source"
   Style: titleMedium
   Color: White
   Size: ~16sp
   Weight: Medium

Section Header
└─ "Language"
   Style: labelMedium
   Color: White (70% opacity)
   Size: ~12sp
   Weight: Medium

Chip Label
└─ "English"
   Style: labelSmall
   Color: White or Black (selected)
   Size: ~11sp
   Weight: Regular

Small Details
└─ "84ms"
   Style: labelSmall
   Color: White (70% opacity)
   Size: ~10sp
   Weight: Regular
```

---

## 8️⃣ EXAMPLE DATA MODELS

### Subtitle Languages
```
SubtitleLanguage("en", "English")
SubtitleLanguage("es", "Spanish")
SubtitleLanguage("de", "German")
SubtitleLanguage("fr", "French")
SubtitleLanguage("pt", "Portuguese")
SubtitleLanguage("ja", "Japanese")
SubtitleLanguage("ko", "Korean")
SubtitleLanguage("zh", "Chinese")
```

### Stream Examples
```
StreamInfo("1080p", "Source 1", 84ms)
StreamInfo("720p", "Source 1", 84ms)
StreamInfo("1080p", "Source 2", 122ms)
StreamInfo("720p", "Source 3", 310ms)
StreamInfo("480p", "Source 3", 245ms)
```

### Subtitle Sizes
```
SubtitleSize.SMALL (S)
SubtitleSize.MEDIUM (M)  ← Default
SubtitleSize.LARGE (L)
```

---

## ✨ Polish Details

### Shadows
```
Bottom Sheet: elevation = 16.dp
Buttons: elevation = 4.dp (when pressed)
Cards: elevation = 8.dp
```

### Rounded Corners
```
Chips: 12.dp
Cards: 12.dp
Buttons: 8.dp
Sheet: Top 28.dp, bottom 0.dp
```

### Haptic Feedback
```
On tap: Light haptic
On selection: Medium haptic
On sheet dismiss: Soft haptic
```

---

**Version**: 1.0  
**Last Updated**: October 3, 2026
