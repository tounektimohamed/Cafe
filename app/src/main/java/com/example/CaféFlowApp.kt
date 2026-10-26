package com.example

import android.app.Application
import com.example.core.backup.BackupRestoreManager
import com.example.core.data.AppDatabase
import com.example.core.license.LicenseManager
import com.example.core.network.LocalNetworkManager
import com.example.core.printer.PosPrinterManager
import com.example.core.repository.PosRepository

class CaféFlowApp : Application() {

    lateinit var database: AppDatabase
        private set
    lateinit var networkManager: LocalNetworkManager
        private set
    lateinit var printerManager: PosPrinterManager
        private set
    lateinit var licenseManager: LicenseManager
        private set
    lateinit var backupRestoreManager: BackupRestoreManager
        private set
    lateinit var repository: PosRepository
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this

        database = AppDatabase.getDatabase(this)
        networkManager = LocalNetworkManager(this)
        printerManager = PosPrinterManager(this)
        licenseManager = LicenseManager(this)
        backupRestoreManager = BackupRestoreManager(this, database)
        repository = PosRepository(this, database, networkManager, printerManager, licenseManager, backupRestoreManager)
    }

    companion object {
        lateinit var instance: CaféFlowApp
            private set
    }
}
