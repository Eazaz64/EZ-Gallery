package com.eazaz.fastgallery

import android.os.Bundle
import android.os.Environment
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import com.eazaz.fastgallery.ui.AppRoot

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { AppRoot(vm, this) }
    }

    override fun onResume() {
        super.onResume()
        vm.onResume(Environment.isExternalStorageManager())
    }
}
