package com.cinemax.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cinemax.app.core.RoadmapPhase

/** The full-screen roadmap behind Settings → خطة التطوير. */
@Composable
fun CinemaxRoadmapScreen(
    phases: List<RoadmapPhase>,
    accent: Color,
    background: Color,
    versionLabel: String,
    onClose: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(background)
            .windowInsetsPadding(WindowInsets.systemBars)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 12.dp, top = 16.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    "خطة التطوير",
                    color = CinemaxPalette.text,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Black
                )
                Text(
                    "الطريق إلى تجربة بمستوى Nuvio • $versionLabel",
                    color = CinemaxPalette.textMuted,
                    fontSize = 12.sp,
                    lineHeight = 17.sp
                )
            }
            TextButton(onClick = onClose) { Text("تم", color = accent, fontWeight = FontWeight.SemiBold) }
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(phases) { phase ->
                RoadmapPhaseCard(phase = phase, accent = accent)
            }
            item {
                Column(Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
                    Text(
                        "المراحل السبع مكتملة: التنقل، الرئيسية، التفاصيل، المشغّل، البحث والمكتبة، إعدادات بصفحات مستقلة، والصقل.",
                        color = CinemaxPalette.textMuted,
                        fontSize = 12.sp,
                        lineHeight = 18.sp
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "الواجهة لا تجلب محتوى: الروابط التي تحتاج تورنت تحتاج محرك بث منفصلًا.",
                        color = CinemaxPalette.textMuted,
                        fontSize = 11.sp,
                        lineHeight = 17.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun RoadmapPhaseCard(phase: RoadmapPhase, accent: Color) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(CinemaxPalette.surface)
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(
                        if (phase.done || phase.current) accent else accent.copy(alpha = 0.16f)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    if (phase.done) "✓" else phase.id.toString(),
                    color = if (phase.done || phase.current) Color.Black else accent,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    phase.title,
                    color = CinemaxPalette.text,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    phase.goal,
                    color = CinemaxPalette.textMuted,
                    fontSize = 12.sp,
                    lineHeight = 17.sp
                )
            }
            if (phase.done) {
                Spacer(Modifier.width(8.dp))
                CinemaxPill("مكتملة", accent)
            } else if (phase.current) {
                Spacer(Modifier.width(8.dp))
                CinemaxPill("الحالية", accent)
            }
        }
        Spacer(Modifier.height(10.dp))
        phase.steps.forEach { step ->
            Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                Text("•", color = accent, fontSize = 13.sp)
                Spacer(Modifier.width(8.dp))
                Text(
                    step,
                    color = CinemaxPalette.text.copy(alpha = 0.86f),
                    fontSize = 13.sp,
                    lineHeight = 19.sp
                )
            }
        }
    }
}
