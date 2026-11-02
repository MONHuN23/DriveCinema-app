package com.drivecinema.app.ui.design

/** Design §3.4: one primary action per screen; secondary is tonal; tertiary and destructive are text only. */
enum class DcButtonKind { Primary, Secondary, Tertiary, Destructive }

/** Info is neutral (tips such as the Windows network prompt): brand red is never used for information. */
enum class DcBannerKind { Info, Warning, Error }

/** Success: connected, allowed. Warning: not reachable. Neutral: not checked yet, cancelled. Brand: in progress. */
enum class DcStatusKind { Success, Warning, Neutral, Brand }
