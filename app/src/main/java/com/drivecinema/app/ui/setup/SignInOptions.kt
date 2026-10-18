package com.drivecinema.app.ui.setup

/** The sign-in screen button that takes the initial (remote) focus. */
enum class SignInFocusTarget { GOOGLE, PAIRING, GUIDE, NONE }

/** Which sign-in actions the sign-in screen offers on this device. */
data class SignInOptions(
    val showGoogleSignIn: Boolean,
    /** Pairing is the first, highlighted action (it gets the initial focus). */
    val pairingIsPrimary: Boolean,
    /** Short explanation shown above the buttons, or null. */
    val notice: String?
) {
    /** First button actually shown: a FocusRequester must be attached to a visible button to request focus. */
    fun initialFocus(hasPairing: Boolean, hasGuide: Boolean): SignInFocusTarget = when {
        showGoogleSignIn -> SignInFocusTarget.GOOGLE
        hasPairing -> SignInFocusTarget.PAIRING
        hasGuide -> SignInFocusTarget.GUIDE
        else -> SignInFocusTarget.NONE
    }

    companion object {
        const val NO_PLAY_SERVICES_NOTICE =
            "This TV has no Google Play Services, so Google sign-in is not available here. " +
                "Sign in on your phone, then pair it with this TV."

        /**
         * Phones sign in through the browser (AppAuth) and never need Play Services. TVs sign in through
         * Play Services; without it (Fire TV, generic boxes) pairing with the phone is the only way in.
         */
        fun forDevice(isTv: Boolean, playServicesAvailable: Boolean): SignInOptions =
            if (isTv && !playServicesAvailable) {
                SignInOptions(showGoogleSignIn = false, pairingIsPrimary = true, notice = NO_PLAY_SERVICES_NOTICE)
            } else {
                SignInOptions(showGoogleSignIn = true, pairingIsPrimary = false, notice = null)
            }
    }
}
