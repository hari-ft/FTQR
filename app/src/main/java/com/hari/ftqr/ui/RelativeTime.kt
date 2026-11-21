package com.hari.ftqr.ui

import android.text.format.DateUtils

private const val JUST_NOW_MILLIS = 60_000L

fun relativeTime(millis: Long, now: Long = System.currentTimeMillis()): String =
    if (now - millis < JUST_NOW_MILLIS) {
        "Just now"
    } else {
        DateUtils.getRelativeTimeSpanString(millis, now, DateUtils.MINUTE_IN_MILLIS).toString()
    }
