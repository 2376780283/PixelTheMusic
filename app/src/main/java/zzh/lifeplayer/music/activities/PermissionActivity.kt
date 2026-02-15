package zzh.lifeplayer.music.activities

import android.Manifest.permission.BLUETOOTH_CONNECT
import android.app.AlarmManager
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.OnBackPressedCallback
import androidx.annotation.RequiresApi
import androidx.core.app.ActivityCompat
import androidx.core.content.getSystemService
import androidx.core.view.isVisible
import zzh.lifeplayer.appthemehelper.util.VersionUtils
import zzh.lifeplayer.music.R
import zzh.lifeplayer.music.activities.base.AbsMusicServiceActivity
import zzh.lifeplayer.music.databinding.ActivityPermissionBinding
import zzh.lifeplayer.music.extensions.*

class PermissionActivity : AbsMusicServiceActivity() {
    private lateinit var binding: ActivityPermissionBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPermissionBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setStatusBarColorAuto()
        setTaskDescriptionColorAuto()
        setupTitle()

        binding.storagePermission.setButtonClick { requestPermissions() }

        if (VersionUtils.hasS()) {
            binding.bluetoothPermission.show()
            binding.bluetoothPermission.setButtonClick {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(BLUETOOTH_CONNECT),
                    BLUETOOTH_PERMISSION_REQUEST,
                )
            }
            binding.alarmPermission.show()
            binding.alarmPermission.setButtonClick {
                val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                startActivity(intent)
            }
        } else {
            binding.audioPermission.setNumber("2")
        }

        binding.finish.accentBackgroundColor()
        binding.finish.setOnClickListener {
            if (hasPermissions()) {
                startActivity(
                    Intent(this, MainActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                )
                finish()
            }
        }
        onBackPressedDispatcher.addCallback(
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    finishAffinity()
                    remove()
                }
            }
        )
    }

    private fun setupTitle() {
        val appName = getString(R.string.message_welcome, "PixelMusic")
        binding.appNameText.text = appName
    }

    override fun onResume() {
        super.onResume()
        binding.finish.isEnabled = hasStoragePermission()
        if (hasStoragePermission()) {
            binding.storagePermission.checkImage.isVisible = true
            binding.storagePermission.checkImage.imageTintList =
                ColorStateList.valueOf(accentColor())
        }
        if (VersionUtils.hasS()) {
            if (hasBluetoothPermission()) {
                binding.bluetoothPermission.checkImage.isVisible = true
                binding.bluetoothPermission.checkImage.imageTintList =
                    ColorStateList.valueOf(accentColor())
            }
            if (hasAlarmPermission()) {
                binding.alarmPermission.checkImage.isVisible = true
                binding.alarmPermission.checkImage.imageTintList =
                    ColorStateList.valueOf(accentColor())
            }
        }
    }

    private fun hasStoragePermission(): Boolean {
        return hasPermissions()
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun hasBluetoothPermission(): Boolean {
        return ActivityCompat.checkSelfPermission(this, BLUETOOTH_CONNECT) ==
            PackageManager.PERMISSION_GRANTED
    }

    private fun hasAudioPermission(): Boolean {
        return Settings.System.canWrite(this)
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun hasAlarmPermission(): Boolean {
        return this.getSystemService<AlarmManager>()?.canScheduleExactAlarms() ?: false
    }
}
