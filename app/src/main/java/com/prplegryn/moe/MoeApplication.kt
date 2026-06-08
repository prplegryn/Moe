package com.prplegryn.moe

import android.app.Application
import com.prplegryn.moe.data.local.MoeDatabase
import com.prplegryn.moe.data.repository.LibraryRepository

class MoeApplication : Application() {
    lateinit var repository: LibraryRepository
        private set

    override fun onCreate() {
        super.onCreate()
        val database = MoeDatabase(this)
        repository = LibraryRepository(database = database)
    }
}
