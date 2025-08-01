package com.example.mindvault.ui

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.gestures.FlingBehavior
import androidx.compose.foundation.gestures.ScrollableDefaults
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Lock
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.*
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.border
import coil.compose.rememberAsyncImagePainter
import com.example.mindvault.data.FocusManager
import com.example.mindvault.model.AppInfo
import com.example.mindvault.model.FocusType
import com.example.mindvault.model.TimeSlot
import com.example.mindvault.ui.theme.MindVaultTheme
import com.example.mindvault.ui.PremiumTab
import java.time.LocalTime
import java.util.UUID
import java.time.format.DateTimeFormatter
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import android.media.AudioAttributes
import android.media.SoundPool
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalContext
import com.example.mindvault.R
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

class FocusModeSetupActivity : ComponentActivity() {

    private val viewModel: FocusModeSetupViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        viewModel.loadConfiguration(this)

        setContent {
            MindVaultTheme {
                var isSaving by remember { mutableStateOf(false) }
                
                FocusModeSetupScreen(
                    viewModel = viewModel,
                    isSaving = isSaving,
                    onBackPressed = { 
                        if (!isSaving) {
                            finish()
                        }
                    },
                    onSaveConfiguration = {
                        if (!isSaving) {
                            isSaving = true
                            
                            try {
                                Log.d("FocusModeSetupActivity", "Starting save operation...")
                                
                                // Save configuration
                                viewModel.saveConfiguration(this@FocusModeSetupActivity)
                                
                                // Show success message
                                Toast.makeText(
                                    this@FocusModeSetupActivity, 
                                    "Configuration saved successfully!", 
                                    Toast.LENGTH_SHORT
                                ).show()
                                
                                // Small delay to ensure everything is saved
                                android.os.Handler(mainLooper).postDelayed({
                                    finish()
                                }, 500)
                                
                            } catch (e: Exception) {
                                Log.e("FocusModeSetupActivity", "Error saving configuration", e)
                                Toast.makeText(
                                    this@FocusModeSetupActivity, 
                                    "Error saving configuration", 
                                    Toast.LENGTH_SHORT
                                ).show()
                                isSaving = false
                            }
                        }
                    }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.loadConfiguration(this)
    }
}

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun FocusModeSetupScreen(
    viewModel: FocusModeSetupViewModel,
    isSaving: Boolean,
    onBackPressed: () -> Unit,
    onSaveConfiguration: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF0F0F23),
                        Color(0xFF1A1A2E),
                        Color(0xFF16213E)
                    )
                )
            )
    ) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val activeTimeSlot by FocusManager.activeSlotFlow.collectAsStateWithLifecycle()
    var showTimeSlotDialog by remember { mutableStateOf(false) }
    var showAppSelectionDialog by remember { mutableStateOf(false) }
    val pagerState = rememberPagerState(pageCount = { 2 })
    val coroutineScope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()
    ) {
        TopAppBar(
            title = { 
                Text(
                    text = "Focus Mode Setup",
                    color = Color.White,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 24.sp,
                    style = MaterialTheme.typography.headlineMedium.copy(
                        shadow = androidx.compose.ui.graphics.Shadow(
                            color = Color(0xFF6C63FF).copy(alpha = 0.5f),
                            offset = androidx.compose.ui.geometry.Offset(0f, 4f),
                            blurRadius = 8f
                        )
                    )
                )
            },
            navigationIcon = {
                IconButton(
                    onClick = onBackPressed,
                    enabled = !isSaving
                ) {
                    Icon(
                        Icons.Default.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
            modifier = Modifier
                .background(
                    brush = Brush.horizontalGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.1f),
                            Color.White.copy(alpha = 0.05f)
                        )
                    ),
                    shape = RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp)
                )
        )

        // Premium Tab Selector
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 16.dp),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color.Transparent)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        brush = Brush.linearGradient(
                            colors = listOf(
                                Color.White.copy(alpha = 0.1f),
                                Color.White.copy(alpha = 0.05f)
                            )
                        )
                    )
                    .border(
                        width = 1.dp,
                        brush = Brush.linearGradient(
                            colors = listOf(
                                Color.White.copy(alpha = 0.3f),
                                Color.White.copy(alpha = 0.1f)
                            )
                        ),
                        shape = RoundedCornerShape(20.dp)
                    )
                    .padding(4.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    PremiumTab(
                        title = "Time Slots",
                        isSelected = pagerState.currentPage == 0,
                        enabled = !isSaving,
                        modifier = Modifier.weight(1f),
                        onClick = { coroutineScope.launch { pagerState.animateScrollToPage(0) } }
                    )
                    PremiumTab(
                        title = "App Selection",
                        isSelected = pagerState.currentPage == 1,
                        enabled = !isSaving,
                        modifier = Modifier.weight(1f),
                        onClick = { coroutineScope.launch { pagerState.animateScrollToPage(1) } }
                    )
                }
            }
        }

        HorizontalPager(
            state = pagerState,
            modifier = Modifier.weight(1f)
        ) {
            page ->
            when (page) {
                0 -> TimeSlotTab(
                    timeSlots = uiState.timeSlots,
                    activeTimeSlot = activeTimeSlot,
                    onAddTimeSlot = { if (!isSaving) showTimeSlotDialog = true },
                    onDeleteTimeSlot = { viewModel.deleteTimeSlot(it) }
                )
                1 -> AppSelectionTab(
                    installedApps = uiState.installedApps,
                    selectedApps = uiState.selectedApps,
                    onShowAppSelection = { if (!isSaving) showAppSelectionDialog = true }
                )
            }
        }

        Button(
            onClick = onSaveConfiguration,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .height(56.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
            shape = RoundedCornerShape(12.dp),
            enabled = !isSaving
        ) {
            if (isSaving) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = Color.White
                )
            } else {
                Text("Save Configuration", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
            }
        }
    }

    if (showTimeSlotDialog) {
        TimeSlotDialog(
            onDismiss = { showTimeSlotDialog = false },
            onAddTimeSlot = {
                viewModel.addTimeSlot(it)
                showTimeSlotDialog = false
            }
        )
    }

    if (showAppSelectionDialog) {
        AppSelectionDialog(
            installedApps = uiState.installedApps,
            selectedApps = uiState.selectedApps,
            isFocusModeActive = uiState.isFocusModeActive,
            onDismiss = { showAppSelectionDialog = false },
            onAppsSelected = {
                viewModel.onAppsSelected(it)
                showAppSelectionDialog = false
            }
        )
    }
    }
}

