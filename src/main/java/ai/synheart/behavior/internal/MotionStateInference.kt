package ai.synheart.behavior.internal

import ai.onnxruntime.*
import ai.synheart.behavior.MotionDataPoint
import ai.synheart.behavior.MotionState
import android.content.Context
import android.util.Log
import java.io.BufferedReader
import java.io.InputStreamReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * Service for running ONNX inference on motion data to predict activity states. Matches Flutter
 * SDK's MotionStateInference implementation.
 */
internal class MotionStateInference(private val context: Context) {
    private var ortEnv: OrtEnvironment? = null
    private var ortSession: OrtSession? = null
    private var classLabels: List<String> = listOf("LAYING", "MOVING", "SITTING", "STANDING")
    private var isLoaded = false

    // Feature order from features.txt (loaded once, cached)
    private var cachedFeatureOrder: List<String>? = null

    // Counter for logging key features only on first prediction
    private var predictionCount = 0

    fun getIsLoaded(): Boolean = isLoaded

    /** Load the ONNX model and label mapping from assets. */
    suspend fun loadModel() =
            withContext(Dispatchers.IO) {
                if (isLoaded) return@withContext

                try {
                    Log.d("MotionStateInference", "Loading ONNX model from assets...")

                    // Initialize ONNX Runtime environment
                    ortEnv = OrtEnvironment.getEnvironment()

                    // Create session options
                    val sessionOptions = OrtSession.SessionOptions()

                    // Load model from assets
                    Log.d(
                            "MotionStateInference",
                            "Opening models/linear_svc_model.onnx from assets..."
                    )
                    val modelInputStream = context.assets.open("models/linear_svc_model.onnx")
                    val modelBytes = modelInputStream.readBytes()
                    modelInputStream.close()
                    Log.d("MotionStateInference", "Loaded ONNX model: ${modelBytes.size} bytes")

                    // Create session from bytes
                    ortSession = ortEnv!!.createSession(modelBytes, sessionOptions)
                    Log.d("MotionStateInference", "ONNX session created successfully")

                    // Load label mapping
                    Log.d("MotionStateInference", "Loading label_mapping.json from assets...")
                    val labelMappingInputStream = context.assets.open("models/label_mapping.json")
                    val labelMappingString =
                            BufferedReader(InputStreamReader(labelMappingInputStream)).use {
                                it.readText()
                            }
                    labelMappingInputStream.close()

                    val labelMapping = JSONObject(labelMappingString)
                    val labelsArray = labelMapping.getJSONArray("labels")
                    classLabels = (0 until labelsArray.length()).map { labelsArray.getString(it) }
                    Log.d(
                            "MotionStateInference",
                            "Loaded class labels: ${classLabels.joinToString(", ")}"
                    )

                    isLoaded = true

                    // Preload feature order silently
                    try {
                        val featureOrder = loadFeatureOrder()
                        Log.d(
                                "MotionStateInference",
                                "Feature order preloaded: ${featureOrder.size} features"
                        )
                    } catch (e: Exception) {
                        Log.w(
                                "MotionStateInference",
                                "Could not preload feature order: ${e.message}"
                        )
                        // Feature order will be loaded on first inference
                    }

                    Log.d("MotionStateInference", "Model loaded successfully!")
                } catch (e: Exception) {
                    Log.e("MotionStateInference", "Error loading model: ${e.message}", e)
                    e.printStackTrace()
                    // Don't rethrow - allow SDK to continue without motion state inference
                    isLoaded = false
                }
            }

