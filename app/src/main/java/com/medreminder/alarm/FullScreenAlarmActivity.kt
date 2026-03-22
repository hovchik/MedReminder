package com.medreminder.alarm

import android.app.NotificationManager
import androidx.activity.OnBackPressedCallback
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.*
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import androidx.activity.compose.setContent
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Snooze
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.medreminder.R
import com.medreminder.data.local.AppDatabase
import kotlinx.coroutines.*
import java.text.SimpleDateFormat
import java.util.*

data class AlarmMedication(
    val doseLogId: Long,
    val scheduleId: Long,
    val medicationId: Long,
    val name: String,
    val dosage: String,
    val color: String
)

class FullScreenAlarmActivity : ComponentActivity() {

    companion object {
        @Volatile
        private var currentInstance: FullScreenAlarmActivity? = null

        const val EXTRA_DOSE_LOG_IDS = "dose_log_ids"
        const val EXTRA_SCHEDULE_IDS = "schedule_ids"
        const val EXTRA_MEDICATION_IDS = "medication_ids"
        const val EXTRA_MEDICATION_NAMES = "medication_names"
        const val EXTRA_MEDICATION_DOSAGES = "medication_dosages"
        const val EXTRA_MEDICATION_COLORS = "medication_colors"

        /** Called from TakenReceiver / SnoozeReceiver to dismiss the alarm screen. */
        fun finishIfShowing() {
            currentInstance?.let {
                it.stopAlarm()
                it.finish()
            }
        }
    }

