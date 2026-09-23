package com.ironlog.app.ui.screens.nutrition

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ironlog.app.data.dao.FoodLogWithFood
import com.ironlog.app.data.entity.ActivityLevel
import com.ironlog.app.data.entity.DietGoal
import com.ironlog.app.data.entity.FoodEntity
import com.ironlog.app.data.entity.FoodGroup
import com.ironlog.app.data.entity.Meal
import com.ironlog.app.data.entity.NutritionGoalEntity
import com.ironlog.app.data.entity.Sex
import com.ironlog.app.data.repository.FoodInput
import com.ironlog.app.data.repository.GoalInput
import com.ironlog.app.domain.usecase.NutritionMath
import com.ironlog.app.ui.components.Caption
import com.ironlog.app.ui.components.EmptyState
import com.ironlog.app.ui.components.ErrorBanner
import com.ironlog.app.ui.components.FilterChipRow
import com.ironlog.app.ui.components.Hairline
import com.ironlog.app.ui.components.IronCard
import com.ironlog.app.ui.components.IronChip
import com.ironlog.app.ui.components.IronTextField
import com.ironlog.app.ui.components.IronTopBar
import com.ironlog.app.ui.components.LoadingBlock
import com.ironlog.app.ui.components.PrimaryButton
import com.ironlog.app.ui.components.SecondaryButton
import com.ironlog.app.ui.components.SectionHeader
import com.ironlog.app.ui.components.StatTile
import com.ironlog.app.ui.theme.Chalk
import com.ironlog.app.ui.theme.ChalkDim
import com.ironlog.app.ui.theme.ChalkFaint
import com.ironlog.app.ui.theme.NumericType
import com.ironlog.app.ui.theme.Plate10
import com.ironlog.app.ui.theme.Plate15
import com.ironlog.app.ui.theme.Plate20
import com.ironlog.app.ui.theme.Plate25
import com.ironlog.app.ui.theme.Slate700
import com.ironlog.app.ui.theme.Slate800
import com.ironlog.app.ui.theme.Space
import kotlin.math.roundToInt
import androidx.compose.material.icons.filled.QrCodeScanner
import com.ironlog.app.core.permissions.AppPermission
import com.ironlog.app.core.permissions.rememberPermissionState
import com.ironlog.app.data.repository.ScanResult
import com.ironlog.app.core.media.BarcodeScannerView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.statusBarsPadding
import com.ironlog.app.ui.theme.Slate900

/**
 * Today's food against the targets, meal by meal.
 *
 * The top of the screen is the day in one glance: energy against the target,
 * then protein, carbohydrate and fat as bars. Below it, the meals, each with
 * what was logged. Adding a food is a sheet over the top rather than a new
 * screen, because logging lunch is a fifteen-second job and should feel like
 * one.
 */
@Composable
fun NutritionScreen(
    onBack: () -> Unit,
    onOpenPlan: () -> Unit,
    viewModel: NutritionViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var addingTo by remember { mutableStateOf<Meal?>(null) }
    var editingGoal by remember { mutableStateOf(false) }

    LaunchedEffect(state.message) {
        if (state.message != null) {
            kotlinx.coroutines.delay(2_500)
            viewModel.dismissMessage()
        }
    }

    Column(Modifier.fillMaxSize()) {
        IronTopBar(title = "Nutrition", onBack = onBack)

        if (state.isLoading) {
            LoadingBlock(Modifier.fillMaxSize())
            return@Column
        }

        LazyColumn(
            contentPadding = PaddingValues(horizontal = Space.lg, vertical = Space.sm),
            verticalArrangement = Arrangement.spacedBy(Space.md)
        ) {
            item { ErrorBanner(state.error) }
            state.message?.let { item { Caption(it) } }

            item {
                TodayCard(
                    totals = state.totals,
                    goal = state.goal,
                    onSetGoal = { editingGoal = true },
                    onOpenPlan = onOpenPlan
                )
            }

            Meal.entries.forEach { meal ->
                val entries = state.forMeal(meal)
                item(key = "header-${meal.name}") {
                    SectionHeader(meal.label) {
                        Text(
                            "${entries.sumOf { it.kcal }.roundToInt()} kcal",
                            style = NumericType.small,
                            color = ChalkDim
                        )
                    }
                }
                if (entries.isEmpty()) {
                    item(key = "empty-${meal.name}") {
                        SecondaryButton(
                            text = "Add to ${meal.label.lowercase()}",
                            onClick = { addingTo = meal },
                            leadingIcon = Icons.Default.Add
                        )
                    }
                } else {
                    items(entries, key = { it.log.id }) { entry ->
                        EntryRow(entry, onDelete = { viewModel.deleteLog(entry) })
                    }
                    item(key = "add-${meal.name}") {
                        SecondaryButton(
                            text = "Add more",
                            onClick = { addingTo = meal },
                            leadingIcon = Icons.Default.Add
                        )
                    }
                }
            }

            item {
                Caption(
                    "Targets are an estimate from your height, weight, age and activity, using the " +
                        "Mifflin-St Jeor equation. They are a starting point, not a prescription; a " +
                        "dietitian can give you one of those."
                )
            }
            item { Spacer(Modifier.height(Space.xl)) }
        }
    }

    addingTo?.let { meal ->
        AddFoodSheet(
            meal = meal,
            foods = state.foods,
            isScanning = state.isScanning,
            onLog = { food, grams -> viewModel.logFood(food, grams, meal); addingTo = null },
            onCreate = { input, then -> viewModel.createFood(input, then) },
            onScan = { code, then -> viewModel.scan(code, then) },
            onDismiss = { addingTo = null }
        )
    }

    if (editingGoal) {
        GoalSheet(
            current = state.goal,
            heightCm = state.profile?.heightCm,
            weightKg = state.profile?.bodyweightKg,
            onSave = { input -> viewModel.saveGoal(input) { editingGoal = false } },
            onDismiss = { editingGoal = false }
        )
    }
}