    /**
     * Load feature order from features.txt file. This ensures features are in the exact order
     * expected by the model.
     */
    private fun loadFeatureOrder(): List<String> {
        // Return cached order if already loaded
        if (cachedFeatureOrder != null) {
            return cachedFeatureOrder!!
        }

        try {
            Log.d("MotionStateInference", "Loading features.txt from assets...")
            val featuresInputStream = context.assets.open("features.txt")
            val featuresText =
                    BufferedReader(InputStreamReader(featuresInputStream)).use { it.readText() }
            featuresInputStream.close()

            Log.d("MotionStateInference", "Loaded features.txt: ${featuresText.length} characters")

            // Parse features.txt: format is "1 tBodyAcc-mean()-X" or "1\ttBodyAcc-mean()-X"
            val lines = featuresText.split("\n")
            val featureOrder = mutableListOf<String>()

            for (line in lines) {
                val trimmed = line.trim()
                if (trimmed.isEmpty()) continue

                // Extract feature name (everything after the number and whitespace)
                // Format: "number featureName" or "number\tfeatureName"
                val regex = Regex("^\\d+\\s+(.+)$")
                val match = regex.find(trimmed)
                if (match != null) {
                    val featureName = match.groupValues[1].trim()
                    featureOrder.add(featureName)
                } else {
                    // Fallback: try splitting on whitespace
                    val parts = trimmed.split(Regex("\\s+"))
                    if (parts.size >= 2) {
                        val featureName = parts.subList(1, parts.size).joinToString(" ").trim()
                        featureOrder.add(featureName)
                    }
                }
            }

            Log.d("MotionStateInference", "Parsed ${featureOrder.size} features from features.txt")
            if (featureOrder.size >= 5) {
                Log.d(
                        "MotionStateInference",
                        "First 5 features: ${featureOrder.take(5).joinToString(", ")}"
                )
            }

            if (featureOrder.size != 561) {
                throw Exception("Expected 561 features in features.txt, got ${featureOrder.size}")
            }

            cachedFeatureOrder = featureOrder
            Log.d(
                    "MotionStateInference",
                    "Successfully loaded and cached feature order from features.txt"
            )
            return featureOrder
        } catch (e: Exception) {
            Log.e("MotionStateInference", "ERROR loading features.txt: ${e.message}", e)
            Log.e(
                    "MotionStateInference",
                    "ERROR - Cannot load features.txt! This will cause incorrect predictions!"
            )
            // Return empty list - will throw error when trying to use it
            return emptyList()
        }
    }