@Composable
fun TimeSlotTab(
    timeSlots: List<TimeSlot>,
    activeTimeSlot: TimeSlot?,
    onAddTimeSlot: () -> Unit,
    onDeleteTimeSlot: (TimeSlot) -> Unit
) {
    val sortedTimeSlots = remember(timeSlots, activeTimeSlot) {
        val now = LocalTime.now()
        val active = activeTimeSlot
        val others = timeSlots.filter { it.id != active?.id }

        val (upcoming, past) = others.partition { it.startTime.isAfter(now) }

        val sortedUpcoming = upcoming.sortedBy { it.startTime }
        val sortedPast = past.sortedBy { it.startTime } // Also sort past for consistency

        buildList {
            active?.let { add(it) }
            addAll(sortedUpcoming)
            addAll(sortedPast)
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                text = "Configure Time Slots",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            Text(
                text = "Study Time: Selected apps will be blocked\nRest Time: Only selected apps will be allowed",
                fontSize = 14.sp,
                color = Color.White.copy(alpha = 0.8f),
                modifier = Modifier.padding(bottom = 16.dp)
            )
        }
        items(sortedTimeSlots) { slot ->
            TimeSlotCard(
                timeSlot = slot,
                isActive = activeTimeSlot?.id == slot.id,
                onDelete = { onDeleteTimeSlot(slot) }
            )
        }
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onAddTimeSlot() },
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color.White.copy(alpha = 0.1f)
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = "Add Time Slot",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Add Time Slot",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.White
                    )
                }
            }
        }
    }
}

