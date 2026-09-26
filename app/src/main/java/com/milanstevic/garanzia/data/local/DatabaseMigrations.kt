package com.milanstevic.garanzia.data.local

import androidx.room.migration.Migration

/**
 * Registro autoritativo delle migrazioni Room.
 *
 * Non usare fallbackToDestructiveMigration: un aggiornamento dell'app
 * non deve mai cancellare gli scontrini dell'utente.
 *
 * Quando lo schema passa da N a N+1:
 * 1. aggiungere qui la Migration;
 * 2. aumentare GaranziaDatabase.SCHEMA_VERSION;
 * 3. conservare lo schema JSON generato da Room;
 * 4. aggiungere un test di migrazione strumentale.
 */
object DatabaseMigrations {
    val ALL: Array<Migration> = emptyArray()
}
