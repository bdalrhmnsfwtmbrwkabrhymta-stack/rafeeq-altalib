package com.app.games

import android.graphics.Paint
import android.graphics.Typeface
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RemoveRedEye
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

private data class TrackCoinEntity(
    val id: Long,
    var lane: Int,
    var z: Float, // 0.0 (horizon) -> 1.0 (player)
    var collected: Boolean = false
)

private data class CelebrationParticle(
    var x: Float,
    var y: Float,
    var vx: Float,
    var vy: Float,
    val color: Color,
    val size: Float,
    val isCoin: Boolean,
    var life: Float = 1f
)

@Composable
fun StudyRunnerGameScreen(
    stageNumber: Int,
    questions: List<GameQuestion>,
    shopState: GameShopState,
    onQuestionShown: (String) -> Unit,
    onRunFinished: suspend (
        stageNumber: Int,
        victory: Boolean,
        trackCoins: Int,
        score: Int,
        correctAnswers: Int,
        wrongAnswers: Int,
        maxCombo: Int
    ) -> RunSummaryResult,
    onAdvanceToStage: (Int) -> Unit,
    onRestartStage: (Int) -> Unit,
    onExitToHub: () -> Unit
) {
    val stageSpec = remember(stageNumber) { StageThemeEngine.stageFor(stageNumber) }
    val palette = remember(stageNumber) { StageThemeEngine.paletteFor(stageNumber) }
    val character = shopState.equippedCharacter
    val scope = rememberCoroutineScope()
    val gatesPerStage = stageSpec.standardGatesCount

    // Core Gameplay State
    var isPaused by remember(stageNumber) { mutableStateOf(false) }
    var hearts by remember(stageNumber) { mutableIntStateOf(3) }
    var currentLane by remember(stageNumber) { mutableIntStateOf(1) } // 0 = Left, 1 = Center, 2 = Right
    var laneTiltTarget by remember(stageNumber) { mutableFloatStateOf(0f) }

    var gatesCleared by remember(stageNumber) { mutableIntStateOf(0) } // 0..gatesPerStage, then Boss Gate
    var currentQuestionIdx by remember(stageNumber) { mutableIntStateOf(0) }
    var gateZ by remember(stageNumber) { mutableFloatStateOf(0.05f) } // 0 -> 1.0
    var gateResolved by remember(stageNumber) { mutableStateOf(false) }

    var trackCoinsEarned by remember(stageNumber) { mutableIntStateOf(0) }
    var score by remember(stageNumber) { mutableIntStateOf(0) }
    var combo by remember(stageNumber) { mutableIntStateOf(1) }
    var maxComboReached by remember(stageNumber) { mutableIntStateOf(1) }
    var correctAnswers by remember(stageNumber) { mutableIntStateOf(0) }
    var wrongAnswers by remember(stageNumber) { mutableIntStateOf(0) }

    // Animation & Physics States
    var scrollPhase by remember(stageNumber) { mutableFloatStateOf(0f) }
    var runTimeSec by remember(stageNumber) { mutableFloatStateOf(0f) }
    var jumpPhase by remember(stageNumber) { mutableFloatStateOf(-1f) } // -1 = on ground, 0..1 = airborne
    var screenFlashColor by remember(stageNumber) { mutableStateOf<Color?>(null) }
    var screenFlashAlpha by remember(stageNumber) { mutableFloatStateOf(0f) }
    var feedbackToast by remember(stageNumber) { mutableStateOf<String?>(null) }
    var feedbackTimer by remember(stageNumber) { mutableFloatStateOf(0f) }

    // Boss Celebration & End-of-Run Summary
    var bossChestOpeningPhase by remember(stageNumber) { mutableFloatStateOf(0f) }
    var isBossCelebrating by remember(stageNumber) { mutableStateOf(false) }
    var runSummary by remember(stageNumber) { mutableStateOf<RunSummaryResult?>(null) }

    // Power-Up Runtime Charges & Timers
    var magnetCharges by remember(stageNumber) { mutableIntStateOf(shopState.magnetLevel) }
    var magnetRemainingSec by remember(stageNumber) { mutableFloatStateOf(0f) }
    var shieldCharges by remember(stageNumber) { mutableIntStateOf(shopState.shieldLevel) }
    var shieldActive by remember(stageNumber) { mutableStateOf(character.startsWithFreeShield) }
    var detectorCharges by remember(stageNumber) { mutableIntStateOf(shopState.detectorLevel) }
    var detectorActiveForGate by remember(stageNumber) { mutableStateOf(false) }

    val coinsOnTrack = remember(stageNumber) { mutableStateListOf<TrackCoinEntity>() }
    val particles = remember(stageNumber) { mutableStateListOf<CelebrationParticle>() }
    var nextCoinId by remember(stageNumber) { mutableIntStateOf(1) }
    var coinSpawnCooldown by remember(stageNumber) { mutableFloatStateOf(0.4f) }

    val activeQuestion: GameQuestion = remember(questions, currentQuestionIdx) {
        if (questions.isNotEmpty()) {
            questions[currentQuestionIdx % questions.size]
        } else {
            GameQuestion(
                id = "fallback_q",
                prompt = "ما هي وحدة قياس شدة التيار الكهربي؟",
                options = listOf("الأمبير", "الفولت", "الأوم"),
                correctIndex = 0
            )
        }
    }
    val isBossGateActive = gatesCleared == gatesPerStage

    LaunchedEffect(activeQuestion.id) {
        onQuestionShown(activeQuestion.prompt)
    }

    // Smooth Spring Lane X (-1f, 0f, +1f) & Banking Tilt
    val animatedLaneOffset by animateFloatAsState(
        targetValue = (currentLane - 1).toFloat(),
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "laneSpring"
    )
    val animatedTilt by animateFloatAsState(
        targetValue = laneTiltTarget,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "tiltSpring"
    )

    BackHandler {
        if (runSummary != null) {
            onExitToHub()
        } else {
            isPaused = !isPaused
        }
    }

    fun triggerLaneSwitch(delta: Int) {
        if (isPaused || runSummary != null || isBossCelebrating) return
        val next = (currentLane + delta).coerceIn(0, 2)
        if (next != currentLane) {
            currentLane = next
            laneTiltTarget = delta * 14f
        }
    }

    fun triggerJump() {
        if (isPaused || runSummary != null || isBossCelebrating) return
        if (jumpPhase < 0f) {
            jumpPhase = 0f
        }
    }

    fun spawnBurstParticles(centerX: Float, centerY: Float, count: Int, includeCoins: Boolean) {
        val confettiColors = listOf(
            Color(0xFFFACC15), Color(0xFF38BDF8), Color(0xFF22C55E),
            Color(0xFFF43F5E), Color(0xFFA855F7), Color(0xFFF97316)
        )
        repeat(count) { idx ->
            val angle = Random.nextFloat() * 2f * PI.toFloat()
            val speed = 160f + Random.nextFloat() * 440f
            particles.add(
                CelebrationParticle(
                    x = centerX,
                    y = centerY,
                    vx = cos(angle) * speed,
                    vy = sin(angle) * speed - 240f,
                    color = confettiColors[idx % confettiColors.size],
                    size = if (includeCoins && idx % 3 == 0) 14f else (7f + Random.nextFloat() * 8f),
                    isCoin = includeCoins && idx % 3 == 0,
                    life = 1.0f
                )
            )
        }
    }

    fun concludeRun(victory: Boolean) {
        if (runSummary != null) return
        scope.launch {
            runSummary = onRunFinished(
                stageNumber,
                victory,
                trackCoinsEarned,
                score,
                correctAnswers,
                wrongAnswers,
                maxComboReached
            )
        }
    }

    // 60 FPS Core Game Loop
    LaunchedEffect(stageNumber, isPaused, runSummary != null) {
        var lastFrameNs = 0L
        while (runSummary == null) {
            withFrameNanos { nowNs ->
                if (lastFrameNs == 0L) {
                    lastFrameNs = nowNs
                    return@withFrameNanos
                }
                val rawDt = (nowNs - lastFrameNs) / 1_000_000_000f
                lastFrameNs = nowNs
                val dt = rawDt.coerceIn(0f, 0.05f)

                if (isPaused) return@withFrameNanos

                runTimeSec += dt
                val endlessBoost = if (stageSpec.isEndlessStage) (gatesCleared * 0.03f).coerceAtMost(0.6f) else 0f
                val worldSpeed = (stageSpec.speedMultiplier + endlessBoost) * 0.85f
                scrollPhase = (scrollPhase + dt * worldSpeed * 0.65f) % 1f

                // Settle lane tilt smoothly back to upright
                if (abs(laneTiltTarget) > 0.2f) {
                    laneTiltTarget *= (1f - dt * 8f).coerceAtLeast(0f)
                }

                // Jump physics
                if (jumpPhase >= 0f) {
                    val jumpSpeed = 1.65f / character.jumpDurationMultiplier
                    jumpPhase += dt * jumpSpeed
                    if (jumpPhase >= 1f) jumpPhase = -1f
                }

                // Flash & feedback timers
                if (screenFlashAlpha > 0f) screenFlashAlpha = (screenFlashAlpha - dt * 2.4f).coerceAtLeast(0f)
                if (feedbackTimer > 0f) {
                    feedbackTimer -= dt
                    if (feedbackTimer <= 0f) feedbackToast = null
                }

                // Magnet countdown
                if (magnetRemainingSec > 0f) {
                    magnetRemainingSec = (magnetRemainingSec - dt).coerceAtLeast(0f)
                }

                // Update particles
                val pIter = particles.listIterator()
                while (pIter.hasNext()) {
                    val p = pIter.next()
                    p.x += p.vx * dt
                    p.y += p.vy * dt
                    p.vy += 680f * dt // gravity
                    p.life -= dt * 0.85f
                    if (p.life <= 0f) pIter.remove()
                }

                // Boss Chest Celebration sequence
                if (isBossCelebrating) {
                    bossChestOpeningPhase = (bossChestOpeningPhase + dt * 0.85f).coerceAtMost(1.35f)
                    if (bossChestOpeningPhase >= 1.25f) {
                        isBossCelebrating = false
                        concludeRun(victory = true)
                    }
                    return@withFrameNanos
                }

                // Spawn Coins along the 3 lanes
                coinSpawnCooldown -= dt
                if (coinSpawnCooldown <= 0f) {
                    val lane = Random.nextInt(0, 3)
                    coinsOnTrack.add(TrackCoinEntity(id = nextCoinId++.toLong(), lane = lane, z = 0.04f))
                    coinSpawnCooldown = (0.52f / stageSpec.speedMultiplier).coerceAtLeast(0.22f)
                }

                // Advance Coins & check pickups (with Magnet pull across all 3 lanes!)
                val cIter = coinsOnTrack.listIterator()
                while (cIter.hasNext()) {
                    val coin = cIter.next()
                    coin.z += dt * worldSpeed * 0.44f
                    val inPickupZone = coin.z in 0.82f..1.04f
                    val laneMatches = coin.lane == currentLane || magnetRemainingSec > 0f
                    if (!coin.collected && inPickupZone && laneMatches) {
                        coin.collected = true
                        val gain = (5 * stageSpec.coinMultiplier * character.coinBonusMultiplier).toInt().coerceAtLeast(5)
                        trackCoinsEarned += gain
                        score += gain * combo
                    }
                    if (coin.collected || coin.z > 1.15f) {
                        cIter.remove()
                    }
                }

                // Advance Question Gates
                val gateApproachRate = 0.140f * (0.86f + stageSpec.speedMultiplier * 0.20f) * character.gateSpeedFactor
                gateZ += dt * gateApproachRate

                // Resolve Gate Collision at Player Line (z >= 0.90)
                if (gateZ >= 0.90f && !gateResolved) {
                    gateResolved = true
                    val isCorrect = currentLane == activeQuestion.correctIndex

                    if (isCorrect) {
                        correctAnswers += 1
                        combo = (combo + 1).coerceAtMost(stageSpec.maxComboCap)
                        if (combo > maxComboReached) maxComboReached = combo

                        val gateCoins = (25 * stageSpec.coinMultiplier * character.coinBonusMultiplier).toInt()
                        val comboPoints = (100 * combo * character.comboBonusMultiplier).toInt()
                        trackCoinsEarned += gateCoins
                        score += comboPoints

                        screenFlashColor = Color(0xFF22C55E)
                        screenFlashAlpha = 0.35f
                        feedbackToast = "إجابة صحيحة! +$gateCoins عملة • COMBO x$combo"
                        feedbackTimer = 1.5f

                        if (isBossGateActive && !stageSpec.isEndlessStage) {
                            // Trigger Boss Golden Treasure Chest opening & Confetti burst!
                            isBossCelebrating = true
                            bossChestOpeningPhase = 0f
                            spawnBurstParticles(centerX = 520f, centerY = 760f, count = 56, includeCoins = true)
                            feedbackToast = "انتصار! فتح ${stageSpec.chestNameAr} (+${stageSpec.bossRewardCoins} عملة) 🎉"
                            feedbackTimer = 2.4f
                            return@withFrameNanos
                        } else if (isBossGateActive && stageSpec.isEndlessStage) {
                            // Stage 20 Endless Legend Milestone Chest!
                            gatesCleared = 0
                            trackCoinsEarned += stageSpec.bossRewardCoins
                            spawnBurstParticles(centerX = 520f, centerY = 760f, count = 48, includeCoins = true)
                            feedbackToast = "تاج أسطورة الثانوية! +${stageSpec.bossRewardCoins} عملة • استمر بالطور اللانهائي 👑"
                            feedbackTimer = 2.5f
                        } else {
                            gatesCleared += 1
                        }
                    } else {
                        // Wrong Gate! Check Knowledge Shield first
                        if (shieldActive) {
                            shieldActive = false
                            screenFlashColor = Color(0xFFF59E0B)
                            screenFlashAlpha = 0.40f
                            feedbackToast = "درع المعرفة حماك من خسارة قلب! 🛡️ الإجابة: ${activeQuestion.options[activeQuestion.correctIndex]}"
                            feedbackTimer = 2.0f
                        } else {
                            wrongAnswers += 1
                            hearts = (hearts - 1).coerceAtLeast(0)
                            combo = 1
                            screenFlashColor = Color(0xFFEF4444)
                            screenFlashAlpha = 0.45f
                            feedbackToast = "بوابة خاطئة! الصحيح: ${activeQuestion.options[activeQuestion.correctIndex]}"
                            feedbackTimer = 2.0f
                            if (hearts <= 0) {
                                concludeRun(victory = false)
                                return@withFrameNanos
                            }
                        }
                    }
                }

                // Recycle to next question gate once passed
                if (gateZ >= 1.12f) {
                    gateZ = 0.04f
                    gateResolved = false
                    detectorActiveForGate = false
                    currentQuestionIdx += 1
                }
            }
        }
    }

    // Native Paint for crisp Arabic gate labels on Canvas
    val gateTextPaint = remember {
        Paint().apply {
            isAntiAlias = true
            color = android.graphics.Color.WHITE
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            setShadowLayer(6f, 0f, 2f, android.graphics.Color.argb(200, 0, 0, 0))
        }
    }
    val coinLetterPaint = remember {
        Paint().apply {
            isAntiAlias = true
            color = android.graphics.Color.rgb(146, 64, 14)
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
    }

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(palette.skyTop)
                .pointerInput(stageNumber) {
                    var dragAccumX = 0f
                    var dragAccumY = 0f
                    var consumed = false
                    detectDragGestures(
                        onDragStart = {
                            dragAccumX = 0f
                            dragAccumY = 0f
                            consumed = false
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            if (consumed) return@detectDragGestures
                            dragAccumX += dragAmount.x
                            dragAccumY += dragAmount.y
                            if (abs(dragAccumX) > 36f && abs(dragAccumX) > abs(dragAccumY)) {
                                // Note: Inside RTL pointerInput or physical screen X:
                                // dragAmount.x > 0 is ALWAYS finger moving right on screen;
                                // lane 0 is Left, lane 1 is Center, lane 2 is Right.
                                triggerLaneSwitch(if (dragAccumX > 0f) 1 else -1)
                                consumed = true
                            } else if (dragAccumY < -44f && abs(dragAccumY) > abs(dragAccumX)) {
                                triggerJump()
                                consumed = true
                            }
                        }
                    )
                }
                .pointerInput(stageNumber) {
                    detectTapGestures { offset ->
                        if (isPaused || runSummary != null || isBossCelebrating) return@detectTapGestures
                        val third = size.width / 3f
                        val tappedLane = when {
                            offset.x < third -> 0
                            offset.x < third * 2f -> 1
                            else -> 2
                        }
                        if (tappedLane != currentLane) {
                            laneTiltTarget = (tappedLane - currentLane) * 14f
                            currentLane = tappedLane
                        }
                    }
                }
        ) {
            var canvasWidthPx by remember { mutableFloatStateOf(1080f) }
            var canvasHeightPx by remember { mutableFloatStateOf(2200f) }

            // 1. Full-Screen 3D Pixar Stage & Runway Backdrop (Matches Reference Images 03-11 100%)
            Image(
                painter = painterResource(id = StageThemeEngine.backdropResForStage(stageNumber)),
                contentDescription = stageSpec.nameAr,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )

            // 2. 60 FPS 3D-Perspective Interactive Runner Layer (Coins, Portals, Ground Trails, Particles)
            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height
                canvasWidthPx = w
                canvasHeightPx = h

                val horizonY = h * 0.36f
                val playerY = h * 0.80f
                val topHalfW = w * 0.25f
                val bottomHalfW = w * 0.45f

                fun projectY(z: Float): Float {
                    val depth = z.coerceIn(0f, 1.2f).pow(1.18f)
                    return horizonY + depth * (playerY - horizonY)
                }

                fun halfWidthAt(z: Float): Float {
                    val depth = z.coerceIn(0f, 1.2f).pow(1.18f)
                    return topHalfW + depth * (bottomHalfW - topHalfW)
                }

                fun laneCenterX(laneIndex: Float, z: Float): Float {
                    val hw = halfWidthAt(z)
                    val laneWidth = (hw * 2f) / 3f
                    return w * 0.5f + (laneIndex - 1f) * laneWidth
                }

                // Subtle atmospheric tint for higher stages (9..20) while keeping the 3D artwork crystal clear
                val stageTint: Color? = when (stageNumber) {
                    9 -> Color(0x22059669)
                    10 -> Color(0x220284C7)
                    11 -> Color(0x22991B1B)
                    12 -> Color(0x267C3AED)
                    13 -> Color(0x222563EB)
                    14 -> Color(0x26EA580C)
                    15 -> Color(0x1FD97706)
                    16 -> Color(0x224F46E5)
                    17 -> Color(0x24DB2777)
                    18 -> Color(0x220EA5E9)
                    19 -> Color(0x22B45309)
                    20 -> Color(0x24F59E0B)
                    else -> null
                }
                if (stageTint != null) {
                    drawRect(color = stageTint)
                }

                // Animated 60 FPS scrolling energy chevrons & light pulses over the 3D runway
                for (i in 0..6) {
                    val z = ((i / 7f + scrollPhase) % 1f)
                    val cy = projectY(z)
                    val hw = halfWidthAt(z) * 0.16f
                    val chevron = Path().apply {
                        moveTo(w * 0.5f - hw, cy + hw * 0.30f)
                        lineTo(w * 0.5f, cy - hw * 0.24f)
                        lineTo(w * 0.5f + hw, cy + hw * 0.30f)
                    }
                    drawPath(
                        path = chevron,
                        color = palette.laneLineColor.copy(alpha = (0.14f + z * 0.26f).coerceAtMost(0.42f)),
                        style = Stroke(width = 2.5f + z * 3.5f, cap = StrokeCap.Round)
                    )
                }

                // Animated glowing lane-divider pulses synchronized with running speed
                for (div in listOf(-1f / 3f, 1f / 3f)) {
                    for (p in 0..4) {
                        val pz0 = ((p / 5f + scrollPhase) % 1f)
                        val pz1 = (pz0 + 0.08f).coerceAtMost(1.05f)
                        val sPt = Offset(w * 0.5f + halfWidthAt(pz0) * div, projectY(pz0))
                        val ePt = Offset(w * 0.5f + halfWidthAt(pz1) * div, projectY(pz1))
                        drawLine(
                            color = palette.laneLineColor.copy(alpha = 0.55f),
                            start = sPt,
                            end = ePt,
                            strokeWidth = 3f + pz0 * 4f,
                            cap = StrokeCap.Round
                        )
                    }
                }

                // 3. Draw Approaching 3D Gold Coins with Embossed "ع" Emblem (Reference 04, 06, 10)
                coinsOnTrack.sortedBy { it.z }.forEach { coin ->
                    if (coin.z in 0.05f..1.05f) {
                        val effectiveLane = if (magnetRemainingSec > 0f && coin.z > 0.52f) {
                            val pull = ((coin.z - 0.52f) / 0.36f).coerceIn(0f, 1f)
                            coin.lane.toFloat() * (1f - pull) + (animatedLaneOffset + 1f) * pull
                        } else {
                            coin.lane.toFloat()
                        }
                        val cx = laneCenterX(effectiveLane, coin.z)
                        val cy = projectY(coin.z) - 22f * (0.35f + coin.z * 0.65f)
                        val r = 13f + coin.z * 19f
                        draw3DGoldCoin(Offset(cx, cy), r, runTimeSec + coin.id * 0.4f, coinLetterPaint)
                    }
                }

                // 4. Draw Boss Golden Treasure Chest & Floating Trophies on Boss Gate (Reference 10!)
                if (isBossGateActive || isBossCelebrating) {
                    val chestZ = if (isBossCelebrating) 0.72f else (gateZ * 0.85f).coerceIn(0.18f, 0.75f)
                    val chestY = projectY(chestZ)
                    val chestScale = 0.58f + chestZ * 0.95f
                    drawGoldenTreasureChest(
                        center = Offset(w * 0.5f, chestY - 28f * chestScale),
                        scale = chestScale,
                        openProgress = bossChestOpeningPhase.coerceIn(0f, 1f),
                        timeSec = runTimeSec
                    )
                }

                // 5. Draw 3 Stage-Specific 3D Architectural Answer Gates (Reference 03-11!)
                if (!isBossCelebrating && gateZ in 0.04f..1.02f) {
                    val gy = projectY(gateZ)
                    val hw = halfWidthAt(gateZ)
                    val gateW = (hw * 2f) / 3f * 0.90f
                    val gateH = (112f + gateZ * 142f)

                    for (laneIdx in 0..2) {
                        val gx = laneCenterX(laneIdx.toFloat(), gateZ)
                        val isCorrectGate = laneIdx == activeQuestion.correctIndex
                        val highlightGold = detectorActiveForGate && isCorrectGate && (sin(runTimeSec * 14f) > -0.2f)
                        val frameColor = when {
                            highlightGold -> Color(0xFFFACC15)
                            isBossGateActive -> Color(0xFFF59E0B)
                            else -> palette.gateFrameColors[laneIdx % palette.gateFrameColors.size]
                        }
                        val glowColor = if (highlightGold) Color(0xFFFEF08A) else palette.gateGlowColors[laneIdx % palette.gateGlowColors.size]
                        val optionLabel = activeQuestion.options.getOrElse(laneIdx) { "" }

                        drawStagePortalGate(
                            style = stageSpec.gateStyle,
                            gx = gx,
                            gy = gy,
                            gateW = gateW,
                            gateH = gateH,
                            gateZ = gateZ,
                            frameColor = frameColor,
                            glowColor = glowColor,
                            highlightGold = highlightGold,
                            optionLabel = optionLabel,
                            textPaint = gateTextPaint
                        )
                    }
                }

                // 6. Draw 3D Ground Shadow, Speed Trail & Power-Up Aura under the Back-Facing Running Character
                val playerX = laneCenterX(animatedLaneOffset + 1f, 0.98f)
                val jumpArcPx = if (jumpPhase >= 0f) {
                    sin(jumpPhase * PI.toFloat()) * (125f * character.jumpDurationMultiplier)
                } else 0f

                drawRunnerCharacterGroundEffects3D(
                    character = character,
                    centerX = playerX,
                    footY = playerY - jumpArcPx * 0.15f,
                    scale = (w / 375f).coerceIn(1.0f, 1.38f),
                    runPhase = (runTimeSec * 2.15f) % 1f,
                    hasShield = shieldActive,
                    hasMagnet = magnetRemainingSec > 0f
                )
            }

            // 7. 3D Back-Facing Running Character Sprite (Turned 180° Looking Forward Into the Stage!)
            val playerDepth = 0.98f.pow(1.18f)
            val liveHorizonY = canvasHeightPx * 0.36f
            val livePlayerFootY = canvasHeightPx * 0.80f
            val liveHalfW = (canvasWidthPx * 0.25f) + playerDepth * (canvasWidthPx * 0.20f)
            val liveLaneW = (liveHalfW * 2f) / 3f
            val livePlayerX = canvasWidthPx * 0.5f + animatedLaneOffset * liveLaneW
            val liveJumpArcPx = if (jumpPhase >= 0f) {
                sin(jumpPhase * PI.toFloat()) * (125f * character.jumpDurationMultiplier)
            } else 0f

            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                RunnerBackCharacter3DOverlay(
                    character = character,
                    centerX = livePlayerX,
                    footY = livePlayerFootY - liveJumpArcPx,
                    runPhase = (runTimeSec * 2.15f) % 1f,
                    laneTilt = animatedTilt / 14f,
                    isHitFlashing = screenFlashColor == Color(0xFFEF4444) && screenFlashAlpha > 0.15f
                )
            }

            // 8. Foreground Confetti & Impact Flash Canvas (Draws above character during celebrations)
            Canvas(modifier = Modifier.fillMaxSize()) {
                particles.forEach { p ->
                    if (p.isCoin) {
                        draw3DGoldCoin(Offset(p.x, p.y), p.size * p.life.coerceAtLeast(0.4f), runTimeSec * 3f, coinLetterPaint)
                    } else {
                        drawRoundRect(
                            color = p.color.copy(alpha = p.life.coerceIn(0f, 1f)),
                            topLeft = Offset(p.x, p.y),
                            size = Size(p.size, p.size * 0.6f),
                            cornerRadius = CornerRadius(3f, 3f)
                        )
                    }
                }
                if (screenFlashAlpha > 0f && screenFlashColor != null) {
                    drawRect(color = screenFlashColor!!.copy(alpha = screenFlashAlpha))
                }
            }

            // ================================================================
            // TOP HUD (Hearts, Stage Badge, Coins, Combo, Question Banner)
            // ================================================================
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Pause Button
                    Surface(
                        onClick = { isPaused = !isPaused },
                        shape = CircleShape,
                        color = Color.Black.copy(alpha = 0.45f),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.3f)),
                        modifier = Modifier
                            .size(40.dp)
                            .testTag("game_pause_button")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = if (isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                                contentDescription = "إيقاف مؤقت",
                                tint = Color.White
                            )
                        }
                    }

                    // 3 Red Hearts
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = Color.Black.copy(alpha = 0.45f),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.25f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            for (i in 1..3) {
                                Icon(
                                    imageVector = if (i <= hearts) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                    contentDescription = "قلب",
                                    tint = if (i <= hearts) Color(0xFFEF4444) else Color.White.copy(alpha = 0.4f),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }

                    // Stage Badge
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = palette.hudBadgeBg,
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.35f))
                    ) {
                        Text(
                            text = "المرحلة ${stageSpec.number} • ${stageSpec.nameAr}",
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }

                    // Live Gold Coins
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = Color(0xDD451A03),
                        border = BorderStroke(1.dp, Color(0xFFFACC15))
                    ) {
                        Text(
                            text = "${shopState.coins + trackCoinsEarned} عملة",
                            color = Color(0xFFFEF08A),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.ExtraBold,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                }

                // Combo Multiplier Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (combo >= 4) Color(0xFFD97706) else Color.Black.copy(alpha = 0.45f),
                        border = BorderStroke(1.dp, Color(0xFFFDE047))
                    ) {
                        Text(
                            text = "COMBO x$combo",
                            color = Color(0xFFFEF08A),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.ExtraBold,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }

                    if (feedbackToast != null) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xE60F172A),
                            border = BorderStroke(1.dp, Color(0xFF38BDF8))
                        ) {
                            Text(
                                text = feedbackToast.orEmpty(),
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }
                }

                // Prominent Question Card (Royal Gold during Boss Gate!)
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = if (isBossGateActive) Color(0xEB291705) else Color(0xF5FFFFFF),
                    border = BorderStroke(
                        width = if (isBossGateActive) 2.5.dp else 2.5.dp,
                        color = if (isBossGateActive) Color(0xFFFACC15) else Color(0xFF38BDF8)
                    ),
                    shadowElevation = 6.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        if (isBossGateActive) {
                            Text(
                                text = "👑 تحدي الزعيم • افتح ${stageSpec.chestNameAr} (+${stageSpec.bossRewardCoins} عملة)",
                                color = Color(0xFFFACC15),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                            Spacer(Modifier.height(3.dp))
                        }
                        Text(
                            text = activeQuestion.prompt,
                            color = if (isBossGateActive) Color.White else Color(0xFF0F172A),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.ExtraBold,
                            textAlign = TextAlign.Center,
                            lineHeight = 20.sp
                        )
                    }
                }
            }

            // ================================================================
            // BOTTOM HUD (Golden Stage Progress Bar + 3 Power-Up Buttons)
            // ================================================================
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // 3 Power-Up Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    PowerUpHudButton(
                        title = "مغناطيس",
                        icon = Icons.Default.Bolt,
                        accent = Color(0xFFF59E0B),
                        badge = if (magnetRemainingSec > 0f) "${magnetRemainingSec.toInt()}ث" else "$magnetCharges",
                        active = magnetRemainingSec > 0f,
                        enabled = magnetCharges > 0 && magnetRemainingSec <= 0f,
                        onClick = {
                            if (magnetCharges > 0) {
                                magnetCharges -= 1
                                magnetRemainingSec = PowerUpType.COIN_MAGNET.magnetDurationSeconds(shopState.magnetLevel)
                                feedbackToast = "تم تفعيل مغناطيس العملات 🧲"
                                feedbackTimer = 1.5f
                            }
                        }
                    )
                    PowerUpHudButton(
                        title = "درع",
                        icon = Icons.Default.Shield,
                        accent = Color(0xFF1A6FE3),
                        badge = if (shieldActive) "نشط" else "$shieldCharges",
                        active = shieldActive,
                        enabled = shieldCharges > 0 && !shieldActive,
                        onClick = {
                            if (shieldCharges > 0 && !shieldActive) {
                                shieldCharges -= 1
                                shieldActive = true
                                feedbackToast = "درع المعرفة نشط الآن 🛡️"
                                feedbackTimer = 1.5f
                            }
                        }
                    )
                    PowerUpHudButton(
                        title = "كاشف",
                        icon = Icons.Default.RemoveRedEye,
                        accent = Color(0xFF10B981),
                        badge = if (detectorActiveForGate) "مضيء" else "$detectorCharges",
                        active = detectorActiveForGate,
                        enabled = detectorCharges > 0 && !detectorActiveForGate,
                        onClick = {
                            if (detectorCharges > 0 && !detectorActiveForGate) {
                                detectorCharges -= 1
                                detectorActiveForGate = true
                                feedbackToast = "تم كشف البوابة الصحيحة بنور ذهبي ✨"
                                feedbackTimer = 1.5f
                            }
                        }
                    )
                }

                // Golden Stage Progress Bar with Boss Treasure Chest Endpoint
                GoldenStageProgressBar(
                    gatesCleared = gatesCleared,
                    totalStandardGates = gatesPerStage,
                    isBossActive = isBossGateActive
                )
            }

            // ================================================================
            // PAUSE OVERLAY DIALOG
            // ================================================================
            if (isPaused && runSummary == null) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.65f)),
                    contentAlignment = Alignment.Center
                ) {
                    Card(
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(28.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(22.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = "إيقاف مؤقت ⏸️",
                                fontSize = 20.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color(0xFF0F172A)
                            )
                            Text(
                                text = "المرحلة ${stageSpec.number}: ${stageSpec.nameAr}",
                                fontSize = 13.sp,
                                color = Color(0xFF64748B)
                            )
                            Button(
                                onClick = { isPaused = false },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1A6FE3)),
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = null)
                                Spacer(Modifier.width(6.dp))
                                Text("متابعة السباق", fontWeight = FontWeight.Bold)
                            }
                            OutlinedButton(
                                onClick = { onRestartStage(stageNumber) },
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = null)
                                Spacer(Modifier.width(6.dp))
                                Text("إعادة المرحلة", fontWeight = FontWeight.Bold)
                            }
                            OutlinedButton(
                                onClick = onExitToHub,
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.Home, contentDescription = null)
                                Spacer(Modifier.width(6.dp))
                                Text("العودة لمركز الألعاب", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            // ================================================================
            // STAGE COMPLETE / BOSS TREASURE VICTORY / GAME OVER CARD
            // ================================================================
            runSummary?.let { summary ->
                StageResultOverlayCard(
                    summary = summary,
                    character = character,
                    onNextStage = {
                        val nextStage = (summary.stageNumber + 1).coerceAtMost(TOTAL_GAME_STAGES)
                        onAdvanceToStage(nextStage)
                    },
                    onRetryStage = { onRestartStage(summary.stageNumber) },
                    onExitToHub = onExitToHub
                )
            }
        }
    }
}