    /**
     * Convert features map to ordered list of 561 floats. Uses the exact feature order from
     * features.txt.
     */
    private suspend fun featuresMapToList(features: Map<String, Double>): FloatArray =
            withContext(Dispatchers.Default) {
                if (features.size != 561) {
                    throw IllegalArgumentException("Expected 561 features, got ${features.size}")
                }

                // Load the correct feature order from features.txt
                val featureOrder = loadFeatureOrder()

                if (featureOrder.isEmpty() || featureOrder.size != 561) {
                    // features.txt must be loaded correctly - no fallback sorting
                    Log.e(
                            "MotionStateInference",
                            "CRITICAL ERROR - features.txt not loaded correctly! Cannot proceed without correct feature order!"
                    )
                    Log.e(
                            "MotionStateInference",
                            "Expected 561 features from features.txt, got ${featureOrder.size}"
                    )
                    throw IllegalStateException(
                            "features.txt must be loaded correctly. Got ${featureOrder.size} features, expected 561."
                    )
                }

                Log.d(
                        "MotionStateInference",
                        "Using feature order from features.txt (${featureOrder.size} features)"
                )

                // CRITICAL: Use the EXACT order from features.txt (index 1-561)
                val orderedFeatures = FloatArray(561)
                var missingCount = 0
                val missingFeatures = mutableListOf<String>()
                val bandEnergyAxisCounter = mutableMapOf<String, Int>()

                // Iterate through featureOrder which is already in the exact order from
                // features.txt
                for (idx in featureOrder.indices) {
                    val featureName = featureOrder[idx]
                    var featureValue: Double? = null

                    // First, try exact match
                    if (features.containsKey(featureName)) {
                        featureValue = features[featureName]
                    } else {
                        // Handle bandsEnergy features: features.txt has
                        // "fBodyAcc-bandsEnergy()-1,8" (no axis)
                        // repeated 3 times (for X, Y, Z), but native code generates with axis
                        // suffix
                        // Map based on occurrence order: 1st occurrence -> -X, 2nd -> -Y, 3rd -> -Z
                        if (featureName.contains("bandsEnergy()") &&
                                        !featureName.contains("-X") &&
                                        !featureName.contains("-Y") &&
                                        !featureName.contains("-Z")
                        ) {
                            val baseName = featureName
                            val axisIndex = bandEnergyAxisCounter[baseName] ?: 0
                            val axes = listOf("-X", "-Y", "-Z")

                            if (axisIndex < axes.size) {
                                val mappedName = "$baseName${axes[axisIndex]}"
                                if (features.containsKey(mappedName)) {
                                    featureValue = features[mappedName]
                                    bandEnergyAxisCounter[baseName] = axisIndex + 1
                                }
                            }
                        }
                    }

                    if (featureValue != null) {
                        orderedFeatures[idx] = featureValue.toFloat()
                    } else {
                        // Feature missing - use 0.0 as fallback and log warning
                        orderedFeatures[idx] = 0.0f
                        missingCount++
                        missingFeatures.add("[$idx] $featureName")
                    }
                }

                if (missingCount > 0) {
                    Log.w(
                            "MotionStateInference",
                            "WARNING - $missingCount features missing from data"
                    )
                    Log.w(
                            "MotionStateInference",
                            "Missing features: ${missingFeatures.take(10).joinToString(", ")}${if (missingCount > 10) " ..." else ""}"
                    )
                    android.util.Log.w(
                            "SynheartBehavior",
                            "⚠️ $missingCount features missing! This will cause incorrect predictions!"
                    )
                } else {
                    // Verify key features match raw values (only log warnings on mismatch)
                    val stdXIdx = featureOrder.indexOf("tBodyAcc-std()-X")
                    val magMeanIdx = featureOrder.indexOf("tBodyAccMag-mean()")
                    if (stdXIdx >= 0 &&
                                    magMeanIdx >= 0 &&
                                    stdXIdx < orderedFeatures.size &&
                                    magMeanIdx < orderedFeatures.size
                    ) {
                        val stdXValue = orderedFeatures[stdXIdx]
                        val magMeanValue = orderedFeatures[magMeanIdx]
                        val rawStdX = features["tBodyAcc-std()-X"]
                        val rawMagMean = features["tBodyAccMag-mean()"]
                        if (rawStdX != null && kotlin.math.abs(rawStdX - stdXValue) > 0.0001) {
                            Log.w(
                                    "MotionStateInference",
                                    "Feature ordering mismatch! Raw stdX=${String.format("%.6f", rawStdX)}, Ordered=${String.format("%.6f", stdXValue)}"
                            )
                        }
                        if (rawMagMean != null &&
                                        kotlin.math.abs(rawMagMean - magMeanValue) > 0.0001
                        ) {
                            Log.w(
                                    "MotionStateInference",
                                    "Feature ordering mismatch! Raw magMean=${String.format("%.6f", rawMagMean)}, Ordered=${String.format("%.6f", magMeanValue)}"
                            )
                        }
                    }
                }

                return@withContext orderedFeatures
            }