@Composable
private fun TodayCard(
    totals: NutritionMath.Totals,
    goal: NutritionGoalEntity?,
    onSetGoal: () -> Unit,
    onOpenPlan: () -> Unit
) {
    IronCard {
        Column {
            if (goal == null) {
                EmptyState(
                    icon = Icons.Default.Restaurant,
                    title = "Set a goal",
                    body = "Pick lose, maintain or build and the app works out a daily target from your stats.",
                    actionLabel = "Set my goal",
                    onAction = onSetGoal
                )
                if (totals.kcal > 0) {
                    Spacer(Modifier.height(Space.md))
                    Text("${totals.kcal.roundToInt()} kcal logged today", style = NumericType.stat, color = Chalk)
                }
                return@Column
            }

            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                Column(Modifier.weight(1f)) {
                    Text("${totals.kcal.roundToInt()}", style = NumericType.hero, color = Chalk)
                    Caption("of ${goal.targetKcal} kcal · ${goal.goal.label}")
                }
                val left = goal.targetKcal - totals.kcal.roundToInt()
                StatTile(
                    value = if (left >= 0) "$left" else "+${-left}",
                    label = if (left >= 0) "left" else "over",
                    accent = if (left >= 0) Chalk else Plate15
                )
            }
            Spacer(Modifier.height(Space.sm))
            Track(totals.kcal / goal.targetKcal, Chalk)
            Spacer(Modifier.height(Space.md))

            MacroRow("Protein", totals.proteinG, goal.proteinG, Plate25)
            Spacer(Modifier.height(Space.sm))
            MacroRow("Carbs", totals.carbsG, goal.carbsG, Plate20)
            Spacer(Modifier.height(Space.sm))
            MacroRow("Fat", totals.fatG, goal.fatG, Plate10)

            Spacer(Modifier.height(Space.md))
            Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                SecondaryButton(text = "Change goal", onClick = onSetGoal, modifier = Modifier.weight(1f))
                PrimaryButton(text = "Build a plan", onClick = onOpenPlan, modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun MacroRow(label: String, current: Double, target: Int, colour: androidx.compose.ui.graphics.Color) {
    Column {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.bodySmall, color = ChalkDim)
            Text("${current.roundToInt()} / $target g", style = NumericType.small, color = ChalkDim)
        }
        Spacer(Modifier.height(4.dp))
        Track(if (target > 0) current / target else 0.0, colour)
    }
}

@Composable
private fun Track(fraction: Double, colour: androidx.compose.ui.graphics.Color) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(8.dp)
            .background(Slate700, RoundedCornerShape(4.dp))
    ) {
        Box(
            Modifier
                .fillMaxWidth(fraction.coerceIn(0.0, 1.0).toFloat())
                .height(8.dp)
                .background(colour, RoundedCornerShape(4.dp))
        )
    }
}

