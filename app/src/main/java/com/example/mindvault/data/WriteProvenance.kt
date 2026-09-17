package com.example.mindvault.data

/** Upload readiness is NOT identity: startup can authenticate while uploads remain paused. */
internal object WriteProvenance {
    fun principal(signedOut: Boolean, firebaseUid: String?, firebaseEmail: String?, localEmail: String?): String? =
        if (!signedOut && firebaseUid != null && !firebaseEmail.isNullOrBlank() &&
            firebaseEmail.equals(localEmail, ignoreCase = true)
        ) firebaseUid else null

    fun afterWrite(token: String, principalUid: String?): String = when {
        token == DeviceDataOwnership.UNOWNED -> token
        principalUid != null && token == DeviceDataOwnership.token(principalUid) -> token
        else -> DeviceDataOwnership.UNKNOWN
    }
}