    /** Predict motion state for a single data point. */
    private suspend fun predictSingle(features: Map<String, Double>): Pair<String, Double> =
            withContext(Dispatchers.Default) {
                if (!isLoaded || ortSession == null) {
                    throw IllegalStateException("Model not loaded. Call loadModel() first.")
                }

                try {
                    predictionCount++

                    // Convert features to ordered array
                    val inputData = featuresMapToList(features)

                    // Validate input data
                    if (inputData.size != 561) {
                        throw IllegalArgumentException(
                                "Input data length mismatch: expected 561, got ${inputData.size}"
                        )
                    }

                    // Check for critical data quality issues
                    if (inputData.isNotEmpty()) {
                        val nanCount = inputData.count { it.isNaN() }
                        val infCount = inputData.count { it.isInfinite() }
                        val zeroCount = inputData.count { it == 0.0f }
                        val nonZeroCount = inputData.size - zeroCount

                        if (nanCount > 0 || infCount > 0) {
                            Log.e(
                                    "MotionStateInference",
                                    "ERROR - Found $nanCount NaN values and $infCount Infinity values!"
                            )
                        }

                        // Check if most features are zero (indicates missing sensor data)
                        if (zeroCount > inputData.size * 0.9) {
                            Log.w(
                                    "MotionStateInference",
                                    "WARNING - ${zeroCount}/${inputData.size} features are zero! This suggests sensor data may not be collected properly."
                            )
                            Log.w(
                                    "MotionStateInference",
                                    "Only $nonZeroCount features have non-zero values."
                            )
                        }

                        // Log warning if most features are zero (indicates missing sensor data)
                        if (zeroCount > inputData.size * 0.9) {
                            Log.w(
                                    "MotionStateInference",
                                    "WARNING - ${zeroCount}/${inputData.size} features are zero! Sensor data may not be collected properly."
                            )
                        }
                    }

                    // Create input tensor with shape [1, 561]
                    val inputShape = longArrayOf(1, 561)

                    // Create FloatBuffer with proper configuration
                    // Use direct buffer for better performance and compatibility with ONNX Runtime
                    // Direct buffers are recommended for zero-copy data transfer
                    val floatBuffer =
                            java.nio.ByteBuffer.allocateDirect(
                                            inputData.size * 4
                                    ) // 4 bytes per float
                                    .order(java.nio.ByteOrder.nativeOrder())
                                    .asFloatBuffer()
                    floatBuffer.put(inputData)
                    floatBuffer.flip() // Set limit to current position and reset position to 0

                    // Buffer is ready for tensor creation

                    val inputTensor = OnnxTensor.createTensor(ortEnv!!, floatBuffer, inputShape)

                    // Prepare inputs - use exact input name the model expects
                    val inputs = mapOf("float_input" to inputTensor)

                    // Run inference
                    val result = ortSession!!.run(inputs)

                    // Extract outputs - match Dart SDK approach
                    // The model has 2 outputs: label (String) and probabilities (Float[1, 4])
                    // Get outputs by index (0 = label, 1 = probabilities)
                    val labelOutput = result.get(0) as? OnnxTensor
                    val probabilitiesOutput = result.get(1) as? OnnxTensor

                    if (labelOutput == null || probabilitiesOutput == null) {
                        Log.e(
                                "MotionStateInference",
                                "ERROR - Failed to extract outputs from model result"
                        )
                        throw IllegalStateException("Failed to extract model outputs")
                    }

                    // Get predicted label - handle different output formats
                    val predictedLabel =
                            try {
                                val labelValue = labelOutput.value
                                when {
                                    labelValue is Array<*> && labelValue.isNotEmpty() -> {
                                        val firstElement = labelValue[0]
                                        when {
                                            firstElement is String -> firstElement
                                            firstElement is Number -> {
                                                // If it's a numeric index, map it to the label
                                                val index = firstElement.toInt()
                                                if (index >= 0 && index < classLabels.size) {
                                                    classLabels[index]
                                                } else {
                                                    classLabels[0]
                                                }
                                            }
                                            else -> classLabels[0]
                                        }
                                    }
                                    labelValue is String -> labelValue
                                    else -> {
                                        Log.w(
                                                "MotionStateInference",
                                                "Unexpected label output type: ${labelValue?.javaClass?.simpleName}"
                                        )
                                        classLabels[0]
                                    }
                                }
                            } catch (e: Exception) {
                                Log.e(
                                        "MotionStateInference",
                                        "Error extracting label: ${e.message}",
                                        e
                                )
                                classLabels[0]
                            }

                    // Get probabilities/scores - handle nested array structure [1, 4]
                    val probabilities =
                            try {
                                val probValue = probabilitiesOutput.value
                                when {
                                    probValue is Array<*> -> {
                                        // Handle nested structure: [[score1, score2, score3,
                                        // score4]]
                                        val firstRow = probValue[0]
                                        when {
                                            firstRow is Array<*> ->
                                                    (firstRow as Array<*>).mapNotNull {
                                                        (it as? Number)?.toDouble() ?: 0.0
                                                    }
                                            firstRow is FloatArray -> firstRow.map { it.toDouble() }
                                            firstRow is DoubleArray -> firstRow.toList()
                                            else -> {
                                                Log.w(
                                                        "MotionStateInference",
                                                        "Unexpected probabilities row type: ${firstRow?.javaClass?.simpleName ?: "null"}"
                                                )
                                                classLabels.map { 0.0 }
                                            }
                                        }
                                    }
                                    probValue is FloatArray -> probValue.map { it.toDouble() }
                                    probValue is DoubleArray -> probValue.toList()
                                    else -> {
                                        Log.w(
                                                "MotionStateInference",
                                                "Unexpected probabilities type: ${probValue?.javaClass?.simpleName}"
                                        )
                                        classLabels.map { 0.0 }
                                    }
                                }
                            } catch (e: Exception) {
                                Log.e(
                                        "MotionStateInference",
                                        "Error extracting probabilities: ${e.message}",
                                        e
                                )
                                classLabels.map { 0.0 }
                            }

                    // Validate probabilities length
                    if (probabilities.size != classLabels.size) {
                        Log.w(
                                "MotionStateInference",
                                "Probabilities size mismatch: expected ${classLabels.size}, got ${probabilities.size}"
                        )
                    }

                    // Clean up tensors
                    inputTensor.close()
                    result.close()

                    // Find confidence (score for predicted label)
                    var confidence = 0.0
                    val predictedIndex = classLabels.indexOf(predictedLabel.uppercase())
                    if (predictedIndex >= 0 && predictedIndex < probabilities.size) {
                        // Use model's raw score directly - no clamping
                        confidence = probabilities[predictedIndex]
                    } else if (probabilities.isNotEmpty()) {
                        // Use highest score directly - no clamping
                        confidence = probabilities.maxOrNull() ?: 0.0
                    }

                    // Use text label from model output directly (as confirmed by ML engineer)
                    // The model outputs labels in uppercase (LAYING, MOVING, SITTING, STANDING)
                    // Convert to lowercase only for consistency with expected output format
                    val outputLabel = predictedLabel.uppercase().lowercase()

                    // Log model scores when movement is detected but STANDING is predicted
                    // This helps diagnose why MOVING class isn't being selected (matching Dart SDK)
                    val featureOrderForCheck = loadFeatureOrder()
                    val bodyAccStdXIdx = featureOrderForCheck.indexOf("tBodyAcc-std()-X")
                    val hasMovement =
                            bodyAccStdXIdx >= 0 &&
                                    bodyAccStdXIdx < inputData.size &&
                                    inputData[bodyAccStdXIdx] > 0.3f // High stdX indicates movement

                    // Log warning if movement detected but STANDING predicted (for debugging)
                    if (hasMovement && outputLabel.uppercase() == "STANDING") {
                        Log.w(
                                "MotionStateInference",
                                "Movement detected (stdX=${String.format("%.4f", inputData[bodyAccStdXIdx])}) but STANDING predicted"
                        )
                    }

                    // Log warnings only for unexpected predictions

                    // Confidence is the raw decision score from the model
                    // For SVC models, scores can be negative or outside [0,1] range
                    // Return the score as-is directly from model output (no calculations)
                    return@withContext Pair(outputLabel, confidence)
                } catch (e: Exception) {
                    Log.e("MotionStateInference", "Error during prediction: ${e.message}", e)
                    throw e
                }
            }