/**
 * Draws the 6 distinct 3D architectural portal gate styles matching Reference Images 03 through 11!
 */
private fun DrawScope.drawStagePortalGate(
    style: GatePortalStyle,
    gx: Float,
    gy: Float,
    gateW: Float,
    gateH: Float,
    gateZ: Float,
    frameColor: Color,
    glowColor: Color,
    highlightGold: Boolean,
    optionLabel: String,
    textPaint: Paint
) {
    val scaleZ = gateZ.coerceAtLeast(0.32f)
    when (style) {
        GatePortalStyle.TECH_ARCH -> {
            // Reference 03: Two glowing cyan-blue vertical pillars + dark navy top banner sign with option text
            val pillarW = 10f * scaleZ
            for (side in listOf(-1f, 1f)) {
                val px = gx + side * (gateW * 0.45f)
                // Pillar base block
                drawRoundRect(
                    color = Color(0xFF475569),
                    topLeft = Offset(px - pillarW * 0.8f, gy - 12f * scaleZ),
                    size = Size(pillarW * 1.6f, 14f * scaleZ),
                    cornerRadius = CornerRadius(4f, 4f)
                )
                // Glowing cyan-blue vertical post
                drawRoundRect(
                    brush = Brush.horizontalGradient(listOf(frameColor, Color.White, frameColor)),
                    topLeft = Offset(px - pillarW * 0.5f, gy - gateH),
                    size = Size(pillarW, gateH),
                    cornerRadius = CornerRadius(pillarW * 0.5f, pillarW * 0.5f)
                )
            }
            // Top arch connector bar & Dark Navy Signboard
            val signH = (28f + gateZ * 34f)
            drawRoundRect(
                color = if (highlightGold) Color(0xFFFACC15) else Color(0xFF1E3A8A),
                topLeft = Offset(gx - gateW * 0.50f, gy - gateH - signH * 0.45f),
                size = Size(gateW, signH),
                cornerRadius = CornerRadius(12f * scaleZ, 12f * scaleZ)
            )
            drawRoundRect(
                color = glowColor,
                topLeft = Offset(gx - gateW * 0.50f, gy - gateH - signH * 0.45f),
                size = Size(gateW, signH),
                cornerRadius = CornerRadius(12f * scaleZ, 12f * scaleZ),
                style = Stroke(width = if (highlightGold) 6f else 3f * scaleZ)
            )
            if (gateZ > 0.12f) {
                drawGateOptionText(optionLabel, gx, gy - gateH + signH * 0.05f, gateZ, textPaint, darkText = highlightGold)
            }
        }

        GatePortalStyle.COSMIC_PORTAL_ARCH -> {
            // Reference 06: Cosmic stone/nebula arched portal with floating white pill badge on top
            val archPath = Path().apply {
                moveTo(gx - gateW * 0.48f, gy)
                lineTo(gx - gateW * 0.48f, gy - gateH * 0.55f)
                quadraticTo(gx, gy - gateH * 1.18f, gx + gateW * 0.48f, gy - gateH * 0.55f)
                lineTo(gx + gateW * 0.48f, gy)
                close()
            }
            drawPath(
                path = archPath,
                brush = Brush.radialGradient(
                    colors = listOf(glowColor.copy(alpha = 0.65f), Color(0xCC090D16)),
                    center = Offset(gx, gy - gateH * 0.45f),
                    radius = gateW * 0.7f
                )
            )
            drawPath(
                path = archPath,
                color = frameColor,
                style = Stroke(width = if (highlightGold) 8f else (4f + gateZ * 5f))
            )
            // Floating White Pill Label above the arch (Reference 06!)
            val pillH = (24f + gateZ * 28f)
            val pillW = gateW * 0.88f
            val pillTop = gy - gateH * 1.04f - pillH
            drawRoundRect(
                color = if (highlightGold) Color(0xFFFEF08A) else Color.White,
                topLeft = Offset(gx - pillW * 0.5f, pillTop),
                size = Size(pillW, pillH),
                cornerRadius = CornerRadius(10f * scaleZ, 10f * scaleZ)
            )
            if (gateZ > 0.12f) {
                drawGateOptionText(optionLabel, gx, pillTop + pillH * 0.52f, gateZ, textPaint, darkText = true)
            }
        }

        GatePortalStyle.GOLDEN_ISLAMIC_ARCH, GatePortalStyle.PHARAONIC_PILLAR_GATE, GatePortalStyle.ROYAL_MARBLE_ARCH -> {
            // Reference 07, 08, 10: Ornate Golden / Marble Archway with Pillars & Glowing Golden Portal
            val archPath = Path().apply {
                moveTo(gx - gateW * 0.48f, gy)
                lineTo(gx - gateW * 0.48f, gy - gateH * 0.65f)
                quadraticTo(gx, gy - gateH * 1.15f, gx + gateW * 0.48f, gy - gateH * 0.65f)
                lineTo(gx + gateW * 0.48f, gy)
                close()
            }
            drawPath(
                path = archPath,
                brush = Brush.verticalGradient(
                    colors = listOf(Color(0xDDFFFBEB), glowColor.copy(alpha = 0.78f), frameColor.copy(alpha = 0.55f))
                )
            )
            drawPath(
                path = archPath,
                brush = Brush.verticalGradient(listOf(Color(0xFFFEF08A), Color(0xFFD97706), Color(0xFF92400E))),
                style = Stroke(width = if (highlightGold) 9f else (5f + gateZ * 5f))
            )
            // Side columns
            val colW = 8f * scaleZ
            drawRect(Color(0xFFB45309), Offset(gx - gateW * 0.50f, gy - gateH * 0.70f), Size(colW, gateH * 0.70f))
            drawRect(Color(0xFFB45309), Offset(gx + gateW * 0.50f - colW, gy - gateH * 0.70f), Size(colW, gateH * 0.70f))

            if (gateZ > 0.12f) {
                drawGateOptionText(optionLabel, gx, gy - gateH * 0.44f, gateZ, textPaint, darkText = true)
            }
        }

        GatePortalStyle.NEON_HOLO_FRAME -> {
            // Reference 04, 05, 09, 11: Translucent Holographic Glass Gate with Checkmark Badge & Sci-Fi Frame
            drawRoundRect(
                color = glowColor.copy(alpha = if (highlightGold) 0.58f else 0.26f),
                topLeft = Offset(gx - gateW * 0.54f, gy - gateH - 6f),
                size = Size(gateW * 1.08f, gateH + 10f),
                cornerRadius = CornerRadius(16f * scaleZ, 16f * scaleZ)
            )
            drawRoundRect(
                brush = Brush.verticalGradient(
                    colors = listOf(frameColor.copy(alpha = 0.55f), Color(0xCC0F172A))
                ),
                topLeft = Offset(gx - gateW * 0.5f, gy - gateH),
                size = Size(gateW, gateH),
                cornerRadius = CornerRadius(14f * scaleZ, 14f * scaleZ)
            )
            drawRoundRect(
                color = frameColor,
                topLeft = Offset(gx - gateW * 0.5f, gy - gateH),
                size = Size(gateW, gateH),
                cornerRadius = CornerRadius(14f * scaleZ, 14f * scaleZ),
                style = Stroke(width = if (highlightGold) 8f else (3.5f + gateZ * 4.5f))
            )
            // Top circular checkmark badge (Reference 04!)
            val badgeR = (9f + gateZ * 11f)
            val badgeCenter = Offset(gx, gy - gateH)
            drawCircle(frameColor, radius = badgeR, center = badgeCenter)
            drawCircle(Color.White, radius = badgeR, center = badgeCenter, style = Stroke(width = 2f * scaleZ))

            if (gateZ > 0.12f) {
                drawGateOptionText(optionLabel, gx, gy - gateH * 0.48f, gateZ, textPaint, darkText = false)
            }
        }
    }
}

