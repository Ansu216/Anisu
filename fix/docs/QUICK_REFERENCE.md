# Quick Reference Guide - Horizontal Player Layouts

## 🚀 Quick Start (5 Minutes)

### 1. File Placement
```
Copy these files to: app/src/main/java/com/ansu/anime/ui/player/

✅ PlayerControls.kt (REPLACE existing)
✅ PlayerSettings.kt (NEW)
✅ PlayerScreen.kt (REPLACE existing)
```

### 2. Key Classes Overview

| Class | Purpose | Status |
|-------|---------|--------|
| `SourceSelectSheet` | Horizontal source picker | READY |
| `SubtitleSettingsSheet` | Full subtitle panel | READY |
| `StreamSelectionSheet` | Multi-option stream picker | READY |
| `SourceChip` | Individual source item | READY |
| `StreamCard` | Stream info display | READY |

### 3. Import These
```kotlin
// Already in provided files - no extra imports needed!
```

---

## 🎯 Core Components Cheat Sheet

### Show Subtitle Settings
```kotlin
if (showSubtitleSheet) {
    SubtitleSettingsSheet(
        languages = listOf(
            SubtitleLanguage("en", "English"),
            SubtitleLanguage("es", "Spanish"),
            // ... more
        ),
        selectedLanguage = state.selectedSubtitleLanguage,
        onLanguageSelect = { lang -> 
            viewModel.selectSubtitle(lang)
        },
        onDismiss = { showSubtitleSheet = false },
    )
}
```

### Show Stream Selection
```kotlin
if (showStreamSheet) {
    StreamSelectionSheet(
        streams = listOf(
            StreamInfo("1080p", "Source 1", 84),
            StreamInfo("720p", "Source 1", 84),
        ),
        selectedStream = state.selectedStream,
        onStreamSelect = { stream ->
            viewModel.selectStream(stream)
        },
        onDismiss = { showStreamSheet = false },
    )
}
```

### Show Source Selection
```kotlin
if (showSourceSheet) {
    SourceSelectSheet(
        sources = state.sources,
        selected = state.selectedSource,
        onSelect = { source ->
            viewModel.selectSource(source)
            showSourceSheet = false
        },
        onDismiss = { showSourceSheet = false },
    )
}
```

---

## 📐 Size & Spacing Quick Reference

| Element | Value |
|---------|-------|
| Chip Height | 44dp |
| Card Width | ~120dp |
| Button Radius | 8-12dp |
| Padding H | 16dp |
| Padding V | 12dp |
| Gap | 8-12dp |

---

## 🎨 Colors Quick Reference

```kotlin
// Selected State
Background: AnsuColors.Accent  // Usually Blue/Purple
Text: Color.Black

// Unselected State  
Background: Color.White.copy(alpha = 0.1f)
Text: Color.White
Headers: Color.White.copy(alpha = 0.7f)
```

---

## ⚡ Common Tasks

### Add New Language
```kotlin
languages = listOf(
    SubtitleLanguage("en", "English"),
    SubtitleLanguage("pt", "Portuguese"),  // NEW
)
```

### Add New Stream
```kotlin
streams = listOf(
    StreamInfo("1080p", "Source 1", 84),
    StreamInfo("4K", "Source 2", 150),     // NEW
)
```

### Change Button Color
```kotlin
.background(
    color = if (isSelected) YOUR_COLOR else Color.White.copy(alpha = 0.1f)
)
```

### Adjust Spacing
```kotlin
horizontalArrangement = Arrangement.spacedBy(16.dp)  // Change here
```

---

## 🧠 Data Models

### SubtitleLanguage
```kotlin
data class SubtitleLanguage(
    val code: String,  // "en", "es", etc.
    val name: String   // "English", "Spanish"
)
```

### StreamInfo
```kotlin
data class StreamInfo(
    val resolution: String,  // "1080p", "720p"
    val source: String,      // "Source 1"
    val delay: Int          // milliseconds: 84
)
```

### SubtitleSize (Enum)
```kotlin
enum class SubtitleSize(val label: String) {
    SMALL("S"),
    MEDIUM("M"),
    LARGE("L")
}
```

---

## 🔧 Customization Hotspots

### Theme Color
**File**: `PlayerSettings.kt`, `PlayerControls.kt`
**Search**: `AnsuColors.Accent`
**Replace with**: Your accent color

### Border Radius
**File**: All files with `RoundedCornerShape`
**Values**: 
- Large buttons: 12.dp
- Small buttons: 8.dp
**Change to**: Any value you prefer

