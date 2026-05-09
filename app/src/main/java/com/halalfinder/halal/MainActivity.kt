package com.halalfinder.halal

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import coil.compose.AsyncImage
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.halalfinder.halal.ui.theme.HalalFinderTheme
import com.halalfinder.halal.ui.theme.HalalGreenPrimary
import java.util.concurrent.Executors

enum class ThemeMode { LIGHT, DARK, AUTO }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            var themeMode by rememberSaveable { mutableStateOf(ThemeMode.AUTO) }
            var accentColorInt by rememberSaveable { mutableIntStateOf(HalalGreenPrimary.toArgb()) }
            var profileImageUri by rememberSaveable { mutableStateOf<String?>(null) }
            var language by rememberSaveable { mutableStateOf("English") }

            val useDarkTheme = when (themeMode) {
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
                ThemeMode.AUTO -> isSystemInDarkTheme()
            }

            HalalFinderTheme(
                darkTheme = useDarkTheme,
                primaryColor = Color(accentColorInt)
            ) {
                HalalFinderApp(
                    currentThemeMode = themeMode,
                    onThemeChange = { themeMode = it },
                    accentColor = Color(accentColorInt),
                    onAccentColorChange = { accentColorInt = it.toArgb() },
                    profileImageUri = profileImageUri,
                    onProfileImageChange = { profileImageUri = it },
                    language = language,
                    onLanguageChange = { language = it }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HalalFinderApp(
    currentThemeMode: ThemeMode,
    onThemeChange: (ThemeMode) -> Unit,
    accentColor: Color,
    onAccentColorChange: (Color) -> Unit,
    profileImageUri: String?,
    onProfileImageChange: (String?) -> Unit,
    language: String,
    onLanguageChange: (String) -> Unit
) {
    var currentDestination by rememberSaveable { mutableStateOf(AppDestinations.HOME) }

    NavigationSuiteScaffold(
        navigationSuiteItems = {
            AppDestinations.entries.forEach {
                item(
                    icon = { Icon(it.icon, contentDescription = it.label) },
                    label = { Text(it.label) },
                    selected = it == currentDestination,
                    onClick = { currentDestination = it }
                )
            }
        }
    ) {
        Scaffold(
            topBar = {
                CenterAlignedTopAppBar(
                    title = {
                        Text(
                            text = "Halal Finder",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.headlineSmall
                        )
                    },
                    actions = {
                        IconButton(onClick = { /* TODO */ }) {
                            Icon(Icons.Default.Notifications, contentDescription = "Notifications")
                        }
                    }
                )
            }
        ) { innerPadding ->
            Box(modifier = Modifier.padding(innerPadding)) {
                when (currentDestination) {
                    AppDestinations.HOME -> HomeScreen()
                    AppDestinations.MAP -> MapScreen()
                    AppDestinations.SCANNER -> ScannerScreen()
                    AppDestinations.PRAYER -> PrayerTimesScreen()
                    AppDestinations.PROFILE -> ProfileScreen(
                        currentThemeMode = currentThemeMode,
                        onThemeChange = onThemeChange,
                        accentColor = accentColor,
                        onAccentColorChange = onAccentColorChange,
                        profileImageUri = profileImageUri,
                        onProfileImageChange = onProfileImageChange,
                        language = language,
                        onLanguageChange = onLanguageChange
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen() {
    var searchQuery by remember { mutableStateOf("") }
    val categories = listOf("All", "Fast Food", "Burgers", "Middle Eastern", "Turkish", "Indian")
    var selectedCategory by remember { mutableStateOf("All") }

    Column(modifier = Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            placeholder = { Text("Search for halal food...") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon = { IconButton(onClick = {}) { Icon(Icons.AutoMirrored.Filled.List, null) } },
            shape = RoundedCornerShape(16.dp),
            singleLine = true
        )

        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(categories) { category ->
                FilterChip(
                    selected = selectedCategory == category,
                    onClick = { selectedCategory = category },
                    label = { Text(category) },
                    shape = RoundedCornerShape(20.dp)
                )
            }
        }

        val filteredPlaces = samplePlaces.filter {
            (selectedCategory == "All" || it.category == selectedCategory) &&
                    it.name.contains(searchQuery, ignoreCase = true)
        }

        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(filteredPlaces) { place ->
                PlaceCard(place)
            }
        }
    }
}

@Composable
fun PlaceCard(place: HalalPlace) {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Place, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
            }
            Column(modifier = Modifier.padding(16.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(place.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    if (place.isHalalCertified) {
                        Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(4.dp)) {
                            Text(
                                "HALAL",
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Black,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
                Text("${place.category} • ${place.distance}", color = Color.Gray)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Star, null, tint = Color(0xFFFFC107), modifier = Modifier.size(18.dp))
                    Text("${place.rating} (${place.reviews} reviews)", modifier = Modifier.padding(start = 4.dp))
                }
            }
        }
    }
}

@Composable
fun MapScreen() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Default.Map, null, Modifier.size(100.dp), tint = MaterialTheme.colorScheme.primary)
            Text("Halal Map", style = MaterialTheme.typography.headlineSmall)
            Button(onClick = {}) { Text("Open Maps") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalGetImage::class)
@Composable
fun ScannerScreen() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var selectedTab by remember { mutableIntStateOf(0) }
    var scanResult by remember { mutableStateOf("Position barcode or ingredients list in the box") }
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }

    var hasCameraPermission by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasCameraPermission = granted
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            SegmentedButton(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0; scanResult = "Scan Barcode" },
                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
            ) { Text("Barcode") }
            SegmentedButton(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1; scanResult = "Scan Ingredients" },
                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
            ) { Text("Ingredients") }
        }

        Box(modifier = Modifier.weight(1f).fillMaxWidth().padding(16.dp).clip(RoundedCornerShape(24.dp)).background(Color.Black)) {
            if (hasCameraPermission) {
                AndroidView(
                    factory = { ctx ->
                        val previewView = PreviewView(ctx)
                        val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                        cameraProviderFuture.addListener({
                            val cameraProvider = cameraProviderFuture.get()
                            val preview = androidx.camera.core.Preview.Builder().build().also {
                                it.setSurfaceProvider(previewView.surfaceProvider)
                            }
                            
                            val imageAnalysis = ImageAnalysis.Builder()
                                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                .build()

                            imageAnalysis.setAnalyzer(cameraExecutor) { imageProxy ->
                                val mediaImage = imageProxy.image
                                if (mediaImage != null) {
                                    val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
                                    if (selectedTab == 0) {
                                        BarcodeScanning.getClient().process(image)
                                            .addOnSuccessListener { barcodes ->
                                                barcodes.firstOrNull()?.displayValue?.let { scanResult = it }
                                            }
                                            .addOnCompleteListener { imageProxy.close() }
                                    } else {
                                        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS).process(image)
                                            .addOnSuccessListener { visionText ->
                                                if (visionText.text.isNotBlank()) {
                                                    scanResult = visionText.text.take(200)
                                                }
                                            }
                                            .addOnCompleteListener { imageProxy.close() }
                                    }
                                } else {
                                    imageProxy.close()
                                }
                            }

                            try {
                                cameraProvider.unbindAll()
                                cameraProvider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageAnalysis)
                            } catch (e: Exception) { Log.e("Scanner", "Binding failed", e) }
                        }, ContextCompat.getMainExecutor(ctx))
                        previewView
                    },
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Camera permission required", color = Color.White)
                }
            }
            
            // Viewfinder overlay
            Box(modifier = Modifier.fillMaxSize().padding(48.dp)) {
                Surface(
                    modifier = Modifier.size(250.dp).align(Alignment.Center),
                    color = Color.Transparent,
                    border = CardDefaults.outlinedCardBorder()
                ) {}
            }
        }
        
        Card(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Result:", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(4.dp))
                Text(scanResult, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
fun PrayerTimesScreen() {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Prayer Times", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(16.dp))
        val times = listOf("Fajr" to "05:12 AM", "Dhuhr" to "01:05 PM", "Asr" to "04:30 PM", "Maghrib" to "07:25 PM", "Isha" to "08:50 PM")
        times.forEach { (name, time) ->
            ListItem(headlineContent = { Text(name) }, trailingContent = { Text(time) })
        }
    }
}

@Composable
fun ProfileScreen(
    currentThemeMode: ThemeMode,
    onThemeChange: (ThemeMode) -> Unit,
    accentColor: Color,
    onAccentColorChange: (Color) -> Unit,
    profileImageUri: String?,
    onProfileImageChange: (String?) -> Unit,
    language: String,
    onLanguageChange: (String) -> Unit
) {
    var showThemeDialog by remember { mutableStateOf(false) }
    var showColorDialog by remember { mutableStateOf(false) }
    var showLanguageDialog by remember { mutableStateOf(false) }
    
    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            onProfileImageChange(uri.toString())
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier.size(120.dp).clickable { 
                photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) 
            }
        ) {
            if (profileImageUri != null) {
                AsyncImage(
                    model = profileImageUri,
                    contentDescription = "Profile Image",
                    modifier = Modifier.fillMaxSize().clip(CircleShape),
                    contentScale = ContentScale.Crop
                )
            } else {
                Surface(modifier = Modifier.fillMaxSize(), shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
                    Icon(Icons.Default.Person, null, Modifier.padding(24.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
                }
            }
            Surface(modifier = Modifier.align(Alignment.BottomEnd).size(32.dp), shape = CircleShape, color = MaterialTheme.colorScheme.primary) {
                Icon(Icons.Default.Edit, null, Modifier.padding(6.dp), tint = Color.White)
            }
        }
        
        Spacer(Modifier.height(16.dp))
        Text("Ahmed Abdallah", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        
        Spacer(Modifier.height(32.dp))

        Column(modifier = Modifier.fillMaxWidth()) {
            ProfileItem(Icons.Default.Palette, "Accent Color", "Customize UI color") { showColorDialog = true }
            ProfileItem(Icons.Default.Brightness6, "Appearance", currentThemeMode.name) { showThemeDialog = true }
            ProfileItem(Icons.Default.Language, "Language", language) { showLanguageDialog = true }
            ProfileItem(Icons.Default.ExitToApp, "Logout", textColor = Color.Red)
        }
    }

    if (showThemeDialog) {
        AlertDialog(onDismissRequest = { showThemeDialog = false }, title = { Text("Appearance") }, text = {
            Column {
                ThemeMode.entries.forEach { mode ->
                    ThemeOption(mode.name, currentThemeMode == mode) { onThemeChange(mode); showThemeDialog = false }
                }
            }
        }, confirmButton = {
            TextButton(onClick = { showThemeDialog = false }) { Text("Close") }
        })
    }

    if (showColorDialog) {
        val colors = listOf(HalalGreenPrimary, Color(0xFF1E88E5), Color(0xFFE53935), Color(0xFF8E24AA), Color(0xFF43A047), Color(0xFFF4511E))
        AlertDialog(onDismissRequest = { showColorDialog = false }, title = { Text("Accent Color") }, text = {
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                colors.forEach { color ->
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(color)
                            .clickable { onAccentColorChange(color); showColorDialog = false }
                    )
                }
            }
        }, confirmButton = {
            TextButton(onClick = { showColorDialog = false }) { Text("Close") }
        })
    }

    if (showLanguageDialog) {
        val langs = listOf("English", "Arabic", "French", "Malay")
        AlertDialog(onDismissRequest = { showLanguageDialog = false }, title = { Text("Language") }, text = {
            Column {
                langs.forEach { lang ->
                    ThemeOption(lang, language == lang) { onLanguageChange(lang); showLanguageDialog = false }
                }
            }
        }, confirmButton = {
            TextButton(onClick = { showLanguageDialog = false }) { Text("Close") }
        })
    }
}

