package com.simplemobiletools.voicerecorder.activities

import android.content.Intent
import android.media.MediaRecorder
import android.os.Bundle
import com.simplemobiletools.commons.dialogs.*
import com.simplemobiletools.commons.extensions.*
import com.simplemobiletools.commons.helpers.*
import com.simplemobiletools.commons.models.RadioItem
import com.simplemobiletools.voicerecorder.R
import com.simplemobiletools.voicerecorder.extensions.config
import com.simplemobiletools.voicerecorder.extensions.emptyTheRecycleBin
import com.simplemobiletools.voicerecorder.extensions.getAllRecordings
import com.simplemobiletools.voicerecorder.helpers.BITRATES
import com.simplemobiletools.voicerecorder.helpers.EXTENSION_M4A
import com.simplemobiletools.voicerecorder.helpers.EXTENSION_MP3
import com.simplemobiletools.voicerecorder.helpers.EXTENSION_OGG
import com.simplemobiletools.voicerecorder.models.Events
import kotlinx.android.synthetic.main.activity_settings.*
import org.greenrobot.eventbus.EventBus
import java.util.Locale
import kotlin.system.exitProcess

class SettingsActivity : SimpleActivity() {
    private var recycleBinContentSize = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        isMaterialActivity = true
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        updateMaterialActivityViews(settings_coordinator, settings_holder, useTransparentNavigation = true, useTopSearchMenu = false)
        setupMaterialScrollListener(settings_nested_scrollview, settings_toolbar)
    }

    override fun onResume() {
        super.onResume()
        setupToolbar(settings_toolbar, NavigationIcon.Arrow)

        setupPurchaseThankYou()
        setupCustomizeColors()
        setupCustomizeWidgetColors()
        setupUseEnglish()
        setupLanguage()
        setupChangeDateTimeFormat()
        setupHideNotification()
        setupSaveRecordingsFolder()
        setupExtension()
        setupBitrate()
        setupAudioSource()
        setupRecordAfterLaunch()
        setupUseRecycleBin()
        setupEmptyRecycleBin()
        updateTextColors(settings_nested_scrollview)

        arrayOf(settings_color_customization_section_label, settings_general_settings_label, settings_recycle_bin_label).forEach {
            it.setTextColor(getProperPrimaryColor())
        }
    }

    private fun setupPurchaseThankYou() {
        settings_purchase_thank_you_holder.beGoneIf(isOrWasThankYouInstalled())
        settings_purchase_thank_you_holder.setOnClickListener {
            launchPurchaseThankYouIntent()
        }
    }

    private fun setupCustomizeColors() {
        settings_color_customization_label.text = getCustomizeColorsString()
        settings_color_customization_holder.setOnClickListener {
            handleCustomizeColorsClick()
        }
    }

    private fun setupCustomizeWidgetColors() {
        settings_widget_color_customization_holder.setOnClickListener {
            Intent(this, WidgetRecordDisplayConfigureActivity::class.java).apply {
                putExtra(IS_CUSTOMIZING_COLORS, true)
                startActivity(this)
            }
        }
    }

    private fun setupUseEnglish() {
        settings_use_english_holder.beVisibleIf((config.wasUseEnglishToggled || Locale.getDefault().language != "en") && !isTiramisuPlus())
        settings_use_english.isChecked = config.useEnglish
        settings_use_english_holder.setOnClickListener {
            settings_use_english.toggle()
            config.useEnglish = settings_use_english.isChecked
            exitProcess(0)
        }
    }

    private fun setupLanguage() {
        settings_language.text = Locale.getDefault().displayLanguage
        settings_language_holder.beVisibleIf(isTiramisuPlus())
        settings_language_holder.setOnClickListener {
            launchChangeAppLanguageIntent()
        }
    }

    private fun setupChangeDateTimeFormat() {
        settings_change_date_time_format_holder.setOnClickListener {
            ChangeDateTimeFormatDialog(this) {}
        }
    }

    private fun setupHideNotification() {
        settings_hide_notification.isChecked = config.hideNotification
        settings_hide_notification_holder.setOnClickListener {
            settings_hide_notification.toggle()
            config.hideNotification = settings_hide_notification.isChecked
        }
    }

    private fun setupSaveRecordingsFolder() {
        settings_save_recordings_label.text = addLockedLabelIfNeeded(R.string.save_recordings_in)
        settings_save_recordings.text = humanizePath(config.saveRecordingsFolder)
        settings_save_recordings_holder.setOnClickListener {
            if (isOrWasThankYouInstalled()) {
                FilePickerDialog(this, config.saveRecordingsFolder, false, showFAB = true) {
                    val path = it
                    handleSAFDialog(path) { grantedSAF ->
                        if (!grantedSAF) {
                            return@handleSAFDialog
                        }

                        handleSAFDialogSdk30(path) { grantedSAF30 ->
                            if (!grantedSAF30) {
                                return@handleSAFDialogSdk30
                            }

                            config.saveRecordingsFolder = path
                            settings_save_recordings.text = humanizePath(config.saveRecordingsFolder)
                        }
                    }
                }
            } else {
                FeatureLockedDialog(this) { }
            }
        }
    }

    private fun setupExtension() {
        settings_extension.text = config.getExtensionText()
        settings_extension_holder.setOnClickListener {
            val items = arrayListOf(
                RadioItem(EXTENSION_M4A, getString(R.string.m4a)),
                RadioItem(EXTENSION_MP3, getString(R.string.mp3))
            )

            if (isQPlus()) {
                items.add(RadioItem(EXTENSION_OGG, getString(R.string.ogg_opus)))
            }

            RadioGroupDialog(this@SettingsActivity, items, config.extension) {
                config.extension = it as Int
                settings_extension.text = config.getExtensionText()
            }
        }
    }

    private fun setupBitrate() {
        settings_bitrate.text = getBitrateText(config.bitrate)
        settings_bitrate_holder.setOnClickListener {
            val items = BITRATES.map { RadioItem(it, getBitrateText(it)) } as ArrayList

            RadioGroupDialog(this@SettingsActivity, items, config.bitrate) {
                config.bitrate = it as Int
                settings_bitrate.text = getBitrateText(config.bitrate)
            }
        }
    }

    private fun getBitrateText(value: Int): String = getString(R.string.bitrate_value).format(value / 1000)

    private fun setupRecordAfterLaunch() {
        settings_record_after_launch.isChecked = config.recordAfterLaunch
        settings_record_after_launch_holder.setOnClickListener {
            settings_record_after_launch.toggle()
            config.recordAfterLaunch = settings_record_after_launch.isChecked
        }
    }

    private fun setupUseRecycleBin() {
        updateRecycleBinButtons()
        settings_use_recycle_bin.isChecked = config.useRecycleBin
        settings_use_recycle_bin_holder.setOnClickListener {
            settings_use_recycle_bin.toggle()
            config.useRecycleBin = settings_use_recycle_bin.isChecked
            updateRecycleBinButtons()
        }
    }

    private fun updateRecycleBinButtons() {
        settings_empty_recycle_bin_holder.beVisibleIf(config.useRecycleBin)
    }

    private fun setupEmptyRecycleBin() {
        ensureBackgroundThread {
            try {
                recycleBinContentSize = getAllRecordings(trashed = true).sumByInt {
                    it.size
                }
            } catch (ignored: Exception) {
            }

            runOnUiThread {
                settings_empty_recycle_bin_size.text = recycleBinContentSize.formatSize()
            }
        }

        settings_empty_recycle_bin_holder.setOnClickListener {
            if (recycleBinContentSize == 0) {
                toast(R.string.recycle_bin_empty)
            } else {
                ConfirmationDialog(this, "", R.string.empty_recycle_bin_confirmation, R.string.yes, R.string.no) {
                    emptyTheRecycleBin()
                    recycleBinContentSize = 0
                    settings_empty_recycle_bin_size.text = 0.formatSize()
                    EventBus.getDefault().post(Events.RecordingTrashUpdated())
                }
            }
        }
    }

    private fun setupAudioSource() {
        settings_audio_source.text = config.getAudioSourceText(config.audioSource)
        settings_audio_source_holder.setOnClickListener {
            val items = getAudioSources().map { RadioItem(it, config.getAudioSourceText(it)) } as ArrayList

            RadioGroupDialog(this@SettingsActivity, items, config.audioSource) {
                config.audioSource = it as Int
                settings_audio_source.text = config.getAudioSourceText(config.audioSource)
            }
        }
    }

    private fun getAudioSources(): ArrayList<Int> {
        val availableSources = arrayListOf(
            MediaRecorder.AudioSource.CAMCORDER,
            MediaRecorder.AudioSource.DEFAULT,
            MediaRecorder.AudioSource.MIC,
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            MediaRecorder.AudioSource.VOICE_COMMUNICATION
        )

        if (isNougatPlus()) {
            availableSources.add(MediaRecorder.AudioSource.UNPROCESSED)
        }

        if (isQPlus()) {
            availableSources.add(MediaRecorder.AudioSource.VOICE_PERFORMANCE)
        }

        return availableSources
    }
}