@Composable
private fun EntryRow(entry: FoodLogWithFood, onDelete: () -> Unit) {
    IronCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(entry.food.name, style = MaterialTheme.typography.bodyLarge, color = Chalk, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Caption("${entry.log.grams.roundToInt()} g · P ${entry.proteinG.roundToInt()} · C ${entry.carbsG.roundToInt()} · F ${entry.fatG.roundToInt()}")
            }
            Text("${entry.kcal.roundToInt()}", style = NumericType.setCell, color = Chalk)
            Spacer(Modifier.width(Space.xs))
            Caption("kcal")
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Remove ${entry.food.name}", tint = ChalkFaint)
            }
        }
    }
}

/**
 * Pick a food and an amount. The list is the whole library filtered as you
 * type; a serving button fills in the ordinary portion so most entries are
 * two taps. A food that is not there can be added without leaving the sheet.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddFoodSheet(
    meal: Meal,
    foods: List<FoodEntity>,
    isScanning: Boolean,
    onLog: (FoodEntity, Double) -> Unit,
    onCreate: (FoodInput, (FoodEntity) -> Unit) -> Unit,
    onScan: (String, (ScanResult) -> Unit) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var query by remember { mutableStateOf("") }
    var chosen by remember { mutableStateOf<FoodEntity?>(null) }
    var grams by remember { mutableStateOf("") }
    var creating by remember { mutableStateOf(false) }
    /** Filled in from a scan, so the form opens with the label's figures. */
    var prefill by remember { mutableStateOf<FoodInput?>(null) }
    var prefillSource by remember { mutableStateOf<String?>(null) }
    var scanning by remember { mutableStateOf(false) }
    var typedCode by remember { mutableStateOf("") }
    val camera = rememberPermissionState(AppPermission.CAMERA)

    fun handle(result: ScanResult) {
        scanning = false
        when (result) {
            is ScanResult.Known -> { chosen = result.food; grams = result.food.servingGrams.roundToInt().toString() }
            is ScanResult.Found -> { prefill = result.input; prefillSource = result.source; creating = true }
        }
    }

    val matches = remember(query, foods) {
        val q = query.trim()
        if (q.isEmpty()) foods else foods.filter { it.name.contains(q, ignoreCase = true) }
    }

    if (scanning) {
        ScannerDialog(
            onBarcode = { code -> onScan(code, ::handle) },
            onDismiss = { scanning = false }
        )
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = Slate800) {
        LazyColumn(
            contentPadding = PaddingValues(start = Space.lg, end = Space.lg, bottom = Space.xl),
            verticalArrangement = Arrangement.spacedBy(Space.sm)
        ) {
            item { Text("Add to ${meal.label.lowercase()}", style = MaterialTheme.typography.titleLarge, color = Chalk) }

            val selected = chosen
            if (creating) {
                item {
                    CreateFoodForm(
                        initial = prefill,
                        source = prefillSource,
                        onCreate = { input -> onCreate(input) { created -> chosen = created; creating = false; prefill = null; prefillSource = null } },
                        onCancel = { creating = false; prefill = null; prefillSource = null }
                    )
                }
            } else if (selected != null) {
                item {
                    IronCard {
                        Column {
                            Text(selected.name, style = MaterialTheme.typography.titleMedium, color = Chalk)
                            Caption("${selected.kcalPer100g.roundToInt()} kcal · P ${selected.proteinPer100g} · C ${selected.carbsPer100g} · F ${selected.fatPer100g} per 100 g")
                            Spacer(Modifier.height(Space.md))
                            IronTextField(
                                value = grams,
                                onValueChange = { grams = it.filter { c -> c.isDigit() || c == '.' }.take(6) },
                                label = "Grams",
                                keyboardType = KeyboardType.Decimal
                            )
                            Spacer(Modifier.height(Space.sm))
                            Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                                IronChip("${selected.servingLabel} (${selected.servingGrams.roundToInt()} g)", grams == selected.servingGrams.roundToInt().toString()) {
                                    grams = selected.servingGrams.roundToInt().toString()
                                }
                                IronChip("100 g", grams == "100") { grams = "100" }
                            }
                            grams.toDoubleOrNull()?.takeIf { it > 0 }?.let { g ->
                                val p = NutritionMath.portion(g, selected.kcalPer100g, selected.proteinPer100g, selected.carbsPer100g, selected.fatPer100g)
                                Spacer(Modifier.height(Space.sm))
                                Caption("${p.kcal.roundToInt()} kcal · P ${p.proteinG.roundToInt()} g · C ${p.carbsG.roundToInt()} g · F ${p.fatG.roundToInt()} g")
                            }
                            Spacer(Modifier.height(Space.md))
                            Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                                SecondaryButton(text = "Back", onClick = { chosen = null }, modifier = Modifier.weight(1f))
                                PrimaryButton(
                                    text = "Log it",
                                    onClick = { grams.toDoubleOrNull()?.let { onLog(selected, it) } },
                                    enabled = (grams.toDoubleOrNull() ?: 0.0) > 0.0,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
            } else {
                item {
                    IronTextField(value = query, onValueChange = { query = it.take(40) }, label = "Search foods", placeholder = "Chicken, rice, banana…")
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                        SecondaryButton(
                            text = if (isScanning) "Looking up" else "Scan a barcode",
                            onClick = { camera.runWithPermission { scanning = true } },
                            enabled = !isScanning,
                            leadingIcon = Icons.Default.QrCodeScanner,
                            modifier = Modifier.weight(1f)
                        )
                        SecondaryButton(text = "Add by hand", onClick = { creating = true }, leadingIcon = Icons.Default.Add, modifier = Modifier.weight(1f))
                    }
                }
                item {
                    // The number under the bars, for a scuffed label or a phone
                    // with no camera. Same lookup, no lens.
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                        IronTextField(
                            value = typedCode,
                            onValueChange = { typedCode = it.filter(Char::isDigit).take(14) },
                            label = "Or type the barcode",
                            keyboardType = KeyboardType.Number,
                            modifier = Modifier.weight(1f)
                        )
                        // A fixed width, because the button fills whatever it is
                        // given and would otherwise starve the field beside it.
                        SecondaryButton(
                            text = "Look up",
                            enabled = typedCode.length >= 8 && !isScanning,
                            onClick = { onScan(typedCode, ::handle) },
                            modifier = Modifier.width(LOOKUP_WIDTH)
                        )
                    }
                }
                items(matches, key = { it.id }) { food ->
                    IronCard(onClick = { chosen = food; grams = food.servingGrams.roundToInt().toString() }) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(food.name, style = MaterialTheme.typography.bodyLarge, color = Chalk, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Caption("${food.group.label}${if (food.isCustom) " · yours" else ""} · ${food.servingLabel}")
                            }
                            Text("${food.kcalPer100g.roundToInt()}", style = NumericType.small, color = ChalkDim)
                            Spacer(Modifier.width(Space.xs))
                            Caption("kcal/100 g")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CreateFoodForm(
    onCreate: (FoodInput) -> Unit,
    onCancel: () -> Unit,
    initial: FoodInput? = null,
    source: String? = null
) {
    fun fmt(v: Double?) = v?.let { if (it % 1.0 == 0.0) it.toInt().toString() else it.toString() } ?: ""
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var group by remember { mutableStateOf(initial?.group ?: FoodGroup.PROTEIN) }
    var kcal by remember { mutableStateOf(fmt(initial?.kcalPer100g)) }
    var protein by remember { mutableStateOf(fmt(initial?.proteinPer100g)) }
    var carbs by remember { mutableStateOf(fmt(initial?.carbsPer100g)) }
    var fat by remember { mutableStateOf(fmt(initial?.fatPer100g)) }
    var serving by remember { mutableStateOf(fmt(initial?.servingGrams ?: 100.0)) }
    fun num(s: String) = s.toDoubleOrNull()

    IronCard {
        Column(verticalArrangement = Arrangement.spacedBy(Space.sm)) {
            Text(if (initial != null) "Check the label" else "New food, per 100 g", style = MaterialTheme.typography.titleMedium, color = Chalk)
            source?.let { Caption("From $it. Check the figures against the packet before saving.") }
            IronTextField(value = name, onValueChange = { name = it.take(60) }, label = "Name")
            FilterChipRow(options = FoodGroup.entries, selected = group, label = { it.label }, onSelect = { it?.let { g -> group = g } })
            Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                IronTextField(kcal, { kcal = it.take(6) }, "kcal", Modifier.weight(1f), keyboardType = KeyboardType.Decimal)
                IronTextField(protein, { protein = it.take(5) }, "Protein g", Modifier.weight(1f), keyboardType = KeyboardType.Decimal)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                IronTextField(carbs, { carbs = it.take(5) }, "Carbs g", Modifier.weight(1f), keyboardType = KeyboardType.Decimal)
                IronTextField(fat, { fat = it.take(5) }, "Fat g", Modifier.weight(1f), keyboardType = KeyboardType.Decimal)
            }
            IronTextField(serving, { serving = it.take(5) }, "One serving, grams", keyboardType = KeyboardType.Decimal)
            Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                SecondaryButton(text = "Cancel", onClick = onCancel, modifier = Modifier.weight(1f))
                PrimaryButton(
                    text = "Save food",
                    enabled = name.isNotBlank() && num(kcal) != null,
                    onClick = {
                        onCreate(
                            FoodInput(
                                name = name, group = group,
                                kcalPer100g = num(kcal) ?: 0.0, proteinPer100g = num(protein) ?: 0.0,
                                carbsPer100g = num(carbs) ?: 0.0, fatPer100g = num(fat) ?: 0.0,
                                servingGrams = num(serving) ?: 100.0,
                                barcode = initial?.barcode
                            )
                        )
                    },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

/** The goal and the stats the target is worked out from. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GoalSheet(
    current: NutritionGoalEntity?,
    heightCm: Double?,
    weightKg: Double?,
    onSave: (GoalInput) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var goal by remember { mutableStateOf(current?.goal ?: DietGoal.MAINTAIN) }
    var sex by remember { mutableStateOf(current?.sex ?: Sex.MALE) }
    var activity by remember { mutableStateOf(current?.activity ?: ActivityLevel.LIGHT) }
    var age by remember { mutableStateOf(current?.ageYears?.toString() ?: "") }
    var height by remember { mutableStateOf(heightCm?.roundToInt()?.toString() ?: "") }
    var weight by remember { mutableStateOf(weightKg?.let { "%.1f".format(it) } ?: "") }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = Slate800) {
        Column(
            Modifier.padding(start = Space.lg, end = Space.lg, bottom = Space.xl),
            verticalArrangement = Arrangement.spacedBy(Space.md)
        ) {
            Text("Your goal", style = MaterialTheme.typography.titleLarge, color = Chalk)
            Column(verticalArrangement = Arrangement.spacedBy(Space.xs)) {
                DietGoal.entries.forEach { option ->
                    IronCard(onClick = { goal = option }) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(option.label, style = MaterialTheme.typography.bodyLarge, color = Chalk)
                                Caption(option.detail)
                            }
                            if (goal == option) Text("Chosen", style = MaterialTheme.typography.labelMedium, color = Chalk)
                        }
                    }
                }
            }
            Hairline()
            Text("About you", style = MaterialTheme.typography.titleMedium, color = Chalk)
            Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                Sex.entries.forEach { option -> IronChip(option.label, sex == option) { sex = option } }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                IronTextField(age, { age = it.filter(Char::isDigit).take(3) }, "Age", Modifier.weight(1f), keyboardType = KeyboardType.Number)
                IronTextField(height, { height = it.filter(Char::isDigit).take(3) }, "Height cm", Modifier.weight(1f), keyboardType = KeyboardType.Number)
                IronTextField(weight, { weight = it.filter { c -> c.isDigit() || c == '.' }.take(5) }, "Weight kg", Modifier.weight(1f), keyboardType = KeyboardType.Decimal)
            }
            FilterChipRow(options = ActivityLevel.entries, selected = activity, label = { it.label }, onSelect = { it?.let { a -> activity = a } })
            Caption(activity.detail)
            PrimaryButton(
                text = "Work out my target",
                enabled = age.toIntOrNull() != null && height.toDoubleOrNull() != null && weight.toDoubleOrNull() != null,
                onClick = {
                    onSave(
                        GoalInput(
                            goal = goal, sex = sex, ageYears = age.toIntOrNull() ?: 0, activity = activity,
                            heightCm = height.toDoubleOrNull(), weightKg = weight.toDoubleOrNull()
                        )
                    )
                }
            )
        }
    }
}

private val LOOKUP_WIDTH = 116.dp

/**
 * The camera, full screen, until a code is read or the user backs out.
 *
 * Its own dialog rather than a panel in the sheet: a viewfinder needs the
 * whole screen to be usable at arm's length, and a half-height camera behind
 * a keyboard is not a scanner.
 */
@Composable
private fun ScannerDialog(onBarcode: (String) -> Unit, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(Slate900)) {
            BarcodeScannerView(onBarcode = onBarcode, modifier = Modifier.fillMaxSize())
            // Controls at the top, under the status bar. The dialog runs edge to
            // edge and its bottom edge is not where the screen's is, so a button
            // anchored down there ended up below the glass, present but
            // unreachable. The top edge is honest.
            Row(
                Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .background(Slate900.copy(alpha = 0.82f))
                    .statusBarsPadding()
                    .padding(Space.lg),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Scan a barcode", style = MaterialTheme.typography.titleMedium, color = Chalk)
                    Caption("Point the camera at the packet. It reads on the phone; nothing is uploaded.")
                }
                Spacer(Modifier.width(Space.md))
                SecondaryButton(text = "Cancel", onClick = onDismiss, modifier = Modifier.width(LOOKUP_WIDTH))
            }
        }
    }
}