@Composable
fun ProfileItem(icon: ImageVector, label: String, subtitle: String? = null, textColor: Color = Color.Unspecified, onClick: () -> Unit = {}) {
    ListItem(
        headlineContent = { Text(label, color = textColor) },
        supportingContent = subtitle?.let { { Text(it) } },
        leadingContent = { Icon(icon, null, tint = if (textColor == Color.Red) Color.Red else MaterialTheme.colorScheme.primary) },
        trailingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null) },
        modifier = Modifier.clickable(onClick = onClick)
    )
}

@Composable
fun ThemeOption(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected = selected, onClick = null)
        Spacer(Modifier.width(12.dp))
        Text(label)
    }
}

data class HalalPlace(val id: String, val name: String, val category: String, val address: String, val rating: Double, val reviews: Int, val distance: String, val isHalalCertified: Boolean = true)

val samplePlaces = listOf(
    HalalPlace("1", "The Halal Guys", "Fast Food", "123 Broadway, NY", 4.5, 1200, "0.5 miles"),
    HalalPlace("2", "Burgers & Co", "Burgers", "456 Main St, NY", 4.2, 850, "1.2 miles"),
    HalalPlace("3", "Middle East Delights", "Middle Eastern", "789 5th Ave, NY", 4.8, 450, "2.0 miles"),
    HalalPlace("4", "Kebab Palace", "Turkish", "101 2nd St, NY", 4.0, 300, "0.8 miles"),
    HalalPlace("5", "Spicy Curry House", "Indian", "202 Garden Ave, NY", 4.7, 600, "1.5 miles"),
)

enum class AppDestinations(val label: String, val icon: ImageVector) {
    HOME("Home", Icons.Default.Home),
    MAP("Map", Icons.Default.LocationOn),
    SCANNER("Scanner", Icons.Default.QrCodeScanner),
    PRAYER("Prayer", Icons.Default.AccessTime),
    PROFILE("Profile", Icons.Default.Person),
}

@Preview(showBackground = true)
@Composable
fun HalalFinderPreview() {
    HalalFinderTheme {
        HalalFinderApp(ThemeMode.AUTO, {}, Color.Green, {}, null, {}, "English", {})
    }
}
