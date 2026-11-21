# Horizontal Player Layout Implementation Guide

## Overview
This guide covers the implementation of horizontal player layouts for source selection, subtitle settings, and stream selection in your anime player application. The design is inspired by modern streaming apps like Netflix and PenguPlay.

---

## 📋 Files Modified/Created

### 1. **PlayerControls.kt** (MODIFIED)
Enhanced with horizontal chip-based source selection.

#### Key Changes:
- Added `LazyRow` for horizontal scrollable sources
- Created `SourceChip` composable for individual source items
- Removed vertical `LazyColumn` list view
- Added close button to header

#### New Composables:
```kotlin
@Composable
fun SourceChip(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
)
```

**Features:**
- Animated selection with accent color
- Rounded corners (12dp)
- Responsive text truncation
- Touch-friendly sizing

---

### 2. **PlayerSettings.kt** (NEW FILE)
Complete subtitle and stream selection UI with multiple settings.

#### Features:

##### A. SubtitleSettingsSheet
```kotlin
@Composable
fun SubtitleSettingsSheet(
    languages: List<SubtitleLanguage>,
    selectedLanguage: SubtitleLanguage?,
    subtitleSize: SubtitleSize = SubtitleSize.MEDIUM,
    onLanguageSelect: (SubtitleLanguage) -> Unit,
    onSizeChange: (SubtitleSize) -> Unit,
    onDismiss: () -> Unit,
)
```

**Sections:**
1. **Language Selection** - Horizontal scrollable language chips
   - English, Spanish, German, French, Japanese
   - OFF option to disable subtitles
   
2. **Style** - Size selection
   - Small (S), Medium (M), Large (L)
   - Horizontal button layout
   
3. **Background** - Background styling
   - None, Shadow, Box options
   - Affects subtitle visibility
   
4. **Position** - Subtitle placement
   - Low (near bottom)
   - High (near middle)
   
5. **Delay** - Synchronization
   - Range: -1000ms to +1000ms
   - Plus/Minus buttons with slider
   - Real-time sync adjustment

##### B. StreamSelectionSheet
```kotlin
@Composable
fun StreamSelectionSheet(
    streams: List<StreamInfo>,
    selectedStream: StreamInfo?,
    onStreamSelect: (StreamInfo) -> Unit,
    onDismiss: () -> Unit,
)
```

**Features:**
- Language toggle (Sub/Dub)
- Resolution display (1080p, 720p)
- Source identification
- Delay information in milliseconds
- Horizontal scrollable cards

##### C. StreamCard Component
```kotlin
@Composable
fun StreamCard(
    resolution: String,
    source: String,
    delay: Int,
    isSelected: Boolean,
    onClick: () -> Unit,
)
```

**Design:**
- 3-line card layout:
  - Resolution (bold, large)
  - Source name (smaller)
  - Delay in milliseconds
- Selection state with accent color
- Compact, card-based design

---

### 3. **PlayerScreen.kt** (MODIFIED)
Updated to manage new sheets and state.

#### Changes:
- Added `showSubtitleSheet` state
- Added `showStreamSheet` state
- Integrated subtitle and stream sheet displays
- Added example data for streams

#### State Management:
```kotlin
var showSourceSheet by remember { mutableStateOf(false) }
var showSubtitleSheet by remember { mutableStateOf(false) }
var showStreamSheet by remember { mutableStateOf(false) }
```

---

## 🎨 UI Components Reference

### Horizontal Layouts

#### 1. Source Selection (Bottom Sheet)
```
┌─────────────────────────────┐
│ Select source          [×]  │
├─────────────────────────────┤
│ ┌──────┐ ┌──────┐ ┌──────┐ │
│ │1080p │ │720p  │ │480p  │ │
│ │Sub   │ │Sub   │ │Sub   │ │
│ └──────┘ └──────┘ └──────┘ │
└─────────────────────────────┘
```

#### 2. Subtitle Settings (Bottom Sheet)
```
┌─────────────────────────────┐
│ Subtitles              [×]  │
├─────────────────────────────┤
│ Language                    │
│ [OFF] [EN] [ES] [DE] [FR]   │
│                             │
│ Style                       │
│ [S] [M] [L]                 │
│                             │
│ Background                  │
│ [None] [Shadow] [Box]       │
│                             │
│ Position                    │
│ [Low] [High]                │
│                             │
│ Delay - In sync             │
│ [-] [====|====] [+]         │
│        0.0s                 │
└─────────────────────────────┘
```

#### 3. Stream Selection (Bottom Sheet)
```
┌─────────────────────────────┐
│ Select Stream          [×]  │
├─────────────────────────────┤
│ [Sub] [Dub]                 │
│                             │
│ Streams                     │
│ ┌──────┐ ┌──────┐ ┌──────┐ │
│ │1080p │ │720p  │ │1080p │ │
│ │Src 1 │ │Src 1 │ │Src 2 │ │
│ │84ms  │ │84ms  │ │122ms │ │
│ └──────┘ └──────┘ └──────┘ │
└─────────────────────────────┘
```

---

## 🎯 Color Scheme

| Element | Selected | Unselected |
|---------|----------|-----------|
| Chips | `AnsuColors.Accent` | `Color.White.copy(alpha = 0.1f)` |
| Text (Selected) | `Color.Black` | `Color.White` |
| Text (Unselected) | - | `Color.White.copy(alpha = 0.7f)` |
| Background | Accent | White (10% opacity) |
| Dividers | - | White (20% opacity) |

---

