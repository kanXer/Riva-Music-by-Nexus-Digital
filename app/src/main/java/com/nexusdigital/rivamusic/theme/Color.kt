package com.nexusdigital.rivamusic.theme

import androidx.compose.ui.graphics.Color

/**
 * Theme hamesha SYSTEM default follow karta hai (light/dark auto).
 * IMPORTANT: getters (`get()`) use hote hain — warna class-load par hi
 * single value freeze ho jati hai aur theme switch par app badalta nahi!
 */
var RivaIsDark: Boolean = true

val Ink get() = Color(if (RivaIsDark) 0xFF140A0A.toInt() else 0xFFEFE2DC.toInt())
val InkSoft get() = Color(if (RivaIsDark) 0xFF1C0E0D.toInt() else 0xFFE9DAD3.toInt())
val Panel get() = Color(if (RivaIsDark) 0xFF261312.toInt() else 0xFFF7ECE7.toInt())
val PanelElevated get() = Color(if (RivaIsDark) 0xFF331A18.toInt() else 0xFFFFF6F2.toInt())
val PanelBorder get() = Color(if (RivaIsDark) 0xFF4C2723.toInt() else 0xFFDCC2BA.toInt())
val Lime get() = Color(if (RivaIsDark) 0xFFFF6B52.toInt() else 0xFFE8503A.toInt())
val LimeDeep get() = Color(if (RivaIsDark) 0xFFE04F35.toInt() else 0xFFC63F2B.toInt())
val Coral get() = Color(if (RivaIsDark) 0xFFFFAB91.toInt() else 0xFFFFA98F.toInt())
val Sky get() = Color(if (RivaIsDark) 0xFFFF8A80.toInt() else 0xFFF2958B.toInt())
val Violet get() = Color(if (RivaIsDark) 0xFFF87171.toInt() else 0xFFD24C4C.toInt())
val TextPrimary get() = Color(if (RivaIsDark) 0xFFFDF6F4.toInt() else 0xFF2B1512.toInt())
val TextSecondary get() = Color(if (RivaIsDark) 0xFFC7A8A3.toInt() else 0xFF6E5350.toInt())
val TextFaint get() = Color(if (RivaIsDark) 0xFF96726D.toInt() else 0xFFA08884.toInt())
val Danger get() = Color(if (RivaIsDark) 0xFFFF3B3B.toInt() else 0xFFE63B3B.toInt())

// Gradient wrappers.

// App root background: middlena hamesha Ink hai.
val BgOuter1 get() = Color(if (RivaIsDark) 0xFF170C0C.toInt() else 0xFFF4E6E0.toInt())
val BgDeep get() = Color(if (RivaIsDark) 0xFF100808.toInt() else 0xFFE7D6CF.toInt())

// Now-playing page background.
val PlayerTop get() = Color(if (RivaIsDark) 0xFF2B1210.toInt() else 0xFFFBEAE4.toInt())
val PlayerMid get() = Color(if (RivaIsDark) 0xFF1D0C0B.toInt() else 0xFFF3DDD5.toInt())

// Sheets/cards gradient.
val SheetMid get() = Color(if (RivaIsDark) 0xFF3B1A16.toInt() else 0xFFF6E3DB.toInt())
val SheetDeep get() = Color(if (RivaIsDark) 0xFF201010.toInt() else 0xFFEBD8D1.toInt())
val CardMid get() = Color(if (RivaIsDark) 0xFF3A1A18.toInt() else 0xFFF3DFD7.toInt())

// Library section card gradients (4 variants).
val SectionATop get() = Color(if (RivaIsDark) 0xFF2E1410.toInt() else 0xFFFDF0EA.toInt())
val SectionAMid get() = Color(if (RivaIsDark) 0xFF3A1C12.toInt() else 0xFFF7DFD5.toInt())
val SectionABottom get() = Color(if (RivaIsDark) 0xFF1D0F0C.toInt() else 0xFFFFEFE8.toInt())

val SectionBTop get() = Color(if (RivaIsDark) 0xFF27120D.toInt() else 0xFFFBE9E1.toInt())
val SectionBMid get() = Color(if (RivaIsDark) 0xFF1E100C.toInt() else 0xFFF3DDD4.toInt())
val SectionBBottom get() = Color(if (RivaIsDark) 0xFF2B1512.toInt() else 0xFFFCEDE5.toInt())

val SectionCTop get() = Color(if (RivaIsDark) 0xFF221110.toInt() else 0xFFFAE7E0.toInt())
val SectionCMid get() = Color(if (RivaIsDark) 0xFF33201D.toInt() else 0xFFF1D9D1.toInt())
val SectionCBottom get() = Color(if (RivaIsDark) 0xFF2C1917.toInt() else 0xFFF8E4DC.toInt())

val SectionDTop get() = Color(if (RivaIsDark) 0xFF2E1914.toInt() else 0xFFFDEFE9.toInt())
val SectionDMid get() = Color(if (RivaIsDark) 0xFF241211.toInt() else 0xFFF5DFD7.toInt())
val SectionDBottom get() = Color(if (RivaIsDark) 0xFF301913.toInt() else 0xFFFBEAE2.toInt())
