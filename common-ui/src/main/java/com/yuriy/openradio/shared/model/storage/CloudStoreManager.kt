/*
 * Copyright 2023 The "Open Radio" Project. Author: Chernyshov Yuriy
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.yuriy.openradio.shared.model.storage

import android.app.Activity
import com.google.firebase.auth.FirebaseAuthRecentLoginRequiredException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.yuriy.openradio.shared.dependencies.DependencyRegistryCommonUi
import com.yuriy.openradio.shared.dependencies.StorageManagerDependency
import com.yuriy.openradio.shared.utils.AppLogger

class CloudStoreManager : StorageManagerDependency {

    private val mAuth = Firebase.auth
    private val mDb = Firebase.firestore
    private lateinit var mStorageManagerLayer: StorageManagerLayer

    init {
        DependencyRegistryCommonUi.injectStorageManagerLayer(this)
    }

    override fun configureWith(storageManagerLayer: StorageManagerLayer) {
        mStorageManagerLayer = storageManagerLayer
    }

    fun isUserExist(): Boolean {
        return getUser() != null
    }

    fun getUserEmail(): String {
        return getUser()?.email ?: "no email found"
    }

    fun signOut() {
        mAuth.signOut()
    }

    fun deleteAccount(
        onSuccess: () -> Unit,
        onFailure: () -> Unit,
        onInternalError: () -> Unit,
        onRecentLoginRequired: () -> Unit
    ) {
        getUser()?.let { user ->
            user.delete()
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        onSuccess()
                    } else {
                        onFailure()
                    }
                }
                .addOnFailureListener {
                    if (it is FirebaseAuthRecentLoginRequiredException) {
                        onRecentLoginRequired()
                    } else {
                        onFailure()
                    }
                }
        } ?: {
            onInternalError()
        }
    }

    fun sendPasswordReset(
        email: String,
        onSuccess: () -> Unit,
        onFailure: () -> Unit
    ) {
        if (email.isEmpty()) {
            onFailure()
            return
        }
        mAuth.sendPasswordResetEmail(email)
            .addOnSuccessListener {
                onSuccess()
            }
            .addOnFailureListener {
                onFailure()
            }
    }

    fun getToken(
        onSuccess: (token: String) -> Unit,
        onFailure: (msg: String) -> Unit
    ) {
        val user = getUser()
        if (user == null) {
            onFailure("User invalid")
            return
        }
        onSuccess(user.uid)
    }

    fun signIn(
        activity: Activity,
        email: String,
        password: String,
        onSuccess: (token: String) -> Unit,
        onFailure: (msg: String) -> Unit
    ) {
        if (email.isEmpty()) {
            return onFailure("Email can not be empty")
        }
        if (password.isEmpty()) {
            return onFailure("Password can not be empty")
        }
        mAuth.signInWithEmailAndPassword(email, password)
            .addOnCompleteListener(activity) { task ->
                if (task.isSuccessful) {
                    // Sign in success, update UI with the signed-in user's information
                    AppLogger.d("$TAG signInWithEmail:success")
                    getToken(onSuccess, onFailure)
                } else {
                    // If sign in fails, display a message to the user.
                    AppLogger.e("$TAG signInWithEmail:failure", task.exception)
                    onFailure("Task not successful")
                }
            }
    }

    fun download(
        token: String,
        onSuccess: () -> Unit,
        onFailure: () -> Unit
    ) {
        // Retrieve user data from Firestore
        val userRef = mDb.collection(COLLECTION_USERS).document(token)
        userRef.get()
            .addOnSuccessListener { documentSnapshot ->
                if (documentSnapshot.exists()) {
                    val user = documentSnapshot.toObject(UserData::class.java)
                    if (user != null) {
                        // Use the user data
                        onSuccess()
                        mStorageManagerLayer.mergeFavorites(user.favorites)
                        mStorageManagerLayer.mergeDeviceLocals(user.locals)
                    }
                } else {
                    // User not found
                    AppLogger.e("$TAG download user not found")
                    onFailure()
                }
            }
            .addOnFailureListener { e ->
                AppLogger.e("$TAG download", e)
                onFailure()
            }
    }

    /**
     * Empty constructor is needed for deserialization by Firestone SDK.
     */
    @Suppress("unused")
    private data class UserData(
        val favorites: String,
        val locals: String
    ) {
        constructor() : this("", "")
    }

    private fun getUser(): FirebaseUser? {
        return mAuth.currentUser
    }

    companion object {

        private const val TAG = "FSM"
        private const val COLLECTION_USERS = "users"
    }
}
