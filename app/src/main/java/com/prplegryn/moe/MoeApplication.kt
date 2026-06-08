package com.prplegryn.moe

import android.app.Application
import com.prplegryn.moe.data.local.MoeDatabase
import com.prplegryn.moe.data.repository.LibraryRepository
import com.prplegryn.moe.data.scraper.MetadataAggregator
import com.prplegryn.moe.data.scraper.R18DevScraper

class MoeApplication : Application() {
    lateinit var repository: LibraryRepository
        private set

    override fun onCreate() {
        super.onCreate()
        val database = MoeDatabase(this)
        val scrapers = listOf(
            R18DevScraper(),
        )
        repository = LibraryRepository(
            database = database,
            aggregator = MetadataAggregator(scrapers),
        )
    }
}