### Font Sizes
**File**: All files with `MaterialTheme.typography`
**Values**:
- Title: `titleMedium`
- Label: `labelMedium`
- Small: `labelSmall`

### Gaps/Spacing
**File**: All files with `spacedBy()`
**Values**: 8.dp, 12.dp, 16.dp
**Change to**: Your preferred spacing

---

## ✅ Testing Checklist

- [ ] Files placed in correct directory
- [ ] No compilation errors
- [ ] Horizontal scroll works
- [ ] Selection state updates
- [ ] Colors match app theme
- [ ] Text is readable
- [ ] Bottom sheet dismisses
- [ ] No crashes on rotation

---

## 🐛 Troubleshooting

| Issue | Solution |
|-------|----------|
| `AnsuColors` not found | Check `ui/theme/Theme.kt` imports |
| Horizontal scroll not working | Ensure `LazyRow` is inside scrollable container |
| Colors look wrong | Verify `AnsuColors.Accent` matches theme |
| Text overflow | Check `maxLines = 1` and `TextOverflow.Ellipsis` |
| Sheet not showing | Verify state variable is true and sheet called in UI tree |

---

## 📚 File Structure

```
app/src/main/java/com/ansu/anime/ui/player/
├── PlayerControls.kt      (MODIFIED)
│   └── SourceSelectSheet()
│       └── SourceChip()
│
├── PlayerSettings.kt      (NEW)
│   ├── SubtitleSettingsSheet()
│   │   ├── SubtitleLanguageChip()
│   │   ├── SubtitleSizeButton()
│   │   ├── BackgroundOption()
│   │   └── SubtitlePositionButton()
│   │
│   └── StreamSelectionSheet()
│       ├── StreamCard()
│       └── LanguageToggleButton()
│
└── PlayerScreen.kt        (MODIFIED)
    └── Integration of all sheets
```

---

## 🎬 State Management Pattern

```kotlin
// In PlayerScreen (or your Screen)
var showSubtitleSheet by remember { mutableStateOf(false) }
var showStreamSheet by remember { mutableStateOf(false) }
var showSourceSheet by remember { mutableStateOf(false) }

// When buttons are clicked
showSubtitleSheet = true  // or false to hide

// In conditional rendering
if (showSubtitleSheet) {
    SubtitleSettingsSheet(...)
}
```

---

## 🔄 Integration Workflow

```
1. Copy Files
   ├── PlayerControls.kt
   ├── PlayerSettings.kt
   └── PlayerScreen.kt

2. Verify Compilation
   └── Check for import errors

3. Add State to Screen
   ├── showSubtitleSheet
   ├── showStreamSheet
   └── showSourceSheet

4. Add Buttons to Header
   └── Update onSelectSourceClick handler

5. Connect ViewModel
   └── Add handlers to update state

6. Test UI
   └── Check layouts, scrolling, selection
```

---

## 💡 Pro Tips

1. **Keep State in ViewModel**: Use StateFlow for persistence
2. **Use Mnemonics**: `Subtitle`, `Stream`, `Source` for clarity
3. **Preload Data**: Fetch languages/streams before showing sheet
4. **Handle Loading**: Add progress indicator while loading streams
5. **Cache Selection**: Remember user's last choice
6. **Add Haptics**: Use `performHapticFeedback()` on selection
7. **Test Landscape**: Verify layouts work in all orientations

---

## 🚨 Critical Checks

```kotlin
// ✅ DO
@Composable
fun MySheet(
    items: List<MyItem>,
    selected: MyItem?,
    onSelect: (MyItem) -> Unit,  // Clear callback
    onDismiss: () -> Unit,         // Always needed
)

// ❌ DON'T  
@Composable
fun MySheet(items: List<String>)  // Missing callbacks
```

---

## 📞 Need Help?

**Common Issues → Solutions:**

1. **Bottom sheet not showing**
   - Check if `showSheet by remember { mutableStateOf(false) }` exists
   - Verify sheet is called inside UI tree
   - Ensure callback sets state to true

2. **Colors wrong**
   - Check `AnsuColors` values
   - Verify dark/light theme settings
   - Use `Color.White` for better contrast on dark backgrounds

3. **Text overflow**
   - Add `maxLines = 1`
   - Add `overflow = TextOverflow.Ellipsis`
   - Increase chip width with `Modifier.widthIn(min = 80.dp)`

4. **Selection not updating**
   - Verify ViewModel's `onSelect` callback is called
   - Check StateFlow is updated correctly
   - Ensure recomposition happens (not filtered out)

---

**Ready to integrate? Start with Step 1: File Placement! 🚀**

**Version**: 1.0  
**Last Updated**: October 3, 2026