## 📱 Responsive Design

### Typography
- **Titles**: `MaterialTheme.typography.titleMedium`
- **Labels**: `MaterialTheme.typography.labelMedium`
- **Small Text**: `MaterialTheme.typography.labelSmall`

### Spacing
- **Horizontal Padding**: 16.dp
- **Vertical Padding**: 12.dp
- **Item Spacing**: 8-12.dp
- **Card Padding**: 16.dp

### Touch Targets
- **Minimum Height**: 48.dp
- **Icon Buttons**: 32-36.dp
- **Chips**: 44.dp min height

---

## 🔗 Integration Steps

### Step 1: Replace Files
```bash
# Copy to your project
PlayerControls.kt → app/src/main/java/com/ansu/anime/ui/player/
PlayerSettings.kt → app/src/main/java/com/ansu/anime/ui/player/
PlayerScreen.kt → app/src/main/java/com/ansu/anime/ui/player/
```

### Step 2: Update ViewModel
Add state for:
```kotlin
// In PlayerViewModel or PlayerUiState
val subtitleLanguages: List<SubtitleLanguage>
val selectedSubtitleLanguage: SubtitleLanguage?
val subtitleSize: SubtitleSize
val streams: List<StreamInfo>
val selectedStream: StreamInfo?
```

### Step 3: Connect Event Handlers
```kotlin
// In PlayerScreen
SubtitleSettingsSheet(
    languages = state.subtitleLanguages,
    selectedLanguage = state.selectedSubtitleLanguage,
    onLanguageSelect = { language ->
        viewModel.selectSubtitleLanguage(language)
    },
    onSizeChange = { size ->
        viewModel.setSubtitleSize(size)
    },
    onDismiss = { showSubtitleSheet = false },
)
```

### Step 4: Update PlayerControls Header
Add buttons to show sheets:
```kotlin
// In PlayerControlsOverlay, add to header Row:
IconButton(onClick = { /* show subtitle sheet */ }) {
    Icon(Icons.Filled.Subtitles, contentDescription = "Subtitles")
}
IconButton(onClick = { /* show stream sheet */ }) {
    Icon(Icons.Filled.MoreVert, contentDescription = "More options")
}
```

---

## 🛠️ Customization

### Change Accent Color
```kotlin
// In your theme or directly in components
color = if (isSelected) CustomColors.Primary else Color.White.copy(alpha = 0.1f)
```

### Adjust Spacing
```kotlin
// All spacing constants in individual composables
horizontalArrangement = Arrangement.spacedBy(16.dp) // Change from 12.dp
```

### Modify Rounded Corners
```kotlin
// Shape in background modifier
shape = RoundedCornerShape(16.dp) // Change from 12.dp
```

### Add More Languages
```kotlin
languages = listOf(
    SubtitleLanguage("en", "English"),
    SubtitleLanguage("es", "Spanish"),
    SubtitleLanguage("pt", "Portuguese"), // Add new
    // ... more languages
)
```

---

## 🎬 Animation Enhancements (Optional)

Add smooth transitions:
```kotlin
// Add to imports
import androidx.compose.animation.animateColorAsState

// In SourceChip
val backgroundColor by animateColorAsState(
    targetValue = if (isSelected) AnsuColors.Accent else Color.White.copy(alpha = 0.1f)
)

Box(
    modifier = Modifier
        .background(color = backgroundColor, shape = RoundedCornerShape(12.dp))
        // ... rest of modifiers
)
```

---

## 🧪 Testing Checklist

- [ ] Horizontal scroll works on all resolutions
- [ ] Selection state updates correctly
- [ ] Touch feedback visible on selection
- [ ] No text overflow issues
- [ ] Sheet dismissal works smoothly
- [ ] Back button hides sheets properly
- [ ] Bottom sheet is dismissible by swiping down
- [ ] All icons display correctly
- [ ] Color contrast meets accessibility standards

---

## 📊 Data Models

### SubtitleLanguage
```kotlin
data class SubtitleLanguage(
    val code: String,      // ISO 639-1 code
    val name: String,      // Display name
)
```

### StreamInfo
```kotlin
data class StreamInfo(
    val resolution: String,  // "1080p", "720p", etc.
    val source: String,      // Source identifier
    val delay: Int,          // Milliseconds
)
```

### SubtitleSize
```kotlin
enum class SubtitleSize(val label: String) {
    SMALL("S"),
    MEDIUM("M"),
    LARGE("L"),
}
```

---

## 💡 Best Practices

1. **State Management**: Keep all player settings in ViewModel, not Composables
2. **Performance**: Use `remember` for expensive calculations
3. **Accessibility**: Ensure all interactive elements have proper descriptions
4. **User Experience**: Disable sheet dismissal while loading
5. **Error Handling**: Show errors in dedicated UI, not as crashes
6. **Offline Support**: Cache available sources/streams locally

---

## 🔄 Future Enhancements

- [ ] Add audio track selection
- [ ] Implement gesture controls
- [ ] Add playback speed settings
- [ ] Video quality auto-adjustment
- [ ] Subtitle sync verification
- [ ] Batch source testing
- [ ] Custom shortcut buttons
- [ ] Remember user preferences

---

## 📞 Support

For issues or questions:
1. Check the implementation against the provided sketches
2. Verify all imports are correct
3. Ensure `AnsuColors` theme is properly set up
4. Test on multiple screen sizes

---

**Version**: 1.0  
**Last Updated**: October 3, 2026  
**Status**: Ready for Integration
