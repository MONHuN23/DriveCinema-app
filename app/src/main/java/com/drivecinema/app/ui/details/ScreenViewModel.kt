package com.drivecinema.app.ui.details

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore

/**
 * The [VM] held by [store], made by [create] the first time. `MainActivity` keeps Home's and search's ViewModels in
 * its own store this way, so a rotation keeps them (Task M.3). The details screens' ViewModels are entry-scoped
 * instead (Navigation 3's ViewModel-store decorator, `AppNavDisplay`); M.2's `rememberScreenViewModel` is gone.
 */
fun <VM : ViewModel> screenViewModel(store: ViewModelStore, type: Class<VM>, create: () -> VM): VM =
    ViewModelProvider(store, object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = create() as T
    })[type]