@Composable
fun TimeSlotCard(
    timeSlot: TimeSlot,
    isActive: Boolean = false,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isActive) {
                Color(0xFF10B981).copy(alpha = 0.4f) // Green tint for active slots
            } else {
                when (timeSlot.type) {
                    FocusType.STUDY_TIME -> Color(0xFF1E40AF).copy(alpha = 0.3f)
                    FocusType.REST_TIME -> Color(0xFF059669).copy(alpha = 0.3f)
                    else -> Color.Gray.copy(alpha = 0.3f)
                }
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = when (timeSlot.type) {
                            FocusType.STUDY_TIME -> "📚 Study Time"
                            FocusType.REST_TIME -> "🎮 Rest Time"
                            else -> "Focus Time"
                        },
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )
                    if (isActive) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "• ACTIVE",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF10B981)
                        )
                    }
                }
                Text(
                    text = "${timeSlot.startTime.format(DateTimeFormatter.ofPattern("HH:mm"))} - ${timeSlot.endTime.format(DateTimeFormatter.ofPattern("HH:mm"))}",
                    fontSize = 14.sp,
                    color = Color.White.copy(alpha = 0.8f)
                )
            }
            if (!isActive) {
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Delete",
                        tint = Color.White.copy(alpha = 0.7f)
                    )
                }
            } else {
                // Show a lock icon to indicate this slot cannot be deleted
                Icon(
                    Icons.Default.Lock,
                    contentDescription = "Active - Cannot Delete",
                    tint = Color(0xFF10B981),
                    modifier = Modifier.padding(12.dp)
                )
            }
        }
    }
}

@Composable
fun AppSelectionTab(
    installedApps: List<AppInfo>,
    selectedApps: List<String>,
    onShowAppSelection: () -> Unit
) {
    val selectedAppInfo = installedApps.filter { it.packageName in selectedApps }
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                text = "Selected Apps (${selectedApps.size})",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            Text(
                text = "These apps will be controlled during focus sessions",
                fontSize = 14.sp,
                color = Color.White.copy(alpha = 0.8f),
                modifier = Modifier.padding(bottom = 16.dp)
            )
        }
        items(selectedAppInfo) { app ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Image(
                    painter = rememberAsyncImagePainter(model = app.icon),
                    contentDescription = app.appName,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                )
                Spacer(modifier = Modifier.width(16.dp))
                Text(
                    text = app.appName,
                    fontSize = 16.sp,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onShowAppSelection() },
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color.White.copy(alpha = 0.1f)
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = "Select Apps",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Select Apps",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.White
                    )
                }
            }
        }
    }
}

