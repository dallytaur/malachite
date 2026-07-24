package com.example

import android.app.Activity
import android.content.Context
import android.util.Log
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetDigitalCredentialOption
import androidx.credentials.DigitalCredential
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.security.SecureRandom
import java.util.Base64

import androidx.credentials.ExperimentalDigitalCredentialApi

class IdentityManager(private val context: Context) {
    private val credentialManager = CredentialManager.create(context)

    @OptIn(ExperimentalDigitalCredentialApi::class)
    suspend fun requestVerifiedEmail(activity: Activity): Boolean {
        return withContext(Dispatchers.Main) {
            try {
                val nonce = generateSecureRandomNonce()
                val openId4vpRequest = """
                {
                  "requests": [
                    {
                      "protocol": "openid4vp-v1-unsigned",
                      "data": {
                        "response_type": "vp_token",
                        "response_mode": "dc_api",
                        "nonce": "$nonce",
                        "dcql_query": {
                          "credentials": [
                            {
                              "id": "user_info_query",
                              "format": "dc+sd-jwt",
                               "meta": { 
                                  "vct_values": ["UserInfoCredential"] 
                               },
                              "claims": [ 
                                {"path": ["email"]}, 
                                {"path": ["name"]},  
                                {"path": ["given_name"]},
                                {"path": ["family_name"]},
                                {"path": ["picture"]},
                                {"path": ["hd"]},
                                {"path": ["email_verified"]}
                              ]
                            }
                          ]
                        }
                      }
                    }
                  ]
                }
                """

                val getDigitalCredentialOption = GetDigitalCredentialOption(requestJson = openId4vpRequest)
                val request = GetCredentialRequest(listOf(getDigitalCredentialOption))
                Log.d("IdentityManager", "Triggering getCredential...")

                val result = credentialManager.getCredential(activity, request)
                Log.d("IdentityManager", "Received result: ${result.credential.type}")

                when (val credential = result.credential) {
                    is DigitalCredential -> {
                        val responseJsonString = credential.credentialJson
                        Log.d("IdentityManager", "Response JSON: $responseJsonString")
                        
                        try {
                            val responseData = JSONObject(responseJsonString)
                            val vpToken = responseData.getJSONObject("vp_token")
                            val credentialId = vpToken.keys().next()
                            val rawSdJwt = vpToken.getJSONArray(credentialId).getString(0)

                            val claims = parseSdJwtClaimsLocally(rawSdJwt)
                            Log.d("IdentityManager", "Parsed Claims: $claims")
                            
                            BrowserState.userProfile = UserProfile(
                                email = claims.optString("email", "Unknown"),
                                displayName = claims.optString("name", claims.optString("given_name", "User"))
                            )
                            return@withContext true
                        } catch (e: Exception) {
                            Log.e("IdentityManager", "Parsing error", e)
                            withContext(Dispatchers.Main) {
                                android.widget.Toast.makeText(context, "Identity parsing failed", android.widget.Toast.LENGTH_SHORT).show()
                            }
                            return@withContext false
                        }
                    }
                    else -> {
                        Log.w("IdentityManager", "Unexpected credential type")
                        return@withContext false
                    }
                }
            } catch (e: Exception) {
                Log.e("IdentityManager", "Error requesting verified email", e)
                return@withContext false
            }
        }
    }

    private fun generateSecureRandomNonce(): String {
        val bytes = ByteArray(32)
        SecureRandom().nextBytes(bytes)
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
        } else {
            android.util.Base64.encodeToString(bytes, android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP or android.util.Base64.NO_PADDING)
        }
    }

    private fun parseSdJwtClaimsLocally(sdJwt: String): JSONObject {
        // SD-JWT format: Issuer JWT ~ Disclosure 1 ~ ... ~ Key Binding JWT
        // We only need the claims from the Issuer JWT part (the first part)
        val parts = sdJwt.split("~")
        val issuerJwt = parts[0]
        val jwtParts = issuerJwt.split(".")
        if (jwtParts.size < 2) return JSONObject()
        
        val payloadBase64 = jwtParts[1]
        val payloadJson = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            String(Base64.getUrlDecoder().decode(payloadBase64))
        } else {
            String(android.util.Base64.decode(payloadBase64, android.util.Base64.URL_SAFE))
        }
        return JSONObject(payloadJson)
    }
}
