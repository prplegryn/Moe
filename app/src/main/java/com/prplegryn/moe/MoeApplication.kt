package com.prplegryn.moe

import android.app.Application
import com.prplegryn.moe.data.local.MoeDatabase
import com.prplegryn.moe.data.repository.LibraryRepository
import com.prplegryn.moe.data.scraper.LibreDmmScraper
import com.prplegryn.moe.data.scraper.MetadataAggregator

class MoeApplication : Application() {
    lateinit var repository: LibraryRepository
        private set

    override fun onCreate() {
        super.onCreate()
        val database = MoeDatabase(this)
        val scrapers = listOf(
            LibreDmmScraper(),
        )
        repository = LibraryRepository(
            database = database,
            aggregator = MetadataAggregator(scrapers),
        )
    }
}
