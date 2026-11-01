package io.github.albertrzad18.moneytracker.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [
        Instrument::class,
        Lot::class,
        Quote::class,
        PricePoint::class,
        FxRate::class,
        SeriesMeta::class,
    ],
    version = 1,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun portfolioDao(): PortfolioDao

    abstract fun historyDao(): HistoryDao
}
