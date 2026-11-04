package io.github.albertrzad18.moneytracker.data.repo

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.albertrzad18.moneytracker.data.db.AppDatabase
import io.github.albertrzad18.moneytracker.domain.Backup
import io.github.albertrzad18.moneytracker.domain.InvalidBackupException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton

data class ImportResult(val added: Int, val skipped: Int)

/** Export and import of all purchases as a JSON file chosen by the user. */
@Singleton
class BackupRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val db: AppDatabase,
    private val clock: Clock,
) {
    private val dao = db.portfolioDao()

    /** Writes every instrument and purchase to [uri]; returns the number of purchases. */
    suspend fun export(uri: Uri): Int {
        val instruments = dao.getInstruments()
        val lots = dao.getLots()
        val text = Backup.encode(instruments, lots, clock.instant())
        withContext(Dispatchers.IO) {
            val stream = context.contentResolver.openOutputStream(uri, "wt") ?: throw IOException("Cannot open $uri")
            stream.bufferedWriter().use { it.write(text) }
        }
        return lots.size
    }

    /**
     * Adds the purchases from [uri] to the portfolio. Instruments are matched by provider id and
     * purchases already present are skipped, so importing the same file again changes nothing.
     */
    @Throws(IOException::class, InvalidBackupException::class)
    suspend fun import(uri: Uri): ImportResult {
        val text = withContext(Dispatchers.IO) {
            val stream = context.contentResolver.openInputStream(uri) ?: throw IOException("Cannot open $uri")
            stream.bufferedReader().use { it.readText() }
        }
        val imported = Backup.decode(text)
        var added = 0
        var skipped = 0
        db.withTransaction {
            for ((instrument, lots) in imported) {
                if (lots.isEmpty()) continue
                val existing = dao.findInstrument(instrument.kind, instrument.providerId)
                val instrumentId = existing?.id
                    ?: dao.insertInstrument(instrument.copy(sortOrder = dao.maxSortOrder() + 1))
                // Each existing purchase matches at most one from the file, so two identical
                // purchases made on the same day both survive an import.
                val unmatched = dao.getLotsForInstrument(instrumentId).toMutableList()
                for (lot in lots) {
                    val match = unmatched.indexOfFirst { Backup.isSamePurchase(it, lot) }
                    if (match >= 0) {
                        unmatched.removeAt(match)
                        skipped++
                    } else {
                        dao.insertLot(lot.copy(instrumentId = instrumentId))
                        added++
                    }
                }
            }
        }
        return ImportResult(added, skipped)
    }
}