    private var mediaPlayer: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private val _medications = mutableStateOf<List<AlarmMedication>>(emptyList())
    private var medications: List<AlarmMedication>
        get() = _medications.value
        set(value) { _medications.value = value }
    private var isProcessing = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        currentInstance = this

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }
        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
            WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
            WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
        )

        medications = parseMedications(intent)

        // Prevent dismissal with back button - user must choose an action
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() { /* no-op: user must choose an action */ }
        })

        startAlarmSound()
        startVibration()

        setContent {
            val currentMeds = _medications.value
            if (currentMeds.size == 1) {
                AlarmScreen(
                    medicationName = currentMeds.first().name,
                    medicationDosage = currentMeds.first().dosage,
                    onTaken = { handleTakenAll() },
                    onSnooze = { handleSnoozeAll() },
                    onCancel = { handleCancelAll() }
                )
            } else if (currentMeds.isNotEmpty()) {
                CombinedAlarmScreen(
                    medications = currentMeds,
                    onTakenAll = { handleTakenAll() },
                    onSnoozeAll = { handleSnoozeAll() },
                    onCancelAll = { handleCancelAll() }
                )
            }
        }
    }

    private fun parseMedications(intent: android.content.Intent): List<AlarmMedication> {
        val doseLogIds = intent.getLongArrayExtra(EXTRA_DOSE_LOG_IDS)
        val scheduleIds = intent.getLongArrayExtra(EXTRA_SCHEDULE_IDS)
        val medicationIds = intent.getLongArrayExtra(EXTRA_MEDICATION_IDS)
        val names = intent.getStringArrayExtra(EXTRA_MEDICATION_NAMES)
        val dosages = intent.getStringArrayExtra(EXTRA_MEDICATION_DOSAGES)
        val colors = intent.getStringArrayExtra(EXTRA_MEDICATION_COLORS)

        if (doseLogIds == null || scheduleIds == null || medicationIds == null ||
            names == null || dosages == null || colors == null
        ) {
            // Fallback: try legacy single-medication extras
            val doseLogId = intent.getLongExtra(AlarmScheduler.EXTRA_DOSE_LOG_ID, -1)
            val scheduleId = intent.getLongExtra(AlarmScheduler.EXTRA_SCHEDULE_ID, -1)
            val medicationId = intent.getLongExtra(AlarmScheduler.EXTRA_MEDICATION_ID, -1)
            val name = intent.getStringExtra(AlarmScheduler.EXTRA_MEDICATION_NAME) ?: "Medication"
            val dosage = intent.getStringExtra(AlarmScheduler.EXTRA_MEDICATION_DOSAGE) ?: ""
            val color = intent.getStringExtra(AlarmScheduler.EXTRA_MEDICATION_COLOR) ?: "#4A90D9"
            return listOf(AlarmMedication(doseLogId, scheduleId, medicationId, name, dosage, color))
        }

        return doseLogIds.indices.map { i ->
            AlarmMedication(
                doseLogId = doseLogIds[i],
                scheduleId = scheduleIds[i],
                medicationId = medicationIds[i],
                name = names[i],
                dosage = dosages[i],
                color = colors[i]
            )
        }
    }

    private fun startAlarmSound() {
        try {
            val alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            mediaPlayer = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                setDataSource(this@FullScreenAlarmActivity, alarmUri)
                isLooping = true
                prepare()
                start()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun startVibration() {
        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vm = getSystemService(VibratorManager::class.java)
            vm?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(VIBRATOR_SERVICE) as? Vibrator
        }
        val pattern = longArrayOf(0, 800, 400, 800, 400, 800, 1000)
        vibrator?.vibrate(VibrationEffect.createWaveform(pattern, 0))
    }

    private fun stopAlarm() {
        mediaPlayer?.apply {
            if (isPlaying) stop()
            release()
        }
        mediaPlayer = null
        vibrator?.cancel()
        vibrator = null

        val nm = getSystemService(NotificationManager::class.java)
        // Cancel combined notification
        nm.cancel(AlarmReceiver.COMBINED_NOTIFICATION_ID)
        // Cancel individual notifications
        for (med in medications) {
            nm.cancel(AlarmReceiver.NOTIFICATION_ID_BASE + med.doseLogId.toInt())
        }
    }

    private fun handleTakenAll() {
        if (isProcessing) return
        isProcessing = true
        stopAlarm()
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val db = AppDatabase.getInstance(this@FullScreenAlarmActivity)
                for (med in medications) {
                    db.doseLogDao().updateDoseStatus(med.doseLogId, "taken")
                    db.medicationDao().decrementStock(med.medicationId)
                }
                for (med in medications) {
                    try {
                        CaregiverNotificationHelper.notifyCaregiversOnTaken(
                            applicationContext, db, med.medicationId, med.name
                        )
                    } catch (e: Exception) { e.printStackTrace() }
                }
            } catch (e: Exception) { e.printStackTrace() }
            withContext(Dispatchers.Main) { finish() }
        }
    }

    private fun handleSnoozeAll() {
        if (isProcessing) return
        isProcessing = true
        stopAlarm()
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val db = AppDatabase.getInstance(this@FullScreenAlarmActivity)
                val snoozeUntil = System.currentTimeMillis() + 10 * 60 * 1000
                val scheduler = AlarmScheduler(applicationContext, db.scheduleDao())

                for (med in medications) {
                    db.doseLogDao().snoozeDose(med.doseLogId, snoozeUntil)
                    scheduler.scheduleSnoozeAlarm(
                        med.doseLogId, med.scheduleId, med.medicationId,
                        med.name, med.dosage, med.color, 10
                    )
                }
            } catch (e: Exception) { e.printStackTrace() }
            withContext(Dispatchers.Main) { finish() }
        }
    }

    private fun handleCancelAll() {
        if (isProcessing) return
        isProcessing = true
        stopAlarm()
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val db = AppDatabase.getInstance(this@FullScreenAlarmActivity)
                for (med in medications) {
                    db.doseLogDao().updateDoseStatus(med.doseLogId, "skipped")
                }
                for (med in medications) {
                    try {
                        CaregiverNotificationHelper.notifyCaregiversOnSkipped(
                            applicationContext, db, med.medicationId, med.name
                        )
                    } catch (e: Exception) { e.printStackTrace() }
                }
            } catch (e: Exception) { e.printStackTrace() }
            withContext(Dispatchers.Main) { finish() }
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        // Activity already showing; update medication list if a new/concurrent alarm arrives
        val updated = parseMedications(intent)
        if (updated.isNotEmpty()) {
            medications = updated
        }
    }

    override fun onDestroy() {
        if (currentInstance == this) currentInstance = null
        stopAlarm()
        super.onDestroy()
    }

}

