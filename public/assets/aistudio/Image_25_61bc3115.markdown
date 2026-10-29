---
name: Nocturne Sound Glass
colors:
  surface: '#101417'
  surface-dim: '#101417'
  surface-bright: '#363a3d'
  surface-container-lowest: '#0b0f12'
  surface-container-low: '#181c1f'
  surface-container: '#1c2023'
  surface-container-high: '#262a2e'
  surface-container-highest: '#313539'
  on-surface: '#e0e3e7'
  on-surface-variant: '#bbcbb8'
  inverse-surface: '#e0e3e7'
  inverse-on-surface: '#2d3134'
  outline: '#859583'
  outline-variant: '#3c4a3c'
  surface-tint: '#29e368'
  primary: '#4efd7d'
  on-primary: '#003913'
  primary-container: '#20df64'
  on-primary-container: '#005d24'
  inverse-primary: '#006e2c'
  secondary: '#d0bcff'
  on-secondary: '#3c0091'
  secondary-container: '#571bc1'
  on-secondary-container: '#c4abff'
  tertiary: '#9eeaff'
  on-tertiary: '#003640'
  tertiary-container: '#46d3f2'
  on-tertiary-container: '#005868'
  error: '#ffb4ab'
  on-error: '#690005'
  error-container: '#93000a'
  on-error-container: '#ffdad6'
  primary-fixed: '#68ff89'
  primary-fixed-dim: '#29e368'
  on-primary-fixed: '#002108'
  on-primary-fixed-variant: '#00531f'
  secondary-fixed: '#e9ddff'
  secondary-fixed-dim: '#d0bcff'
  on-secondary-fixed: '#23005c'
  on-secondary-fixed-variant: '#5516be'
  tertiary-fixed: '#acedff'
  tertiary-fixed-dim: '#4cd7f6'
  on-tertiary-fixed: '#001f26'
  on-tertiary-fixed-variant: '#004e5c'
  background: '#101417'
  on-background: '#e0e3e7'
  surface-variant: '#313539'
typography:
  display-lg:
    fontFamily: Plus Jakarta Sans
    fontSize: 40px
    fontWeight: '800'
    lineHeight: 48px
    letterSpacing: -0.02em
  display-lg-mobile:
    fontFamily: Plus Jakarta Sans
    fontSize: 32px
    fontWeight: '800'
    lineHeight: 40px
    letterSpacing: -0.02em
  headline-lg:
    fontFamily: Plus Jakarta Sans
    fontSize: 28px
    fontWeight: '700'
    lineHeight: 36px
    letterSpacing: -0.015em
  headline-md:
    fontFamily: Plus Jakarta Sans
    fontSize: 22px
    fontWeight: '700'
    lineHeight: 28px
    letterSpacing: -0.01em
  headline-sm:
    fontFamily: Plus Jakarta Sans
    fontSize: 18px
    fontWeight: '600'
    lineHeight: 24px
    letterSpacing: 0em
  body-lg:
    fontFamily: Inter
    fontSize: 16px
    fontWeight: '400'
    lineHeight: 24px
    letterSpacing: -0.01em
  body-md:
    fontFamily: Inter
    fontSize: 14px
    fontWeight: '400'
    lineHeight: 20px
    letterSpacing: -0.005em
  body-sm:
    fontFamily: Inter
    fontSize: 12px
    fontWeight: '400'
    lineHeight: 16px
    letterSpacing: 0em
  label-lg:
    fontFamily: Plus Jakarta Sans
    fontSize: 14px
    fontWeight: '600'
    lineHeight: 20px
    letterSpacing: 0.01em
  label-md:
    fontFamily: Plus Jakarta Sans
    fontSize: 12px
    fontWeight: '600'
    lineHeight: 16px
    letterSpacing: 0.02em
  label-sm:
    fontFamily: Plus Jakarta Sans
    fontSize: 10px
    fontWeight: '700'
    lineHeight: 14px
    letterSpacing: 0.04em
rounded:
  sm: 0.25rem
  DEFAULT: 0.5rem
  md: 0.75rem
  lg: 1rem
  xl: 1.5rem
  full: 9999px
spacing:
  gutter: 1rem
  gutter-tablet: 1.5rem
  margin: 1rem
  margin-tablet: 2rem
  space-xs: 0.25rem
  space-sm: 0.5rem
  space-md: 1rem
  space-lg: 1.5rem
  space-xl: 2rem
---

## Brand & Style

The design system establishes a high-fidelity, nocturnal audio environment engineered specifically for premium Android music and podcast streaming. The visual atmosphere unites the tactile precision of contemporary Material Design 3 surface management with the immersive, media-forward presence of editorial audio applications.

### Personality & Audience
- **Tone:** Immersive, focused, acoustic, and effortless.
- **Target Audience:** Modern families, audiophiles, and daily podcast listeners who require seamless profile switching, individual listening privacy, and collaborative family queues without visual clutter.
- **Emotional Impact:** The UI recedes into the background, allowing album artwork, artist portraits, and waveform data to anchor the experience, while interactive accents trigger crisp, tactile confirmation.

