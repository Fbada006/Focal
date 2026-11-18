package io.disruption.focal.data.di

import io.disruption.focal.core.apps.AppCategoryResolver
import io.disruption.focal.core.repository.AppPreferences
import io.disruption.focal.core.repository.BlockListRepository
import io.disruption.focal.core.repository.FocusSessionRepository
import io.disruption.focal.core.repository.ScheduleRepository
import io.disruption.focal.core.website.SuggestedWebsitesCatalog
import io.disruption.focal.data.curated.CuratedAppCategories
import io.disruption.focal.data.curated.CuratedSuggestedWebsites
import io.disruption.focal.data.db.FocalDatabase
import io.disruption.focal.data.preferences.DataStoreFocalPreferences
import io.disruption.focal.data.repository.RoomBlockListRepository
import io.disruption.focal.data.repository.RoomFocusSessionRepository
import io.disruption.focal.data.repository.RoomScheduleRepository
import org.koin.core.module.Module
import org.koin.dsl.module

/** Provides the [FocalDatabase] and the preferences `DataStore` for the platform. */
expect fun platformDataModule(): Module

val dataModule: Module = module {
    includes(platformDataModule())
    single { get<FocalDatabase>().blockListDao() }
    single { get<FocalDatabase>().focusSessionDao() }
    single { get<FocalDatabase>().scheduleDao() }
    single<BlockListRepository> { RoomBlockListRepository(get()) }
    single<FocusSessionRepository> { RoomFocusSessionRepository(get()) }
    single<ScheduleRepository> { RoomScheduleRepository(get()) }
    single<AppPreferences> { DataStoreFocalPreferences(get()) }
    single<SuggestedWebsitesCatalog> { CuratedSuggestedWebsites }
    single { AppCategoryResolver(CuratedAppCategories.byPackage) }
}