@Composable
fun AppListItem(
    appInfo: AppInfo,
    isSelected: Boolean,
    isLocked: Boolean,
    onSelectionChanged: (Boolean) -> Unit
) {
    val rowAlpha = if (isLocked) 0.6f else 1f
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !isLocked) {
                onSelectionChanged(!isSelected)
            }
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .alpha(rowAlpha),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Image(
            painter = rememberAsyncImagePainter(
                model = appInfo.icon,
                onSuccess = { /* Icon loaded successfully */ },
                onError = { /* Use default icon on error */ }
            ),
            contentDescription = appInfo.appName,
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
        )
        
        Spacer(modifier = Modifier.width(16.dp))
        
        Text(
            text = appInfo.appName,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            fontSize = 16.sp
        )
        
        Checkbox(
            checked = isSelected,
            onCheckedChange = { checked ->
                if (!isLocked) {
                    onSelectionChanged(checked)
                }
            },
            enabled = !isLocked
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppSelectionDialog(
    installedApps: List<AppInfo>,
    selectedApps: List<String>,
    isFocusModeActive: Boolean,
    onDismiss: () -> Unit,
    onAppsSelected: (List<String>) -> Unit
) {
    var tempSelectedApps by remember { mutableStateOf(selectedApps.toSet()) }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Select Apps") },
        text = {
            val flingBehavior: FlingBehavior = ScrollableDefaults.flingBehavior()

            LazyColumn(
                contentPadding = PaddingValues(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                flingBehavior = flingBehavior
            ) {
                if (installedApps.isEmpty()) {
                    item(key = "loading", contentType = "loading") {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    }
                } else {
                    items(
                        items = installedApps,
                        key = { it.packageName },
                        contentType = { "app_item" }
                    ) { appInfo ->
                        val isLocked = isFocusModeActive && selectedApps.contains(appInfo.packageName)
                        val isSelected = tempSelectedApps.contains(appInfo.packageName)
                        
                        AppListItem(
                            appInfo = appInfo,
                            isSelected = isSelected,
                            isLocked = isLocked,
                            onSelectionChanged = { selected ->
                                if (!isLocked) {
                                    tempSelectedApps = if (selected) {
                                        tempSelectedApps + appInfo.packageName
                                    } else {
                                        tempSelectedApps - appInfo.packageName
                                    }
                                }
                            }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onAppsSelected(tempSelectedApps.toList()) }
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimeSlotDialog(
    onDismiss: () -> Unit,
    onAddTimeSlot: (TimeSlot) -> Unit
) {
        val context = LocalContext.current
    val hapticFeedback = LocalHapticFeedback.current

    val soundPool = remember {
        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        SoundPool.Builder()
            .setMaxStreams(1)
            .setAudioAttributes(attributes)
            .build()
    }

    // TODO: Add a 'tick_sound.mp3' or similar to the 'res/raw' folder to enable sound effects.
    // var soundId by remember { mutableStateOf(0) }
    // LaunchedEffect(Unit) {
    //     soundId = soundPool.load(context, R.raw.tick_sound, 1)
    // }

    DisposableEffect(Unit) {
        onDispose {
            soundPool.release()
        }
    }

    var selectedType by remember { mutableStateOf(FocusType.STUDY_TIME) }
    var isSettingStartTime by remember { mutableStateOf(true) }
    val startTimeState = rememberTimePickerState(is24Hour = false)
        val endTimeState = rememberTimePickerState(is24Hour = false)

    val activeTimePickerState = if (isSettingStartTime) startTimeState else endTimeState

    LaunchedEffect(activeTimePickerState) {
        snapshotFlow { activeTimePickerState.hour to activeTimePickerState.minute }
            .drop(1) // Skip initial value
            .collect {
                hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                // TODO: Uncomment when tick_sound.mp3 is added to res/raw folder
                // if (soundId != 0) soundPool.play(soundId, 1f, 1f, 0, 0, 1f)
            }
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier.fillMaxWidth(0.95f),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF2c2f33))
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = if (isSettingStartTime) "Set Start Time" else "Set End Time",
                    style = MaterialTheme.typography.headlineSmall, 
                    color = Color.White, 
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(24.dp))

                // Time Pickers
                
                Spacer(modifier = Modifier.height(16.dp))
                TimePicker(
                    state = activeTimePickerState,
                    colors = TimePickerDefaults.colors(
                        periodSelectorBorderColor = Color.Transparent
                    )
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Type selection
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FocusType.values().forEach { type ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(CircleShape)
                                .clickable { selectedType = type }
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            RadioButton(
                                selected = selectedType == type,
                                onClick = { selectedType = type },
                                colors = RadioButtonDefaults.colors(
                                    selectedColor = Color(0xFF7289da),
                                    unselectedColor = Color.Gray
                                )
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (type == FocusType.STUDY_TIME) "Study" else "Rest",
                                color = Color.White, 
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))

                // Action Buttons
                                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    if (isSettingStartTime) {
                        Button(
                            onClick = onDismiss, 
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray)
                        ) {
                            Text("Cancel")
                        }
                        Button(
                            onClick = { isSettingStartTime = false },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7289da))
                        ) {
                            Text("End Time", fontWeight = FontWeight.Bold)
                        }
                    } else {
                        Button(
                            onClick = { isSettingStartTime = true }, 
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray)
                        ) {
                            Text("Back")
                        }
                        Button(
                            onClick = {
                                val startTime = LocalTime.of(startTimeState.hour, startTimeState.minute)
                                val endTime = LocalTime.of(endTimeState.hour, endTimeState.minute)
                                val timeSlot = TimeSlot(
                                    id = UUID.randomUUID().toString(),
                                    startTime = startTime,
                                    endTime = endTime,
                                    type = selectedType
                                )
                                onAddTimeSlot(timeSlot)
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7289da))
                        ) {
                            Text("Save", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}
