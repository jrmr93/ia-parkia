package com.example.service

import android.content.Intent
import androidx.car.app.CarAppService
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.Session
import androidx.car.app.model.Action
import androidx.car.app.model.Pane
import androidx.car.app.model.PaneTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import androidx.car.app.validation.HostValidator
import com.example.data.AppDatabase
import com.example.data.ParkingConfig
import com.example.data.ParkingRepository
import kotlinx.coroutines.runBlocking

class ParkiaCarAppService : CarAppService() {

    override fun createHostValidator(): HostValidator {
        return HostValidator.ALLOW_ALL_HOSTS_VALIDATOR
    }

    override fun onCreateSession(): Session {
        return ParkiaCarSession()
    }
}

class ParkiaCarSession : Session() {
    override fun onCreateScreen(intent: Intent): Screen {
        return ParkiaCarScreen(carContext)
    }
}

class ParkiaCarScreen(carContext: CarContext) : Screen(carContext) {

    private val repository by lazy {
        ParkingRepository(
            AppDatabase.getDatabase(carContext.applicationContext).parkingDao()
        )
    }

    override fun onGetTemplate(): Template {
        val config: ParkingConfig = try {
            runBlocking {
                repository.catchUpSession()
                repository.getOrCreateConfig()
            }
        } catch (e: Exception) {
            ParkingConfig()
        }

        val availableTime = repository.calculateAvailableTime(
            config.balance,
            config.rateAmount,
            config.rateMinutes
        )

        val statusText = if (config.isSessionActive) {
            "🟢 Estacionamiento ACTIVO"
        } else {
            "⚪ Estacionamiento DETENIDO"
        }

        val isSessionActive = config.isSessionActive

        val paneBuilder = Pane.Builder()
            .addRow(
                Row.Builder()
                    .setTitle("Estado del Parquímetro")
                    .addText(statusText)
                    .build()
            )
            .addRow(
                Row.Builder()
                    .setTitle("Saldo Disponible")
                    .addText("\$${String.format("%.2f", config.balance)} ($availableTime)")
                    .build()
            )
            .addRow(
                Row.Builder()
                    .setTitle("Tarifa Configurada")
                    .addText("\$${String.format("%.2f", config.rateAmount)} por ${config.rateMinutes} min")
                    .build()
            )

        val actionTitle = if (isSessionActive) "Finalizar Estacionamiento" else "Iniciar Estacionamiento"

        paneBuilder.addAction(
            Action.Builder()
                .setTitle(actionTitle)
                .setOnClickListener {
                    runBlocking {
                        val current = repository.getOrCreateConfig()
                        if (current.isSessionActive) {
                            repository.stopSession(reason = "Finalizado desde Android Auto")
                            ParkiaForegroundService.stop(carContext.applicationContext)
                        } else {
                            val success = repository.startSession()
                            if (success) {
                                ParkiaForegroundService.startOrUpdate(carContext.applicationContext, forceUpdate = true)
                            }
                        }
                    }
                    invalidate()
                }
                .build()
        )

        return PaneTemplate.Builder(paneBuilder.build())
            .setTitle("Parkia Auto")
            .setHeaderAction(Action.APP_ICON)
            .build()
    }
}