### Visual Style
A hybrid of **Refined Dark Mode Minimalism** and **Targeted Glassmorphism**. The visual hierarchy relies on deep obsidian depths layered with translucent charcoal tints, micro-borders (1px with 8-12% white alpha), and high-energy electric neon mint accents. Gradients are restrained to spectral ambient glows behind active album art and interactive scrubbers.

## Colors

The palette is tuned for high-contrast accessibility (WCAG AA/AAA compliance) across dark OLED screens, minimizing battery consumption and eye fatigue during prolonged listening sessions.

### Semantic Palette Breakdown
- **Primary (`#20DF64` / `#1DB954`):** Electric Neon Mint. Reserved for active states, playback execution, scrubbers, toggle switches, and verified audio badges. `#20DF64` functions as the high-contrast foreground interactive token; `#1DB954` anchors resting state branding and deeper fills.
- **Secondary (`#8B5CF6`):** Acoustic Violet. Directs family-oriented metadata, multi-profile switches, blended playlists, and podcast chapter markers.
- **Tertiary (`#06B6D4`):** Warm Cyan. Applied to collaborative sessions, lossless/Hi-Res spatial audio indicators, and real-time audio casting (Chromecast/Android Auto).
- **Surfaces & Neutrals:**
  - `surface-canvas`: `#0B0F12` (Pitch Black canvas)
  - `surface-container-low`: `#12181F` (Base cards, list panels)
  - `surface-container-high`: `#1A222C` (Elevated modals, persistent bottom player, floating sheets)
  - `surface-container-highest`: `#242E3B` (Interactive hover/pressed states, pill chips)
  - `text-primary`: `#F8FAFC` (Pure optical legibility, 98% opacity)
  - `text-secondary`: `#94A3B8` (Artist credits, durations, metadata)
  - `text-tertiary`: `#64748B` (Inactive icons, timestamps)
  - `border-subtle`: `rgba(255, 255, 255, 0.08)` (1px structural definition)

## Typography

Typography prioritizes multilingual fidelity, specifically accommodating accented and extended character sets such as Turkish glyphs (`ğ, ü, ş, ı, ö, ç, İ, Ğ, Ş`) without vertical metrics clipping or diacritic collisions.

### Role Allocation
- **Plus Jakarta Sans (Headlines, Display, Labels):** Provides geometric confidence and rounded terminal curves that match the hardware radii of modern flagship Android screens.
- **Inter (Body, Captions, Numeric Metadata):** Delivers clean text rendering for dense tracklists, real-time synced lyrics, and podcast show notes. Inter's tabular figures (`tnum`) must be forced for song durations, scrub timers, and bit-rate counters.

## Layout & Spacing

The layout is built on a strict **8pt base grid with a 4pt micro-subgrid** for component-internal alignments (e.g., icon-to-label spacing, badge padding).

### Layout System
- **Mobile Handset (0–599dp):** Single fluid content column with dynamic vertical stacking. Content padding is locked to `16px` (`margin`). Grid tracks for horizontal scroll trays (albums, podcasts) expose `20%` of the off-screen item to signal horizontal scroll affordance.
- **Large Handset & Foldables (600–839dp):** Two-column split layout. Navigation rail replaces bottom navigation; media playback splits to a pinned right-rail player while browse views stay active.
- **Tablet & Large Screens (840dp+):** Multi-column responsive layout (up to 12 columns, `gutter-tablet` at `24px`). Album artwork anchors the upper hero quadrant; tracklists span a 7-column group with companion context (lyrics, credits, family presence) on the remaining 5 columns.

### Safe Areas & Bottom Insets
- All primary scroll views must implement dynamic bottom padding: `calc(56px + 64px + env(safe-area-inset-bottom) + 16px)` to avoid occlusion by the persistent mini-player and bottom navigation bar.

## Elevation & Depth

Visual hierarchy uses tonal surface layering and localized frosted glass (blur: 20px–32px) rather than harsh drop shadows, preserving pitch black contrast.

### Elevation Hierarchy
1. **Level 0 (Base Canvas - `#0B0F12`):** Primary background behind scroll views and page scaffolding.
2. **Level 1 (Sub-surfaces - `#12181F`):** Standard media cards, playlist tiles, horizontal carousels. Outlined with `1px solid rgba(255, 255, 255, 0.05)`.
3. **Level 2 (Active Panels - `#1A222C`):** Expanded tracklists, account switcher drawer, search flyouts. Casts an ambient shadow: `0px 8px 24px rgba(0, 0, 0, 0.45)`.
4. **Level 3 (Floating Persistent Controls - Glassmorphism):**
   - **Mini-Player Bar:** Backed by `#12181F` at 85% opacity with `backdrop-filter: blur(24px)`, anchored by a 1px border (`rgba(255, 255, 255, 0.1)`) on the top edge.
   - **Bottom Navigation Bar:** Backed by `#0B0F12` at 90% opacity with `backdrop-filter: blur(20px)`.
