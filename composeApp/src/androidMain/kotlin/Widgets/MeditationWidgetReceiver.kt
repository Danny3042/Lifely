package org.danielramzani.HealthCompose

import widgets.MindfulWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver

// Kept under its old name so widgets already on someone's home screen carry on working
class MeditationWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget = MindfulWidget()
}