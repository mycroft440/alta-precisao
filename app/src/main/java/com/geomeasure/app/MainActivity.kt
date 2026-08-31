package com.geomeasure.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.viewmodel.compose.viewModel
import com.geomeasure.app.ui.GeoMeasureApp
import com.geomeasure.app.ui.SurveyViewModel
import com.geomeasure.app.ui.theme.GeoMeasureTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            GeoMeasureTheme {
                val surveyViewModel: SurveyViewModel = viewModel()
                GeoMeasureApp(surveyViewModel)
            }
        }
    }
}