@Composable
fun AlarmScreen(
    medicationName: String,
    medicationDosage: String,
    onTaken: () -> Unit,
    onSnooze: () -> Unit,
    onCancel: () -> Unit
) {
    val pulseAnim = rememberInfiniteTransition(label = "pulse")
    val scale by pulseAnim.animateFloat(
        initialValue = 1f, targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse
        ), label = "scale"
    )

    var currentTime by remember { mutableStateOf(SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date())) }
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(30_000L)
            currentTime = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date())
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF1A1A2E),
                        Color(0xFF16213E),
                        Color(0xFF0F3460)
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp),
            modifier = Modifier.padding(32.dp)
        ) {
            // Time
            Text(
                text = currentTime,
                fontSize = 20.sp,
                color = Color.White.copy(alpha = 0.7f)
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Pulsing pill icon
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .scale(scale)
                    .background(
                        Color(0xFF4A90D9).copy(alpha = 0.2f),
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .background(Color(0xFF4A90D9).copy(alpha = 0.4f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text("\uD83D\uDC8A", fontSize = 40.sp)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Medication info
            Text(
                text = stringResource(R.string.time_to_take),
                fontSize = 18.sp,
                color = Color.White.copy(alpha = 0.7f)
            )
            Text(
                text = medicationName,
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                textAlign = TextAlign.Center
            )
            if (medicationDosage.isNotBlank()) {
                Text(
                    text = medicationDosage,
                    fontSize = 20.sp,
                    color = Color.White.copy(alpha = 0.8f)
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            // TAKEN button - large and prominent
            Button(
                onClick = onTaken,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF2ECC71)
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(28.dp))
                Spacer(modifier = Modifier.width(12.dp))
                Text(stringResource(R.string.i_took_it), fontSize = 20.sp, fontWeight = FontWeight.Bold)
            }

            // Snooze and Skip row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onSnooze,
                    modifier = Modifier
                        .weight(1f)
                        .height(56.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = Color(0xFFF39C12)
                    ),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Icon(Icons.Default.Snooze, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.snooze_10m), fontSize = 16.sp)
                }

                OutlinedButton(
                    onClick = onCancel,
                    modifier = Modifier
                        .weight(1f)
                        .height(56.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = Color(0xFFE74C3C)
                    ),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Icon(Icons.Default.Close, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.cancel), fontSize = 16.sp)
                }
            }
        }
    }
}

@Composable
fun CombinedAlarmScreen(
    medications: List<AlarmMedication>,
    onTakenAll: () -> Unit,
    onSnoozeAll: () -> Unit,
    onCancelAll: () -> Unit
) {
    val pulseAnim = rememberInfiniteTransition(label = "pulse")
    val scale by pulseAnim.animateFloat(
        initialValue = 1f, targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse
        ), label = "scale"
    )

    var currentTime by remember { mutableStateOf(SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date())) }
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(30_000L)
            currentTime = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date())
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF1A1A2E),
                        Color(0xFF16213E),
                        Color(0xFF0F3460)
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier
                .padding(32.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // Time
            Text(
                text = currentTime,
                fontSize = 20.sp,
                color = Color.White.copy(alpha = 0.7f)
            )

            Spacer(modifier = Modifier.height(4.dp))

            // Pulsing pill icon
            Box(
                modifier = Modifier
                    .size(100.dp)
                    .scale(scale)
                    .background(
                        Color(0xFF4A90D9).copy(alpha = 0.2f),
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .background(Color(0xFF4A90D9).copy(alpha = 0.4f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text("\uD83D\uDC8A", fontSize = 32.sp)
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Header
            Text(
                text = stringResource(R.string.time_to_take),
                fontSize = 18.sp,
                color = Color.White.copy(alpha = 0.7f)
            )
            Text(
                text = stringResource(R.string.medications_count, medications.size),
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Medication cards
            for (med in medications) {
                MedicationCard(name = med.name, dosage = med.dosage, color = med.color)
            }

            Spacer(modifier = Modifier.height(16.dp))

            // TAKEN ALL button
            Button(
                onClick = onTakenAll,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF2ECC71)
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(28.dp))
                Spacer(modifier = Modifier.width(12.dp))
                Text(stringResource(R.string.i_took_all), fontSize = 20.sp, fontWeight = FontWeight.Bold)
            }

            // Snooze and Skip row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onSnoozeAll,
                    modifier = Modifier
                        .weight(1f)
                        .height(56.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = Color(0xFFF39C12)
                    ),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Icon(Icons.Default.Snooze, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.snooze_10m), fontSize = 16.sp)
                }

                OutlinedButton(
                    onClick = onCancelAll,
                    modifier = Modifier
                        .weight(1f)
                        .height(56.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = Color(0xFFE74C3C)
                    ),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Icon(Icons.Default.Close, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.cancel_all), fontSize = 16.sp)
                }
            }
        }
    }
}

@Composable
private fun MedicationCard(name: String, dosage: String, color: String) {
    val medColor = try {
        Color(android.graphics.Color.parseColor(color))
    } catch (e: Exception) {
        Color(0xFF4A90D9)
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = Color.White.copy(alpha = 0.1f)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Color indicator
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(medColor.copy(alpha = 0.3f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text("\uD83D\uDC8A", fontSize = 20.sp)
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = name,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                if (dosage.isNotBlank()) {
                    Text(
                        text = dosage,
                        fontSize = 14.sp,
                        color = Color.White.copy(alpha = 0.7f)
                    )
                }
            }
        }
    }
}
