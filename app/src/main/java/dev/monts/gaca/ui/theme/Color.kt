package dev.monts.gaca.ui.theme

import androidx.compose.ui.graphics.Color

// The Slate palette from the approved design canvas, converted from its
// oklch source values to sRGB — see the design artifact for the originals.
// Kept as named constants (not just a Material ColorScheme) because several
// of these — severity colors, card tints — are looked up by data, not by
// theme role.

val PageBackground = Color(0xFFF0F4F9)
val HeaderAccent = Color(0xFF2D3C4C)
val OnAccent = Color(0xFFF4F9FE)
val TextPrimary = Color(0xFF282F35)
val TextSecondary = Color(0xFF5A6169)
val TextMeta = Color(0xFF747B83)
val ChipBackground = Color(0xFFD8DFE6)
val BorderColor = Color(0xFFC8CED5)
val SuggestBackground = Color(0xFFD7E3F0)

val SeverityUrgentDot = Color(0xFFC55C43)
val SeverityUrgentBg = Color(0xFFFBDDD5)
val SeverityWarnDot = Color(0xFFC6A670)
val SeverityWarnBg = Color(0xFFECE1D0)
val SeverityInfoDot = Color(0xFFABB6C1)
val SeverityInfoBg = Color(0xFFE3E8EE)

/** A crew member's own color comes over the wire as a hex string (CrewMember.color) —
 * the server is the one source of truth for it, so the client just parses and renders it. */
fun crewColor(hex: String): Color = Color(android.graphics.Color.parseColor(hex))

/** A low-alpha wash of a crew member's color, for a chip/pill background — the
 * same trick as the existing `kind` chip's translucent white, just tinted per crew. */
fun crewChipTint(hex: String): Color = crewColor(hex).copy(alpha = 0.16f)

/** The user's own chat bubbles. */
val UserBubble = HeaderAccent
val ActivityPill = Color(0xFFE3E8EE)

/** The crew color mixed 80% toward white — pill and icon-tile backgrounds. */
fun crewTint(hex: String): Color = androidx.compose.ui.graphics.lerp(crewColor(hex), Color.White, 0.8f)

/** The crew color mixed 25% toward black — names and text that sit on a light surface. */
fun crewInk(hex: String): Color = androidx.compose.ui.graphics.lerp(crewColor(hex), Color.Black, 0.25f)

/** The crew color mixed 12% toward black — buttons that carry light text. */
fun crewSolid(hex: String): Color = androidx.compose.ui.graphics.lerp(crewColor(hex), Color.Black, 0.12f)