5. **Level 4 (Full Player Modal & Overlays):** Dynamic tint derived from real-time color extraction of the active album art, mixed with 80% `#0B0F12` and subtle ambient vignette (`box-shadow: inset 0 -120px 180px rgba(11, 15, 18, 0.9)`).

## Shapes

The shape hierarchy mirrors modern Android device form factors, shifting from subtle rounding on interior elements to wide curves on major media containers.

### Shape Scale Guidelines
- **Small Controls & Action Badges (`8px` / `0.5rem`):** Explicit lyric tags ("E"), audio format chips (Lossless, Dolby Atmos), and timestamp badges.
- **Medium Inputs & List Containers (`12px` - `16px`):** Song rows on press, input text fields, filter chips, dropdown selections.
- **Primary Media Cards (`16px` - `20px`):** Playlist covers, podcast show cards, dynamic family activity blocks.
- **Floating Overlays & Mini-Player (`20px` - `24px`):** Persistent mini-player bar, bottom sheet modals, context menus.
- **Full Pill (`9999px`):** Primary playback buttons, active profile toggles, quick filters, scrub thumbs.

## Components

### 1. Interactive Persistent Mini-Player Bar
- **Dimensions & Placement:** Floating anchored dock sitting `8px` above the bottom navigation bar with `8px` lateral margins. Height: `64px`. Radius: `20px`.
- **Styling:** Glass surface (`#12181F` @ 85% + 24px blur) with a 1px inner border (`rgba(255, 255, 255, 0.12)`).
- **Contents:**
  - 48x48px album artwork (radius: `12px`) on the left.
  - Middle metadata: Marquee-scrolling track title (`label-lg`, white), artist subtitle (`body-sm`, `#94A3B8`).
  - Integrated family listening icon (avatar dot showing which family member is streaming if shared session is active).
  - Trailing controls: Cast button, Play/Pause circle button (`36px` diameter, `#20DF64` icon fill).
  - Base progress indicator: A 2px micro-progress bar seamlessly pinned to the bottom border line of the mini-player shell.

### 2. Buttons
- **Primary Play Button:** Full pill shape, solid `#20DF64` background, deep charcoal icon/text (`#0B0F12`). On pressed state: scales to `0.96` with `#1DB954` background.
- **Secondary Action Button:** Transparent surface with 1px border (`rgba(255, 255, 255, 0.2)`), text in `#F8FAFC`. On press: fill transforms to `rgba(255, 255, 255, 0.08)`.
- **Tertiary / Ghost Button:** No border or fill. High-emphasis icon or text in `#94A3B8`, switching to `#20DF64` on active/selected.

### 3. Media Cards & Playlists
- **Album / Show Card:** Aspect ratio 1:1 for music, 1:1 or 16:9 for podcasts. Radius: `16px`. Artwork features an ambient behind-card blur glow derived from dominant artwork hues (opacity: 30%, blur: 24px).
- **Family Mix Card:** Dual-tone diagonal gradient border featuring `#8B5CF6` and `#20DF64`. Overlapping mini-avatars (24px circles with 2px `#0B0F12` border) display contributing family profiles.

### 4. Compact Bottom Navigation
- **Height & Layout:** Height: `56px` + safe-area inset. 4–5 destination items max (Home, Search, Your Library, Family Hub).
- **Active State:** Icon renders with `#20DF64` fill accompanied by a 4px green glow pill underneath. Inactive icons: `#64748B`. Labels follow `label-sm`.

### 5. Chips & Category Filters
- **Resting:** `#1A222C` background, `#94A3B8` label, full pill radius, `12px` vertical padding, `16px` horizontal padding.
- **Selected:** Solid `#20DF64` background, deep black text (`#0B0F12`), bold weight (`label-md`).

### 6. Track List Row
- **Structure:** `56px` fixed height row. 40x40px rounded media thumbnail (`8px` radius).
- **Content:** Song title (`body-lg`, `#F8FAFC`) stacked over artist and badge metadata (`body-sm`, `#94A3B8`).
- **Interactive State:** Background transitions to `rgba(255, 255, 255, 0.04)` on pressed state. Active playing track shifts title color to `#20DF64` and displays an animated 3-bar equalizer graphic.

### 7. Input Fields & Search Bars
- **Surface:** `#12181F` background, full pill or `16px` radius. Leading search icon in `#64748B`.
- **Text Entry:** `#F8FAFC` input text, `#64748B` placeholder ("Sanatçılar, şarkılar veya podcast'ler").
- **Focus State:** 1.5px continuous outline in `#20DF64` with an ambient glow of `rgba(32, 223, 100, 0.2)`.