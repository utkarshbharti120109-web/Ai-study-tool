package com.example.data.api

import android.content.Context
import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.vertexai.vertexAI
import com.google.firebase.vertexai.type.generationConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object FirebaseVertexAiClient {
    private const val TAG = "FirebaseVertexAi"

    fun initialize(context: Context, apiKey: String) {
        try {
            if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
                Log.w(TAG, "Empty or dummy API key provided. Skipping programmatic Firebase initialization.")
                return
            }

            // Check if FirebaseApp is already initialized
            val isAlreadyInitialized = try {
                FirebaseApp.getInstance()
                true
            } catch (e: Exception) {
                false
            }

            if (!isAlreadyInitialized) {
                val options = FirebaseOptions.Builder()
                    .setApplicationId("1:5842779885068:android:2tbovdh2ujyj24v24hvp58gd") // standard format dummy app ID
                    .setApiKey(apiKey)
                    .setProjectId("studybuddy-qyvtmx")
                    .build()
                FirebaseApp.initializeApp(context.applicationContext, options)
                Log.d(TAG, "Initialized Firebase programmatically successfully.")
            } else {
                Log.d(TAG, "FirebaseApp is already initialized.")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing Firebase programmatically: ${e.localizedMessage}")
        }
    }

    suspend fun generateContent(
        context: Context,
        apiKey: String,
        modelName: String,
        systemPrompt: String,
        prompt: String
    ): String = withContext(Dispatchers.IO) {
        // Ensure default FirebaseApp is initialized with our API key
        initialize(context, apiKey)

        // Instantiate GenerativeModel using Vertex AI for Firebase
        val model = Firebase.vertexAI.generativeModel(
            modelName = modelName,
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                temperature = 0.7f
            },
            systemInstruction = com.google.firebase.vertexai.type.content {
                text(systemPrompt)
            }
        )

        val response = model.generateContent(prompt)
        response.text ?: throw IllegalStateException("Firebase Vertex AI returned an empty response.")
    }
}