    /**
     * Run inference on all motion data points and return aggregated motion state.
     *
     * Returns a MotionState object with:
     * - state: List of predicted states for each window
     * - major_state: Most common state
     * - major_state_pct: Percentage of major state
     * - ml_model: Model identifier
     * - confidence: Average confidence
     */
    suspend fun inferMotionState(motionData: List<MotionDataPoint>): MotionState =
            withContext(Dispatchers.Default) {
                // Use both tags to ensure visibility
                Log.d(
                        "MotionStateInference",
                        "=== Starting inference on ${motionData.size} data points ==="
                )
                android.util.Log.d(
                        "SynheartBehavior",
                        "MotionStateInference: Starting inference on ${motionData.size} data points"
                )

                if (motionData.isEmpty()) {
                    Log.w("MotionStateInference", "No motion data provided")
                    return@withContext MotionState(
                            state = emptyList(),
                            majorState = "unknown",
                            majorStatePct = 0.0,
                            mlModel = "motion_state_svc_classifier_v0.1",
                            confidence = 0.0
                    )
                }

                if (!isLoaded || ortSession == null) {
                    Log.e("MotionStateInference", "ERROR - Model not loaded!")
                    throw IllegalStateException("Model not loaded. Call loadModel() first.")
                }

                // Run inference on each motion data point
                val states = mutableListOf<String>()
                val confidences = mutableListOf<Double>()

                for (i in motionData.indices) {
                    val dataPoint = motionData[i]
                    try {
                        // Log detailed diagnostics for first few data points to compare activities
                        if (i < 3) {
                            Log.d(
                                    "MotionStateInference",
                                    "--- Data point ${i + 1}/${motionData.size} - Feature count: ${dataPoint.features.size} ---"
                            )
                            if (dataPoint.features.isEmpty()) {
                                Log.e(
                                        "MotionStateInference",
                                        "ERROR - Data point ${i + 1} has no features! Sensor data may not be collected."
                                )
                            } else {
                                // Log key movement indicators (matching Dart SDK)
                                val stdX = dataPoint.features["tBodyAcc-std()-X"]
                                val stdY = dataPoint.features["tBodyAcc-std()-Y"]
                                val stdZ = dataPoint.features["tBodyAcc-std()-Z"]
                                val magMean = dataPoint.features["tBodyAccMag-mean()"]
                                val magStd = dataPoint.features["tBodyAccMag-std()"]

                                if (stdX != null && magMean != null) {
                                    Log.d(
                                            "MotionStateInference",
                                            ">>> Data point ${i + 1} KEY FEATURES <<<"
                                    )
                                    // Also log with SynheartBehavior tag for visibility
                                    android.util.Log.d(
                                            "SynheartBehavior",
                                            "MotionState[${i + 1}]: stdX=${String.format("%.4f", stdX)}, magMean=${String.format("%.4f", magMean)} (expected: stdX>0.1 for moving, <0.1 for standing)"
                                    )
                                    Log.d(
                                            "MotionStateInference",
                                            "  stdX: ${String.format("%.4f", stdX)} | stdY: ${stdY?.let { String.format("%.4f", it) } ?: "N/A"} | stdZ: ${stdZ?.let { String.format("%.4f", it) } ?: "N/A"}"
                                    )
                                    Log.d(
                                            "MotionStateInference",
                                            "  magMean: ${String.format("%.4f", magMean)} | magStd: ${magStd?.let { String.format("%.4f", it) } ?: "N/A"}"
                                    )
                                    Log.d(
                                            "MotionStateInference",
                                            "  → Expected: stdX > 0.1 for walking/moving, < 0.1 for standing/sitting"
                                    )

                                    // Check if values suggest issue (matching Dart SDK)
                                    if (stdX < 0.05 && magMean < 0.1) {
                                        Log.w(
                                                "MotionStateInference",
                                                "WARNING - Very low std/magnitude. Possible causes:"
                                        )
                                        Log.w(
                                                "MotionStateInference",
                                                "  1. Phone is stationary (not capturing movement during test)"
                                        )
                                        Log.w(
                                                "MotionStateInference",
                                                "  2. Feature extraction issue (check native code)"
                                        )
                                        Log.w(
                                                "MotionStateInference",
                                                "  3. Sensor data not being collected properly"
                                        )
                                    }
                                }
                            }
                        }

                        val result = predictSingle(dataPoint.features)
                        val predictedState = result.first
                        val confidence = result.second

                        states.add(predictedState)
                        confidences.add(confidence)

                        // Log all predictions to see what's happening (matching Dart SDK)
                        Log.d(
                                "MotionStateInference",
                                "✓ Data point ${i + 1}/${motionData.size} → Prediction: $predictedState (confidence: ${String.format("%.3f", confidence)})"
                        )
                        // Also log with SynheartBehavior tag for visibility
                        android.util.Log.d(
                                "SynheartBehavior",
                                "MotionState[${i + 1}]: Prediction=$predictedState"
                        )
                    } catch (e: Exception) {
                        Log.e(
                                "MotionStateInference",
                                "ERROR predicting for data point ${i + 1}: ${e.message}",
                                e
                        )
                        states.add("unknown")
                        confidences.add(0.0)
                    }
                }

                // Calculate major state (most common)
                val stateCounts = states.groupingBy { it }.eachCount()
                val majorState = stateCounts.maxByOrNull { it.value }?.key ?: "unknown"
                val maxCount = stateCounts[majorState] ?: 0

                // Log state distribution summary
                Log.d("MotionStateInference", "=== STATE DISTRIBUTION SUMMARY ===")
                val stateSummary =
                        stateCounts
                                .map { (state, count) ->
                                    "$state: $count/${states.size} (${String.format("%.1f", count * 100.0 / states.size)}%)"
                                }
                                .joinToString(", ")
                android.util.Log.d(
                        "SynheartBehavior",
                        "MotionState Summary: $stateSummary → Major: $majorState"
                )
                stateCounts.forEach { (state, count) ->
                    Log.d(
                            "MotionStateInference",
                            "  $state: $count/${states.size} (${String.format("%.1f", count * 100.0 / states.size)}%)"
                    )
                }
                Log.d(
                        "MotionStateInference",
                        "  → Major state: $majorState (${String.format("%.1f", maxCount * 100.0 / states.size)}%)"
                )

                // Calculate percentage
                val majorStatePct =
                        if (states.isNotEmpty()) maxCount.toDouble() / states.size else 0.0

                // Use confidence from major state prediction directly (no calculations)
                // Get the confidence of the most common state from model outputs
                var confidence = 0.0
                if (confidences.isNotEmpty() && states.isNotEmpty()) {
                    // Find the confidence of the major state (most common prediction)
                    val majorStateIndices =
                            states.mapIndexedNotNull { index, state ->
                                if (state == majorState) index else null
                            }

                    if (majorStateIndices.isNotEmpty()) {
                        // Use the confidence from the first occurrence of major state
                        // (raw model output, no averaging or calculations)
                        val firstMajorStateIndex = majorStateIndices.first()
                        if (firstMajorStateIndex < confidences.size) {
                            confidence = confidences[firstMajorStateIndex]
                        }
                    } else {
                        // Fallback: use first confidence if major state not found
                        confidence = confidences.first()
                    }
                }

                return@withContext MotionState(
                        state = states,
                        majorState = majorState,
                        majorStatePct = majorStatePct,
                        mlModel = "motion_state_svc_classifier_v0.1",
                        confidence = confidence
                )
            }

    /** Dispose of resources. */
    fun dispose() {
        ortSession?.close()
        ortSession = null
        isLoaded = false
    }
}