private fun DrawScope.drawGateOptionText(
    optionLabel: String,
    gx: Float,
    centerY: Float,
    gateZ: Float,
    textPaint: Paint,
    darkText: Boolean
) {
    drawIntoCanvas { canvas ->
        textPaint.textSize = (15f + gateZ * 25f).coerceIn(16f, 38f)
        if (darkText) {
            textPaint.color = android.graphics.Color.rgb(15, 23, 42)
            textPaint.setShadowLayer(3f, 0f, 1f, android.graphics.Color.argb(110, 255, 255, 255))
        } else {
            textPaint.color = android.graphics.Color.WHITE
            textPaint.setShadowLayer(6f, 0f, 2f, android.graphics.Color.argb(210, 0, 0, 0))
        }
        val lines = wrapShortGateText(optionLabel, maxChars = 12)
        val lineHeight = textPaint.textSize * 1.16f
        val startTextY = centerY - ((lines.size - 1) * lineHeight * 0.5f) + textPaint.textSize * 0.34f
        lines.forEachIndexed { idx, line ->
            canvas.nativeCanvas.drawText(line, gx, startTextY + idx * lineHeight, textPaint)
        }
    }
}

@Composable
private fun PowerUpHudButton(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    accent: Color,
    badge: String,
    active: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(16.dp),
        color = if (active) accent else Color.Black.copy(alpha = 0.55f),
        border = BorderStroke(1.5.dp, if (enabled || active) accent else Color.White.copy(alpha = 0.2f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = title, tint = Color.White, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(title, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(6.dp))
            Surface(
                shape = CircleShape,
                color = Color.White.copy(alpha = 0.22f)
            ) {
                Text(
                    text = badge,
                    color = Color.White,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }
    }
}

@Composable
private fun GoldenStageProgressBar(
    gatesCleared: Int,
    totalStandardGates: Int,
    isBossActive: Boolean
) {
    val progress = ((gatesCleared.toFloat() + if (isBossActive) 0.5f else 0f) / (totalStandardGates + 1f)).coerceIn(0f, 1f)
    Surface(
        shape = RoundedCornerShape(50),
        color = Color(0xCC1E1405),
        border = BorderStroke(1.5.dp, Color(0xFFF59E0B)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "البوابات $gatesCleared/$totalStandardGates",
                color = Color(0xFFFEF08A),
                fontSize = 11.sp,
                fontWeight = FontWeight.ExtraBold
            )
            Spacer(Modifier.width(10.dp))
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(10.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Color.White.copy(alpha = 0.16f))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(progress)
                        .height(10.dp)
                        .clip(RoundedCornerShape(50))
                        .background(Brush.horizontalGradient(listOf(Color(0xFFF59E0B), Color(0xFFFEF08A))))
                )
            }
            Spacer(Modifier.width(10.dp))
            Icon(
                imageVector = Icons.Default.EmojiEvents,
                contentDescription = "بوابة الزعيم وصندوق الكنز",
                tint = if (isBossActive) Color(0xFFFACC15) else Color(0xFFD97706),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun StageResultOverlayCard(
    summary: RunSummaryResult,
    character: GameCharacterId,
    onNextStage: () -> Unit,
    onRetryStage: () -> Unit,
    onExitToHub: () -> Unit
) {
    val stageSpec = StageThemeEngine.stageFor(summary.stageNumber)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.72f)),
        contentAlignment = Alignment.Center
    ) {
        Card(
            shape = RoundedCornerShape(26.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            border = BorderStroke(2.5.dp, if (summary.victory) Color(0xFFF59E0B) else Color(0xFFEF4444)),
            modifier = Modifier
                .fillMaxWidth()
                .padding(22.dp)
                .testTag("stage_result_card")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                CharacterPortraitCanvas(
                    character = character,
                    modifier = Modifier.size(92.dp)
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = if (summary.victory) "مبارك! أتممت المرحلة ${summary.stageNumber} 🏆" else "انتهت المحاولة • أحسنت!",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = if (summary.victory) Color(0xFFB45309) else Color(0xFF0F172A),
                    textAlign = TextAlign.Center
                )
                Text(
                    text = stageSpec.nameAr,
                    fontSize = 13.sp,
                    color = Color(0xFF64748B),
                    fontWeight = FontWeight.SemiBold
                )

                Spacer(Modifier.height(14.dp))

                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color(0xFFFEF3C7),
                    border = BorderStroke(1.dp, Color(0xFFFCD34D)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        SummaryStatRow("عملات المسار والبوابات", "+${summary.coinsEarnedFromTrack} عملة")
                        if (summary.victory) {
                            SummaryStatRow("مكافأة ${summary.chestNameAr}", "+${summary.bossChestBonus} عملة 🎁")
                        }
                        SummaryStatRow("إجمالي العملات المضافة", "+${summary.totalCoinsAdded} عملة 🪙", bold = true)
                        SummaryStatRow("الإجابات الصحيحة / أعلى Combo", "${summary.correctAnswers} صحيحة • x${summary.maxCombo}")
                        if (summary.newlyUnlockedStage != null) {
                            Text(
                                text = "🔓 تم فتح المرحلة ${summary.newlyUnlockedStage}: ${StageThemeEngine.stageFor(summary.newlyUnlockedStage).nameAr}!",
                                color = Color(0xFF047857),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.ExtraBold,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

                if (summary.victory && summary.stageNumber < TOTAL_GAME_STAGES) {
                    Button(
                        onClick = onNextStage,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1A6FE3)),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("next_stage_button")
                    ) {
                        Text(
                            text = "الانتقال للمرحلة التالية ➔",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onRetryStage,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("العب مجدداً", fontWeight = FontWeight.Bold)
                    }
                    OutlinedButton(
                        onClick = onExitToHub,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("مركز الألعاب", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun SummaryStatRow(label: String, value: String, bold: Boolean = false) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            color = Color(0xFF78350F),
            fontWeight = if (bold) FontWeight.ExtraBold else FontWeight.Medium
        )
        Text(
            text = value,
            fontSize = 13.sp,
            color = Color(0xFF92400E),
            fontWeight = FontWeight.ExtraBold
        )
    }
}

private fun DrawScope.draw3DGoldCoin(center: Offset, radius: Float, phase: Float, letterPaint: Paint) {
    val spinWidth = (0.62f + 0.38f * abs(cos(phase * 3.0f))) * radius
    // Ground drop shadow under coin
    drawOval(
        color = Color.Black.copy(alpha = 0.25f),
        topLeft = Offset(center.x - spinWidth * 0.9f, center.y + radius * 0.85f),
        size = Size(spinWidth * 1.8f, radius * 0.45f)
    )
    // 3D Gold Edge Thickness
    drawOval(
        color = Color(0xFFB45309),
        topLeft = Offset(center.x - spinWidth - 3.5f, center.y - radius),
        size = Size(spinWidth * 2f + 7f, radius * 2f)
    )
    // Main Metallic Gold Face
    drawOval(
        brush = Brush.linearGradient(
            colors = listOf(Color(0xFFFFFBEB), Color(0xFFFACC15), Color(0xFFD97706)),
            start = Offset(center.x - spinWidth, center.y - radius),
            end = Offset(center.x + spinWidth, center.y + radius)
        ),
        topLeft = Offset(center.x - spinWidth, center.y - radius),
        size = Size(spinWidth * 2f, radius * 2f)
    )
    // Inner Embossed Gold Rim
    drawOval(
        color = Color(0xFFB45309).copy(alpha = 0.65f),
        topLeft = Offset(center.x - spinWidth * 0.72f, center.y - radius * 0.72f),
        size = Size(spinWidth * 1.44f, radius * 1.44f),
        style = Stroke(width = 2.2f)
    )
    // Embossed Arabic "ع" emblem inside coin (Reference 04!)
    if (radius > 13f && spinWidth > radius * 0.55f) {
        drawIntoCanvas { canvas ->
            letterPaint.textSize = radius * 0.92f
            canvas.nativeCanvas.drawText("ع", center.x, center.y + radius * 0.32f, letterPaint)
        }
    }
}

private fun DrawScope.drawGoldenTreasureChest(
    center: Offset,
    scale: Float,
    openProgress: Float,
    timeSec: Float
) {
    // Radiant golden aura behind chest
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(Color(0xEEFEF08A), Color(0x77F59E0B), Color.Transparent),
            center = center,
            radius = (76f + openProgress * 58f) * scale
        ),
        radius = (76f + openProgress * 58f) * scale,
        center = center
    )

    val chestW = 82f * scale
    val chestH = 48f * scale
    // Chest base box
    drawRoundRect(
        brush = Brush.verticalGradient(listOf(Color(0xFFD97706), Color(0xFF78350F))),
        topLeft = Offset(center.x - chestW * 0.5f, center.y - chestH * 0.25f),
        size = Size(chestW, chestH * 0.85f),
        cornerRadius = CornerRadius(8f * scale, 8f * scale)
    )
    // Gold straps & keyhole lock
    drawRoundRect(
        color = Color(0xFFFACC15),
        topLeft = Offset(center.x - chestW * 0.5f, center.y - chestH * 0.25f),
        size = Size(chestW, chestH * 0.85f),
        cornerRadius = CornerRadius(8f * scale, 8f * scale),
        style = Stroke(width = 4.5f * scale)
    )
    drawRect(Color(0xFFFACC15), Offset(center.x - chestW * 0.28f, center.y - chestH * 0.25f), Size(6f * scale, chestH * 0.85f))
    drawRect(Color(0xFFFACC15), Offset(center.x + chestW * 0.22f, center.y - chestH * 0.25f), Size(6f * scale, chestH * 0.85f))
    drawCircle(Color(0xFFFEF08A), radius = 6.5f * scale, center = Offset(center.x, center.y + 4f * scale))

    // Opening Lid lifting upward with glowing light beam inside
    val lidLift = openProgress * 26f * scale
    if (openProgress > 0.05f) {
        drawOval(
            color = Color(0xFFFFFBEB).copy(alpha = (openProgress * 0.9f).coerceAtMost(0.95f)),
            topLeft = Offset(center.x - chestW * 0.42f, center.y - chestH * 0.4f - lidLift * 0.4f),
            size = Size(chestW * 0.84f, chestH * 0.4f)
        )
    }
    drawRoundRect(
        brush = Brush.verticalGradient(listOf(Color(0xFFFEF08A), Color(0xFFD97706))),
        topLeft = Offset(center.x - chestW * 0.52f, center.y - chestH * 0.62f - lidLift),
        size = Size(chestW * 1.04f, chestH * 0.42f),
        cornerRadius = CornerRadius(10f * scale, 10f * scale)
    )

    // 3 Floating Golden Championship Trophies flanking the chest (Reference 10!)
    val bob = sin(timeSec * 3.2f) * 6f * scale
    for (side in listOf(-1f, 0f, 1f)) {
        val tx = center.x + side * 64f * scale
        val ty = center.y + (if (side == 0f) 46f else 12f) * scale + bob * (if (side == 0f) 1f else side)
        // Trophy Cup Bowl
        val cup = Path().apply {
            moveTo(tx - 13f * scale, ty - 14f * scale)
            lineTo(tx + 13f * scale, ty - 14f * scale)
            quadraticTo(tx + 10f * scale, ty + 4f * scale, tx, ty + 4f * scale)
            quadraticTo(tx - 10f * scale, ty + 4f * scale, tx - 13f * scale, ty - 14f * scale)
            close()
        }
        drawPath(cup, Brush.linearGradient(listOf(Color(0xFFFEF08A), Color(0xFFD97706))))
        // Trophy Stem & Pedestal Base
        drawRect(Color(0xFFD97706), Offset(tx - 3f * scale, ty + 3f * scale), Size(6f * scale, 10f * scale))
        drawRoundRect(Color(0xFF92400E), Offset(tx - 10f * scale, ty + 12f * scale), Size(20f * scale, 6f * scale), CornerRadius(3f, 3f))
    }
}

private fun wrapShortGateText(text: String, maxChars: Int): List<String> {
    if (text.length <= maxChars) return listOf(text)
    val words = text.split(' ')
    val lines = mutableListOf<String>()
    var current = StringBuilder()
    for (w in words) {
        if (current.isNotEmpty() && current.length + 1 + w.length > maxChars) {
            lines.add(current.toString())
            current = StringBuilder(w)
        } else {
            if (current.isNotEmpty()) current.append(' ')
            current.append(w)
        }
    }
    if (current.isNotEmpty()) lines.add(current.toString())
    return lines.take(3)
}
