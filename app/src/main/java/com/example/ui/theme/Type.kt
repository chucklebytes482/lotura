package com.example.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.example.R

val RobotoFamily = FontFamily(
    Font(R.font.roboto_regular, FontWeight.Normal),
    Font(R.font.roboto_medium, FontWeight.Medium),
    Font(R.font.roboto_medium, FontWeight.SemiBold),
    Font(R.font.roboto_bold, FontWeight.Bold)
)

val RobotoMonoFamily = FontFamily(
    Font(R.font.roboto_mono_regular, FontWeight.Normal),
    Font(R.font.roboto_mono_medium, FontWeight.Medium)
)

// Skala Material 3
val SyncBebasTypography = Typography(
    headlineSmall = TextStyle(fontFamily = RobotoFamily, fontWeight = FontWeight.Normal, fontSize = 24.sp, lineHeight = 32.sp),
    titleLarge = TextStyle(fontFamily = RobotoFamily, fontWeight = FontWeight.Normal, fontSize = 22.sp, lineHeight = 28.sp),
    titleMedium = TextStyle(fontFamily = RobotoFamily, fontWeight = FontWeight.Medium, fontSize = 16.sp, lineHeight = 24.sp),
    titleSmall = TextStyle(fontFamily = RobotoFamily, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp),
    bodyLarge = TextStyle(fontFamily = RobotoFamily, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontFamily = RobotoFamily, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontFamily = RobotoFamily, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 16.sp),
    labelLarge = TextStyle(fontFamily = RobotoFamily, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp),
    labelMedium = TextStyle(fontFamily = RobotoFamily, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp),
    labelSmall = TextStyle(fontFamily = RobotoMonoFamily, fontWeight = FontWeight.Normal, fontSize = 11.sp, lineHeight = 16.sp)
)
